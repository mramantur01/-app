package com.example.data.model

/** Class of a vehicle model: decides which tariffs it can work in. */
enum class CarClass { ECONOMY, COMFORT, BUSINESS, PREMIUM, MINIVAN, MICROBUS, CARGO_15, CARGO_35, CARGO_5, CARGO_10, TOW }

/** What kind of vehicle the driver has (first choice in registration). */
enum class VehicleType(val label: String) {
    CAR("Легковая"),
    BUS("Минивэн / микроавтобус"),
    TRUCK("Грузовая"),
    TOW("Эвакуатор")
}

data class CarModel(val name: String, val carClass: CarClass, val seats: Int = 4)

data class CarMake(val name: String, val models: List<CarModel>)

/** Extra things the driver ticks in registration; they add tariffs. */
data class DriverOptions(
    val childSeat: Boolean = false,
    val ladyDriver: Boolean = false,
    val soberDriver: Boolean = false,
    val courier: Boolean = false
)

/**
 * Vehicles common in Kyrgyzstan and the tariffs each one fits. Used by driver registration:
 * the driver picks the vehicle type, make, model and year, ticks options, and the app
 * chooses the tariffs automatically (cargo by tonnage: 1.5 t, 3.5 t, 5 t, 10 t).
 */
object CarCatalog {
    private fun e(n: String) = CarModel(n, CarClass.ECONOMY)
    private fun c(n: String) = CarModel(n, CarClass.COMFORT)
    private fun b(n: String) = CarModel(n, CarClass.BUSINESS)
    private fun p(n: String) = CarModel(n, CarClass.PREMIUM)
    private fun m(n: String, seats: Int = 7) = CarModel(n, CarClass.MINIVAN, seats)
    private fun bus(n: String, seats: Int = 15) = CarModel(n, CarClass.MICROBUS, seats)
    private fun t15(n: String) = CarModel("$n (1,5 т)", CarClass.CARGO_15, 2)
    private fun t35(n: String) = CarModel("$n (3,5 т)", CarClass.CARGO_35, 2)
    private fun t5(n: String) = CarModel("$n (5 т)", CarClass.CARGO_5, 2)
    private fun t10(n: String) = CarModel("$n (10 т)", CarClass.CARGO_10, 2)
    private fun tow(n: String) = CarModel(n, CarClass.TOW, 2)

    /** Passenger cars. */
    private val cars: List<CarMake> = listOf(
        CarMake("Toyota", listOf(e("Corolla"), e("Prius"), e("Yaris"), e("Vitz"), c("Camry"), c("Avalon"), c("RAV4"), c("Highlander"), b("Land Cruiser Prado"), p("Land Cruiser"))),
        CarMake("Honda", listOf(e("Fit"), e("Civic"), c("Accord"), c("CR-V"))),
        CarMake("Lexus", listOf(b("ES"), b("GS"), p("LS"), b("RX"), b("GX"), p("LX"), c("NX"))),
        CarMake("Hyundai", listOf(e("Solaris"), e("Accent"), e("Elantra"), e("Avante"), c("Sonata"), c("Grandeur"), c("Tucson"), c("Santa Fe"), b("Genesis"))),
        CarMake("Kia", listOf(e("Rio"), e("Cerato"), c("K5"), c("Optima"), c("K7"), c("Sportage"), c("Sorento"), b("K9"))),
        CarMake("Chevrolet", listOf(e("Cobalt"), e("Nexia"), e("Lacetti"), e("Spark"), e("Gentra"), c("Malibu"), c("Captiva"), b("Tahoe"))),
        CarMake("Daewoo", listOf(e("Matiz"), e("Nexia"), e("Gentra"), e("Lacetti"))),
        CarMake("Mercedes-Benz", listOf(c("C-Class"), b("E-Class"), p("S-Class"), b("GLE"), p("G-Class"))),
        CarMake("Volkswagen", listOf(e("Polo"), e("Jetta"), c("Passat"), c("Tiguan"))),
        CarMake("Nissan", listOf(e("Almera"), e("Sunny"), e("Note"), e("Tiida"), c("Teana"), c("X-Trail"))),
        CarMake("Mitsubishi", listOf(e("Lancer"), c("Outlander"), c("Pajero"))),
        CarMake("Subaru", listOf(e("Impreza"), c("Legacy"), c("Outback"), c("Forester"))),
        CarMake("Mazda", listOf(e("Mazda 3"), e("Demio"), c("Mazda 6"), c("CX-5"))),
        CarMake("BMW", listOf(c("3 Series"), b("5 Series"), p("7 Series"), c("X3"), b("X5"), p("X7"))),
        CarMake("Audi", listOf(c("A4"), b("A6"), p("A8"), c("Q5"), b("Q7"))),
        CarMake("Lada", listOf(e("Granta"), e("Vesta"), e("Priora"), e("Largus"))),
        CarMake("Skoda", listOf(e("Rapid"), c("Octavia"), c("Superb"))),
        CarMake("Chery", listOf(e("Tiggo 4"), c("Tiggo 7"), c("Tiggo 8 Pro"))),
        // Electric cars: also get the Green EV tariff.
        CarMake("BYD", listOf(e("Dolphin"), c("Song Plus"), c("Qin Plus"), b("Han"), b("Seal"))),
        CarMake("Tesla", listOf(c("Model 3"), b("Model Y"), p("Model S"))),
        CarMake("Zeekr", listOf(b("001"), b("7X"))),
        CarMake("Li Auto", listOf(b("L7"), p("L9")))
    )

    /** Minivans (up to 8 seats) and microbuses (9+ seats). */
    private val buses: List<CarMake> = listOf(
        CarMake("Toyota", listOf(m("Alphard"), m("Estima"), m("Sienna"), m("Noah"), m("Voxy"), bus("HiAce", 12))),
        CarMake("Honda", listOf(m("Odyssey"), m("Stepwgn"), m("Freed", 6))),
        CarMake("Hyundai", listOf(m("Starex"), m("Staria"), bus("County", 25))),
        CarMake("Kia", listOf(m("Carnival"))),
        CarMake("Mercedes-Benz", listOf(m("Vito", 8), m("V-Class"), bus("Sprinter", 18))),
        CarMake("Volkswagen", listOf(m("Multivan"), m("Caravelle", 8), bus("Crafter", 16))),
        CarMake("Nissan", listOf(m("Serena"), m("Elgrand"))),
        CarMake("Mitsubishi", listOf(m("Delica"))),
        CarMake("Ford", listOf(bus("Transit", 16))),
        CarMake("ГАЗ", listOf(bus("ГАЗель Next (пассажирская)", 17)))
    )

    /** Trucks by carrying capacity. */
    private val trucks: List<CarMake> = listOf(
        CarMake("Hyundai", listOf(t15("Porter"), t35("HD35"), t5("HD65"), t5("HD78"), t10("HD120"))),
        CarMake("Kia", listOf(t15("Bongo"))),
        CarMake("ГАЗ", listOf(t15("ГАЗель Бизнес"), t15("ГАЗель Next"), t5("ГАЗон Next"))),
        CarMake("Isuzu", listOf(t35("Elf 3.5"), t5("Elf 5"), t10("Forward"))),
        CarMake("Mitsubishi Fuso", listOf(t35("Canter 3.5"), t5("Canter 5"))),
        CarMake("Hino", listOf(t5("300"), t10("500"))),
        CarMake("Foton", listOf(t15("Aumark S"), t35("Aumark"), t5("Ollin"))),
        CarMake("JAC", listOf(t35("N56"), t5("N80"))),
        CarMake("Mercedes-Benz", listOf(t15("Sprinter грузовой"), t10("Atego"))),
        CarMake("КамАЗ", listOf(t5("4308"), t10("65115"))),
        CarMake("MAN", listOf(t10("TGL"), t10("TGM")))
    )

    /** Tow trucks. */
    private val towTrucks: List<CarMake> = listOf(
        CarMake("Эвакуатор", listOf(tow("на базе ГАЗель"), tow("на базе Isuzu"), tow("на базе Hyundai HD"), tow("на базе Hino"), tow("на базе КамАЗ")))
    )

    fun makesFor(type: VehicleType): List<CarMake> = when (type) {
        VehicleType.CAR -> cars
        VehicleType.BUS -> buses
        VehicleType.TRUCK -> trucks
        VehicleType.TOW -> towTrucks
    }

    /** All makes of passenger cars (kept for older callers). */
    val makes: List<CarMake> get() = cars

    private val electricMakes = setOf("BYD", "Tesla", "Zeekr", "Li Auto")

    val colors: List<String> = listOf(
        "Белый", "Чёрный", "Серебристый", "Серый", "Синий", "Красный", "Бежевый", "Коричневый", "Зелёный", "Жёлтый"
    )

    /** Oldest year accepted for passenger cars. */
    const val MIN_YEAR = 2005
    /** Trucks and tow trucks can be older. */
    const val MIN_YEAR_TRUCK = 1995
    const val COMFORT_MIN_YEAR = 2012
    const val COMFORT_PLUS_MIN_YEAR = 2016
    const val BUSINESS_MIN_YEAR = 2018
    const val PREMIUM_MIN_YEAR = 2020

    val years: List<Int> = (java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) downTo MIN_YEAR_TRUCK).toList()

    fun modelsOf(make: String, type: VehicleType = VehicleType.CAR): List<CarModel> =
        makesFor(type).firstOrNull { it.name == make }?.models.orEmpty()

    /**
     * Tariff ids the vehicle fits, best first. Empty if the vehicle is too old.
     * Passenger cars by class and year; minivans and microbuses; trucks by tonnage (a heavier truck
     * also takes the next lighter class); tow trucks; plus options (child seat, lady, sober, courier)
     * and Green EV for electric cars.
     */
    fun tariffsFor(
        make: String,
        model: String,
        year: Int,
        type: VehicleType = VehicleType.CAR,
        options: DriverOptions = DriverOptions()
    ): List<String> {
        val cls = modelsOf(make, type).firstOrNull { it.name == model }?.carClass
            ?: when (type) {
                VehicleType.CAR -> CarClass.ECONOMY
                VehicleType.BUS -> CarClass.MINIVAN
                VehicleType.TRUCK -> CarClass.CARGO_15
                VehicleType.TOW -> CarClass.TOW
            }
        val heavy = type == VehicleType.TRUCK || type == VehicleType.TOW
        if (year < (if (heavy) MIN_YEAR_TRUCK else MIN_YEAR)) return emptyList()

        val base = when (cls) {
            CarClass.PREMIUM -> when {
                year >= PREMIUM_MIN_YEAR -> listOf("premium", "business", "comfort_plus", "comfort", "econom")
                year >= BUSINESS_MIN_YEAR -> listOf("business", "comfort_plus", "comfort", "econom")
                year >= COMFORT_PLUS_MIN_YEAR -> listOf("comfort_plus", "comfort", "econom")
                else -> listOf("comfort", "econom")
            }
            CarClass.BUSINESS -> when {
                year >= BUSINESS_MIN_YEAR -> listOf("business", "comfort_plus", "comfort", "econom")
                year >= COMFORT_PLUS_MIN_YEAR -> listOf("comfort_plus", "comfort", "econom")
                year >= COMFORT_MIN_YEAR -> listOf("comfort", "econom")
                else -> listOf("econom")
            }
            CarClass.COMFORT -> when {
                year >= COMFORT_PLUS_MIN_YEAR -> listOf("comfort_plus", "comfort", "econom")
                year >= COMFORT_MIN_YEAR -> listOf("comfort", "econom")
                else -> listOf("econom")
            }
            CarClass.ECONOMY -> listOf("econom")
            CarClass.MINIVAN -> listOf("minivan", "econom")
            CarClass.MICROBUS -> listOf("microbus", "minivan")
            CarClass.CARGO_15 -> listOf("cargo_15")
            CarClass.CARGO_35 -> listOf("cargo_35", "cargo_15")
            CarClass.CARGO_5 -> listOf("cargo_5", "cargo_35")
            CarClass.CARGO_10 -> listOf("cargo_10", "cargo_5")
            CarClass.TOW -> listOf("tow")
        }
        val passenger = !heavy
        val extra = buildList {
            if (passenger && make in electricMakes) add("green_ev")
            if (passenger && options.childSeat) add("child")
            if (passenger && options.ladyDriver) add("lady")
            if (passenger && options.courier) add("courier")
            if (options.soberDriver && type == VehicleType.CAR) add("sober_driver")
        }
        return base + extra
    }

    fun tariffLabel(id: String): String = when (id) {
        "econom" -> "Эконом"
        "comfort" -> "Комфорт"
        "comfort_plus" -> "Комфорт+"
        "business" -> "Бизнес"
        "premium" -> "Премиум"
        "minivan" -> "Минивэн"
        "microbus" -> "Микроавтобус"
        "child" -> "Детский"
        "lady" -> "Леди-такси"
        "green_ev" -> "Электро"
        "courier" -> "Курьер"
        "sober_driver" -> "Трезвый водитель"
        "cargo_15" -> "Грузовой 1,5 т"
        "cargo_35" -> "Грузовой 3,5 т"
        "cargo_5" -> "Грузовой 5 т"
        "cargo_10" -> "Грузовой 10 т"
        "tow" -> "Эвакуатор"
        else -> id
    }
}
