package com.example.data.model

import kotlin.math.*

data class GeoPoint(
    val lat: Double,
    val lng: Double
)

data class KgPlace(
    val id: String,
    val name: String,
    val address: String,
    val city: String,
    val point: GeoPoint,
    val category: String,
    val rating: Double,
    val reviewCount: Int,
    val priceLevel: Int // 1 = $, 2 = $$, 3 = $$$
)

data class SurgeZone(
    val id: String,
    val name: String,
    val center: GeoPoint,
    val radiusKm: Double,
    val multiplier: Double
)

data class KgCity(
    val name: String,
    val nameEn: String,
    val center: GeoPoint,
    val baseFareSom: Int,
    val perKmSom: Int,
    val surgeMultiplier: Double,
    val streets: List<String>,
    val landmarks: List<KgPlace>,
    val surgeZones: List<SurgeZone>
)

data class TaxiTariff(
    val id: String,
    val nameRu: String,
    val nameKg: String,
    val nameEn: String,
    val subtitle: String,
    val baseSom: Int,
    val perKmSom: Int,
    val etaMin: Int,
    val seats: Int,
    val ecoGreen: Boolean = false,
    val ladyOnly: Boolean = false,
    val soberDriver: Boolean = false
)

data class ExcursionPreset(
    val id: String,
    val title: String,
    val subtitle: String,
    val hours: Int,
    val distanceKm: Double,
    val destination: GeoPoint,
    val priceSom: Int
)

data class IntercityRoute(
    val id: String,
    val fromCity: String,
    val toCity: String,
    val distanceKm: Double,
    val durationMin: Int,
    val seatPriceSom: Int,
    val frontSeatExtraSom: Int
)

data class FoodMenuItem(
    val id: String,
    val name: String,
    val description: String,
    val priceSom: Int,
    val weightGrams: Int
)

data class FoodVenue(
    val id: String,
    val name: String,
    val city: String,
    val cuisine: String,
    val rating: Double,
    val reviewCount: Int,
    val deliveryMin: Int,
    val deliveryFeeSom: Int,
    val priceLevel: Int,
    val menu: List<FoodMenuItem>
)

data class FlightInfo(
    val code: String,
    val airport: String, // FRU or OSS
    val origin: String,
    val etaTime: String,
    val status: String
)

object KgGeoCatalog {

    val cities: List<KgCity> = listOf(
        KgCity(
            name = "Бишкек",
            nameEn = "Bishkek",
            center = GeoPoint(42.8746, 74.5698),
            baseFareSom = 90,
            perKmSom = 22,
            surgeMultiplier = 1.15,
            streets = listOf(
                "пр. Чуй", "ул. Киевская", "ул. Токтогула", "ул. Московская",
                "ул. Абдрахманова", "пр. Манаса", "ул. Байтик Баатыра", "ул. Ахунбаева",
                "ул. Горького", "ул. Юнусалиева", "ул. Токомбаева"
            ),
            landmarks = listOf(
                KgPlace("b1", "Площадь Ала-Тоо", "пр. Чуй 194, Бишкек", "Бишкек", GeoPoint(42.8764, 74.6037), "Центр", 4.9, 1840, 1),
                KgPlace("b2", "Аэропорт Манас (FRU)", "Аэропорт Манас, терминал 1", "Бишкек", GeoPoint(43.0613, 74.4776), "Аэропорт", 4.6, 2310, 2),
                KgPlace("b3", "ТРЦ Asia Mall", "пр. Ч. Айтматова 3, Бишкек", "Бишкек", GeoPoint(42.8559, 74.5855), "ТЦ", 4.8, 1520, 2),
                KgPlace("b4", "ЦУМ Айчурек", "пр. Чуй 155, Бишкек", "Бишкек", GeoPoint(42.8763, 74.6143), "ТЦ", 4.7, 980, 2),
                KgPlace("b5", "Дордой Плаза 2", "ул. Ибраимова 115, Бишкек", "Бишкек", GeoPoint(42.8749, 74.6188), "ТЦ", 4.8, 1120, 2),
                KgPlace("b6", "Ошский базар", "ул. Бейшеналиевой 42, Бишкек", "Бишкек", GeoPoint(42.8753, 74.5701), "Рынок", 4.4, 3210, 1),
                KgPlace("b7", "Южные ворота / Асанбай", "ул. Токомбаева 21А, Бишкек", "Бишкек", GeoPoint(42.8205, 74.6114), "Район", 4.7, 640, 2),
                KgPlace("b8", "Чайхана Navat", "ул. Киевская 114/1, Бишкек", "Бишкек", GeoPoint(42.8742, 74.5964), "Кафе", 4.9, 1940, 2),
                KgPlace("b9", "Природный парк Ала-Арча", "Ущелье Ала-Арча, 38 км", "Бишкек", GeoPoint(42.5653, 74.4820), "Туризм", 4.9, 2890, 3)
            ),
            surgeZones = listOf(
                SurgeZone("sb1", "Центр / Чуй", GeoPoint(42.8764, 74.6037), 1.8, 1.3),
                SurgeZone("sb2", "Asia Mall", GeoPoint(42.8559, 74.5855), 1.4, 1.25),
                SurgeZone("sb3", "Южные микрорайоны", GeoPoint(42.8260, 74.6100), 1.6, 1.2)
            )
        ),
        KgCity(
            name = "Ош",
            nameEn = "Osh",
            center = GeoPoint(40.5140, 72.8161),
            baseFareSom = 80,
            perKmSom = 20,
            surgeMultiplier = 1.1,
            streets = listOf("пр. Масалиева", "ул. Курманжан Датка", "ул. Ленина", "ул. Алиева", "ул. Навои"),
            landmarks = listOf(
                KgPlace("o1", "Гора Сулайман-Тоо", "ул. Алиева 1, Ош", "Ош", GeoPoint(40.5305, 72.7824), "Туризм", 4.9, 2100, 1),
                KgPlace("o2", "Аэропорт Ош (OSS)", "Аэропорт Ош, терминал", "Ош", GeoPoint(40.6090, 72.7933), "Аэропорт", 4.5, 920, 2),
                KgPlace("o3", "Центральный базар Ош", "ул. Навои 12, Ош", "Ош", GeoPoint(40.5326, 72.8012), "Рынок", 4.6, 1450, 1),
                KgPlace("o4", "Площадь им. Ленина", "ул. Ленина 221, Ош", "Ош", GeoPoint(40.5283, 72.7985), "Центр", 4.7, 730, 1)
            ),
            surgeZones = listOf(
                SurgeZone("so1", "Центр Ош", GeoPoint(40.5283, 72.7985), 1.5, 1.25),
                SurgeZone("so2", "Аэропорт OSS", GeoPoint(40.6090, 72.7933), 1.3, 1.35)
            )
        ),
        KgCity(
            name = "Чолпон-Ата",
            nameEn = "Cholpon-Ata",
            center = GeoPoint(42.6494, 77.0853),
            baseFareSom = 100,
            perKmSom = 25,
            surgeMultiplier = 1.2,
            streets = listOf("ул. Советская", "ул. Советская (набережная)", "ул. Акматбай Ата", "ул. Садовая"),
            landmarks = listOf(
                KgPlace("c1", "Культурный центр Рух Ордо", "ул. Советская 112, Чолпон-Ата", "Чолпон-Ата", GeoPoint(42.6512, 77.0954), "Туризм", 4.9, 1620, 2),
                KgPlace("c2", "Пляж Золотые Пески", "Бостери, берег Иссык-Куля", "Чолпон-Ата", GeoPoint(42.6481, 77.1844), "Пляж", 4.7, 1320, 2),
                KgPlace("c3", "Ипподром Чолпон-Ата", "с. Бактуу-Долоноту", "Чолпон-Ата", GeoPoint(42.6525, 77.1319), "Спорт", 4.8, 540, 2)
            ),
            surgeZones = listOf(SurgeZone("sc1", "Набережная Иссык-Куль", GeoPoint(42.6500, 77.0950), 1.6, 1.3))
        ),
        KgCity(
            name = "Каракол",
            nameEn = "Karakol",
            center = GeoPoint(42.4907, 78.3936),
            baseFareSom = 85,
            perKmSom = 21,
            surgeMultiplier = 1.1,
            streets = listOf("ул. Токтогула", "ул. Абдрахманова", "ул. Гебзе", "ул. Ленина"),
            landmarks = listOf(
                KgPlace("k1", "Горнолыжная база Каракол", "Ущелье Каракол, 7 км", "Каракол", GeoPoint(42.4042, 78.4631), "Спорт", 4.9, 1980, 3),
                KgPlace("k2", "Дунганская мечеть", "ул. Бектенова 1, Каракол", "Каракол", GeoPoint(42.4936, 78.3849), "Туризм", 4.8, 1210, 1),
                KgPlace("k3", "Рынок Ак-Тилек", "ул. Торгоева 15, Каракол", "Каракол", GeoPoint(42.4870, 78.3970), "Рынок", 4.5, 610, 1)
            ),
            surgeZones = listOf(SurgeZone("sk1", "Центр Каракол", GeoPoint(42.4907, 78.3936), 1.4, 1.2))
        ),
        KgCity(
            name = "Джалал-Абад",
            nameEn = "Jalal-Abad",
            center = GeoPoint(40.9333, 73.0000),
            baseFareSom = 75,
            perKmSom = 19,
            surgeMultiplier = 1.05,
            streets = listOf("ул. Ленина", "ул. Эркиндик", "ул. Токтогула", "ул. Барпы Сейил"),
            landmarks = listOf(
                KgPlace("j1", "Курорт Джалал-Абад", "Холм Аюб-Тоо, Джалал-Абад", "Джалал-Абад", GeoPoint(40.9462, 73.0218), "Туризм", 4.7, 780, 2),
                KgPlace("j2", "Парк Токтогула", "ул. Эркиндик 45, Джалал-Абад", "Джалал-Абад", GeoPoint(40.9338, 72.9896), "Парк", 4.6, 510, 1)
            ),
            surgeZones = listOf(SurgeZone("sj1", "Центр Джалал-Абад", GeoPoint(40.9333, 73.0000), 1.2, 1.15))
        ),
        KgCity(
            name = "Талас",
            nameEn = "Talas",
            center = GeoPoint(42.5228, 72.2427),
            baseFareSom = 75,
            perKmSom = 19,
            surgeMultiplier = 1.0,
            streets = listOf("ул. Бердике Баатыра", "ул. Оторбаева", "ул. Сарыгулова"),
            landmarks = listOf(
                KgPlace("t1", "Комплекс Манас Ордо", "с. Таш-Арык, Талас", "Талас", GeoPoint(42.5210, 72.3711), "Туризм", 4.9, 890, 1),
                KgPlace("t2", "Центральный сквер Талас", "ул. Бердике Баатыра 204", "Талас", GeoPoint(42.5225, 72.2422), "Центр", 4.6, 320, 1)
            ),
            surgeZones = listOf(SurgeZone("st1", "Центр Талас", GeoPoint(42.5228, 72.2427), 1.1, 1.1))
        ),
        KgCity(
            name = "Нарын",
            nameEn = "Naryn",
            center = GeoPoint(41.4287, 75.9911),
            baseFareSom = 80,
            perKmSom = 20,
            surgeMultiplier = 1.05,
            streets = listOf("ул. Ленина", "ул. Сагынбай Орозбак", "ул. Раззакова"),
            landmarks = listOf(
                KgPlace("n1", "Университет Центральной Азии", "ул. Тош-Булак, Нарын", "Нарын", GeoPoint(41.4302, 75.9325), "Образование", 4.9, 640, 2),
                KgPlace("n2", "Набережная реки Нарын", "ул. Ленина 74, Нарын", "Нарын", GeoPoint(41.4279, 75.9950), "Центр", 4.7, 410, 1)
            ),
            surgeZones = listOf(SurgeZone("sn1", "Центр Нарын", GeoPoint(41.4287, 75.9911), 1.2, 1.15))
        ),
        KgCity(
            name = "Баткен",
            nameEn = "Batken",
            center = GeoPoint(40.0626, 70.8194),
            baseFareSom = 75,
            perKmSom = 19,
            surgeMultiplier = 1.0,
            streets = listOf("ул. Нургазиева", "ул. Раззакова", "ул. Сайдиман Ажы"),
            landmarks = listOf(
                KgPlace("bt1", "Гора Айгуль-Тоо", "15 км от г. Баткен", "Баткен", GeoPoint(40.0082, 70.9310), "Туризм", 4.9, 530, 1),
                KgPlace("bt2", "Аэропорт Баткен", "ул. Нургазиева 120, Баткен", "Баткен", GeoPoint(40.0425, 70.8380), "Аэропорт", 4.5, 290, 2)
            ),
            surgeZones = listOf(SurgeZone("sbt1", "Центр Баткен", GeoPoint(40.0626, 70.8194), 1.1, 1.1))
        )
    )

    val taxiTariffs: List<TaxiTariff> = listOf(
        TaxiTariff("econom", "Эконом", "Эконом", "Economy", "Быстрая подача на каждый день", 90, 20, 3, 4),
        TaxiTariff("comfort", "Комфорт", "Комфорт", "Comfort", "Просторный седан, кондиционер", 130, 26, 4, 4),
        TaxiTariff("business", "Бизнес", "Бизнес", "Black", "Toyota Camry 75 / Lexus ES, тишина", 250, 42, 6, 4),
        TaxiTariff("minivan", "Минивэн", "Минивэн", "UberXL", "До 6 пассажиров и большой багаж", 190, 32, 7, 6),
        TaxiTariff("lady", "Леди-такси", "Айым-такси", "Lady Taxi", "Только женщина-водитель, комфорт и безопасность", 145, 26, 5, 4, ladyOnly = true),
        TaxiTariff("green_ev", "Green EV", "Green EV", "Green EV", "100% электромобили BYD / Tesla • Эко-скидка", 110, 22, 4, 4, ecoGreen = true),
        TaxiTariff("sober_driver", "Трезвый водитель", "Айдоочу (перегон)", "Sober Driver", "Перегон вашего авто до дома опытным водителем", 450, 45, 8, 4, soberDriver = true)
    )

    val pickupSpotsByKeyword: List<Pair<String, List<String>>> = listOf(
        "Манас" to listOf("Терминал 1 • Выход Прилёт", "VIP-парковка справа", "Остановка у шлагбаума"),
        "Asia Mall" to listOf("Главный вход (пр. Айтматова)", "Южный заезд (подземный паркинг)", "Сторона ул. Горького"),
        "Ала-Тоо" to listOf("У Исторического музея", "Со стороны пр. Чуй", "У кинотеатра Ала-Тоо"),
        "ЦУМ" to listOf("Главный вход (пр. Чуй)", "Со стороны ул. Шопокова", "Парковка ЦУМ-2"),
        "Дордой" to listOf("Главный вход (Ибраимова)", "У фонтана (ул. Чуй)", "Восточный выезд"),
        "Ошский" to listOf("Центральная арка", "Со стороны Бейшеналиевой", "У автовокзала"),
        "Аэропорт Ош" to listOf("Зал прилёта • Выход 1", "Главная парковка OSS", "Линия такси"),
        "Рух Ордо" to listOf("Главные ворота", "Набережная парковка", "Вход со стороны ул. Советской")
    )

    fun pickupSpotsForAddress(address: String): List<String> {
        val match = pickupSpotsByKeyword.firstOrNull { (key, _) ->
            address.contains(key, ignoreCase = true)
        }
        return match?.second ?: listOf("У главного входа", "У шлагбаума / арки", "Со стороны дороги")
    }

    val excursionPresets: List<ExcursionPreset> = listOf(
        ExcursionPreset("ex1", "Ущелье Ала-Арча", "Альпийские пики и прогулка • 4 часа", 4, 42.0, GeoPoint(42.5653, 74.4820), 2600),
        ExcursionPreset("ex2", "Башня Бурана", "Великий Шёлковый путь, Токмок • 5 часов", 5, 78.0, GeoPoint(42.7465, 75.2504), 3250),
        ExcursionPreset("ex3", "Чункурчак и Супара", "Панорама гор и юрты • 4 часа", 4, 34.0, GeoPoint(42.6345, 74.6231), 2600),
        ExcursionPreset("ex4", "Иссык-Куль Day Tour", "Чолпон-Ата, Рух Ордо и пляж • 10 часов", 10, 265.0, GeoPoint(42.6494, 77.0853), 6500)
    )

    val intercityRoutes: List<IntercityRoute> = listOf(
        IntercityRoute("ic1", "Бишкек", "Чолпон-Ата", 265.0, 240, 750, 150),
        IntercityRoute("ic2", "Бишкек", "Каракол", 400.0, 360, 1100, 200),
        IntercityRoute("ic3", "Бишкек", "Ош", 670.0, 620, 2200, 400),
        IntercityRoute("ic4", "Бишкек", "Талас", 300.0, 280, 900, 150),
        IntercityRoute("ic5", "Бишкек", "Нарын", 355.0, 320, 1000, 200),
        IntercityRoute("ic6", "Ош", "Джалал-Абад", 105.0, 110, 450, 100)
    )

    val foodVenues: List<FoodVenue> = listOf(
        FoodVenue(
            id = "v1", name = "Чайхана Navat", city = "Бишкек", cuisine = "Кыргызская и восточная",
            rating = 4.9, reviewCount = 2410, deliveryMin = 28, deliveryFeeSom = 120, priceLevel = 2,
            menu = listOf(
                FoodMenuItem("m1", "Бешбармак классический", "Отварная конина, чучук, домашняя лапша и сорпо", 540, 420),
                FoodMenuItem("m2", "Плов Чайханский", "Узгенский рис, баранина, жёлтая морковь и нут", 420, 380),
                FoodMenuItem("m3", "Тандырная самса (2 шт)", "Слоёное тесто, рубленая говядина и зира", 240, 260)
            )
        ),
        FoodVenue(
            id = "v2", name = "Фаиза (Faiza)", city = "Бишкек", cuisine = "Легендарный лагман и манты",
            rating = 4.8, reviewCount = 3190, deliveryMin = 25, deliveryFeeSom = 100, priceLevel = 1,
            menu = listOf(
                FoodMenuItem("m4", "Босо Лагман", "Жареная тянутая лапша с телятиной и овощами", 360, 400),
                FoodMenuItem("m5", "Манты с мясом (5 шт)", "Рубленое мясо, лук, тонкое тесто на пару", 340, 350),
                FoodMenuItem("m6", "Ашлян-фу Каракольское", "Охлаждённый крахмал, лапша и острый лазы", 190, 350)
            )
        ),
        FoodVenue(
            id = "v3", name = "Adriano Coffee", city = "Бишкек", cuisine = "Кофейня и завтраки",
            rating = 4.7, reviewCount = 1480, deliveryMin = 20, deliveryFeeSom = 90, priceLevel = 2,
            menu = listOf(
                FoodMenuItem("m7", "Раф Фисташка 350 мл", "Эспрессо, сливки и натуральная паста", 260, 350),
                FoodMenuItem("m8", "Круассан с лососем", "Хрустящий круассан, творожный сыр, авокадо", 390, 220)
            )
        ),
        FoodVenue(
            id = "v4", name = "Царский Плов Ош", city = "Ош", cuisine = "Ошский плов на огне",
            rating = 4.9, reviewCount = 1760, deliveryMin = 24, deliveryFeeSom = 90, priceLevel = 1,
            menu = listOf(
                FoodMenuItem("m9", "Ошский лазерный плов", "Рис Лазер, курдюк, баранина, ачуу-чучук", 380, 420),
                FoodMenuItem("m10", "Шашлык из баранины", "На саксауле с маринованным луком", 310, 250)
            )
        )
    )

    val flights: List<FlightInfo> = listOf(
        FlightInfo("KC 109", "FRU", "Алматы (ALA)", "14:25", "По расписанию"),
        FlightInfo("TK 344", "FRU", "Стамбул (IST)", "16:10", "В полёте • Вовремя"),
        FlightInfo("FZ 1765", "FRU", "Дубай (DXB)", "18:45", "Ожидается"),
        FlightInfo("SU 1882", "FRU", "Москва (SVO)", "21:15", "Задержка +15 мин"),
        FlightInfo("YK 741", "OSS", "Бишкек (FRU)", "13:50", "Посадка"),
        FlightInfo("S7 5557", "OSS", "Новосибирск (OVB)", "17:30", "По расписанию")
    )

    fun cityByName(name: String): KgCity =
        cities.firstOrNull { it.name.equals(name, ignoreCase = true) || it.nameEn.equals(name, ignoreCase = true) }
            ?: cities.first()

    fun haversineKm(a: GeoPoint, b: GeoPoint): Double {
        val r = 6371.0
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLng = Math.toRadians(b.lng - a.lng)
        val sinLat = sin(dLat / 2)
        val sinLng = sin(dLng / 2)
        val h = sinLat * sinLat + cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sinLng * sinLng
        val directKm = 2 * r * asin(sqrt(h.coerceIn(0.0, 1.0)))
        // Apply realistic urban/mountain road winding factor (1.28x)
        return max(0.8, (directKm * 1.28 * 10.0).roundToInt() / 10.0)
    }

    fun estimateDurationMin(distanceKm: Double): Int {
        val avgSpeedKmh = if (distanceKm > 35.0) 64.0 else 28.0
        return max(4, ((distanceKm / avgSpeedKmh) * 60.0).roundToInt() + 2)
    }

    fun reverseGeocodeFallback(city: KgCity, point: GeoPoint): String {
        val nearestLandmark = city.landmarks.minByOrNull { haversineKm(it.point, point) }
        if (nearestLandmark != null && haversineKm(nearestLandmark.point, point) < 0.7) {
            return "${nearestLandmark.name} (${nearestLandmark.address})"
        }
        val idx = (abs(((point.lat * 10000).toInt() + (point.lng * 10000).toInt())) % city.streets.size)
        val house = (abs((point.lat * 100000).toInt()) % 180) + 1
        return "${city.streets[idx]}, $house, ${city.name}"
    }

    fun hourlyDemandForecast(city: KgCity): List<HourlyDemandPoint> {
        val hub = city.surgeZones.firstOrNull()?.name ?: city.landmarks.firstOrNull()?.name ?: city.name
        val hub2 = city.surgeZones.getOrNull(1)?.name ?: city.landmarks.lastOrNull()?.name ?: hub
        return listOf(
            HourlyDemandPoint("08:00", 0.88f, "x1.5", hub),
            HourlyDemandPoint("10:00", 0.55f, "x1.1", hub2),
            HourlyDemandPoint("13:00", 0.72f, "x1.3", hub, isCurrentHour = true),
            HourlyDemandPoint("16:00", 0.64f, "x1.2", hub2),
            HourlyDemandPoint("18:00", 0.98f, "x1.8", hub),
            HourlyDemandPoint("21:00", 0.82f, "x1.4", hub2),
            HourlyDemandPoint("00:00", 0.68f, "x1.3", "Аэропорт / Центр")
        )
    }

    fun intercityScheduleForRoute(routeId: String): List<IntercityScheduledTrip> = listOf(
        IntercityScheduledTrip(
            id = "${routeId}_dep1",
            routeId = routeId,
            departureTime = "Через 25 мин (14:00)",
            driverName = "Алмаз Т.",
            driverRating = 4.97,
            carModel = "Honda Stepwgn Spada",
            occupiedSeats = setOf(1, 2, 4, 5)
        ),
        IntercityScheduledTrip(
            id = "${routeId}_dep2",
            routeId = routeId,
            departureTime = "15:30 • Комфорт+",
            driverName = "Кубаныч Б.",
            driverRating = 4.94,
            carModel = "Toyota Alphard Hybrid",
            occupiedSeats = setOf(1, 4)
        ),
        IntercityScheduledTrip(
            id = "${routeId}_dep3",
            routeId = routeId,
            departureTime = "17:00 • Вечерний",
            driverName = "Самат Ж.",
            driverRating = 4.92,
            carModel = "Mercedes-Benz Vito",
            occupiedSeats = setOf(3)
        )
    )
}


/** Surge multiplier actually used for prices and shown in the app (1.0 while surge is off). */
val KgCity.effectiveSurge: Double
    get() = if (com.example.FeatureFlags.SURGE_PRICING) surgeMultiplier else 1.0

/** Busy zones actually shown in the app (none while surge is off). */
val KgCity.activeSurgeZones: List<SurgeZone>
    get() = if (com.example.FeatureFlags.SURGE_PRICING) surgeZones else emptyList()
