package com.example.data.model

/**
 * All regions, districts, district centres, cities and urban-type settlements of Kyrgyzstan.
 * Coordinates: OpenStreetMap. Used to detect where the phone is (the nearest settlement).
 */
enum class SettlementType(val label: String) {
    CAPITAL("Столица"),
    CITY("Город"),
    PGT("Посёлок городского типа"),
    DISTRICT_CENTER("Районный центр"),
    VILLAGE("Село")
}

data class KgSettlement(
    val name: String,
    val type: SettlementType,
    val region: String,
    val district: String,
    val point: GeoPoint,
    /** Also a district centre (shown with the type). */
    val districtCenter: Boolean = false
)

data class KgDistrict(val name: String, val center: String)
data class KgRegion(val name: String, val center: String, val districts: List<KgDistrict>)

object KgSettlements {
    private const val CHUY = "Чуйская область"
    private const val ISSYK = "Иссык-Кульская область"
    private const val NARYN = "Нарынская область"
    private const val TALAS = "Таласская область"
    private const val JALAL = "Джалал-Абадская область"
    private const val OSH_R = "Ошская область"
    private const val BATKEN = "Баткенская область"
    private const val BISHKEK = "город Бишкек"
    private const val OSH_C = "город Ош"

    val regions: List<KgRegion> = listOf(
        KgRegion(BISHKEK, "Бишкек", listOf(
            KgDistrict("Ленинский район", "Бишкек"), KgDistrict("Октябрьский район", "Бишкек"),
            KgDistrict("Первомайский район", "Бишкек"), KgDistrict("Свердловский район", "Бишкек")
        )),
        KgRegion(OSH_C, "Ош", emptyList()),
        KgRegion(CHUY, "Токмок", listOf(
            KgDistrict("Аламудунский район", "Лебединовка"), KgDistrict("Жайылский район", "Кара-Балта"),
            KgDistrict("Кеминский район", "Кемин"), KgDistrict("Московский район", "Беловодское"),
            KgDistrict("Панфиловский район", "Каинды"), KgDistrict("Сокулукский район", "Сокулук"),
            KgDistrict("Чуйский район", "Токмок"), KgDistrict("Ысык-Атинский район", "Кант")
        )),
        KgRegion(ISSYK, "Каракол", listOf(
            KgDistrict("Ак-Суйский район", "Ак-Суу"), KgDistrict("Джети-Огузский район", "Кызыл-Суу"),
            KgDistrict("Иссык-Кульский район", "Чолпон-Ата"), KgDistrict("Тонский район", "Боконбаево"),
            KgDistrict("Тюпский район", "Тюп")
        )),
        KgRegion(NARYN, "Нарын", listOf(
            KgDistrict("Ак-Талинский район", "Баетово"), KgDistrict("Ат-Башинский район", "Ат-Баши"),
            KgDistrict("Жумгальский район", "Чаек"), KgDistrict("Кочкорский район", "Кочкорка"),
            KgDistrict("Нарынский район", "Нарын")
        )),
        KgRegion(TALAS, "Талас", listOf(
            KgDistrict("Бакай-Атинский район", "Бакай-Ата"), KgDistrict("Кара-Бууринский район", "Кызыл-Адыр"),
            KgDistrict("Манасский район", "Семетей"), KgDistrict("Таласский район", "Манас")
        )),
        KgRegion(JALAL, "Джалал-Абад", listOf(
            KgDistrict("Аксыйский район", "Кербен"), KgDistrict("Ала-Букинский район", "Ала-Бука"),
            KgDistrict("Базар-Коргонский район", "Базар-Коргон"), KgDistrict("Ноокенский район", "Массы"),
            KgDistrict("Сузакский район", "Сузак"), KgDistrict("Тогуз-Тороуский район", "Казарман"),
            KgDistrict("Токтогульский район", "Токтогул"), KgDistrict("Чаткальский район", "Каныш-Кыя")
        )),
        KgRegion(OSH_R, "Ош", listOf(
            KgDistrict("Алайский район", "Гульча"), KgDistrict("Араванский район", "Араван"),
            KgDistrict("Кара-Кульджинский район", "Кара-Кульджа"), KgDistrict("Кара-Сууский район", "Кара-Суу"),
            KgDistrict("Ноокатский район", "Ноокат"), KgDistrict("Узгенский район", "Узген"),
            KgDistrict("Чон-Алайский район", "Дароот-Коргон")
        )),
        KgRegion(BATKEN, "Баткен", listOf(
            KgDistrict("Баткенский район", "Баткен"), KgDistrict("Кадамжайский район", "Кадамжай"),
            KgDistrict("Лейлекский район", "Раззаков")
        ))
    )

    private fun s(name: String, type: SettlementType, region: String, district: String, lat: Double, lng: Double, dc: Boolean = false) =
        KgSettlement(name, type, region, district, GeoPoint(lat, lng), dc)

    private val C = SettlementType.CITY
    private val P = SettlementType.PGT
    private val D = SettlementType.DISTRICT_CENTER
    private val V = SettlementType.VILLAGE

    val all: List<KgSettlement> = listOf(
        // Capital and Osh (cities of republican significance)
        s("Бишкек", SettlementType.CAPITAL, BISHKEK, "", 42.8761, 74.6037),
        s("Ош", C, OSH_C, "", 40.5173, 72.8050),
        // Chuy
        s("Токмок", C, CHUY, "Чуйский район", 42.8377, 75.2952, true),
        s("Кара-Балта", C, CHUY, "Жайылский район", 42.8011, 73.8523, true),
        s("Кант", C, CHUY, "Ысык-Атинский район", 42.8888, 74.8501, true),
        s("Шопоков", C, CHUY, "Сокулукский район", 42.8575, 74.3420),
        s("Кемин", C, CHUY, "Кеминский район", 42.7821, 75.6895, true),
        s("Каинды", C, CHUY, "Панфиловский район", 42.8271, 73.6784, true),
        s("Орловка", C, CHUY, "Кеминский район", 42.7427, 75.5888),
        s("Ак-Тюз", P, CHUY, "Кеминский район", 42.8799, 76.1341),
        s("Лебединовка", D, CHUY, "Аламудунский район", 42.8856, 74.6755),
        s("Беловодское", D, CHUY, "Московский район", 42.8319, 74.0969),
        s("Сокулук", D, CHUY, "Сокулукский район", 42.8565, 74.2983),
        s("Ивановка", V, CHUY, "Ысык-Атинский район", 42.8874, 75.0850),
        s("Новопокровка", V, CHUY, "Ысык-Атинский район", 42.8707, 74.7341),
        s("Таш-Мойнок", V, CHUY, "Аламудунский район", 42.7394, 74.6555),
        // Issyk-Kul
        s("Каракол", C, ISSYK, "", 42.4917, 78.3969),
        s("Балыкчы", C, ISSYK, "", 42.4571, 76.1962),
        s("Чолпон-Ата", C, ISSYK, "Иссык-Кульский район", 42.6417, 77.0665, true),
        s("Ак-Суу", D, ISSYK, "Ак-Суйский район", 42.4997, 78.5224),
        s("Кызыл-Суу", D, ISSYK, "Джети-Огузский район", 42.3395, 78.0047),
        s("Боконбаево", D, ISSYK, "Тонский район", 42.1141, 76.9962),
        s("Тюп", D, ISSYK, "Тюпский район", 42.7336, 78.3594),
        s("Каджи-Сай", P, ISSYK, "Тонский район", 42.1379, 77.1829),
        s("Бостери", V, ISSYK, "Иссык-Кульский район", 42.6631, 77.1875),
        s("Тамчы", V, ISSYK, "Иссык-Кульский район", 42.5647, 76.6606),
        s("Ананьево", V, ISSYK, "Иссык-Кульский район", 42.7398, 77.6728),
        s("Барскоон", V, ISSYK, "Джети-Огузский район", 42.1579, 77.6044),
        s("Тамга", V, ISSYK, "Джети-Огузский район", 42.1507, 77.5494),
        s("Чон-Таш", V, ISSYK, "Тюпский район", 42.7724, 78.6217),
        // Naryn
        s("Нарын", C, NARYN, "Нарынский район", 41.4286, 75.9976, true),
        s("Баетово", D, NARYN, "Ак-Талинский район", 41.2670, 74.9609),
        s("Ат-Баши", D, NARYN, "Ат-Башинский район", 41.1693, 75.8088),
        s("Чаек", D, NARYN, "Жумгальский район", 41.9266, 74.5165),
        s("Кочкорка", D, NARYN, "Кочкорский район", 42.2132, 75.7541),
        s("Мин-Куш", P, NARYN, "Жумгальский район", 41.6735, 74.4552),
        // Talas
        s("Талас", C, TALAS, "Таласский район", 42.5220, 72.2477),
        s("Бакай-Ата", D, TALAS, "Бакай-Атинский район", 42.4904, 71.9242),
        s("Кызыл-Адыр", D, TALAS, "Кара-Бууринский район", 42.6179, 71.5880),
        s("Семетей", D, TALAS, "Манасский район", 42.7368, 71.6109),
        s("Манас", D, TALAS, "Таласский район", 42.4859, 72.5271),
        // Jalal-Abad
        s("Джалал-Абад", C, JALAL, "", 40.9298, 73.0012),
        s("Таш-Кумыр", C, JALAL, "", 41.3453, 72.2215),
        s("Майлуу-Суу", C, JALAL, "", 41.2457, 72.4508),
        s("Кара-Куль", C, JALAL, "Токтогульский район", 41.6316, 72.6717),
        s("Кок-Джангак", C, JALAL, "Сузакский район", 41.0309, 73.1978),
        s("Кербен", C, JALAL, "Аксыйский район", 41.4896, 71.7546, true),
        s("Токтогул", C, JALAL, "Токтогульский район", 41.8719, 72.9378, true),
        s("Базар-Коргон", D, JALAL, "Базар-Коргонский район", 41.0349, 72.7429),
        s("Ала-Бука", D, JALAL, "Ала-Букинский район", 41.4061, 71.4863),
        s("Массы", D, JALAL, "Ноокенский район", 41.0665, 72.6405),
        s("Сузак", D, JALAL, "Сузакский район", 40.8969, 72.9119),
        s("Каныш-Кыя", D, JALAL, "Чаткальский район", 41.7652, 71.0639),
        s("Казарман", P, JALAL, "Тогуз-Тороуский район", 41.4034, 74.0202, true),
        s("Кочкор-Ата", P, JALAL, "Ноокенский район", 41.0354, 72.4834),
        s("Шамалды-Сай", P, JALAL, "Ноокенский район", 41.1987, 72.1802),
        s("Кетмен-Тёбё", P, JALAL, "Токтогульский район", 41.7315, 73.1291),
        s("Терек-Сай", P, JALAL, "Чаткальский район", 41.5074, 71.1470),
        s("Кызыл-Джар", V, JALAL, "Аксыйский район", 41.2739, 72.0044),
        // Osh region
        s("Узген", C, OSH_R, "Узгенский район", 40.7688, 73.2987, true),
        s("Кара-Суу", C, OSH_R, "Кара-Сууский район", 40.7069, 72.8799, true),
        s("Ноокат", C, OSH_R, "Ноокатский район", 40.2657, 72.6178, true),
        s("Араван", D, OSH_R, "Араванский район", 40.5115, 72.4998),
        s("Гульча", D, OSH_R, "Алайский район", 40.3118, 73.4438),
        s("Кара-Кульджа", D, OSH_R, "Кара-Кульджинский район", 40.6334, 73.5910),
        s("Дароот-Коргон", D, OSH_R, "Чон-Алайский район", 39.5510, 72.2156),
        s("Сары-Таш", V, OSH_R, "Алайский район", 39.7282, 73.2489),
        // Batken
        s("Баткен", C, BATKEN, "Баткенский район", 40.0612, 70.8201, true),
        s("Кызыл-Кия", C, BATKEN, "", 40.2600, 72.1312),
        s("Сулюкта", C, BATKEN, "Лейлекский район", 39.9407, 69.5712),
        s("Раззаков", C, BATKEN, "Лейлекский район", 39.8361, 69.5312, true),
        s("Кадамжай", C, BATKEN, "Кадамжайский район", 40.1302, 71.7288, true),
        s("Айдаркен", C, BATKEN, "Кадамжайский район", 39.9464, 71.3424),
        s("Восточный", P, BATKEN, "Лейлекский район", 39.9219, 69.6502),
        s("Уч-Коргон", P, BATKEN, "Кадамжайский район", 40.2399, 72.0522)
    )

    /** The settlement closest to a point (null outside Kyrgyzstan). */
    fun nearest(point: GeoPoint): KgSettlement? {
        if (point.lat !in 39.1..43.3 || point.lng !in 69.2..80.3) return null
        return all.minByOrNull { KgGeoCatalog.haversineKm(it.point, point) }
    }

    fun byName(name: String): KgSettlement? = all.firstOrNull { it.name.equals(name, ignoreCase = true) }

    /**
     * A taxi "city" for any settlement: the 8 big cities keep their landmarks and prices,
     * other settlements get the regional defaults.
     */
    fun cityFor(st: KgSettlement): KgCity {
        KgGeoCatalog.cities.firstOrNull { it.name == st.name }?.let { return it }
        val regional = when (st.region) {
            ISSYK -> "Каракол"; NARYN -> "Нарын"; TALAS -> "Талас"; JALAL -> "Джалал-Абад"
            OSH_R, OSH_C -> "Ош"; BATKEN -> "Баткен"; else -> "Бишкек"
        }
        val base = KgGeoCatalog.cityByName(regional)
        return KgCity(
            name = st.name,
            nameEn = st.name,
            center = st.point,
            baseFareSom = base.baseFareSom,
            perKmSom = base.perKmSom,
            surgeMultiplier = 1.0,
            streets = listOf("центр", "главная улица"),
            landmarks = emptyList(),
            surgeZones = emptyList()
        )
    }
}
