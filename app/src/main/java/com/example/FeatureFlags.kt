package com.example

import com.example.data.model.ServiceType

/**
 * What is switched on in this build.
 *
 * Launch plan: city taxi + intercity, cash payment only. Everything else is hidden until it
 * works for real (a backend, a real payment provider). To bring a feature back, flip its flag
 * to true — the old code is still there.
 */
object FeatureFlags {
    /**
     * Real orders through Firestore (`rides`): the passenger needs to be signed in, a driver with
     * the "driver" role takes the order. Off: the old on-phone demo ride with made-up drivers.
     */
    const val REAL_RIDES = true

    /** Wallet balance, top-up and referral bonus. Off: there is no real payment provider yet. */
    const val WALLET = false

    /** Paying in the app (MBank, O!Деньги, Элкарт, cards, ELQR). Off: only cash to the driver. */
    const val ONLINE_PAYMENTS = false

    /** Owner payouts to a bank. Off: the payout key must live on a server, not in the APK. */
    const val OWNER_PAYOUTS = false

    /** AI route chat. */
    const val AI_ASSISTANT = false

    /** AI tour guide on the passenger home (falls back to ready-made stories without AI). */
    const val AI_TOUR_GUIDE = true

    /** Voice commands. */
    const val VOICE_COMMANDS = false

    /** Family accounts with spending limits (contacts are still used for SOS). */
    const val FAMILY_LIMITS = false

    /** Driver shift passes (paid subscriptions). */
    const val DRIVER_SHIFT_PASSES = false

    /**
     * Surge pricing ("Спрос x1.15", busy zones). Off: there is no real demand data yet, so a fixed
     * multiplier would just make every ride more expensive and show made-up busy districts.
     */
    const val SURGE_PRICING = false

    /**
     * Extras that only pretend to work today: Wait & Save / Priority pickup, shared "Попутчик" rides,
     * driver quests, coffee pause, "По пути домой" filter, auto-accept. Off until they are real.
     */
    const val DEMO_EXTRAS = false

    /** Tariffs we can actually guarantee with the drivers we have. */
    val ENABLED_TARIFF_IDS: Set<String> = setOf(
        "econom", "economy", "econom_plus", "comfort", "comfort_plus", "business", "premium", "minivan", "microbus",
        "airport", "hourly", "child", "lady", "green_ev", "inva", "courier", "sober_driver", "wedding",
        "cargo_15", "cargo_35", "cargo_5", "cargo_10", "cargo_loaders", "tow"
    )

    /** Services passengers can order now. */
    val ENABLED_SERVICES: Set<ServiceType> = setOf(ServiceType.TAXI, ServiceType.INTERCITY, ServiceType.FOOD)

    fun isServiceEnabled(type: ServiceType): Boolean = type in ENABLED_SERVICES
}
