package com.example.data.firebase

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.content
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout

private const val AI_TAG = "KgAiRouteAssistant"

sealed interface AiStreamEvent {
    data class Chunk(val accumulatedText: String) : AiStreamEvent
    data class Complete(val fullText: String) : AiStreamEvent
    data class Error(val message: String) : AiStreamEvent
}

class AiRouteAssistant {
    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            systemInstruction = content {
                text(
                    """
                    Ты — встроенный ИИ-помощник приложения KG Taxi по планированию маршрутов и поездок по Кыргызстану.
                    Помогай пользователям на том языке, на котором они пишут (Русский, Кыргызча или English).
                    Отвечай лаконично, структурированно и практично:
                    - Знаешь все города Кыргызстана (Бишкек, Ош, Чолпон-Ата, Каракол, Джалал-Абад, Талас, Нарын, Баткен), аэропорты Манас (FRU) и Ош (OSS), перевалы Тоо-Ашуу и Боомское ущелье, экскурсии (Ала-Арча, Башня Бурана, Чункурчак, Иссык-Куль) и популярные заведения (Navat, Faiza, Adriano Coffee).
                    - Оценивай примерное время в пути, километраж, ориентировочную стоимость в сомах (KGS) и рекомендуемый тариф KG Taxi (Эконом, Комфорт, Бизнес, Минивэн, Леди-такси, Green EV, Почасовая 650 сом/ч или Межгород).
                    - Давай советы по погоде, трафику и расходу топлива при необходимости.
                    """.trimIndent()
                )
            }
        )
    }

    fun streamRouteAdvice(
        userPrompt: String,
        cityContext: String,
        languageCode: String
    ): Flow<AiStreamEvent> = flow {
        val fullPrompt = "Текущий город: $cityContext. Язык интерфейса: $languageCode.\nЗапрос пользователя: $userPrompt"
        val accumulated = StringBuilder()
        try {
            withTimeout(60_000L) {
                model.generateContentStream(fullPrompt).collect { chunk ->
                    val piece = chunk.text.orEmpty()
                    if (piece.isNotEmpty()) {
                        accumulated.append(piece)
                        emit(AiStreamEvent.Chunk(accumulated.toString()))
                    }
                }
            }
            if (accumulated.isNotBlank()) {
                emit(AiStreamEvent.Complete(accumulated.toString()))
            } else {
                emit(AiStreamEvent.Error("Модель вернула пустой ответ. Попробуйте переформулировать запрос."))
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(AI_TAG, "Gemini route stream timed out", e)
            emit(AiStreamEvent.Error("Истекло время ожидания ответа (60 сек). Попробуйте ещё раз."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(AI_TAG, "Gemini route generation failed", e)
            emit(AiStreamEvent.Error(e.localizedMessage ?: "Ошибка связи с ИИ-ассистентом."))
        }
    }

    /**
     * Translates short passenger/driver chat messages seamlessly between RU, KG, and EN
     * using Firebase AI Logic (Gemini 3.8 Flash), with an instant phrase fallback when offline.
     */
    suspend fun translateChatMessage(
        text: String,
        targetLangCode: String,
        offlineMode: Boolean
    ): String {
        val langName = when (targetLangCode.uppercase()) {
            "KG" -> "Кыргызча (Kyrgyz)"
            "EN" -> "English"
            else -> "Русский (Russian)"
        }
        if (!offlineMode) {
            try {
                val prompt = "Переведи это короткое сообщение из чата такси на язык $langName. " +
                    "Верни ТОЛЬКО перевод без кавычек и пояснений:\n$text"
                val resp = withTimeout(15_000L) { model.generateContent(prompt) }
                val out = resp.text?.trim()
                if (!out.isNullOrBlank()) return out
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(AI_TAG, "Chat translation fallback used: ${e.message}")
            }
        }
        return offlinePhraseTranslate(text, targetLangCode)
    }

    /**
     * Parses natural-language voice taxi commands (RU / KG / EN) into structured pickup,
     * dropoff, tariff, and ride options using Gemini 3.8 Flash with an instant offline parser fallback.
     */
    suspend fun parseVoiceOrderCommand(
        transcript: String,
        currentCity: String,
        offlineMode: Boolean
    ): com.example.data.model.VoiceOrderIntent {
        val localParsed = parseVoiceOrderOffline(transcript)
        if (offlineMode) return localParsed

        return try {
            val prompt = """
                Разбери голосовую команду для заказа такси в Кыргызстане (город: $currentCity).
                Верни ответ СТРОГО в 6 строках формата КЛЮЧ=ЗНАЧЕНИЕ (пусто если не указано):
                PICKUP=<адрес или ориентир отправления>
                DROPOFF=<адрес или ориентир назначения>
                TARIFF=<economy|comfort|business|minivan|lady|green_ev|sober_driver>
                QUIET=<true|false>
                AC=<true|false>
                LUGGAGE=<true|false>
                Команда пользователя: "$transcript"
            """.trimIndent()
            val resp = withTimeout(12_000L) { model.generateContent(prompt) }
            val text = resp.text.orEmpty()
            fun field(key: String): String? = Regex("""(?m)^$key\s*=\s*(.+)$""")
                .find(text)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() && !it.equals("none", true) && !it.equals("null", true) }

            val pickup = field("PICKUP") ?: localParsed.pickupQuery
            val dropoff = field("DROPOFF") ?: localParsed.dropoffQuery
            val tariff = field("TARIFF")?.lowercase()?.takeIf {
                it in setOf("economy", "comfort", "business", "minivan", "lady", "green_ev", "sober_driver")
            } ?: localParsed.tariffId
            val quiet = field("QUIET")?.toBooleanStrictOrNull() ?: localParsed.quietRide
            val ac = field("AC")?.toBooleanStrictOrNull() ?: localParsed.climateAc
            val luggage = field("LUGGAGE")?.toBooleanStrictOrNull() ?: localParsed.luggageHelp

            val summaryParts = buildList {
                if (!pickup.isNullOrBlank()) add("от: $pickup")
                if (!dropoff.isNullOrBlank()) add("куда: $dropoff")
                if (!tariff.isNullOrBlank()) add("тариф: $tariff")
                if (quiet == true) add("тихая поездка")
                if (ac == true) add("кондиционер")
                if (luggage == true) add("багаж")
            }
            com.example.data.model.VoiceOrderIntent(
                rawTranscript = transcript,
                pickupQuery = pickup,
                dropoffQuery = dropoff,
                tariffId = tariff,
                quietRide = quiet,
                climateAc = ac,
                luggageHelp = luggage,
                petFriendly = localParsed.petFriendly,
                businessTrip = localParsed.businessTrip,
                summary = if (summaryParts.isNotEmpty()) "ИИ распознал: ${summaryParts.joinToString(" • ")}" else localParsed.summary
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            localParsed
        }
    }

    companion object {
        fun parseVoiceOrderOffline(transcript: String): com.example.data.model.VoiceOrderIntent {
            val lower = transcript.lowercase()
            val tariff = when {
                "трезв" in lower || "перегон" in lower -> "sober_driver"
                "леди" in lower || "женщин" in lower || "lady" in lower -> "lady"
                "электро" in lower || "green" in lower || "эко" in lower && "эконом" !in lower -> "green_ev"
                "минивэн" in lower || "minivan" in lower || "6 мест" in lower -> "minivan"
                "бизнес" in lower || "business" in lower -> "business"
                "комфорт" in lower || "comfort" in lower -> "comfort"
                "эконом" in lower || "economy" in lower -> "economy"
                else -> null
            }

            val knownPlaces = listOf(
                "манас" to "Аэропорт Манас (FRU)",
                "аэропорт ош" to "Аэропорт Ош (OSS)",
                "цум" to "ЦУМ Айчурек",
                "asia mall" to "ТРЦ Asia Mall",
                "азия молл" to "ТРЦ Asia Mall",
                "ала-тоо" to "Площадь Ала-Тоо",
                "ошский" to "Ошский базар",
                "дордой" to "Dordoi Plaza",
                "гум" to "ГУМ Чынар",
                "сулайман" to "Сулайман-Тоо",
                "рух ордо" to "Рух Ордо",
                "ала-арча" to "Ущелье Ала-Арча",
                "нават" to "Navat"
            )

            // Detect "с/от <pickup> в/до/на <dropoff>"
            var pickup: String? = null
            var dropoff: String? = null
            val fromMatch = Regex("""(?:^|\s)(?:с|со|от)\s+([^,]+?)(?:\s+(?:в|до|на)\s+|$)""").find(lower)
            val toMatch = Regex("""(?:^|\s)(?:в|до|на)\s+([^,]+?)(?:\s+(?:на|тариф|комфорт|эконом|бизнес|с\s+кондиц|тихо|багаж)|,|$)""").find(lower)

            val rawFrom = fromMatch?.groupValues?.getOrNull(1)?.trim()
            val rawTo = toMatch?.groupValues?.getOrNull(1)?.trim()

            if (!rawFrom.isNullOrBlank()) {
                pickup = knownPlaces.firstOrNull { it.first in rawFrom }?.second ?: rawFrom.replaceFirstChar { it.uppercase() }
            }
            if (!rawTo.isNullOrBlank()) {
                dropoff = knownPlaces.firstOrNull { it.first in rawTo }?.second ?: rawTo.replaceFirstChar { it.uppercase() }
            }
            if (dropoff == null) {
                dropoff = knownPlaces.firstOrNull { it.first in lower && it.second != pickup }?.second ?: transcript.trim()
            }

            val quiet = if ("тих" in lower || "молч" in lower || "quiet" in lower) true else null
            val ac = if ("кондиц" in lower || "климат" in lower || "прохлад" in lower || "ac" in lower) true else null
            val luggage = if ("багаж" in lower || "чемодан" in lower || "luggage" in lower) true else null
            val pets = if ("питом" in lower || "собак" in lower || "кошк" in lower || "pet" in lower) true else null
            val b2b = if ("бизнес-проф" in lower || "командиров" in lower || "корпоратив" in lower) true else null

            val parts = buildList {
                if (!pickup.isNullOrBlank()) add("от: $pickup")
                if (!dropoff.isNullOrBlank()) add("куда: $dropoff")
                if (tariff != null) add("тариф: $tariff")
                if (ac == true) add("кондиционер")
                if (quiet == true) add("тихо")
                if (luggage == true) add("багаж")
            }
            return com.example.data.model.VoiceOrderIntent(
                rawTranscript = transcript,
                pickupQuery = pickup,
                dropoffQuery = dropoff,
                tariffId = tariff,
                quietRide = quiet,
                climateAc = ac,
                luggageHelp = luggage,
                petFriendly = pets,
                businessTrip = b2b,
                summary = if (parts.isNotEmpty()) "Распознано: ${parts.joinToString(" • ")}" else "Поиск: $transcript"
            )
        }
    }

    private fun offlinePhraseTranslate(text: String, targetLangCode: String): String {
        val lower = text.lowercase()
        return when (targetLangCode.uppercase()) {
            "EN" -> when {
                "выхожу" in lower || "чыгып" in lower -> "I'm coming out in 2 minutes."
                "вход" in lower || "кире" in lower -> "I'm waiting at the main entrance."
                "шлагбаум" in lower -> "Please stop near the barrier gate."
                "подъезж" in lower || "жетип" in lower -> "I'm arriving now, hazard lights are on."
                else -> "[EN] $text"
            }
            "KG" -> when {
                "выхожу" in lower || "coming out" in lower -> "2 мүнөттөн кийин чыгам."
                "вход" in lower || "entrance" in lower -> "Башкы кире бериште күтүп турам."
                "шлагбаум" in lower || "barrier" in lower -> "Шлагбаумдун жанына токтоп коюңуз."
                "подъезж" in lower || "arriving" in lower -> "Жетип калдым, авариялык жарыкты күйгүздүм."
                else -> "[KG] $text"
            }
            else -> when {
                "coming out" in lower || "чыгам" in lower -> "Выхожу через 2 минуты."
                "entrance" in lower || "кире" in lower -> "Жду у главного входа."
                "barrier" in lower || "шлагбаум" in lower -> "Остановитесь, пожалуйста, у шлагбаума."
                "arriving" in lower || "жетип" in lower -> "Подъезжаю к вам, включил аварийку."
                else -> "[RU] $text"
            }
        }
    }

    /**
     * Generates engaging, concise audio-guide stories for Kyrgyzstan landmarks, mountain passes,
     * and intercity routes via Firebase AI Logic (Gemini 3.8 Flash), with offline curated fallback.
     */
    suspend fun generateTourGuideStory(
        topic: String,
        languageCode: String,
        offlineMode: Boolean
    ): String {
        val langLabel = when (languageCode.uppercase()) {
            "KG" -> "Кыргызча (кыргыз тилинде)"
            "EN" -> "English"
            else -> "Русский"
        }
        if (!offlineMode) {
            try {
                val prompt = """
                    Ты — харизматичный аудиогид KG Taxi для туристов и путешественников по Кыргызстану.
                    Расскажи 3 коротких, ярких и достоверных факта о месте «$topic» на языке $langLabel.
                    Формат: 4-5 предложений без markdown-звёздочек, удобно для прослушивания в дороге.
                """.trimIndent()
                val resp = withTimeout(18_000L) { model.generateContent(prompt) }
                val text = resp.text?.trim()
                if (!text.isNullOrBlank()) return text
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(AI_TAG, "Tour guide offline fallback used: ${e.message}")
            }
        }
        return offlineTourGuideFallback(topic, languageCode)
    }

    private fun offlineTourGuideFallback(topic: String, languageCode: String): String {
        val lower = topic.lowercase()
        val isEn = languageCode.equals("EN", true)
        val isKg = languageCode.equals("KG", true)
        return when {
            "бурана" in lower || "burana" in lower -> when {
                isEn -> "Burana Tower is an 11th-century minaret from the ancient Silk Road city of Balasagun. Originally standing 45 meters tall, it now rises 25 meters overlooking the Chuy Valley and a remarkable garden of ancient Turkic balbal stone statues."
                isKg -> "Бурана мунарасы — XI кылымда Улуу Жибек жолундагы байыркы Баласагын шаарында курулган тарыхый эстелик. Мунаранын жанында байыркы түрк балбал таштарынын ачык асман алдындагы музейи жайгашкан."
                else -> "Башня Бурана — минарет XI века на месте древней столицы Караханидов Баласагуна. Изначально её высота достигала 45 метров, а рядом сохранился уникальный музей балбалов — древнетюркских каменных изваяний вдоль Великого Шёлкового пути."
            }
            "ала-арча" in lower || "ala-archa" in lower -> when {
                isEn -> "Ala-Archa National Park sits just 40 km south of Bishkek in the Kyrgyz Ala-Too range. Named after the vibrant juniper trees ('archa'), its trails lead to Ak-Sai Waterfall and peaks soaring above 4,800 meters."
                isKg -> "Ала-Арча улуттук паркы Бишкектен 40 км түштүктө, Кыргыз Ала-Тоосунун кооз капчыгайында жайгашкан. Бул жерде Ак-Сай шаркыратмасы жана 4800 метрден бийик ак карлуу чокулар көрүнүп турат."
                else -> "Природный парк Ала-Арча расположен всего в 40 км от Бишкека в живописном ущелье Кыргызского Ала-Тоо. Название переводится как «пёстрый можжевельник», а тропы ведут к водопаду Ак-Сай и вечным ледникам на высоте свыше 4000 метров."
            }
            "боом" in lower || "boom" in lower -> when {
                isEn -> "Boom Gorge is a dramatic 30 km canyon carved by the Chu River, serving as the main gateway from Bishkek to Lake Issyk-Kul. Look up for red sandstone formations and the famous Kok-Moinok Aeolian castles."
                isKg -> "Боом капчыгайы — Чүй дарыясы жарып өткөн 30 чакырымдык кооз капчыгай, Ысык-Көлгө кеткен башкы дарбаза. Жол боюнда кызыл аскалар жана Көк-Мойноктун өзгөчө каньондору жайгашкан."
                else -> "Боомское ущелье — величественный 30-километровый каньон реки Чу, главные ворота из Бишкека на озеро Иссык-Куль. Его скалы меняют оттенок от охристо-красного до изумрудного, а в районе Кок-Мойнока скрыты живописные эоловые замки."
            }
            "тоо-ашуу" in lower || "too-ashuu" in lower || "ош" in lower -> when {
                isEn -> "The Too-Ashuu Pass rises over 3,100 meters along the scenic Bishkek–Osh highway. After crossing the mountain tunnel, travelers emerge into the breathtaking high-altitude Suusamyr Valley, famous for summer jailoo pastures and fresh kymyz."
                isKg -> "Төө-Ашуу ашуусу Бишкек–Ош унаа жолунда 3100 метрден ашуун бийиктикте жайгашкан. Туннелден өткөндөн кийин Суусамырдын кең жайлоосу ачылат."
                else -> "Перевал Тоо-Ашуу на трассе Бишкек–Ош поднимается выше 3100 метров над уровнем моря. Сразу за знаменитым тоннелем открывается панорама высокогорной долины Суусамыр — легендарного летнего джайлоо с юртами и свежим кумысом."
            }
            else -> when {
                isEn -> "Lake Issyk-Kul is the world's second-largest alpine lake at 1,607 meters elevation. Its slightly saline waters never freeze even in winter, earning it the poetic name 'Pearl of the Tian Shan'."
                isKg -> "Ысык-Көл — деңиз деңгээлинен 1607 метр бийиктикте жайгашкан, кышында тоңбогон дүйнөдөгү эң кооз тоо көлдөрүнүн бири. Ал «Теңир-Тоонун бермети» деп аталат."
                else -> "Иссык-Куль — второе по величине высокогорное озеро мира на высоте 1607 метров. Благодаря лёгкой солёности и глубине до 668 метров оно никогда не замерзает зимой и по праву называется Жемчужиной Тянь-Шаня."
            }
        }
    }
}

