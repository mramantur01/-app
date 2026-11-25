package com.example.data.model

import com.example.data.firebase.RideStatus
import com.example.data.firebase.SharedRide
import com.example.util.OwnerPayoutGatewayClient

/**
 * Passenger's request to change the route during a trip. It changes the ride only after
 * the driver accepts it. status: "pending", "accepted" or "rejected".
 */
data class RouteRequest(
    val dropoffAddress: String,
    val dropoffLat: Double,
    val dropoffLng: Double,
    val stopAddress: String? = null,
    val stopLat: Double? = null,
    val stopLng: Double? = null,
    val priceSom: Int,
    val status: String = "pending"
) {
    fun toMap(): Map<String, Any> = buildMap {
        put("dropoffAddress", dropoffAddress.take(200))
        put("dropoffLat", dropoffLat)
        put("dropoffLng", dropoffLng)
        if (!stopAddress.isNullOrBlank() && stopLat != null && stopLng != null) {
            put("stopAddress", stopAddress.take(200))
            put("stopLat", stopLat)
            put("stopLng", stopLng)
        }
        put("priceSom", priceSom)
        put("status", status)
    }

    companion object {
        fun from(m: Map<*, *>?): RouteRequest? {
            if (m == null) return null
            return RouteRequest(
                dropoffAddress = m["dropoffAddress"] as? String ?: return null,
                dropoffLat = (m["dropoffLat"] as? Number)?.toDouble() ?: return null,
                dropoffLng = (m["dropoffLng"] as? Number)?.toDouble() ?: return null,
                stopAddress = m["stopAddress"] as? String,
                stopLat = (m["stopLat"] as? Number)?.toDouble(),
                stopLng = (m["stopLng"] as? Number)?.toDouble(),
                priceSom = (m["priceSom"] as? Number)?.toInt() ?: return null,
                status = m["status"] as? String ?: "pending"
            )
        }

        /**
         * New price for a changed route: the old price plus the tariff's per-km price for the extra
         * distance (or minus it for a shorter route), never below the tariff's start price.
         */
        fun newPrice(
            oldPrice: Int,
            pickup: GeoPoint,
            oldDropoff: GeoPoint,
            newDropoff: GeoPoint,
            stop: GeoPoint?,
            perKmSom: Int,
            baseSom: Int
        ): Int {
            val road = 1.3 // straight line to road distance
            val oldKm = KgGeoCatalog.haversineKm(pickup, oldDropoff) * road
            val newKm = (if (stop != null) KgGeoCatalog.haversineKm(pickup, stop) + KgGeoCatalog.haversineKm(stop, newDropoff)
            else KgGeoCatalog.haversineKm(pickup, newDropoff)) * road
            val stopFee = if (stop != null) 30 else 0
            return (oldPrice + ((newKm - oldKm) * perKmSom).toInt() + stopFee).coerceAtLeast(baseSom).coerceAtMost(100000)
        }
    }
}

/** Money between the driver and the service. Debt > 0: the driver owes the service. */
data class DriverBalance(
    val commissionSom: Int = 0,
    val cardPayoutSom: Int = 0,
    val bonusSom: Int = 0,
    val bonusDays: Int = 0,
    val paidSom: Int = 0
) {
    val debtSom: Int get() = commissionSom - cardPayoutSom - bonusSom - paidSom

    companion object {
        /**
         * Same formula in the driver app and in the owner panel.
         * Cash ride: the driver keeps the money and owes the commission.
         * Card ride: the service got the money and owes the driver his part.
         * Bonus: for each day with at least [goalTrips] trips the driver gets [goalBonusSom].
         */
        fun compute(rides: List<SharedRide>, commissionPercent: Int, goalTrips: Int, goalBonusSom: Int, paidSom: Int): DriverBalance {
            val done = rides.filter { it.status == RideStatus.COMPLETED }
            var commission = 0
            var cardPayout = 0
            done.forEach { r ->
                val split = OwnerPayoutGatewayClient.calculateOrderSplit(r.priceSom, commissionPercent)
                if (r.paymentMethod == "CARD") cardPayout += split.driverNetSom
                else commission += r.priceSom - split.driverNetSom
            }
            val days = if (goalTrips > 0 && goalBonusSom > 0) {
                done.groupBy { dayKey(it.createdAtMillis) }.count { (_, list) -> list.size >= goalTrips }
            } else 0
            return DriverBalance(commission, cardPayout, days * goalBonusSom, days, paidSom)
        }

        fun dayKey(millis: Long): Int {
            val c = java.util.Calendar.getInstance().apply { timeInMillis = millis }
            return c.get(java.util.Calendar.YEAR) * 1000 + c.get(java.util.Calendar.DAY_OF_YEAR)
        }
    }
}

/** Reasons a driver can give when cancelling an accepted order. */
val DRIVER_CANCEL_REASONS = listOf(
    "Пассажир не вышел",
    "Пассажир попросил отменить",
    "Слишком далеко ехать",
    "Неверный адрес подачи",
    "Поломка или ДТП",
    "Пассажир ведёт себя опасно",
    "Другая причина"
)
