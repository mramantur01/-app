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
    /** Wallet balance, top-up and referral bonus. Off: there is no real payment provider yet. */
    const val WALLET = false

    /** Paying in the app (MBank, O!Деньги, Элкарт, cards, ELQR). Off: only cash to the driver. */
    const val ONLINE_PAYMENTS = false

    /** Owner payouts to a bank. Off: the payout key must live on a server, not in the APK. */
    const val OWNER_PAYOUTS = false

    /** AI route chat and AI tour guide. */
    const val AI_ASSISTANT = false

    /** Voice commands. */
    const val VOICE_COMMANDS = false

    /** Family accounts with spending limits (contacts are still used for SOS). */
    const val FAMILY_LIMITS = false

    /** Driver shift passes (paid subscriptions). */
    const val DRIVER_SHIFT_PASSES = false

    /** Services passengers can order now. */
    val ENABLED_SERVICES: Set<ServiceType> = setOf(ServiceType.TAXI, ServiceType.INTERCITY)

    fun isServiceEnabled(type: ServiceType): Boolean = type in ENABLED_SERVICES
}
