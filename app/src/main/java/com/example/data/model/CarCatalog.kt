package com.example.data.model

/** Class of a car model: decides which tariffs it can work in. */
enum class CarClass { ECONOMY, COMFORT, MINIVAN }

data class CarModel(val name: String, val carClass: CarClass, val seats: Int = 4)

data class CarMake(val name: String, val models: List<CarModel>)

/**
 * Popular cars in Kyrgyzstan and the tariff each one fits. Used by driver registration:
 * the driver picks make, model and year, and the app chooses tariffs automatically.
 */
object CarCatalog {
    private fun e(n: String) = CarModel(n, CarClass.ECONOMY)
    private fun c(n: String) = CarModel(n, CarClass.COMFORT)
    private fun m(n: String, seats: Int = 7) = CarModel(n, CarClass.MINIVAN, seats)

    val makes: List<CarMake> = listOf(
        CarMake("Toyota", listOf(e("Corolla"), e("Prius"), e("Yaris"), e("Vitz"), c("Camry"), c("Avalon"), c("RAV4"), c("Highlander"), c("Land Cruiser Prado"), m("Alphard"), m("Estima"), m("Sienna"), m("Noah"), m("Voxy"))),
        CarMake("Honda", listOf(e("Fit"), e("Civic"), c("Accord"), c("CR-V"), m("Odyssey"), m("Stepwgn"), m("Freed", 6))),
        CarMake("Lexus", listOf(c("ES"), c("GS"), c("RX"), c("GX"), c("NX"))),
        CarMake("Hyundai", listOf(e("Solaris"), e("Accent"), e("Elantra"), e("Avante"), c("Sonata"), c("Grandeur"), c("Tucson"), c("Santa Fe"), m("Starex"), m("Staria"))),
        CarMake("Kia", listOf(e("Rio"), e("Cerato"), c("K5"), c("Optima"), c("K7"), c("Sportage"), c("Sorento"), m("Carnival"))),
        CarMake("Chevrolet", listOf(e("Cobalt"), e("Nexia"), e("Lacetti"), e("Spark"), e("Gentra"), c("Malibu"), c("Captiva"))),
        CarMake("Daewoo", listOf(e("Matiz"), e("Nexia"), e("Gentra"), e("Lacetti"))),
        CarMake("Mercedes-Benz", listOf(c("C-Class"), c("E-Class"), c("S-Class"), m("Vito", 8), m("V-Class"), m("Sprinter", 8))),
        CarMake("Volkswagen", listOf(e("Polo"), e("Jetta"), c("Passat"), c("Tiguan"), m("Multivan"), m("Caravelle", 8))),
        CarMake("Nissan", listOf(e("Almera"), e("Sunny"), e("Note"), e("Tiida"), c("Teana"), c("X-Trail"), m("Serena"), m("Elgrand"))),
        CarMake("Mitsubishi", listOf(e("Lancer"), c("Outlander"), c("Pajero"), m("Delica"))),
        CarMake("Subaru", listOf(e("Impreza"), c("Legacy"), c("Outback"), c("Forester"))),
        CarMake("Mazda", listOf(e("Mazda 3"), e("Demio"), c("Mazda 6"), c("CX-5"), m("MPV"))),
        CarMake("BMW", listOf(c("3 Series"), c("5 Series"), c("X3"), c("X5"))),
        CarMake("Audi", listOf(c("A4"), c("A6"), c("Q5"), c("Q7"))),
        CarMake("Lada", listOf(e("Granta"), e("Vesta"), e("Priora"), e("Largus"))),
        CarMake("Skoda", listOf(e("Rapid"), c("Octavia"), c("Superb"))),
        CarMake("BYD", listOf(e("Dolphin"), c("Song Plus"), c("Han"), c("Qin Plus"))),
        CarMake("Chery", listOf(e("Tiggo 4"), c("Tiggo 7"), c("Tiggo 8 Pro")))
    )

    val colors: List<String> = listOf(
        "Белый", "Чёрный", "Серебристый", "Серый", "Синий", "Красный", "Бежевый", "Коричневый", "Зелёный", "Жёлтый"
    )

    /** Oldest car year accepted at all. */
    const val MIN_YEAR = 2005
    /** Comfort needs a fresher car. */
    const val COMFORT_MIN_YEAR = 2012

    val years: List<Int> = (java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) downTo MIN_YEAR).toList()

    fun modelsOf(make: String): List<CarModel> = makes.firstOrNull { it.name == make }?.models.orEmpty()

    /**
     * Tariff ids the car fits, best first. Empty if the car is too old.
     * - minivan models: "minivan" (and "econom" so the driver is never idle);
     * - comfort models from [COMFORT_MIN_YEAR]: "comfort" + "econom";
     * - everything else from [MIN_YEAR]: "econom".
     */
    fun tariffsFor(make: String, model: String, year: Int): List<String> {
        if (year < MIN_YEAR) return emptyList()
        val cls = modelsOf(make).firstOrNull { it.name == model }?.carClass ?: CarClass.ECONOMY
        return when {
            cls == CarClass.MINIVAN -> listOf("minivan", "econom")
            cls == CarClass.COMFORT && year >= COMFORT_MIN_YEAR -> listOf("comfort", "econom")
            else -> listOf("econom")
        }
    }

    fun tariffLabel(id: String): String = when (id) {
        "econom" -> "Эконом"
        "comfort" -> "Комфорт"
        "minivan" -> "Минивэн"
        else -> id
    }
}
