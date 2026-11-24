package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class AppRole { PASSENGER, DRIVER, DISPATCHER }

enum class ServiceType { TAXI, HOURLY, COURIER, CARGO, INTERCITY, FOOD }

enum class PickupSpeed(val multiplier: Double, val flatDeltaSom: Int) {
    WAIT_AND_SAVE(0.85, 0),
    STANDARD(1.0, 0),
    PRIORITY(1.0, 30)
}

enum class PaymentMethod(val code: String, val displayName: String, val badge: String) {
    CASH("CASH", "Наличные", "KGS"),
    MBANK("MBANK", "MBank", "QR / Счёт"),
    ODENGI("ODENGI", "O!Деньги", "Кошелёк"),
    ELCART("ELCART", "Элкарт", "Нац. карта"),
    CARD("CARD", "Visa / Mastercard", "•••• 4821");

    companion object {
        fun fromCode(code: String?): PaymentMethod =
            entries.firstOrNull { it.code == code } ?: MBANK
    }
}

enum class TripStatus {
    SEARCHING, ASSIGNED, ARRIVED, IN_PROGRESS, COMPLETED, CANCELLED;

    companion object {
        fun fromName(raw: String?): TripStatus =
            entries.firstOrNull { it.name == raw } ?: COMPLETED
    }
}

// --- FIRESTORE DATA MODELS (@Keep + @IgnoreExtraProperties + defaults, NO is-prefixed Booleans) ---

@Keep
@IgnoreExtraProperties
data class UserProfileDto(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "Гость KG Taxi",
    val phone: String = "+996 555 010 996",
    val email: String = "",
    val homeAddress: String = "пр. Чуй 155, Бишкек",
    val workAddress: String = "ТРЦ Asia Mall, пр. Ч. Айтматова 3",
    val paymentMethod: String = "MBANK",
    val kgOneActive: Boolean = false,
    val businessProfileActive: Boolean = false,
    val companyName: String = "Ala-Too Solutions",
    val companyEmail: String = "receipts@company.kg",
    val bonusPoints: Long = 250L,
    val preferredCity: String = "Бишкек",
    val language: String = "RU",
    val darkTheme: Boolean = false,
    val soundEnabled: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class TripRecordDto(
    @DocumentId val id: String = "",
    val userId: String = "",
    val serviceType: String = "TAXI",
    val tariffName: String = "Комфорт",
    val city: String = "Бишкек",
    val pickupAddress: String = "",
    val dropoffAddress: String = "",
    val distanceKm: Double = 0.0,
    val durationMin: Long = 0L,
    val totalPriceSom: Long = 0L,
    val paymentMethod: String = "MBANK",
    val status: String = "COMPLETED",
    val driverName: String = "",
    val driverCar: String = "",
    val safetyPin: String = "4829",
    val rating: Long = 0L,
    val tipSom: Long = 0L,
    val businessTrip: Boolean = false,
    val lostItemNote: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class SavedPlaceDto(
    @DocumentId val id: String = "",
    val userId: String = "",
    val label: String = "",
    val address: String = "",
    val city: String = "Бишкек",
    val lat: Double = 42.8746,
    val lng: Double = 74.5698,
    val category: String = "FAVORITE",
    val rating: Double = 4.8,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FamilyMemberDto(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val relation: String = "Teen",
    val phone: String = "",
    val monthlyLimitSom: Long = 10000L,
    val teenControlEnabled: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class VehicleMaintenanceDto(
    @DocumentId val id: String = "",
    val userId: String = "",
    val vehicleName: String = "Toyota Camry 70",
    val serviceTitle: String = "Замена моторного масла",
    val dueMileageKm: Long = 95000L,
    val currentMileageKm: Long = 92400L,
    val fuelLitersPer100Km: Double = 8.5,
    val completed: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

// --- UI / INTERACTIVE MODELS ---

data class PriceBreakdown(
    val baseSom: Int,
    val distanceSom: Int,
    val surgeSom: Int,
    val speedDeltaSom: Int,
    val kgOneDiscountSom: Int,
    val promoDiscountSom: Int,
    val bonusUsedSom: Int,
    val finalTotalSom: Int,
    val splitCount: Int,
    val waypointsSom: Int = 0,
    val meetAndGreetSom: Int = 0,
    val carpoolDiscountSom: Int = 0,
    val kgPlusCashbackSom: Int = 0
) {
    val perPersonSom: Int get() = if (splitCount > 1) (finalTotalSom + splitCount - 1) / splitCount else finalTotalSom
}

enum class AppThemeMode(val label: String) {
    SYSTEM("Авто"),
    LIGHT("Светлая"),
    DARK("Тёмная")
}

enum class MapLayerStyle(val label: String) {
    STANDARD("Схема"),
    SATELLITE("Спутник"),
    NIGHT("Ночная")
}

enum class DriverShiftPass(val id: String, val title: String, val subtitle: String, val hours: Int, val costSom: Int) {
    NONE("none", "Комиссия 14%", "Оплата % с каждого заказа", 0, 0),
    SHIFT_12H("shift_12h", "Смена 12 часов", "0% комиссии сервиса • выгодно при 10+ заказах", 12, 450),
    SHIFT_24H("shift_24h", "Смена 24 часа", "0% комиссии на сутки • максимум выгоды", 24, 790)
}

data class HourlyDemandPoint(
    val hourLabel: String,
    val demandFraction: Float, // 0.2f..1.0f
    val surgeLabel: String,
    val hotDistrict: String,
    val isCurrentHour: Boolean = false
)

data class IntercityScheduledTrip(
    val id: String,
    val routeId: String,
    val departureTime: String,
    val driverName: String,
    val driverRating: Double,
    val carModel: String,
    val totalSeats: Int = 6,
    val occupiedSeats: Set<Int>
) {
    val freeSeatsCount: Int get() = (totalSeats - occupiedSeats.size).coerceAtLeast(0)
}

enum class DisplayCurrency(val code: String, val symbol: String, val somPerUnit: Double) {
    KGS("KGS", "сом", 1.0),
    USD("USD", "$", 87.5),
    RUB("RUB", "₽", 0.92),
    KZT("KZT", "₸", 0.17),
    EUR("EUR", "€", 95.0);

    fun formatSom(amountSom: Int): String {
        if (this == KGS) return "$amountSom сом"
        val converted = amountSom / somPerUnit
        val formatted = if (this == KZT || this == RUB) {
            "${converted.toInt()}$symbol"
        } else {
            String.format(java.util.Locale.US, "%s%.2f", symbol, converted)
        }
        return "$amountSom с (~$formatted)"
    }

    fun formatShort(amountSom: Int): String {
        if (this == KGS) return "$amountSom с"
        val converted = amountSom / somPerUnit
        return if (this == KZT || this == RUB) {
            "${converted.toInt()}$symbol"
        } else {
            String.format(java.util.Locale.US, "%s%.1f", symbol, converted)
        }
    }

    companion object {
        fun fromCode(code: String?): DisplayCurrency =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: KGS
    }
}

data class DriverBidOffer(
    val id: String,
    val driverName: String,
    val carModel: String,
    val plateNumber: String,
    val rating: Double,
    val etaMin: Int,
    val offeredPriceSom: Int
)

data class RidePreferences(
    val quietRide: Boolean = false,
    val climateAc: Boolean = true,
    val luggageHelp: Boolean = false,
    val petFriendly: Boolean = false
)

data class ActiveRideState(
    val tripId: String,
    val serviceType: ServiceType,
    val tariffName: String,
    val city: String,
    val pickupAddress: String,
    val dropoffAddress: String,
    val pickupPoint: GeoPoint,
    val dropoffPoint: GeoPoint,
    val distanceKm: Double,
    val durationMin: Int,
    val totalPriceSom: Int,
    val paymentMethod: PaymentMethod,
    val status: TripStatus,
    val driverName: String,
    val driverCar: String,
    val driverRating: Double,
    val driverEtaMin: Int,
    val safetyPin: String,
    val progress: Float, // 0f..1f animation along route
    val rideCheckAlert: Boolean = false,
    val businessTrip: Boolean = false,
    val splitCount: Int = 1,
    val scheduledLabel: String? = null,
    val pickupSpotNote: String? = null,
    val recipientName: String? = null,
    val recipientPhone: String? = null,
    val waypoints: List<KgPlace> = emptyList(),
    val waitingSeconds: Int = 0,
    val meetAndGreetSignText: String? = null,
    val courierParcelPhotoUri: String? = null,
    /** Driver phone for a direct bank transfer (real rides only). */
    val driverPayPhone: String? = null,
    /** Live position of the driver, shared every ~10 s during a real ride. */
    val driverPoint: GeoPoint? = null
)

data class NearbyDriverMarker(
    val id: String,
    val point: GeoPoint,
    val headingDeg: Float,
    val isEv: Boolean
)

enum class DriverOrderStage {
    EN_ROUTE_TO_PICKUP,
    WAITING_AT_PICKUP,
    IN_TRIP
}

data class DriverIncomingOrder(
    val id: String,
    val passengerName: String,
    val passengerRating: Double,
    val serviceType: ServiceType,
    val tariffName: String,
    val pickupAddress: String,
    val dropoffAddress: String,
    val distanceKm: Double,
    val durationMin: Int,
    val grossFareSom: Int,
    val upfrontNetEarningsSom: Int,
    val surgeLabel: String? = null,
    val city: String = "Бишкек",
    val pickupPoint: GeoPoint = GeoPoint(42.8764, 74.6037),
    val dropoffPoint: GeoPoint = GeoPoint(42.8559, 74.5855),
    val assignedDriverName: String? = null,
    val meetAndGreetSign: String? = null,
    val pickupEtaMin: Int = 4,
    val pickupDistanceKm: Double = 1.4,
    val paymentMethodLabel: String = "MBank",
    val passengerPin: String = "4829"
)

data class FoodCartEntry(
    val venueId: String,
    val venueName: String,
    val item: FoodMenuItem,
    val quantity: Int
)

data class AiChatMessage(
    val id: String,
    val fromUser: Boolean,
    val text: String,
    val suggestedDestination: String? = null,
    val isStreaming: Boolean = false
)

data class DriverChatMessage(
    val id: String,
    val fromPassenger: Boolean,
    val originalText: String,
    val translatedText: String? = null,
    val timeLabel: String = "Сейчас"
)

data class WalletTransaction(
    val id: String,
    val title: String,
    val method: String,
    val amountSom: Int, // positive = top-up/bonus, negative = ride payment
    val timeLabel: String
)

data class DriverDocumentItem(
    val id: String,
    val title: String,
    val number: String,
    val validUntil: String,
    val daysRemaining: Int
)

data class EmergencySosAlert(
    val id: String,
    val passengerName: String,
    val driverName: String,
    val carInfo: String,
    val city: String,
    val locationAddress: String,
    val timeLabel: String,
    val resolved: Boolean = false
)

data class CityWeatherInfo(
    val cityName: String,
    val tempC: Int,
    val windKmh: Int,
    val conditionLabel: String,
    val aqiUs: Int,
    val aqiStatus: String,
    val isLive: Boolean
)

data class KgMountainPass(
    val id: String,
    val name: String,
    val highway: String,
    val statusLabel: String,
    val isCaution: Boolean,
    val tempC: Int,
    val advisory: String
)

data class VoiceOrderIntent(
    val rawTranscript: String,
    val pickupQuery: String? = null,
    val dropoffQuery: String? = null,
    val tariffId: String? = null,
    val quietRide: Boolean? = null,
    val climateAc: Boolean? = null,
    val luggageHelp: Boolean? = null,
    val petFriendly: Boolean? = null,
    val businessTrip: Boolean? = null,
    val summary: String
)

data class LostItemTicket(
    val id: String,
    val tripId: String,
    val driverName: String,
    val itemCategory: String,
    val note: String,
    val status: String,
    val createdLabel: String
)

data class DriverTipEntry(
    val id: String,
    val passengerName: String,
    val amountSom: Int,
    val compliment: String,
    val timeLabel: String
)

data class DriverPayoutRecord(
    val id: String,
    val bankName: String,
    val targetAccount: String,
    val amountSom: Int,
    val timeLabel: String
)

data class OwnerBankConfig(
    val bankName: String = "MBank",
    val accountOrCard: String = "",
    val beneficiaryName: String = "ИП KG Taxi Владелец",
    val inn: String = "",
    val bik: String = "103001",
    val commissionPercent: Int = 14,
    val autoPayoutEnabled: Boolean = true,
    val instantPerOrderSplit: Boolean = true,
    val autoPayoutThresholdSom: Int = 500
) {
    val isConfigured: Boolean
        get() = accountOrCard.trim().length >= 8 && beneficiaryName.trim().length >= 2
}

data class OwnerCommissionEntry(
    val id: String,
    val orderId: String,
    val sourceLabel: String,
    val city: String,
    val grossAmountSom: Int,
    val commissionPercent: Int,
    val ownerCommissionSom: Int,
    val driverNetSom: Int,
    val settledAutomatically: Boolean,
    val destinationMasked: String,
    val timeLabel: String
)

data class OwnerBankSettlementRecord(
    val id: String,
    val bankName: String,
    val destinationMasked: String,
    val amountSom: Int,
    val entriesCount: Int,
    val automatic: Boolean,
    val liveGatewayDelivered: Boolean,
    val referenceCode: String,
    val timeLabel: String
)

