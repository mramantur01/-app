package com.example.ui

import com.example.util.DriverWorkPrefs
import com.example.util.VoiceAnnouncer

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.FeatureFlags
import com.example.data.firebase.AiRouteAssistant
import com.example.data.firebase.AiStreamEvent
import com.example.data.firebase.FirestoreSyncRepository
import com.example.data.firebase.RideRepository
import com.example.data.firebase.PushTokenRegistry
import com.example.data.firebase.RideStatus
import com.example.util.formatDeparture
import com.example.data.firebase.SharedRide
import com.example.data.local.AppPreferencesStore
import com.example.data.local.KgTaxiDao
import com.example.data.local.KgTaxiDatabase
import com.example.data.local.LocalFamilyMemberEntity
import com.example.data.local.LocalMaintenanceEntity
import com.example.data.local.LocalProfileEntity
import com.example.data.local.LocalSavedPlaceEntity
import com.example.data.local.LocalTripEntity
import com.example.data.model.*
import com.example.i18n.AppLanguage
import com.example.util.EmergencyHelper
import com.example.util.KgWeatherAndPassesClient
import com.example.util.KyrgyzstanRoutingClient
import com.example.util.LocationAndVoiceHelper
import com.example.util.OwnerPayoutGatewayClient
import com.example.util.SoundAndNotificationManager
import com.example.util.SoundCue
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

data class KgTaxiUiState(
    val role: AppRole = AppRole.PASSENGER,
    /** Roles this account may open. Comes from roles/{uid} in Firestore; everyone is a passenger. */
    val allowedRoles: Set<AppRole> = setOf(AppRole.PASSENGER),
    /** Driver application dialog is open. */
    val driverApplicationOpen: Boolean = false,
    /** "pending" / "approved" / "rejected" / null — the signed-in user's driver application. */
    val driverApplicationStatus: String? = null,
    val driverApplicationSending: Boolean = false,
    /** Verified car and plate of this driver (from roles/{uid}). */
    val driverCar: String = "",
    val driverPlate: String = "",
    /** Rides this driver took (newest first), from the database. */
    val driverHistory: List<SharedRide> = emptyList(),
    /** Average of passengers' stars for this driver; null until the first rating. */
    val driverRatingAvg: Double? = null,
    val driverRatingCount: Int = 0,
    val profile: LocalProfileEntity = LocalProfileEntity(),
    val language: AppLanguage = AppLanguage.RU,
    val darkTheme: Boolean = false,
    val soundEnabled: Boolean = true,
    val offlineMode: Boolean = false,
    val isSignedIn: Boolean = false,
    val signedInUid: String? = null,
    val cloudSyncError: String? = null,
    val statusBanner: String? = null,
    // Map & Geo
    val selectedCity: KgCity = KgGeoCatalog.cities.first(),
    val pickupPoint: GeoPoint = GeoPoint(42.8764, 74.6037),
    val dropoffPoint: GeoPoint = GeoPoint(42.8559, 74.5855),
    val pickupAddress: String = "Площадь Ала-Тоо, Бишкек",
    val dropoffAddress: String = "ТРЦ Asia Mall, пр. Ч. Айтматова 3",
    val waypoints: List<KgPlace> = emptyList(),
    val selectedPickupSpot: String? = null,
    val showSurgeHeatmap: Boolean = false,
    val showTrafficOverlay: Boolean = false,
    val mapPickModeIsDropoff: Boolean = true,
    val routePolyline: List<GeoPoint> = emptyList(),
    val liveRouteDistanceKm: Double? = null,
    val liveRouteDurationMin: Int? = null,
    val routeFromOsrm: Boolean = false,
    val nearbyDrivers: List<NearbyDriverMarker> = emptyList(),
    // Search & Place Filters
    val searchQuery: String = "",
    val liveSearchResults: List<KgPlace> = emptyList(),
    val filterMinRating: Double = 0.0,
    val filterMaxPriceLevel: Int = 3,
    // Service & Booking Config
    val serviceType: ServiceType = ServiceType.TAXI,
    val selectedTariffId: String = "comfort",
    val pickupSpeed: PickupSpeed = PickupSpeed.STANDARD,
    val preferences: RidePreferences = RidePreferences(),
    val promoCodeInput: String = "",
    val appliedPromoPercent: Int = 0,
    val useBonusPoints: Boolean = false,
    val splitCount: Int = 1,
    val reserveTimeLabel: String? = null,
    val selectedFlight: FlightInfo? = null,
    // Order for Someone Else
    val bookingForOther: Boolean = false,
    val recipientName: String = "",
    val recipientPhone: String = "",
    // Bidding
    val biddingActive: Boolean = false,
    val userBidSom: Int = 0,
    val bidOffers: List<DriverBidOffer> = emptyList(),
    // Hourly / Courier / Cargo / Intercity / Food
    val hourlyHours: Int = 3,
    val selectedExcursion: ExcursionPreset? = null,
    val courierRecipient: String = "",
    val courierComment: String = "Документы / посылка до 5 кг",
    val cargoVehicle: String = "Портер (до 1.5 т)",
    val cargoLoaders: Int = 1,
    val selectedIntercityRoute: IntercityRoute = KgGeoCatalog.intercityRoutes.first(),
    val selectedIntercitySeats: Set<Int> = setOf(2),
    val occupiedIntercitySeats: Set<Int> = emptySet(),
    /** Pickup point is known (GPS, map or chosen address). An order can't go without it. */
    val pickupConfirmed: Boolean = false,
    /** Payment for the order: "CASH" (cash or QR) or "CARD" (linked card, when payments are connected). */
    val ridePayment: String = "CASH",
    val pickupDialogOpen: Boolean = false,
    val pickupSearchQuery: String = "",
    val pickupSearchResults: List<KgPlace> = emptyList(),
    /** Passenger sees the home menu (search + tiles); false = the order screen with the map. */
    val passengerHome: Boolean = true,
    /** Owner settings: tariff prices, commission, promo (config/app). */
    val appConfig: com.example.data.firebase.AppConfig = com.example.data.firebase.AppConfig(),
    /** Passenger's real orders, newest first. */
    val passengerRides: List<SharedRide> = emptyList(),
    val supportSending: Boolean = false,
    val supportTickets: List<com.example.data.firebase.SupportTicket> = emptyList(),
    val accountDeleting: Boolean = false,
    /** Driver documents: OSAGO and technical inspection valid until (millis), saved on the phone. */
    val osagoUntilMillis: Long? = null,
    val inspectionUntilMillis: Long? = null,
    /** A newer version is on the website (for installs outside Google Play). */
    val updateAvailable: com.example.util.UpdateInfo? = null,
    /** Registration of this account; null until loaded after sign-in. */
    val registration: com.example.data.firebase.RegistrationInfo? = null,
    /** Registration screen is open (first sign-in, or "Стать водителем"). */
    val registrationOpen: Boolean = false,
    val registrationStartAsDriver: Boolean = false,
    val registrationSending: Boolean = false,
    /** Owner account (role "admin"): sees the owner panel. */
    val isAdmin: Boolean = false,
    /** Tariffs the owner approved for this driver's car (empty = not limited). */
    val driverAllowedTariffs: Set<String> = emptySet(),
    val ownerApplications: List<com.example.data.firebase.OwnerApplication> = emptyList(),
    val ownerDrivers: List<com.example.data.firebase.OwnerDriver> = emptyList(),
    val ownerBlockedDrivers: List<com.example.data.firebase.OwnerDriver> = emptyList(),
    val ownerStats: com.example.data.firebase.OwnerDayStats = com.example.data.firebase.OwnerDayStats(),
    /** Owner stats period in days: 1 = today, 7 = week, 30 = month. */
    val ownerStatsDays: Int = 1,
    /** Photos of the application the owner opened: kind id -> base64 JPEG. */
    val ownerPhotos: Map<String, String> = emptyMap(),
    val ownerPhotosUid: String? = null,
    /** Departure of an intercity pre-order; null = "right now". */
    val intercityDepartureMillis: Long? = null,
    /** Passenger's intercity pre-orders that haven't started yet (searching or booked). */
    val passengerPreorders: List<SharedRide> = emptyList(),
    /** Open intercity pre-orders in the driver's city. */
    val driverOpenPreorders: List<SharedRide> = emptyList(),
    /** Pre-orders this driver has booked. */
    val driverBookedPreorders: List<SharedRide> = emptyList(),
    /** Week days (Calendar.DAY_OF_WEEK) the driver wants pre-order alerts for. */
    val driverPreorderAlertDays: Set<Int> = emptySet(),
    /** Only orders within this distance to the pickup, km. 0 = any. */
    val driverPickupRadiusKm: Int = 0,
    val driverVoiceAnnounce: Boolean = true,
    /** Dark map from 20:00 to 6:00. */
    val driverNightMapAuto: Boolean = true,
    val driverHomePoint: GeoPoint? = null,
    val driverHomeAddress: String = "",
    /** "Домой": only orders that bring the driver closer to home. */
    val driverHomeModeOn: Boolean = false,
    val driverHomeUsesLeft: Int = 2,
    /** Leave the line after the current trip. */
    val driverLastOrder: Boolean = false,
    val driverOnlineMinutes24h: Int = 0,
    /** Must rest until this time (12 h on the line rule). */
    val driverRestUntil: Long? = null,
    val driverBalance: com.example.data.model.DriverBalance = com.example.data.model.DriverBalance(),
    /** Where passengers are waiting for a car right now (demand map). */
    val driverDemandPoints: List<GeoPoint> = emptyList(),
    val driverShowDemand: Boolean = false,
    /** Passenger asks to change the route; the driver answers yes or no. */
    val driverRouteRequest: com.example.data.model.RouteRequest? = null,
    /** Passenger side: "pending", "accepted", "rejected" or null. */
    val passengerRouteRequestStatus: String? = null,
    val driverPhotoCheckSending: Boolean = false,
    /** In-app update: -1 = not downloading, 0..100 = progress. */
    val updateProgress: Int = -1,
    val updateError: String? = null,
    /** All open orders that pass the driver's filters (to choose from a list). */
    val driverRadarOrders: List<DriverIncomingOrder> = emptyList(),
    val driverMinPassengerRating: Double = 0.0,
    val driverMinTripKm: Int = 0,
    /** The driver also takes food and grocery deliveries. */
    val driverTakesDelivery: Boolean = false,
    /** Turn-by-turn guidance for the accepted order: to the passenger, then to point B. */
    val driverNav: com.example.util.NavGuidance? = null,
    val driverNavLine: List<GeoPoint> = emptyList(),
    val driverPosition: GeoPoint? = null,
    /** Courier (approved "Курьер" tariff): always gets food and grocery orders; other drivers switch them on themselves. */
    val isCourier: Boolean = false,
    val driverDeafMode: Boolean = false,
    val driverAcceptancePercent: Int? = null,
    /** Taxi ordered for later (millis), null = now. */
    val taxiScheduledMillis: Long? = null,
    /** Share of accepted orders the driver cancelled (last 100), percent. */
    val driverCancelRatePercent: Int = 0,
    /** Special needs chosen for the next order (keys of ACCESSIBILITY_OPTIONS). */
    val passengerAccessibility: Set<String> = emptySet(),
    /** Family and trusted contacts. */
    val passengerPeople: List<com.example.util.ClosePerson> = emptyList(),
    /** Owner panel: each driver's balance, by uid. */
    val ownerDriverBalances: Map<String, com.example.data.model.DriverBalance> = emptyMap(),
    val selectedVenueId: String = KgGeoCatalog.foodVenues.first().id,
    val foodCart: List<FoodCartEntry> = emptyList(),
    // Active Trip, Driver Chat & Post-Trip
    val activeRide: ActiveRideState? = null,
    val driverChatMessages: List<DriverChatMessage> = emptyList(),
    val chatTranslateTargetLang: String = "EN",
    val chatTranslating: Boolean = false,
    val postTripReceipt: LocalTripEntity? = null,
    // Wallet, Referral & Onboarding
    val showOnboarding: Boolean = false,
    val walletBalanceSom: Int = 0,
    val referralClaimed: Boolean = false,
    val walletTransactions: List<WalletTransaction> = emptyList(),
    // Driver Mode, Docs & Analytics
    val driverOnline: Boolean = false,
    val driverTodayEarningsSom: Int = 0,
    val driverShiftHours: Double = 0.0,
    val driverWeeklyEarnings: List<Pair<String, Int>> = listOf(
        "Пн" to 0, "Вт" to 0, "Ср" to 0, "Чт" to 0, "Пт" to 0, "Сб" to 0, "Вс" to 0
    ),
    val driverDocuments: List<DriverDocumentItem> = emptyList(),
    val driverQuestTripsDone: Int = 0,
    val driverQuestTarget: Int = 12,
    val driverIncomingOrders: List<DriverIncomingOrder> = emptyList(),
    val driverAcceptedOrder: DriverIncomingOrder? = null,
    val dispatcherActiveOrders: List<DriverIncomingOrder> = emptyList(),
    val sosAlerts: List<EmergencySosAlert> = emptyList(),
    // Fuel Calculator
    val fuelDistanceKm: String = "120",
    val fuelConsumptionPer100: String = "8.5",
    val fuelPriceSom: String = "74",
    // Persisted Lists (Room + Firestore Sync)
    val trips: List<LocalTripEntity> = emptyList(),
    val savedPlaces: List<LocalSavedPlaceEntity> = emptyList(),
    val familyMembers: List<LocalFamilyMemberEntity> = emptyList(),
    val maintenanceLogs: List<LocalMaintenanceEntity> = emptyList(),
    // AI Route Chat
    val aiMessages: List<AiChatMessage> = listOf(
        AiChatMessage(
            id = "welcome",
            fromUser = false,
            text = "Салам! Я ИИ-маршрутизатор KG Taxi. Помогу спланировать маршрут по Бишкеку и всему Кыргызстану, рассчитать поездку на Иссык-Куль, Ала-Арчу или в аэропорт Манас."
        )
    ),
    val aiLoading: Boolean = false,
    // Tourist Currency, Business INN, Weather & Mountain Passes
    val displayCurrency: DisplayCurrency = DisplayCurrency.KGS,
    val businessCompanyInn: String = "",
    val cityWeather: CityWeatherInfo = KgWeatherAndPassesClient.offlineFallbackWeather("Бишкек"),
    val mountainPasses: List<KgMountainPass> = KgWeatherAndPassesClient.buildMountainPasses(
        KgWeatherAndPassesClient.offlineFallbackWeather("Бишкек")
    ),
    // Meet & Greet Airport Sign, Courier Parcel Photo, Voice-to-Ride AI
    val meetAndGreetEnabled: Boolean = false,
    val meetAndGreetSignText: String = "",
    val courierParcelPhotoUri: String? = null,
    val voiceOrderParsing: Boolean = false,
    val lastVoiceOrderSummary: String? = null,
    // Favorite / Blocked Drivers & Lost & Found Tickets
    val favoriteDrivers: Set<String> = emptySet(),
    val blockedDrivers: Set<String> = emptySet(),
    val lostItemTickets: List<LostItemTicket> = emptyList(),
    // Driver Pro: Destination Filter, Tips & Instant Payouts
    val driverHomeFilterEnabled: Boolean = false,
    val driverHomeTargetDistrict: String = "Джал",
    val driverOrderStage: DriverOrderStage = DriverOrderStage.EN_ROUTE_TO_PICKUP,
    val driverOrderWaitingSeconds: Int = 0,
    val driverActivityScore: Int = 100,
    val driverPriorityPoints: Int = 0,
    val driverAutoAccept: Boolean = false,
    val driverEnabledTariffs: Set<String> = setOf("econom", "comfort", "comfort_plus", "business", "minivan"),
    val driverPendingPassengerRating: DriverIncomingOrder? = null,
    val driverPhotoControlVerified: Boolean = true,
    val driverTips: List<DriverTipEntry> = emptyList(),
    val driverPayouts: List<DriverPayoutRecord> = emptyList(),
    // 12-Feature Ecosystem Upgrade State
    val themeMode: AppThemeMode = AppThemeMode.LIGHT,
    val mapLayerStyle: MapLayerStyle = MapLayerStyle.STANDARD,
    val carpoolModeEnabled: Boolean = false,
    val driverBackToBackOffer: DriverIncomingOrder? = null,
    val driverQueuedNextOrder: DriverIncomingOrder? = null,
    val driverShiftPass: DriverShiftPass = DriverShiftPass.NONE,
    val driverCoffeeBreakActive: Boolean = false,
    val driverDrivingMinutesToday: Int = 0,
    val driverActiveOrderMessages: List<DriverChatMessage> = emptyList(),
    val selectedIntercityTripId: String? = "ic1_dep2",
    val tourGuideTopic: String? = null,
    val tourGuideText: String? = null,
    val tourGuideLoading: Boolean = false,
    // Owner Bank Details & Automated Commission Split
    val ownerBankConfig: OwnerBankConfig = OwnerBankConfig(),
    val ownerPendingBalanceSom: Int = 0,
    val ownerTotalSettledSom: Int = 0,
    val ownerCommissionLedger: List<OwnerCommissionEntry> = emptyList(),
    val ownerSettlements: List<OwnerBankSettlementRecord> = listOf(
        OwnerBankSettlementRecord(
            id = "os_seed_1",
            bankName = "MBank",
            destinationMasked = "•••• 9960",
            amountSom = 6840,
            entriesCount = 42,
            automatic = true,
            liveGatewayDelivered = false,
            referenceCode = "KGT-AUTO-88412",
            timeLabel = "Вчера, 23:55"
        )
    ),
    val ownerPayoutBusy: Boolean = false
) {
    val referralCode: String
        get() {
            val clean = profile.displayName.uppercase().filter { it in 'A'..'Z' || it in '0'..'9' }.take(4).ifBlank { "KG" }
            return "KG-${clean}250"
        }
    val routeDistanceKm: Double
        get() = when (serviceType) {
            ServiceType.INTERCITY -> selectedIntercityRoute.distanceKm
            ServiceType.HOURLY -> selectedExcursion?.distanceKm ?: (hourlyHours * 18.0)
            else -> liveRouteDistanceKm ?: KgGeoCatalog.haversineKm(pickupPoint, dropoffPoint)
        }

    val routeDurationMin: Int
        get() = when (serviceType) {
            ServiceType.INTERCITY -> selectedIntercityRoute.durationMin
            ServiceType.HOURLY -> (selectedExcursion?.hours ?: hourlyHours) * 60
            else -> liveRouteDurationMin ?: KgGeoCatalog.estimateDurationMin(routeDistanceKm)
        }

    val selectedTariff: TaxiTariff
        get() = priced(
            KgGeoCatalog.taxiTariffs.firstOrNull { it.id == selectedTariffId } ?: KgGeoCatalog.taxiTariffs[1]
        )

    /** Intercity route with the owner's seat price, if set. */
    fun pricedRoute(r: IntercityRoute): IntercityRoute =
        appConfig.intercity[r.id]?.let { r.copy(seatPriceSom = it) } ?: r

    /** Tariff with the owner's current prices (from the owner panel), if set. */
    fun priced(t: TaxiTariff): TaxiTariff =
        appConfig.tariffs[t.id]?.let { t.copy(baseSom = it.baseSom, perKmSom = it.perKmSom) } ?: t

    val priceBreakdown: PriceBreakdown
        get() {
            val base: Int
            val distCost: Int
            when (serviceType) {
                ServiceType.TAXI -> {
                    val t = selectedTariff
                    base = t.baseSom
                    distCost = (routeDistanceKm * t.perKmSom).roundToInt()
                }
                ServiceType.HOURLY -> {
                    base = selectedExcursion?.priceSom ?: (hourlyHours * 650)
                    distCost = 0
                }
                ServiceType.COURIER -> {
                    base = 120
                    distCost = (routeDistanceKm * 18).roundToInt()
                }
                ServiceType.CARGO -> {
                    val vehicleBase = when {
                        cargoVehicle.contains("Газель") -> 900
                        cargoVehicle.contains("Спринтер") -> 750
                        else -> 550
                    }
                    base = vehicleBase + cargoLoaders * 350
                    distCost = (routeDistanceKm * 38).roundToInt()
                }
                ServiceType.INTERCITY -> {
                    val seats = selectedIntercitySeats.ifEmpty { setOf(2) }
                    base = seats.sumOf { seat ->
                        pricedRoute(selectedIntercityRoute).seatPriceSom +
                            if (seat == 1) selectedIntercityRoute.frontSeatExtraSom else 0
                    }
                    distCost = 0
                }
                ServiceType.FOOD -> {
                    val foodSum = foodCart.sumOf { it.item.priceSom * it.quantity }
                    val delivery = if ((FeatureFlags.WALLET && profile.kgOneActive) || foodCart.isEmpty()) 0 else 110
                    base = foodSum
                    distCost = delivery
                }
            }

            val waypointsCost = if (serviceType == ServiceType.TAXI || serviceType == ServiceType.COURIER) {
                waypoints.size * 45
            } else 0
            val meetAndGreetCost = if (meetAndGreetEnabled && serviceType == ServiceType.TAXI) {
                if ((FeatureFlags.WALLET && profile.kgOneActive)) 0 else 150
            } else 0
            val rawSubtotal = base + distCost + waypointsCost + meetAndGreetCost
            val surgeExtraRaw = if (serviceType == ServiceType.TAXI && selectedCity.effectiveSurge > 1.0) {
                ((rawSubtotal * (selectedCity.effectiveSurge - 1.0))).roundToInt()
            } else 0
            // KG Plus protects against 50% of surge during rush hour
            val surgeExtra = if ((FeatureFlags.WALLET && profile.kgOneActive)) (surgeExtraRaw * 0.5).roundToInt() else surgeExtraRaw
            val withSurge = rawSubtotal + surgeExtra
            val speedDelta = if (serviceType == ServiceType.TAXI) {
                val flatPriority = if (pickupSpeed == PickupSpeed.PRIORITY && (FeatureFlags.WALLET && profile.kgOneActive)) 0 else pickupSpeed.flatDeltaSom
                ((withSurge * (pickupSpeed.multiplier - 1.0)).roundToInt()) + flatPriority
            } else 0
            val afterSpeed = max(50, withSurge + speedDelta)
            val carpoolDisc = if (serviceType == ServiceType.TAXI && carpoolModeEnabled) {
                (afterSpeed * 0.30).roundToInt()
            } else 0
            val afterCarpool = max(45, afterSpeed - carpoolDisc)
            val kgOneDisc = if (FeatureFlags.WALLET && profile.kgOneActive && serviceType != ServiceType.FOOD) {
                (afterCarpool * 0.07).roundToInt()
            } else 0
            val promoDisc = if (appliedPromoPercent > 0) {
                (afterCarpool * appliedPromoPercent / 100.0).roundToInt()
            } else 0
            val afterDisc = max(40, afterCarpool - kgOneDisc - promoDisc)
            val bonusUsed = if (useBonusPoints) minOf(profile.bonusPoints, afterDisc / 2) else 0
            val total = max(40, afterDisc - bonusUsed)
            val cashback = if ((FeatureFlags.WALLET && profile.kgOneActive)) max(10, (total * 0.10).roundToInt()) else max(5, (total * 0.05).roundToInt())

            return PriceBreakdown(
                baseSom = base,
                distanceSom = distCost,
                surgeSom = surgeExtra,
                speedDeltaSom = speedDelta,
                kgOneDiscountSom = kgOneDisc,
                promoDiscountSom = promoDisc,
                bonusUsedSom = bonusUsed,
                finalTotalSom = total,
                splitCount = splitCount,
                waypointsSom = waypointsCost,
                meetAndGreetSom = meetAndGreetCost,
                carpoolDiscountSom = carpoolDisc,
                kgPlusCashbackSom = cashback
            )
        }
}
/** Delivery of food or groceries by a courier, the goods are paid separately. */
const val FOOD_DELIVERY_FEE_SOM = 150

/** Order marks in the ride comment (the driver sees them). */
const val PRIORITY_MARK = "Приоритет: подача быстрее"
const val WAIT_SAVE_MARK = "Подождать: дешевле, подача позже"

/** Drivers repeat the photo check of the car and themselves this often. */
const val PHOTO_CHECK_DAYS = 30

class KgTaxiViewModel(
    private val appContext: Context,
    private val dao: KgTaxiDao,
    private val firestoreSync: FirestoreSyncRepository,
    private val aiAssistant: AiRouteAssistant,
    private val prefsStore: AppPreferencesStore,
    private val rides: RideRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(KgTaxiUiState())
    val uiState: StateFlow<KgTaxiUiState> = _uiState.asStateFlow()

    private var rideSimulationJob: Job? = null

    // Real rides (Firestore). Null when the current ride is a local demo ride.
    private var passengerRideId: String? = null
    private var passengerRideJob: Job? = null
    private var driverRideId: String? = null
    private var driverOpenRidesJob: Job? = null
    private var driverLocationJob: Job? = null
    private var driverRideWatchJob: Job? = null
    private var dispatcherFeedJob: Job? = null
    private var passengerChatJob: Job? = null
    private var driverChatJob: Job? = null
    private var driverHistoryJob: Job? = null
    private var passengerPreordersJob: Job? = null
    private val registrationRepo by lazy { com.example.data.firebase.RegistrationRepository(appContext) }
    private val ownerRepo by lazy { com.example.data.firebase.OwnerRepository(appContext) }
    private val ownerJobs = mutableListOf<Job>()
    private var configJob: Job? = null
    private var applicationWatchJob: Job? = null
    private val configRepo by lazy { com.example.data.firebase.AppConfigRepository(appContext) }
    private val supportRepo by lazy { com.example.data.firebase.SupportRepository(appContext) }
    private val referralRepo by lazy { com.example.data.firebase.ReferralRepository(appContext) }

    /** My invite code for "Пригласи друга". */
    fun myInviteCode(): String? = referralRepo.myCode()
    private val deletionRepo by lazy { com.example.data.firebase.AccountDeletionRepository(appContext) }
    private val appWatcher by lazy { com.example.data.firebase.DriverApplicationWatcher(appContext) }
    private val docPrefs by lazy { appContext.getSharedPreferences("driver_docs", Context.MODE_PRIVATE) }
    private var driverPreordersJob: Job? = null
    private var verifiedDriverCar: String = ""
    private var verifiedDriverPlate: String = ""
    private var verifiedDriverPayPhone: String = ""
    private var driverWaitingTimerJob: Job? = null
    private var routeFetchJob: Job? = null
    private var addressSearchJob: Job? = null
    private var cloudObserverJobs: MutableList<Job> = mutableListOf()

    init {
        _uiState.update { it.copy(passengerPeople = com.example.util.PassengerPeople.load(appContext)) }
        SoundAndNotificationManager.ensureChannels(appContext)
        seedAndObserveLocalDatabase()
        observePreferences()
        loadDriverDocs()
        checkForUpdate()
        startDriverNavigationLoop()
        com.example.util.AppUpdater.scheduleDailyCheck(appContext)
        generateNearbyDrivers(_uiState.value.selectedCity)
        generateDriverIncomingOrders(_uiState.value.selectedCity)
        _uiState.update { it.copy(offlineMode = !LocationAndVoiceHelper.isNetworkAvailable(appContext)) }
        refreshLiveRoadRoute()
        refreshCityWeatherAndPasses()
    }

    private fun observePreferences() {
        viewModelScope.launch {
            prefsStore.prefsFlow.collect { snap ->
                _uiState.update {
                    it.copy(
                        showOnboarding = !snap.onboardingSeen,
                        walletBalanceSom = snap.walletBalanceSom,
                        referralClaimed = snap.referralClaimed,
                        displayCurrency = DisplayCurrency.fromCode(snap.displayCurrencyCode),
                        businessCompanyInn = snap.businessCompanyInn,
                        favoriteDrivers = snap.favoriteDrivers,
                        blockedDrivers = snap.blockedDrivers,
                        ownerBankConfig = snap.ownerBankConfig,
                        ownerPendingBalanceSom = snap.ownerPendingBalanceSom,
                        ownerTotalSettledSom = snap.ownerTotalSettledSom
                    )
                }
            }
        }
    }

    private fun seedAndObserveLocalDatabase() {
        viewModelScope.launch {
            if (dao.getProfileOnce() == null) {
                dao.upsertProfile(LocalProfileEntity())
            }
            // Older versions seeded made-up data on first launch (a fake family member would even
            // receive SOS text messages). Remove those demo rows if they are still on the phone.
            listOf("home_1", "work_1", "fav_1").forEach { dao.deleteSavedPlace(it) }
            dao.deleteFamilyMember("fam_demo")
            dao.deleteMaintenance("maint_1")
            dao.deleteTrip("trip_seed_1")
        }

        viewModelScope.launch {
            dao.observeProfile().filterNotNull().collect { profile ->
                val city = KgGeoCatalog.cityByName(profile.preferredCity)
                _uiState.update { state ->
                    state.copy(
                        profile = profile,
                        language = AppLanguage.fromCode(profile.language),
                        darkTheme = profile.darkTheme,
                        soundEnabled = profile.soundEnabled,
                        selectedCity = if (!cityFromGps && state.selectedCity.name != city.name) city else state.selectedCity
                    )
                }
            }
        }
        viewModelScope.launch {
            dao.observeTrips().collect { list ->
                _uiState.update { it.copy(trips = list) }
            }
        }
        viewModelScope.launch {
            dao.observeSavedPlaces().collect { list ->
                _uiState.update { it.copy(savedPlaces = list) }
            }
        }
        viewModelScope.launch {
            dao.observeFamilyMembers().collect { list ->
                _uiState.update { it.copy(familyMembers = list) }
            }
        }
        viewModelScope.launch {
            dao.observeMaintenance().collect { list ->
                _uiState.update { it.copy(maintenanceLogs = list) }
            }
        }
    }

    // --- GOOGLE AUTH & REALTIME FIRESTORE SYNC ---

    fun onUserSignedIn(uid: String, displayName: String?, email: String?, phoneNumber: String? = null) {
        if (_uiState.value.signedInUid == uid && cloudObserverJobs.isNotEmpty()) return
        stopCloudObservers()

        _uiState.update {
            it.copy(isSignedIn = true, signedInUid = uid, cloudSyncError = null, statusBanner = "Вы вошли в аккаунт")
        }
        refreshAllowedRoles()
        syncPushToken()
        startPassengerPreorders()
        refreshRegistration()
        startAppConfig()
        watchDriverApplication()
        viewModelScope.launch { referralRepo.ensureMyCode() }

        viewModelScope.launch {
            // Read the saved profile first: the screen state may still be the empty default at startup,
            // and writing it back would wipe the phone and saved addresses.
            val current = dao.getProfileOnce() ?: _uiState.value.profile
            val keepName = current.displayName.takeIf { it.isNotBlank() && it != "Пассажир" && it != "Гость" }
            val updated = current.copy(
                displayName = keepName ?: displayName?.takeIf { it.isNotBlank() } ?: current.displayName,
                email = email ?: current.email,
                phone = phoneNumber?.takeIf { it.isNotBlank() } ?: current.phone
            )
            dao.upsertProfile(updated)
            firestoreSync.syncProfile(updated)
        }

        // Attach scoped Firestore realtime snapshot listeners with two-tier CancellationException unwrapping
        cloudObserverJobs += viewModelScope.launch {
            firestoreSync.observeProfile(uid)
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    _uiState.update { it.copy(cloudSyncError = (fsErr ?: error).message) }
                }
                .collect { remote ->
                    if (remote != null) {
                        val local = _uiState.value.profile
                        dao.upsertProfile(
                            local.copy(
                                displayName = remote.displayName.ifBlank { local.displayName },
                                phone = remote.phone.ifBlank { local.phone },
                                email = remote.email.ifBlank { local.email },
                                homeAddress = remote.homeAddress.ifBlank { local.homeAddress },
                                workAddress = remote.workAddress.ifBlank { local.workAddress },
                                paymentMethod = remote.paymentMethod,
                                kgOneActive = remote.kgOneActive,
                                businessProfileActive = remote.businessProfileActive,
                                companyName = remote.companyName,
                                companyEmail = remote.companyEmail,
                                bonusPoints = remote.bonusPoints.toInt(),
                                preferredCity = remote.preferredCity,
                                language = remote.language,
                                darkTheme = remote.darkTheme,
                                soundEnabled = remote.soundEnabled
                            )
                        )
                    }
                }
        }

        cloudObserverJobs += viewModelScope.launch {
            firestoreSync.observeTrips(uid)
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    Log.w("KgTaxiVM", "Trips sync error: ${(fsErr ?: error).message}")
                }
                .collect { remoteTrips ->
                    if (remoteTrips.isNotEmpty()) {
                        dao.upsertTrips(
                            remoteTrips.map { t ->
                                LocalTripEntity(
                                    id = t.id.ifBlank { UUID.randomUUID().toString() },
                                    serviceType = t.serviceType,
                                    tariffName = t.tariffName,
                                    city = t.city,
                                    pickupAddress = t.pickupAddress,
                                    dropoffAddress = t.dropoffAddress,
                                    distanceKm = t.distanceKm,
                                    durationMin = t.durationMin.toInt(),
                                    totalPriceSom = t.totalPriceSom.toInt(),
                                    paymentMethod = t.paymentMethod,
                                    status = t.status,
                                    driverName = t.driverName,
                                    driverCar = t.driverCar,
                                    safetyPin = t.safetyPin,
                                    rating = t.rating.toInt(),
                                    tipSom = t.tipSom.toInt(),
                                    businessTrip = t.businessTrip,
                                    lostItemNote = t.lostItemNote,
                                    createdAtMillis = t.createdAt?.toDate()?.time ?: System.currentTimeMillis()
                                )
                            }
                        )
                    }
                }
        }

        cloudObserverJobs += viewModelScope.launch {
            firestoreSync.observeSavedPlaces(uid)
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                }
                .collect { list ->
                    if (list.isNotEmpty()) {
                        dao.upsertSavedPlaces(
                            list.map { p ->
                                LocalSavedPlaceEntity(p.id, p.label, p.address, p.city, p.lat, p.lng, p.category, p.rating)
                            }
                        )
                    }
                }
        }

        cloudObserverJobs += viewModelScope.launch {
            firestoreSync.observeFamilyMembers(uid)
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                }
                .collect { list ->
                    if (list.isNotEmpty()) {
                        dao.upsertFamilyMembers(
                            list.map { m ->
                                LocalFamilyMemberEntity(m.id, m.name, m.relation, m.phone, m.monthlyLimitSom.toInt(), m.teenControlEnabled)
                            }
                        )
                    }
                }
        }

        cloudObserverJobs += viewModelScope.launch {
            firestoreSync.observeMaintenanceLogs(uid)
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                }
                .collect { list ->
                    if (list.isNotEmpty()) {
                        dao.upsertMaintenanceAll(
                            list.map { m ->
                                LocalMaintenanceEntity(
                                    m.id, m.vehicleName, m.serviceTitle,
                                    m.dueMileageKm.toInt(), m.currentMileageKm.toInt(),
                                    m.fuelLitersPer100Km, m.completed
                                )
                            }
                        )
                    }
                }
        }
    }

    fun stopCloudObservers() {
        cloudObserverJobs.forEach { it.cancel() }
        cloudObserverJobs.clear()
    }

    fun onUserSignedOut() {
        stopOwnerFeeds()
        configJob?.cancel(); configJob = null
        applicationWatchJob?.cancel(); applicationWatchJob = null
        _uiState.update { it.copy(passengerRides = emptyList(), supportTickets = emptyList(), accountDeleting = false) }
        _uiState.update { it.copy(registration = null, registrationOpen = false, isAdmin = false, driverAllowedTariffs = emptySet()) }
        passengerPreordersJob?.cancel()
        passengerPreordersJob = null
        driverPreordersJob?.cancel()
        driverPreordersJob = null
        _uiState.update { it.copy(passengerPreorders = emptyList(), driverOpenPreorders = emptyList(), driverBookedPreorders = emptyList()) }
        stopDispatcherFeed()
        stopDriverOrderFeed()
        stopCloudObservers()
        _uiState.update {
            it.copy(
                isSignedIn = false,
                signedInUid = null,
                role = AppRole.PASSENGER,
                allowedRoles = setOf(AppRole.PASSENGER),
                statusBanner = "Вы вышли из аккаунта. Локальные данные сохранены."
            )
        }
    }

    /** Roles not loaded yet after sign-in: a tap on "Водитель" waits for them instead of opening registration. */
    private var rolesLoaded = false
    private var pendingRole: AppRole? = null

    private fun refreshAllowedRoles() {
        viewModelScope.launch {
            val info = firestoreSync.fetchRoleInfo()
            verifiedDriverCar = info.car
            verifiedDriverPlate = info.plate
            verifiedDriverPayPhone = info.payPhone
            _uiState.update {
                it.copy(
                    driverCar = info.car,
                    driverPlate = info.plate,
                    isAdmin = info.role == "admin",
                    driverAllowedTariffs = info.tariffs.toSet(),
                    isCourier = "courier" in info.tariffs,
                    driverEnabledTariffs = if (info.tariffs.isNotEmpty()) info.tariffs.toSet() else it.driverEnabledTariffs
                )
            }
            if (info.role == "admin") startOwnerFeeds() else stopOwnerFeeds()
            if (info.role == "driver" || info.role == "admin") {
                startDriverHistory()
                startDriverPreorders(_uiState.value.selectedCity.name)
            } else stopDriverHistory()
            // Matches firestore.rules: only "driver" may take orders; dispatchers see the panel.
            val allowed = when (info.role) {
                "driver" -> setOf(AppRole.PASSENGER, AppRole.DRIVER)
                "admin" -> setOf(AppRole.PASSENGER, AppRole.DRIVER, AppRole.DISPATCHER)
                "dispatcher" -> setOf(AppRole.PASSENGER, AppRole.DISPATCHER)
                else -> setOf(AppRole.PASSENGER)
            }
            _uiState.update {
                it.copy(
                    allowedRoles = allowed,
                    role = if (it.role in allowed) it.role else AppRole.PASSENGER
                )
            }
            rolesLoaded = true
            pendingRole?.let { p -> pendingRole = null; setRole(p) }
        }
    }

    /** Tells the server this phone's push token, city and whether the driver is on the line. */
    private fun syncPushToken() {
        if (!_uiState.value.isSignedIn) return
        val s = _uiState.value
        viewModelScope.launch {
            PushTokenRegistry.update(
                context = appContext,
                city = s.selectedCity.name,
                driverOnline = s.driverOnline && AppRole.DRIVER in s.allowedRoles
            )
        }
    }

    // --- DRIVER HISTORY & REAL EARNINGS ---

    private fun startDriverHistory() {
        driverHistoryJob?.cancel()
        driverHistoryJob = viewModelScope.launch {
            rides.observeMyRides(asDriver = true, limit = 500).collect { list ->
                restoreDriverActiveRide(list)
                driverHistoryCache = list
                recomputeDriverBalance()
                val commission = _uiState.value.ownerBankConfig.commissionPercent
                fun net(r: SharedRide) = OwnerPayoutGatewayClient.calculateOrderSplit(r.priceSom, commission).driverNetSom
                val done = list.filter { it.status == RideStatus.COMPLETED }
                val cal = java.util.Calendar.getInstance()
                fun dayStart(daysAgo: Int): Long {
                    val c = cal.clone() as java.util.Calendar
                    c.add(java.util.Calendar.DAY_OF_YEAR, -daysAgo)
                    c.set(java.util.Calendar.HOUR_OF_DAY, 0); c.set(java.util.Calendar.MINUTE, 0)
                    c.set(java.util.Calendar.SECOND, 0); c.set(java.util.Calendar.MILLISECOND, 0)
                    return c.timeInMillis
                }
                val todayStart = dayStart(0)
                val today = done.filter { it.createdAtMillis >= todayStart }
                val dayNames = listOf("Вс", "Пн", "Вт", "Ср", "Чт", "Пт", "Сб")
                val week = (6 downTo 0).map { ago ->
                    val from = dayStart(ago)
                    val to = from + 24 * 60 * 60 * 1000L
                    val c = java.util.Calendar.getInstance().apply { timeInMillis = from }
                    dayNames[c.get(java.util.Calendar.DAY_OF_WEEK) - 1] to
                        done.filter { it.createdAtMillis in from until to }.sumOf { net(it) }
                }
                val rated = list.mapNotNull { it.driverRating }
                _uiState.update {
                    it.copy(
                        driverRatingAvg = if (rated.isEmpty()) null else rated.average(),
                        driverRatingCount = rated.size,
                        driverHistory = list,
                        driverBookedPreorders = list.filter { r -> r.status == RideStatus.BOOKED }
                            .sortedBy { r -> r.scheduledAtMillis ?: 0L },
                        driverTodayEarningsSom = today.sumOf { r -> net(r) },
                        driverQuestTripsDone = today.size,
                        driverWeeklyEarnings = week
                    )
                }
            }
        }
    }

    private fun stopDriverHistory() {
        driverHistoryJob?.cancel()
        driverHistoryJob = null
        driverPreordersJob?.cancel()
        driverPreordersJob = null
        _uiState.update { it.copy(driverHistory = emptyList(), driverOpenPreorders = emptyList(), driverBookedPreorders = emptyList()) }
    }

    /** After the app restarts mid-trip, bring the driver's current order back on the screen. */
    private fun restoreDriverActiveRide(list: List<SharedRide>) {
        if (driverRideId != null || _uiState.value.driverAcceptedOrder != null) return
        val active = list.firstOrNull { it.status in setOf(RideStatus.ASSIGNED, RideStatus.ARRIVED, RideStatus.IN_PROGRESS) } ?: return
        driverRideId = active.id
        if (!_uiState.value.driverOnline) toggleDriverOnline()
        applyAcceptedDriverOrder(active.toDriverOrder(_uiState.value.pickupPoint))
        val stage = when (active.status) {
            RideStatus.ARRIVED -> DriverOrderStage.WAITING_AT_PICKUP
            RideStatus.IN_PROGRESS -> DriverOrderStage.IN_TRIP
            else -> DriverOrderStage.EN_ROUTE_TO_PICKUP
        }
        _uiState.update { it.copy(driverOrderStage = stage, statusBanner = "Текущий заказ восстановлен") }
        startDriverLocationSharing(active.id)
        watchDriverRide(active.id)
        startDriverChat(active.id)
    }

    // --- INTERCITY PRE-ORDERS ---

    /** Passenger: watches own pre-orders; when the driver starts one, it opens like a normal trip. */
    private fun startPassengerPreorders() {
        if (!FeatureFlags.REAL_RIDES) return
        passengerPreordersJob?.cancel()
        passengerPreordersJob = viewModelScope.launch {
            var known = emptyMap<String, String>()
            rides.observeMyRides(asDriver = false, limit = 30).collect { list ->
                _uiState.update { it.copy(passengerRides = list) }
                val pre = list.filter { it.scheduledAtMillis != null }
                pre.forEach { r ->
                    if (known[r.id] == RideStatus.SEARCHING && r.status == RideStatus.BOOKED) {
                        val text = "${r.driverName ?: "Водитель"} • ${r.driverCar.orEmpty()} ${r.driverPlate.orEmpty()}".trim()
                        SoundAndNotificationManager.playCue(SoundCue.DRIVER_ASSIGNED, _uiState.value.soundEnabled)
                        SoundAndNotificationManager.postNotification(appContext, "Водитель взял ваш предзаказ", text)
                        _uiState.update { it.copy(statusBanner = "Водитель взял ваш предзаказ: $text") }
                    }
                }
                known = pre.associate { it.id to it.status }
                val waiting = pre.filter { it.status == RideStatus.SEARCHING || it.status == RideStatus.BOOKED }
                    .sortedBy { it.scheduledAtMillis ?: 0L }
                _uiState.update { it.copy(passengerPreorders = waiting) }
                // Bring back an unfinished order after the app restarts (or a pre-order the driver started).
                val started = list.firstOrNull {
                    it.status in setOf(RideStatus.ASSIGNED, RideStatus.ARRIVED, RideStatus.IN_PROGRESS) ||
                        (it.status == RideStatus.SEARCHING && it.scheduledAtMillis == null)
                }
                if (started != null && _uiState.value.activeRide == null && passengerRideId == null) {
                    attachPassengerRide(started)
                }
            }
        }
    }

    private fun attachPassengerRide(shared: SharedRide) {
        val pickup = GeoPoint(shared.pickupLat, shared.pickupLng)
        val dropoff = GeoPoint(shared.dropoffLat, shared.dropoffLng)
        val km = KgGeoCatalog.haversineKm(pickup, dropoff)
        val ride = ActiveRideState(
            tripId = shared.id,
            serviceType = when (shared.serviceType) { "INTERCITY" -> ServiceType.INTERCITY; "FOOD" -> ServiceType.FOOD; else -> ServiceType.TAXI },
            tariffName = shared.tariffName ?: when (shared.serviceType) { "INTERCITY" -> "Межгород"; "FOOD" -> "Доставка"; else -> "Такси" },
            city = shared.city,
            pickupAddress = shared.pickupAddress,
            dropoffAddress = shared.dropoffAddress,
            pickupPoint = pickup,
            dropoffPoint = dropoff,
            distanceKm = (km * 10).roundToInt() / 10.0,
            durationMin = max(3, (km / 0.45).roundToInt()),
            totalPriceSom = shared.priceSom,
            paymentMethod = PaymentMethod.CASH,
            status = TripStatus.SEARCHING,
            driverName = shared.driverName ?: if (shared.serviceType == "FOOD") "Ищем курьера…" else "Водитель",
            driverCar = shared.driverCar.orEmpty(),
            driverRating = shared.driverRatingAvg ?: 0.0,
            driverEtaMin = 0,
            safetyPin = shared.comment?.substringAfter("PIN ", "")?.take(4).orEmpty(),
            progress = 0f,
            scheduledLabel = shared.scheduledAtMillis?.let { formatDeparture(it) }
        )
        passengerRideId = shared.id
        _uiState.update { it.copy(activeRide = ride, driverChatMessages = emptyList()) }
        startPassengerChat(shared.id)
        observePassengerRide(shared.id)
    }

    fun setIntercityDeparture(millis: Long?) =
        _uiState.update { it.copy(intercityDepartureMillis = millis) }

    fun cancelPreorder(rideId: String) {
        viewModelScope.launch {
            val res = rides.cancelAsPassenger(rideId, "Отменил предзаказ")
            _uiState.update {
                it.copy(statusBanner = if (res.isSuccess) "Предзаказ отменён" else "Не удалось отменить. Проверьте интернет.")
            }
        }
    }

    /** Driver: open pre-orders in the city (visible even when not on the line). */
    private fun startDriverPreorders(city: String) {
        if (!FeatureFlags.REAL_RIDES) return
        driverPreordersJob?.cancel()
        com.example.util.PreorderAlerts.setCity(appContext, city)
        _uiState.update { it.copy(driverPreorderAlertDays = com.example.util.PreorderAlerts.days(appContext)) }
        loadDriverWorkPrefs()
        com.example.util.PreorderAlerts.schedule(appContext)
        driverPreordersJob = viewModelScope.launch {
            rides.observeOpenRides(city).collect { open ->
                val pre = open.filter { r -> r.scheduledAtMillis != null }.sortedBy { r -> r.scheduledAtMillis ?: 0L }
                val demand = open.filter { r -> !r.isFuturePreorder && r.pickupLat != 0.0 }.map { r -> GeoPoint(r.pickupLat, r.pickupLng) }
                _uiState.update { it.copy(driverOpenPreorders = pre, driverDemandPoints = demand) }
                com.example.util.PreorderAlerts.check(appContext, pre)
            }
        }
    }

    private var driverHistoryCache: List<SharedRide> = emptyList()
    private var driverPaidSom = 0
    private var driverPaidJob: Job? = null
    private var driverOnlineTickJob: Job? = null

    private fun recomputeDriverBalance() {
        val s = _uiState.value
        val b = com.example.data.model.DriverBalance.compute(
            driverHistoryCache, s.ownerBankConfig.commissionPercent, s.appConfig.goalTrips, s.appConfig.goalBonusSom, driverPaidSom
        )
        val mine = driverHistoryCache.filter { it.status != RideStatus.SEARCHING && it.status != RideStatus.BOOKED }.take(100)
        val byDriver = mine.count { it.status == RideStatus.CANCELLED && it.cancelReason?.startsWith("Водитель") == true }
        val rate = if (mine.isEmpty()) 0 else (byDriver * 100 / mine.size)
        _uiState.update { it.copy(driverBalance = b, driverCancelRatePercent = rate) }
    }

    /**
     * Driver navigation: every few seconds takes the GPS position, keeps an OSRM route with turns to the
     * passenger (then to point B after boarding), rebuilds it when the driver leaves the route and speaks
     * the next turn in Russian.
     */
    private fun startDriverNavigationLoop() {
        viewModelScope.launch {
            var route: com.example.util.NavRoute? = null
            var routeKey = ""
            var stepIdx = 0
            var farSpoken = -1
            var nearSpoken = -1
            var arrivedSpoken = false
            while (true) {
                delay(3_000)
                val s = _uiState.value
                val order = s.driverAcceptedOrder
                val stage = s.driverOrderStage
                if (order == null || stage == DriverOrderStage.WAITING_AT_PICKUP) {
                    if (s.driverNav != null || s.driverNavLine.isNotEmpty()) {
                        _uiState.update { it.copy(driverNav = null, driverNavLine = emptyList()) }
                    }
                    route = null
                    routeKey = ""
                    continue
                }
                val target = if (stage == DriverOrderStage.IN_TRIP) order.dropoffPoint else order.pickupPoint
                val key = order.id + stage.name
                val gps = withTimeoutOrNull(7_000) {
                    runCatching { LocationAndVoiceHelper.fetchCurrentLocation(appContext, s.selectedCity) }.getOrNull()
                } ?: continue
                if (!gps.fromRealGps) continue
                val pos = gps.point
                val current = route
                if (current == null || key != routeKey || com.example.util.TurnByTurnNavigator.offRouteMeters(current, pos) > 80.0) {
                    val fresh = com.example.util.TurnByTurnNavigator.fetchRoute(pos, target)
                    if (fresh != null) {
                        val newOrder = key != routeKey
                        route = fresh
                        routeKey = key
                        stepIdx = 0
                        farSpoken = -1
                        nearSpoken = -1
                        arrivedSpoken = false
                        if (s.driverVoiceAnnounce) {
                            VoiceAnnouncer.speak(
                                appContext,
                                if (!newOrder) "Маршрут перестроен"
                                else if (stage == DriverOrderStage.IN_TRIP) "Маршрут до точки назначения построен"
                                else "Маршрут до пассажира построен"
                            )
                        }
                    }
                }
                val r = route ?: continue
                stepIdx = com.example.util.TurnByTurnNavigator.nextStepIndex(r, pos, stepIdx)
                val g = com.example.util.TurnByTurnNavigator.guidance(r, pos, stepIdx)
                _uiState.update { it.copy(driverNav = g, driverNavLine = r.line, driverPosition = pos) }
                if (!_uiState.value.driverVoiceAnnounce) continue
                when {
                    g.arrived -> if (!arrivedSpoken) {
                        arrivedSpoken = true
                        VoiceAnnouncer.speak(appContext, if (stage == DriverOrderStage.IN_TRIP) "Вы прибыли в точку назначения" else "Вы на месте подачи")
                    }
                    g.distanceM <= 70 && nearSpoken != stepIdx -> {
                        nearSpoken = stepIdx
                        farSpoken = stepIdx
                        VoiceAnnouncer.speak(appContext, com.example.util.TurnByTurnNavigator.spoken(g, near = true))
                    }
                    g.distanceM in 150..450 && farSpoken != stepIdx -> {
                        farSpoken = stepIdx
                        VoiceAnnouncer.speak(appContext, com.example.util.TurnByTurnNavigator.spoken(g, near = false))
                    }
                }
            }
        }
    }

    fun setDriverTakesDelivery(on: Boolean) {
        DriverWorkPrefs.setTakesDelivery(appContext, on)
        _uiState.update { it.copy(driverTakesDelivery = on) }
    }

    fun setDriverMinTripKm(km: Int) {
        DriverWorkPrefs.setMinTripKm(appContext, km)
        _uiState.update { it.copy(driverMinTripKm = km) }
    }

    fun setDriverDeafMode(on: Boolean) {
        DriverWorkPrefs.setDeaf(appContext, on)
        _uiState.update { it.copy(driverDeafMode = on) }
    }

    fun setTaxiSchedule(millis: Long?) {
        _uiState.update {
            it.copy(
                taxiScheduledMillis = millis,
                statusBanner = millis?.let { m -> "Заказ на ${formatDeparture(m)}" } ?: "Заказ на сейчас"
            )
        }
    }

    /** Stop on the way picked on the map. */
    fun addWaypointPoint(point: GeoPoint) {
        viewModelScope.launch {
            val address = LocationAndVoiceHelper.reverseGeocode(appContext, _uiState.value.selectedCity, point)
            addWaypoint(KgPlace("map_${System.currentTimeMillis()}", address, address, _uiState.value.selectedCity.name, point, "Остановка", 0.0, 0, 1))
        }
    }

    fun setDriverMinPassengerRating(v: Double) {
        DriverWorkPrefs.setMinRating(appContext, v)
        _uiState.update { it.copy(driverMinPassengerRating = v) }
    }

    private var queuedRideId: String? = null
    private var queuedWatchJob: Job? = null

    /** Take one more order while on a trip: it starts right after the current one ("цепочка"). */
    fun acceptNextDriverOrder(order: DriverIncomingOrder) {
        if (driverRideId == null || _uiState.value.driverAcceptedOrder == null) {
            acceptDriverOrder(order)
            return
        }
        if (queuedRideId != null) {
            _uiState.update { it.copy(statusBanner = "Следующий заказ уже взят") }
            return
        }
        viewModelScope.launch {
            val res = rides.acceptAsDriver(
                rideId = order.id,
                driverName = _uiState.value.profile.displayName,
                driverCar = verifiedDriverCar,
                driverPlate = verifiedDriverPlate,
                driverPayPhone = verifiedDriverPayPhone,
                driverRatingAvg = _uiState.value.driverRatingAvg,
                driverTrips = _uiState.value.driverHistory.count { it.status == RideStatus.COMPLETED }
            )
            res.onSuccess {
                queuedRideId = order.id
                _uiState.update {
                    it.copy(
                        driverQueuedNextOrder = order,
                        driverRadarOrders = it.driverRadarOrders.filterNot { o -> o.id == order.id },
                        driverIncomingOrders = it.driverIncomingOrders.filterNot { o -> o.id == order.id },
                        statusBanner = "Следующий заказ взят: начнётся после этой поездки"
                    )
                }
                queuedWatchJob?.cancel()
                queuedWatchJob = viewModelScope.launch {
                    rides.observeRide(order.id).collect { shared ->
                        if (shared?.status == RideStatus.CANCELLED && queuedRideId == order.id) {
                            queuedRideId = null
                            _uiState.update { it.copy(driverQueuedNextOrder = null, statusBanner = "Пассажир следующего заказа отменил его") }
                            queuedWatchJob?.cancel()
                        }
                    }
                }
            }.onFailure { e ->
                _uiState.update { it.copy(statusBanner = e.message ?: "Не удалось взять заказ") }
            }
        }
    }

    /** After the current ride ends, the queued ride becomes the current one. */
    private fun promoteQueuedRide() {
        val next = queuedRideId ?: return
        queuedRideId = null
        queuedWatchJob?.cancel()
        driverRideId = next
        startDriverLocationSharing(next)
        watchDriverRide(next)
        startDriverChat(next)
    }

    private fun loadDriverWorkPrefs() {
        val c = appContext
        val home = DriverWorkPrefs.home(c)
        _uiState.update {
            it.copy(
                driverPickupRadiusKm = DriverWorkPrefs.radiusKm(c),
                driverMinPassengerRating = DriverWorkPrefs.minRating(c),
                driverMinTripKm = DriverWorkPrefs.minTripKm(c),
                driverTakesDelivery = DriverWorkPrefs.takesDelivery(c),
                driverDeafMode = DriverWorkPrefs.deaf(c),
                driverAcceptancePercent = DriverWorkPrefs.acceptanceRate(c),
                driverVoiceAnnounce = DriverWorkPrefs.voice(c),
                driverNightMapAuto = DriverWorkPrefs.nightMap(c),
                driverHomePoint = home?.first,
                driverHomeAddress = home?.second.orEmpty(),
                driverHomeUsesLeft = DriverWorkPrefs.homeUsesLeft(c),
                driverOnlineMinutes24h = DriverWorkPrefs.onlineMinutes24h(c),
                driverRestUntil = DriverWorkPrefs.restUntil(c)
            )
        }
        if (driverPaidJob == null) {
            driverPaidJob = viewModelScope.launch {
                rides.observeMyPaidSom().collect { paid ->
                    driverPaidSom = paid
                    recomputeDriverBalance()
                }
            }
        }
    }

    /** Driver photo check: needed every 30 days (only for drivers registered in the app). */
    fun photoCheckOverdue(now: Long = System.currentTimeMillis()): Boolean {
        val reg = _uiState.value.registration ?: return false
        if (reg.type != "driver") return false
        val at = reg.photoCheckAt ?: return false
        return now - at > PHOTO_CHECK_DAYS * 24 * 3600_000L
    }

    fun submitDriverPhotoCheck(photos: Map<com.example.data.firebase.RegPhoto, android.net.Uri>) {
        _uiState.update { it.copy(driverPhotoCheckSending = true) }
        viewModelScope.launch {
            val encoded = mutableMapOf<com.example.data.firebase.RegPhoto, String>()
            for ((kind, uri) in photos) {
                val data = registrationRepo.encodePhoto(uri)
                if (data == null) {
                    _uiState.update { it.copy(driverPhotoCheckSending = false, statusBanner = "Не удалось прочитать фото «${kind.title}». Снимите ещё раз.") }
                    return@launch
                }
                encoded[kind] = data
            }
            val res = registrationRepo.submitPhotoCheck(encoded)
            _uiState.update {
                it.copy(
                    driverPhotoCheckSending = false,
                    registration = if (res.isSuccess) it.registration?.copy(photoCheckAt = System.currentTimeMillis()) else it.registration,
                    statusBanner = if (res.isSuccess) "Фотоконтроль пройден. Следующий — через $PHOTO_CHECK_DAYS дней." else "Не удалось отправить фото. Проверьте интернет."
                )
            }
        }
    }

    fun togglePassengerAccessibility(key: String) {
        _uiState.update {
            val cur = it.passengerAccessibility
            it.copy(passengerAccessibility = if (key in cur) cur - key else cur + key)
        }
    }

    fun savePassengerPeople(list: List<com.example.util.ClosePerson>) {
        com.example.util.PassengerPeople.save(appContext, list)
        _uiState.update { it.copy(passengerPeople = list.take(com.example.util.PassengerPeople.MAX)) }
    }

    fun setDriverPickupRadius(km: Int) {
        DriverWorkPrefs.setRadiusKm(appContext, km)
        _uiState.update { it.copy(driverPickupRadiusKm = km) }
    }

    fun setDriverVoice(on: Boolean) {
        DriverWorkPrefs.setVoice(appContext, on)
        _uiState.update { it.copy(driverVoiceAnnounce = on) }
        if (on) VoiceAnnouncer.speak(appContext, "Голосовые подсказки включены")
    }

    fun setDriverNightMap(on: Boolean) {
        DriverWorkPrefs.setNightMap(appContext, on)
        _uiState.update { it.copy(driverNightMapAuto = on) }
    }

    fun toggleDriverDemandMap() {
        _uiState.update { it.copy(driverShowDemand = !it.driverShowDemand) }
    }

    fun setDriverHome(point: GeoPoint) {
        viewModelScope.launch {
            val address = LocationAndVoiceHelper.reverseGeocode(appContext, _uiState.value.selectedCity, point)
            DriverWorkPrefs.setHome(appContext, point, address)
            _uiState.update { it.copy(driverHomePoint = point, driverHomeAddress = address, statusBanner = "Домашний адрес сохранён") }
        }
    }

    fun toggleDriverHomeMode() {
        val s = _uiState.value
        if (s.driverHomeModeOn) {
            _uiState.update { it.copy(driverHomeModeOn = false, statusBanner = "Режим «Домой» выключен") }
        } else {
            when {
                s.driverHomePoint == null -> _uiState.update { it.copy(statusBanner = "Сначала укажите домашний адрес в настройках линии") }
                DriverWorkPrefs.homeUsesLeft(appContext) <= 0 -> _uiState.update { it.copy(statusBanner = "«Домой» можно включать 2 раза в день. Попробуйте завтра.") }
                else -> {
                    DriverWorkPrefs.useHome(appContext)
                    _uiState.update {
                        it.copy(
                            driverHomeModeOn = true,
                            driverHomeUsesLeft = DriverWorkPrefs.homeUsesLeft(appContext),
                            statusBanner = "«Домой»: будем предлагать заказы в сторону дома"
                        )
                    }
                }
            }
        }
        generateDriverIncomingOrders(_uiState.value.selectedCity)
    }

    fun toggleDriverLastOrder() {
        _uiState.update {
            val next = !it.driverLastOrder
            it.copy(driverLastOrder = next, statusBanner = if (next) "После этой поездки вы уйдёте с линии" else "Продолжаете работать после поездки")
        }
    }

    /** Driver answers the passenger's route change. */
    fun answerRouteChange(accept: Boolean) {
        val req = _uiState.value.driverRouteRequest ?: return
        val id = driverRideId ?: return
        viewModelScope.launch {
            val res = rides.answerRouteChange(id, req, accept)
            if (res.isFailure) {
                _uiState.update { it.copy(statusBanner = "Нет связи с сервером. Попробуйте ещё раз.") }
                return@launch
            }
            _uiState.update { st ->
                val order = st.driverAcceptedOrder
                val updated = if (accept && order != null) {
                    val split = OwnerPayoutGatewayClient.calculateOrderSplit(req.priceSom, st.ownerBankConfig.commissionPercent)
                    order.copy(
                        dropoffAddress = if (req.stopAddress.isNullOrBlank()) req.dropoffAddress else "${req.stopAddress} → ${req.dropoffAddress}",
                        dropoffPoint = GeoPoint(req.dropoffLat, req.dropoffLng),
                        grossFareSom = req.priceSom,
                        upfrontNetEarningsSom = split.driverNetSom
                    )
                } else order
                st.copy(
                    driverAcceptedOrder = updated,
                    driverRouteRequest = null,
                    statusBanner = if (accept) "Маршрут изменён. Новая цена ${req.priceSom} сом" else "Вы отказались менять маршрут"
                )
            }
        }
    }

    /** Passenger asks the driver to change the destination (and maybe add a stop). */
    fun requestRouteChange(newDropoff: GeoPoint, stop: GeoPoint?) {
        val ride = _uiState.value.activeRide ?: return
        val id = passengerRideId ?: return
        viewModelScope.launch {
            val city = _uiState.value.selectedCity
            val dropAddr = LocationAndVoiceHelper.reverseGeocode(appContext, city, newDropoff)
            val stopAddr = stop?.let { LocationAndVoiceHelper.reverseGeocode(appContext, city, it) }
            val tariff = KgGeoCatalog.taxiTariffs.firstOrNull { it.nameRu == ride.tariffName }?.let { _uiState.value.priced(it) }
            val price = com.example.data.model.RouteRequest.newPrice(
                oldPrice = ride.totalPriceSom,
                pickup = ride.pickupPoint,
                oldDropoff = ride.dropoffPoint,
                newDropoff = newDropoff,
                stop = stop,
                perKmSom = tariff?.perKmSom ?: 20,
                baseSom = tariff?.baseSom ?: 90
            )
            val req = com.example.data.model.RouteRequest(
                dropoffAddress = dropAddr, dropoffLat = newDropoff.lat, dropoffLng = newDropoff.lng,
                stopAddress = stopAddr, stopLat = stop?.lat, stopLng = stop?.lng, priceSom = price
            )
            val res = rides.requestRouteChange(id, req)
            _uiState.update {
                it.copy(
                    passengerRouteRequestStatus = if (res.isSuccess) "pending" else it.passengerRouteRequestStatus,
                    statusBanner = if (res.isSuccess) "Запрос отправлен водителю. Новая цена $price сом — если водитель согласится."
                    else "Не удалось отправить. Проверьте интернет."
                )
            }
        }
    }

    fun loadOwnerDriverBalances() {
        val s = _uiState.value
        val drivers = s.ownerDrivers + s.ownerBlockedDrivers
        viewModelScope.launch {
            val map = mutableMapOf<String, com.example.data.model.DriverBalance>()
            drivers.forEach { d ->
                ownerRepo.fetchDriverBalance(d.uid, s.ownerBankConfig.commissionPercent, s.appConfig.goalTrips, s.appConfig.goalBonusSom)
                    ?.let { map[d.uid] = it }
            }
            _uiState.update { it.copy(ownerDriverBalances = map) }
        }
    }

    fun addOwnerDriverPayment(uid: String, amountSom: Int) {
        if (amountSom <= 0) return
        viewModelScope.launch {
            val res = ownerRepo.addDriverPayment(uid, amountSom)
            _uiState.update { it.copy(statusBanner = if (res.isSuccess) "Оплата $amountSom сом записана" else "Не удалось сохранить. Проверьте интернет.") }
            if (res.isSuccess) loadOwnerDriverBalances()
        }
    }

    /** Driver: turn pre-order alerts on/off for a week day. */
    fun toggleDriverPreorderAlertDay(day: Int) {
        val now = _uiState.value.driverPreorderAlertDays
        val next = if (day in now) now - day else now + day
        com.example.util.PreorderAlerts.setDays(appContext, next)
        _uiState.update { it.copy(driverPreorderAlertDays = next) }
    }

    fun bookDriverPreorder(rideId: String) {
        viewModelScope.launch {
            val res = rides.bookPreorder(
                rideId = rideId,
                driverName = _uiState.value.profile.displayName,
                driverCar = verifiedDriverCar,
                driverPlate = verifiedDriverPlate,
                driverPayPhone = verifiedDriverPayPhone,
                driverRatingAvg = _uiState.value.driverRatingAvg,
                driverTrips = _uiState.value.driverHistory.count { it.status == RideStatus.COMPLETED }
            )
            _uiState.update {
                it.copy(
                    statusBanner = res.fold(
                        { "Предзаказ ваш. Пассажир получил уведомление." },
                        { e -> e.message ?: "Не удалось взять предзаказ" }
                    )
                )
            }
        }
    }

    fun releaseDriverPreorder(rideId: String) {
        viewModelScope.launch {
            val res = rides.releasePreorder(rideId)
            _uiState.update {
                it.copy(statusBanner = if (res.isSuccess) "Вы отказались от предзаказа. Он снова доступен другим водителям." else "Не удалось. Проверьте интернет.")
            }
        }
    }

    /** Driver starts the booked pre-order: from here it is a normal trip. */
    fun startDriverPreorder(ride: SharedRide) {
        if (driverRideId != null || _uiState.value.driverAcceptedOrder != null) {
            _uiState.update { it.copy(statusBanner = "Сначала завершите текущий заказ.") }
            return
        }
        viewModelScope.launch {
            val res = rides.setStatusAsDriver(ride.id, RideStatus.ASSIGNED)
            res.onSuccess {
                if (!_uiState.value.driverOnline) toggleDriverOnline()
                driverRideId = ride.id
                applyAcceptedDriverOrder(ride.toDriverOrder(_uiState.value.pickupPoint))
                startDriverLocationSharing(ride.id)
                watchDriverRide(ride.id)
                startDriverChat(ride.id)
            }.onFailure {
                _uiState.update { it.copy(statusBanner = "Не удалось начать поездку. Проверьте интернет.") }
            }
        }
    }

    // --- DRIVER APPLICATION ---

    // --- REGISTRATION (passenger: passport; driver: passport, licence, car, photo check) ---

    fun refreshRegistration() {
        if (!_uiState.value.isSignedIn) return
        viewModelScope.launch {
            val info = registrationRepo.fetch()
            // Restore the phone from registration if the local profile lost it.
            if (info.phone.isNotBlank()) {
                val saved = dao.getProfileOnce()
                if (saved != null && saved.phone.isBlank()) {
                    val fixed = saved.copy(phone = info.phone)
                    dao.upsertProfile(fixed)
                    firestoreSync.syncProfile(fixed)
                }
            }
            _uiState.update {
                it.copy(
                    registration = info,
                    // First sign-in: ask who the user is.
                    registrationOpen = it.registrationOpen || info.type == null,
                    driverApplicationStatus = info.driverStatus ?: it.driverApplicationStatus
                )
            }
        }
    }

    fun openRegistration(asDriver: Boolean) {
        if (!_uiState.value.isSignedIn) {
            _uiState.update { it.copy(statusBanner = "Сначала войдите через Google") }
            return
        }
        _uiState.update { it.copy(registrationOpen = true, registrationStartAsDriver = asDriver) }
    }

    fun closeRegistration() = _uiState.update { it.copy(registrationOpen = false, registrationStartAsDriver = false) }

    fun registerPassenger(fullName: String, phone: String, passportNumber: String, photos: Map<com.example.data.firebase.RegPhoto, android.net.Uri>) {
        _uiState.update { it.copy(registrationSending = true) }
        viewModelScope.launch {
            val encoded = mutableMapOf<com.example.data.firebase.RegPhoto, String>()
            for ((kind, uri) in photos) {
                val data = registrationRepo.encodePhoto(uri)
                if (data == null) {
                    _uiState.update { it.copy(registrationSending = false, statusBanner = "Не удалось прочитать фото «${kind.title}». Сфотографируйте ещё раз.") }
                    return@launch
                }
                encoded[kind] = data
            }
            val res = registrationRepo.registerPassenger(fullName, phone, passportNumber, encoded)
            res.onSuccess {
                updateProfile { p -> p.copy(displayName = fullName.trim(), phone = phone.trim()) }
            }
            _uiState.update {
                it.copy(
                    registrationSending = false,
                    registrationOpen = res.isFailure,
                    registration = if (res.isSuccess) com.example.data.firebase.RegistrationInfo("passenger", fullName.trim()) else it.registration,
                    role = if (res.isSuccess) AppRole.PASSENGER else it.role,
                    statusBanner = if (res.isSuccess) "Регистрация завершена. Можно заказывать такси!" else "Не удалось сохранить. Проверьте интернет и попробуйте снова."
                )
            }
        }
    }

    fun registerDriver(reg: com.example.data.firebase.DriverRegistration, photos: Map<com.example.data.firebase.RegPhoto, android.net.Uri>) {
        _uiState.update { it.copy(registrationSending = true) }
        viewModelScope.launch {
            val encoded = mutableMapOf<com.example.data.firebase.RegPhoto, String>()
            for ((kind, uri) in photos) {
                val data = registrationRepo.encodePhoto(uri)
                if (data == null) {
                    _uiState.update { it.copy(registrationSending = false, statusBanner = "Не удалось прочитать фото «${kind.title}». Сфотографируйте ещё раз.") }
                    return@launch
                }
                encoded[kind] = data
            }
            val res = registrationRepo.registerDriver(reg, encoded)
            res.onSuccess {
                updateProfile { p -> p.copy(displayName = reg.fullName.trim(), phone = reg.phone.trim()) }
            }
            _uiState.update {
                it.copy(
                    registrationSending = false,
                    registrationOpen = res.isFailure,
                    registration = if (res.isSuccess) com.example.data.firebase.RegistrationInfo("driver", reg.fullName.trim(), "pending") else it.registration,
                    driverApplicationStatus = if (res.isSuccess) "pending" else it.driverApplicationStatus,
                    statusBanner = if (res.isSuccess) "Заявка водителя отправлена. После проверки документов откроется экран водителя." else "Не удалось отправить. Проверьте интернет и попробуйте снова."
                )
            }
        }
    }

    // --- OWNER PANEL (role "admin") ---

    private fun startOwnerFeeds() {
        if (ownerJobs.isNotEmpty()) return
        val dayStart = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        ownerJobs += viewModelScope.launch {
            supportRepo.observeAll().collect { list -> _uiState.update { it.copy(supportTickets = list) } }
        }
        ownerJobs += viewModelScope.launch {
            ownerRepo.observeApplications().collect { list -> _uiState.update { it.copy(ownerApplications = list) } }
        }
        ownerJobs += viewModelScope.launch {
            ownerRepo.observeDrivers().collect { list -> _uiState.update { it.copy(ownerDrivers = list) } }
        }
        ownerJobs += viewModelScope.launch {
            ownerRepo.observeBlockedDrivers().collect { list -> _uiState.update { it.copy(ownerBlockedDrivers = list) } }
        }
        startOwnerStats(_uiState.value.ownerStatsDays)
    }

    private var ownerStatsJob: Job? = null

    private fun startOwnerStats(days: Int) {
        ownerStatsJob?.cancel()
        val from = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
            add(java.util.Calendar.DAY_OF_YEAR, -(days - 1))
        }.timeInMillis
        ownerStatsJob = viewModelScope.launch {
            ownerRepo.observeRidesSince(from).collect { st -> _uiState.update { it.copy(ownerStats = st) } }
        }
    }

    fun setOwnerStatsDays(days: Int) {
        _uiState.update { it.copy(ownerStatsDays = days) }
        if (_uiState.value.isAdmin) startOwnerStats(days)
    }

    /** Saves the period's orders as a CSV table and opens "Поделиться" (Excel, Google Sheets, Telegram…). */
    fun exportOwnerCsv() {
        val s = _uiState.value
        val rows = s.ownerStats.rows
        if (rows.isEmpty()) {
            _uiState.update { it.copy(statusBanner = "За этот период заказов нет") }
            return
        }
        viewModelScope.launch {
            val file = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val fmt = java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.US)
                fun cell(v: String) = "\"" + v.replace("\"", "\"\"") + "\""
                val sb = StringBuilder("\uFEFF") // BOM so Excel opens Cyrillic correctly
                sb.append("Дата;Город;Откуда;Куда;Тариф;Цена, сом;Статус;Оплата;Пассажир;Водитель\n")
                rows.forEach { r ->
                    sb.append(listOf(
                        if (r.createdAtMillis > 0) fmt.format(java.util.Date(r.createdAtMillis)) else "",
                        r.city, r.from, r.to, r.tariff, r.priceSom.toString(), r.status, r.payment, r.passenger, r.driver
                    ).joinToString(";") { cell(it) }).append("\n")
                }
                val dir = java.io.File(appContext.cacheDir, "exports").apply { mkdirs() }
                java.io.File(dir, "kgtaxi_zakazy_${s.ownerStatsDays}d.csv").apply { writeText(sb.toString()) }
            }
            val uri = androidx.core.content.FileProvider.getUriForFile(appContext, appContext.packageName + ".fileprovider", file)
            val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = android.content.Intent.createChooser(send, "Выгрузка заказов").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { appContext.startActivity(chooser) }
        }
    }

    private fun stopOwnerFeeds() {
        ownerJobs.forEach { it.cancel() }
        ownerJobs.clear()
    }

    fun loadApplicationPhotos(uid: String) {
        _uiState.update { it.copy(ownerPhotosUid = uid, ownerPhotos = emptyMap()) }
        viewModelScope.launch {
            val photos = ownerRepo.fetchPhotos(uid)
            _uiState.update { if (it.ownerPhotosUid == uid) it.copy(ownerPhotos = photos) else it }
        }
    }

    fun closeApplicationPhotos() = _uiState.update { it.copy(ownerPhotosUid = null, ownerPhotos = emptyMap()) }

    fun approveApplication(app: com.example.data.firebase.OwnerApplication) {
        viewModelScope.launch {
            val res = ownerRepo.approve(app)
            _uiState.update {
                it.copy(statusBanner = if (res.isSuccess) "${app.fullName}: водитель одобрен" else "Не удалось одобрить. Проверьте интернет.")
            }
        }
    }

    fun rejectApplication(uid: String, reason: String) {
        viewModelScope.launch {
            val res = ownerRepo.reject(uid, reason.ifBlank { "Документы не прошли проверку" })
            _uiState.update { it.copy(statusBanner = if (res.isSuccess) "Заявка отклонена" else "Не удалось. Проверьте интернет.") }
        }
    }

    fun setDriverBlocked(uid: String, blocked: Boolean) {
        viewModelScope.launch {
            val res = ownerRepo.setBlocked(uid, blocked)
            _uiState.update {
                it.copy(statusBanner = if (res.isFailure) "Не удалось. Проверьте интернет." else if (blocked) "Водитель заблокирован" else "Водитель разблокирован")
            }
        }
    }

    // --- OWNER SETTINGS (prices, commission, promo) ---

    private fun startAppConfig() {
        configJob?.cancel()
        configJob = viewModelScope.launch {
            configRepo.observe().collect { cfg ->
                _uiState.update {
                    it.copy(
                        appConfig = cfg,
                        ownerBankConfig = cfg.commissionPercent?.let { pct -> it.ownerBankConfig.copy(commissionPercent = pct) } ?: it.ownerBankConfig
                    )
                }
            }
        }
    }

    fun saveAppConfig(config: com.example.data.firebase.AppConfig) {
        viewModelScope.launch {
            val res = configRepo.save(config)
            _uiState.update { it.copy(statusBanner = if (res.isSuccess) "Цены и промокод сохранены — у всех обновятся сразу" else "Не удалось сохранить. Проверьте интернет.") }
        }
    }

    // --- DRIVER APPLICATION STATUS (approved / rejected) ---

    private fun watchDriverApplication() {
        applicationWatchJob?.cancel()
        applicationWatchJob = viewModelScope.launch {
            var last: String? = null
            appWatcher.observe().collect { (status, reason) ->
                val prev = last
                last = status
                if (prev == null || status == prev) return@collect
                if (status == "approved") {
                    refreshAllowedRoles()
                    _uiState.update {
                        it.copy(
                            registration = it.registration?.copy(driverStatus = "approved"),
                            driverApplicationStatus = "approved",
                            role = AppRole.DRIVER,
                            statusBanner = "Вас одобрили! Вы водитель KG Taxi — нажмите GO"
                        )
                    }
                    SoundAndNotificationManager.playCue(SoundCue.TRIP_COMPLETED, _uiState.value.soundEnabled)
                    SoundAndNotificationManager.postNotification(appContext, "Вас одобрили в KG Taxi", "Откройте приложение и нажмите GO, чтобы выйти на линию.")
                } else if (status == "rejected") {
                    _uiState.update {
                        it.copy(
                            registration = it.registration?.copy(driverStatus = "rejected", rejectReason = reason),
                            driverApplicationStatus = "rejected",
                            statusBanner = "Заявка водителя отклонена: ${reason ?: "документы не прошли проверку"}"
                        )
                    }
                    SoundAndNotificationManager.postNotification(appContext, "Заявка водителя отклонена", reason ?: "Документы не прошли проверку. Можно подать заново.")
                }
            }
        }
    }

    // --- MY RIDES, SUPPORT, ACCOUNT DELETION ---

    // --- PASSENGER HOME MENU ---

    fun openPassengerOrder(service: ServiceType) {
        if (!_uiState.value.pickupConfirmed) locateMyGps()
        if (_uiState.value.serviceType != service) setServiceType(service)
        if (service == ServiceType.INTERCITY) setIntercityDeparture(null)
        _uiState.update { it.copy(passengerHome = false) }
    }

    /** Intercity pre-order: opens the intercity order with tomorrow 08:00 already chosen. */
    fun openPassengerPreorder() {
        if (_uiState.value.serviceType != ServiceType.INTERCITY) setServiceType(ServiceType.INTERCITY)
        setIntercityDeparture(com.example.util.departureMillis(1, 8))
        _uiState.update { it.copy(passengerHome = false) }
    }

    fun backToPassengerHome() = _uiState.update { it.copy(passengerHome = true) }

    fun setRidePayment(method: String) {
        if (method == "CARD" && !FeatureFlags.ONLINE_PAYMENTS) {
            _uiState.update { it.copy(statusBanner = "Оплата картой появится после подключения платёжной системы") }
            return
        }
        _uiState.update { it.copy(ridePayment = method) }
    }

    // --- PICKUP POINT (point A) ---

    fun openPickupDialog(open: Boolean) =
        _uiState.update { it.copy(pickupDialogOpen = open, pickupSearchQuery = "", pickupSearchResults = emptyList()) }

    private var pickupSearchJob: Job? = null

    fun setPickupSearchQuery(q: String) {
        _uiState.update { it.copy(pickupSearchQuery = q) }
        pickupSearchJob?.cancel()
        if (q.trim().length < 2) {
            _uiState.update { it.copy(pickupSearchResults = emptyList()) }
            return
        }
        pickupSearchJob = viewModelScope.launch {
            delay(380)
            val s = _uiState.value
            val live = KyrgyzstanRoutingClient.searchKyrgyzstanAddresses(
                query = q,
                cityName = s.selectedCity.name,
                offlineMode = s.offlineMode,
                cityCenter = s.selectedCity.center
            )
            val curated = KgGeoCatalog.cities.flatMap { it.landmarks }.filter {
                it.name.contains(q, ignoreCase = true) || it.address.contains(q, ignoreCase = true)
            }
            _uiState.update { it.copy(pickupSearchResults = (live + curated).distinctBy { p -> p.name }.take(8)) }
        }
    }

    fun setPickupFromPlace(place: KgPlace) {
        _uiState.update {
            it.copy(
                pickupPoint = place.point,
                pickupAddress = place.address.ifBlank { place.name },
                pickupConfirmed = true,
                pickupDialogOpen = false,
                pickupSearchQuery = "",
                pickupSearchResults = emptyList(),
                statusBanner = "Точка подачи: ${place.name}"
            )
        }
        refreshLiveRoadRoute()
    }

    /** "My location" in the pickup dialog: GPS sets point A and the city. */
    fun usePickupMyLocation() {
        _uiState.update { it.copy(pickupDialogOpen = false) }
        // The passenger chose this point himself: it counts as confirmed.
        locateMyGps(confirm = true)
    }

    fun goToSavedAddress(label: String, address: String) {
        if (address.isBlank()) {
            _uiState.update { it.copy(statusBanner = "Укажите адрес «$label» в профиле") }
            return
        }
        if (_uiState.value.serviceType != ServiceType.TAXI) setServiceType(ServiceType.TAXI)
        selectSavedAddressQuick(label, address)
        _uiState.update { it.copy(passengerHome = false) }
    }

    /** Recent destination: keep the current pickup, set where to go. */
    fun goToRecentDestination(r: SharedRide) {
        if (_uiState.value.serviceType != ServiceType.TAXI) setServiceType(ServiceType.TAXI)
        _uiState.update {
            it.copy(
                passengerHome = false,
                dropoffAddress = r.dropoffAddress,
                dropoffPoint = GeoPoint(r.dropoffLat, r.dropoffLng)
            )
        }
        refreshLiveRoadRoute()
    }

    /** Puts the addresses of a past order back on the order screen. */
    fun repeatRide(r: SharedRide) {
        val city = if (cityFromGps) _uiState.value.selectedCity else KgGeoCatalog.cityByName(r.city)
        _uiState.update {
            it.copy(
                role = AppRole.PASSENGER,
                passengerHome = false,
                selectedCity = city,
                serviceType = when (r.serviceType) { "INTERCITY" -> ServiceType.INTERCITY; "FOOD" -> ServiceType.FOOD; else -> ServiceType.TAXI },
                pickupAddress = r.pickupAddress,
                pickupPoint = GeoPoint(r.pickupLat, r.pickupLng),
                dropoffAddress = r.dropoffAddress,
                dropoffPoint = GeoPoint(r.dropoffLat, r.dropoffLng),
                statusBanner = "Маршрут повторён: ${r.pickupAddress} → ${r.dropoffAddress}"
            )
        }
        refreshLiveRoadRoute()
    }

    fun sendSupportMessage(text: String, rideId: String?) {
        if (text.isBlank()) return
        _uiState.update { it.copy(supportSending = true) }
        viewModelScope.launch {
            val p = _uiState.value.profile
            val res = supportRepo.send(p.displayName, p.phone, text, rideId)
            _uiState.update {
                it.copy(
                    supportSending = false,
                    statusBanner = if (res.isSuccess) "Сообщение отправлено. Ответим в ближайшее время." else "Не удалось отправить. Проверьте интернет."
                )
            }
        }
    }

    fun resolveSupportTicket(id: String) {
        viewModelScope.launch { supportRepo.resolve(id) }
    }

    fun deleteAccount() {
        if (_uiState.value.accountDeleting) return
        _uiState.update { it.copy(accountDeleting = true) }
        viewModelScope.launch {
            val res = deletionRepo.deleteEverything()
            if (res.isSuccess) {
                dao.upsertProfile(LocalProfileEntity())
                docPrefs.edit().clear().apply()
                _uiState.update {
                    it.copy(
                        accountDeleting = false,
                        profile = LocalProfileEntity(),
                        osagoUntilMillis = null,
                        inspectionUntilMillis = null,
                        statusBanner = "Аккаунт и данные удалены"
                    )
                }
                onUserSignedOut()
            } else {
                _uiState.update {
                    it.copy(accountDeleting = false, statusBanner = res.exceptionOrNull()?.message ?: "Не удалось удалить аккаунт")
                }
            }
        }
    }

    // --- DRIVER DOCUMENTS: OSAGO / TECHNICAL INSPECTION ---

    fun loadDriverDocs() {
        val osago = docPrefs.getLong("osago", 0L).takeIf { it > 0 }
        val insp = docPrefs.getLong("inspection", 0L).takeIf { it > 0 }
        _uiState.update { it.copy(osagoUntilMillis = osago, inspectionUntilMillis = insp) }
        remindExpiringDocs()
    }

    fun setDriverDocDate(kind: String, millis: Long?) {
        docPrefs.edit().apply { if (millis == null) remove(kind) else putLong(kind, millis) }.apply()
        _uiState.update {
            if (kind == "osago") it.copy(osagoUntilMillis = millis) else it.copy(inspectionUntilMillis = millis)
        }
    }

    /** Notifies once a day when OSAGO or inspection ends within 7 days (or already ended). */
    private fun remindExpiringDocs() {
        val s = _uiState.value
        val now = System.currentTimeMillis()
        val week = 7 * 24 * 60 * 60 * 1000L
        val today = now / (24 * 60 * 60 * 1000L)
        if (docPrefs.getLong("remindedDay", -1) == today) return
        val soon = listOfNotNull(
            s.osagoUntilMillis?.takeIf { it - now < week }?.let { "ОСАГО" },
            s.inspectionUntilMillis?.takeIf { it - now < week }?.let { "техосмотр" }
        )
        if (soon.isEmpty()) return
        docPrefs.edit().putLong("remindedDay", today).apply()
        val text = "Скоро заканчивается: ${soon.joinToString(" и ")}. Продлите, чтобы работать без штрафов."
        _uiState.update { it.copy(statusBanner = text) }
        SoundAndNotificationManager.postNotification(appContext, "Документы водителя", text, isHighPriority = false)
    }

    // --- APP UPDATES FROM THE WEBSITE ---

    fun checkForUpdate() {
        viewModelScope.launch {
            val info = com.example.util.UpdateChecker.check(appContext)
            if (info != null) _uiState.update { it.copy(updateAvailable = info) }
        }
    }

    fun dismissUpdate() = _uiState.update { it.copy(updateAvailable = null, updateProgress = -1, updateError = null) }

    private var updateFile: java.io.File? = null

    /** Downloads the new version and opens the installer (asks once for permission to install). */
    fun startUpdate() {
        val info = _uiState.value.updateAvailable ?: return
        if (!com.example.util.AppUpdater.canInstall(appContext)) {
            _uiState.update { it.copy(updateError = "Разрешите KG Taxi устанавливать обновления: включите переключатель и вернитесь назад.") }
            com.example.util.AppUpdater.openInstallPermission(appContext)
            return
        }
        updateFile?.takeIf { it.exists() }?.let { f ->
            runCatching { com.example.util.AppUpdater.install(appContext, f) }
            return
        }
        _uiState.update { it.copy(updateProgress = 0, updateError = null) }
        viewModelScope.launch {
            val file = com.example.util.AppUpdater.download(appContext, info) { p ->
                _uiState.update { it.copy(updateProgress = p) }
            }
            if (file == null) {
                _uiState.update { it.copy(updateProgress = -1, updateError = "Не удалось скачать. Проверьте интернет и нажмите «Обновить» ещё раз.") }
                return@launch
            }
            updateFile = file
            _uiState.update { it.copy(updateProgress = 100) }
            runCatching { com.example.util.AppUpdater.install(appContext, file) }
                .onFailure { _uiState.update { it.copy(updateError = "Не удалось открыть установщик. Скачайте с сайта: mramantur-site.vercel.app/download.html") } }
        }
    }

    fun openDriverApplication() {
        if (FeatureFlags.REAL_RIDES) {
            openRegistration(asDriver = true)
            return
        }
        _uiState.update { it.copy(driverApplicationOpen = true) }
        viewModelScope.launch {
            val status = firestoreSync.fetchDriverApplicationStatus()
            _uiState.update { it.copy(driverApplicationStatus = status) }
        }
    }

    fun closeDriverApplication() = _uiState.update { it.copy(driverApplicationOpen = false) }

    fun submitDriverApplication(app: FirestoreSyncRepository.DriverApplication) {
        _uiState.update { it.copy(driverApplicationSending = true) }
        viewModelScope.launch {
            val res = firestoreSync.submitDriverApplication(app)
            _uiState.update {
                it.copy(
                    driverApplicationSending = false,
                    driverApplicationStatus = if (res.isSuccess) "pending" else it.driverApplicationStatus,
                    driverApplicationOpen = res.isFailure,
                    statusBanner = if (res.isSuccess) "Заявка отправлена. После проверки откроется режим водителя."
                    else "Не удалось отправить заявку: проверьте поля и интернет."
                )
            }
        }
    }

    // --- HEADER & GLOBAL SETTINGS ---

    fun setRole(role: AppRole) {
        val s = _uiState.value
        if (role in s.allowedRoles) {
            _uiState.update { it.copy(role = role) }
            if (FeatureFlags.REAL_RIDES) {
                if (role == AppRole.DISPATCHER) startDispatcherFeed() else stopDispatcherFeed()
            }
            return
        }
        val message = when {
            !s.isSignedIn -> "Войдите в аккаунт, чтобы открыть этот режим."
            !rolesLoaded -> {
                pendingRole = role
                "Проверяем ваш аккаунт…"
            }
            role == AppRole.DRIVER -> {
                openDriverApplication()
                return
            }
            else -> "Панель диспетчера доступна только сотрудникам KG Taxi."
        }
        _uiState.update { it.copy(statusBanner = message) }
    }

    fun cycleLanguage() {
        val next = when (_uiState.value.language) {
            AppLanguage.RU -> AppLanguage.KG
            AppLanguage.KG -> AppLanguage.EN
            AppLanguage.EN -> AppLanguage.RU
        }
        updateProfile { it.copy(language = next.code) }
    }

    fun toggleDarkTheme() {
        val nextDark = !_uiState.value.darkTheme
        _uiState.update {
            it.copy(
                themeMode = if (nextDark) AppThemeMode.DARK else AppThemeMode.LIGHT,
                mapLayerStyle = if (nextDark && it.mapLayerStyle == MapLayerStyle.STANDARD) MapLayerStyle.NIGHT else if (!nextDark && it.mapLayerStyle == MapLayerStyle.NIGHT) MapLayerStyle.STANDARD else it.mapLayerStyle
            )
        }
        updateProfile { it.copy(darkTheme = nextDark) }
    }

    fun setThemeMode(mode: AppThemeMode) {
        _uiState.update {
            it.copy(
                themeMode = mode,
                statusBanner = "Тема приложения: ${mode.label}"
            )
        }
        if (mode == AppThemeMode.DARK || mode == AppThemeMode.LIGHT) {
            updateProfile { it.copy(darkTheme = mode == AppThemeMode.DARK) }
        }
    }

    fun setMapLayerStyle(style: MapLayerStyle) {
        _uiState.update {
            it.copy(
                mapLayerStyle = style,
                statusBanner = "Слой карты: ${style.label}"
            )
        }
    }

    fun cycleMapLayerStyle() {
        val all = MapLayerStyle.entries
        val next = all[(all.indexOf(_uiState.value.mapLayerStyle) + 1) % all.size]
        setMapLayerStyle(next)
    }

    fun toggleSound() = updateProfile { it.copy(soundEnabled = !it.soundEnabled) }

    fun toggleOfflineMode() {
        _uiState.update {
            val next = !it.offlineMode
            it.copy(
                offlineMode = next,
                statusBanner = if (next) "Офлайн-режим: доступны сохранённые маршруты и векторная карта" else "Онлайн-режим активен"
            )
        }
        refreshLiveRoadRoute()
    }

    fun clearBanner() = _uiState.update { it.copy(statusBanner = null) }

    /** The city is set only by GPS; a manual choice is not allowed. */
    fun selectCity(city: KgCity) {
        if (city.name == _uiState.value.selectedCity.name) return
        _uiState.update { it.copy(statusBanner = "Город определяется автоматически по геолокации: ${it.selectedCity.name}") }
    }

    private fun applyCity(city: KgCity) {
        viewModelScope.launch { delay(300); syncPushToken() }
        val firstLandmark = city.landmarks.firstOrNull()
        val secondLandmark = city.landmarks.getOrNull(1) ?: firstLandmark
        _uiState.update {
            it.copy(
                selectedCity = city,
                pickupPoint = firstLandmark?.point ?: city.center,
                pickupAddress = firstLandmark?.address ?: "${city.streets.first()}, 1, ${city.name}",
                dropoffPoint = secondLandmark?.point ?: GeoPoint(city.center.lat + 0.015, city.center.lng + 0.015),
                dropoffAddress = secondLandmark?.address ?: "${city.streets.last()}, 24, ${city.name}"
            )
        }
        updateProfile { it.copy(preferredCity = city.name) }
        generateNearbyDrivers(city)
        generateDriverIncomingOrders(city)
        refreshLiveRoadRoute()
        refreshCityWeatherAndPasses()
    }

    fun toggleSurgeHeatmap() = _uiState.update { it.copy(showSurgeHeatmap = !it.showSurgeHeatmap) }

    fun toggleTrafficOverlay() {
        _uiState.update {
            val next = !it.showTrafficOverlay
            it.copy(
                showTrafficOverlay = next,
                statusBanner = if (next) "Слой пробок включён (${it.selectedCity.name})" else null
            )
        }
    }

    fun toggleMapPickTarget() {
        _uiState.update {
            val nextIsDropoff = !it.mapPickModeIsDropoff
            it.copy(
                mapPickModeIsDropoff = nextIsDropoff,
                statusBanner = if (nextIsDropoff) "Нажмите карту для выбора Точки Б (куда)" else "Нажмите карту для выбора Точки А (подача)"
            )
        }
    }

    fun refreshLiveRoadRoute() {
        routeFetchJob?.cancel()
        routeFetchJob = viewModelScope.launch {
            delay(180)
            val s = _uiState.value
            val result = KyrgyzstanRoutingClient.fetchRoadRoute(
                pickup = s.pickupPoint,
                dropoff = s.dropoffPoint,
                offlineMode = s.offlineMode,
                waypoints = s.waypoints.map { it.point }
            )
            _uiState.update {
                it.copy(
                    routePolyline = result.polyline,
                    liveRouteDistanceKm = result.distanceKm,
                    liveRouteDurationMin = result.durationMin,
                    routeFromOsrm = result.fromLiveOsrm
                )
            }
        }
    }

    fun addWaypoint(place: KgPlace) {
        if (_uiState.value.waypoints.size >= 2) {
            _uiState.update { it.copy(statusBanner = "Максимум 2 промежуточные остановки") }
            return
        }
        if (_uiState.value.waypoints.any { it.id == place.id }) return
        _uiState.update {
            it.copy(
                waypoints = it.waypoints + place,
                statusBanner = "Добавлена остановка: ${place.name} (+45 сом)"
            )
        }
        refreshLiveRoadRoute()
    }

    fun removeWaypoint(placeId: String) {
        _uiState.update { it.copy(waypoints = it.waypoints.filterNot { w -> w.id == placeId }) }
        refreshLiveRoadRoute()
    }

    fun selectPickupSpot(spot: String?) {
        _uiState.update {
            it.copy(
                selectedPickupSpot = if (it.selectedPickupSpot == spot) null else spot,
                statusBanner = if (spot != null && it.selectedPickupSpot != spot) "Точка встречи: $spot" else null
            )
        }
    }

    fun setBookingForOther(enabled: Boolean, name: String, phone: String) {
        _uiState.update {
            it.copy(
                bookingForOther = enabled,
                recipientName = name.trim(),
                recipientPhone = phone.trim(),
                statusBanner = if (enabled && name.isNotBlank()) "Заказ для получателя: ${name.trim()}" else null
            )
        }
    }

    fun onMapTapPickPoint(point: GeoPoint, isDropoff: Boolean = _uiState.value.mapPickModeIsDropoff) {
        viewModelScope.launch {
            val city = _uiState.value.selectedCity
            val resolved = LocationAndVoiceHelper.reverseGeocode(appContext, city, point)
            if (_uiState.value.activeRide != null && _uiState.value.activeRide?.status == TripStatus.IN_PROGRESS) {
                editDestinationInTrip(resolved, point)
                return@launch
            }
            _uiState.update {
                if (isDropoff) {
                    it.copy(dropoffPoint = point, dropoffAddress = resolved, statusBanner = "Точка Б: $resolved")
                } else {
                    it.copy(pickupPoint = point, pickupAddress = resolved, pickupConfirmed = true, pickupDialogOpen = false, statusBanner = "Подача (А): $resolved")
                }
            }
            refreshLiveRoadRoute()
        }
    }

    /** True once the city came from the phone's GPS: then nobody can change it by hand. */
    private var cityFromGps = false

    /** Detects the settlement by GPS (on start, when a driver goes on the line, on "Моё местоположение"). */
    fun detectCityByGps() {
        viewModelScope.launch {
            val res = LocationAndVoiceHelper.fetchCurrentLocation(appContext, _uiState.value.selectedCity)
            if (!res.fromRealGps) return@launch
            cityFromGps = true
            val city = KgGeoCatalog.cityByName(res.matchedCityName)
            if (city.name != _uiState.value.selectedCity.name) applyCity(city)
        }
    }

    fun locateMyGps(confirm: Boolean = false) {
        viewModelScope.launch {
            val res = LocationAndVoiceHelper.fetchCurrentLocation(appContext, _uiState.value.selectedCity)
            if (res.fromRealGps) cityFromGps = true
            val matchedCity = KgGeoCatalog.cityByName(res.matchedCityName)
            _uiState.update {
                it.copy(
                    selectedCity = matchedCity,
                    pickupPoint = res.point,
                    pickupAddress = res.address,
                    pickupConfirmed = it.pickupConfirmed || res.fromRealGps || confirm,
                    // An old destination left in another city: clear it ("Куда едем?").
                    dropoffAddress = if (it.serviceType == ServiceType.TAXI && KgGeoCatalog.haversineKm(res.point, it.dropoffPoint) > 40.0) "" else it.dropoffAddress,
                    dropoffPoint = if (it.serviceType == ServiceType.TAXI && KgGeoCatalog.haversineKm(res.point, it.dropoffPoint) > 40.0) res.point else it.dropoffPoint,
                    statusBanner = if (res.fromRealGps) "GPS определён: ${res.address}" else "Точка подачи (${matchedCity.name}): ${res.address}"
                )
            }
            refreshLiveRoadRoute()
        }
    }

    // --- SEARCH, FILTERS & SERVICES ---

    fun setSearchQuery(q: String) {
        _uiState.update { it.copy(searchQuery = q) }
        addressSearchJob?.cancel()
        if (q.trim().length < 2 || _uiState.value.offlineMode) {
            _uiState.update { it.copy(liveSearchResults = emptyList()) }
            return
        }
        addressSearchJob = viewModelScope.launch {
            delay(380)
            val live = KyrgyzstanRoutingClient.searchKyrgyzstanAddresses(
                query = q,
                cityName = _uiState.value.selectedCity.name,
                offlineMode = _uiState.value.offlineMode,
                cityCenter = _uiState.value.selectedCity.center
            )
            _uiState.update { it.copy(liveSearchResults = live) }
        }
    }

    /** Where exactly the driver should come (entrance, side of the street). Goes into the order. */
    fun setPickupNote(text: String) = _uiState.update { it.copy(selectedPickupSpot = text.take(80).ifBlank { null }) }

    fun setDestinationFromMap(point: GeoPoint) {
        viewModelScope.launch {
            val address = LocationAndVoiceHelper.reverseGeocode(appContext, _uiState.value.selectedCity, point)
            _uiState.update { it.copy(dropoffPoint = point, dropoffAddress = address, searchQuery = "", liveSearchResults = emptyList()) }
            refreshLiveRoadRoute()
        }
    }

    fun setRatingFilter(minRating: Double) = _uiState.update { it.copy(filterMinRating = minRating) }
    fun setPriceLevelFilter(maxLevel: Int) = _uiState.update { it.copy(filterMaxPriceLevel = maxLevel) }

    fun selectDestinationPlace(place: KgPlace) {
        _uiState.update {
            it.copy(
                dropoffPoint = place.point,
                dropoffAddress = "${place.name} • ${place.address}",
                searchQuery = "",
                liveSearchResults = emptyList()
            )
        }
        refreshLiveRoadRoute()
    }

    fun selectSavedAddressQuick(label: String, address: String, point: GeoPoint? = null) {
        val city = _uiState.value.selectedCity
        val p = point ?: GeoPoint(city.center.lat + 0.012, city.center.lng - 0.011)
        _uiState.update {
            it.copy(dropoffAddress = "$label: $address", dropoffPoint = p)
        }
        refreshLiveRoadRoute()
    }

    fun saveCurrentRouteOffline() {
        val s = _uiState.value
        val label = "Маршрут: ${s.pickupAddress.substringBefore(",")} → ${s.dropoffAddress.substringBefore(",")}"
        val entity = LocalSavedPlaceEntity(
            id = "offline_route_${System.currentTimeMillis()}",
            label = label.take(44),
            address = s.dropoffAddress,
            city = s.selectedCity.name,
            lat = s.dropoffPoint.lat,
            lng = s.dropoffPoint.lng,
            category = "OFFLINE_ROUTE",
            rating = 5.0
        )
        viewModelScope.launch {
            dao.upsertSavedPlace(entity)
            if (s.isSignedIn) firestoreSync.syncSavedPlace(entity)
            _uiState.update { it.copy(statusBanner = "Маршрут сохранён для офлайн-доступа") }
        }
    }

    fun loadSavedPlaceToRoute(place: LocalSavedPlaceEntity) {
        val city = if (cityFromGps) _uiState.value.selectedCity else KgGeoCatalog.cityByName(place.city)
        val targetPoint = GeoPoint(place.lat, place.lng)
        _uiState.update {
            it.copy(
                selectedCity = city,
                dropoffPoint = targetPoint,
                dropoffAddress = "${place.label} • ${place.address}",
                statusBanner = "Маршрут загружен: ${place.label}"
            )
        }
        generateNearbyDrivers(city)
        refreshLiveRoadRoute()
    }

    fun setServiceType(type: ServiceType) {
        if (!FeatureFlags.isServiceEnabled(type)) {
            showComingSoon("Эта услуга")
            return
        }
        _uiState.update { it.copy(serviceType = type, biddingActive = false) }
    }

    /** Banner for features that are switched off in [FeatureFlags]. */
    fun showComingSoon(featureName: String) {
        _uiState.update { it.copy(statusBanner = "$featureName скоро появится в KG Taxi.") }
    }

    fun selectTariff(tariffId: String) {
        if (tariffId !in FeatureFlags.ENABLED_TARIFF_IDS) {
            showComingSoon("Этот тариф")
            return
        }
        _uiState.update { it.copy(selectedTariffId = tariffId) }
        // Adaptive UI learning: remember frequently used tariff
        val profile = _uiState.value.profile
        val counts = profile.tariffUsageCounts.split(",")
            .mapNotNull { entry ->
                val parts = entry.split(":")
                if (parts.size == 2) parts[0] to (parts[1].toIntOrNull() ?: 0) else null
            }.toMap().toMutableMap()
        counts[tariffId] = (counts[tariffId] ?: 0) + 1
        val top = counts.maxByOrNull { it.value }?.key ?: tariffId
        val serialized = counts.entries.joinToString(",") { "${it.key}:${it.value}" }
        updateProfile { it.copy(preferredTariffId = top, tariffUsageCounts = serialized) }
    }

    fun setPickupSpeed(speed: PickupSpeed) {
        _uiState.update { it.copy(pickupSpeed = speed) }
    }

    fun updatePreferences(transform: (RidePreferences) -> RidePreferences) =
        _uiState.update { it.copy(preferences = transform(it.preferences)) }

    fun setPromoInput(code: String) = _uiState.update { it.copy(promoCodeInput = code) }

    fun applyPromoCode() {
        val s0 = _uiState.value
        val code = s0.promoCodeInput.trim().uppercase()
        val cfg = s0.appConfig
        val firstRide = s0.passengerRides.none { it.status == RideStatus.COMPLETED }
        // A friend's invite code (KG…): checked online, works only for the first ride.
        val ownPromo = cfg.promoActive && cfg.promoCode.isNotBlank() && code == cfg.promoCode.uppercase()
        if (!ownPromo && code.startsWith("KG") && code.length in 6..12) {
            viewModelScope.launch {
                val owner = referralRepo.ownerOf(code)
                val me = _uiState.value.signedInUid
                val p = when {
                    owner == null -> -1
                    owner == me -> -3
                    !firstRide -> -2
                    else -> cfg.referralPercent
                }
                _uiState.update {
                    it.copy(
                        appliedPromoPercent = p.coerceAtLeast(0),
                        statusBanner = when (p) {
                            -1 -> "Такого кода нет"
                            -2 -> "Код друга действует только на первую поездку"
                            -3 -> "Это ваш код — отправьте его друзьям"
                            else -> "Код друга применён: −$p%"
                        }
                    )
                }
            }
            return
        }
        val pct = when {
            code.isBlank() -> 0
            !ownPromo -> -1
            !firstRide -> -2
            else -> cfg.promoPercent
        }
        _uiState.update {
            it.copy(
                appliedPromoPercent = pct.coerceAtLeast(0),
                statusBanner = when (pct) {
                    -1 -> "Такого промокода нет"
                    -2 -> "Промокод действует только на первую поездку"
                    0 -> "Введите промокод"
                    else -> "Промокод $code применён: −$pct%"
                }
            )
        }
    }

    fun toggleUseBonusPoints() = _uiState.update { it.copy(useBonusPoints = !it.useBonusPoints) }
    fun setSplitCount(count: Int) = _uiState.update { it.copy(splitCount = count.coerceIn(1, 4)) }
    fun setReserveSchedule(timeLabel: String?, flight: FlightInfo?) {
        _uiState.update {
            val drop = when (flight?.airport) {
                "FRU" -> "Аэропорт Манас (FRU) • Рейс ${flight.code}"
                "OSS" -> "Аэропорт Ош (OSS) • Рейс ${flight.code}"
                else -> it.dropoffAddress
            }
            it.copy(
                reserveTimeLabel = timeLabel,
                selectedFlight = flight,
                dropoffAddress = drop,
                statusBanner = if (timeLabel != null) "Reserve: подача запланирована на $timeLabel" else null
            )
        }
    }

    // --- BIDDING ("ПРЕДЛОЖИТЬ СВОЮ ЦЕНУ") ---

    fun startBidding(offerSom: Int) {
        if (FeatureFlags.REAL_RIDES) {
            // "Offer your price": the order goes to drivers with the passenger's price; no made-up bids.
            val base = _uiState.value.priceBreakdown.finalTotalSom
            requestRealRide(offerSom.coerceIn(max(60, (base * 0.6).roundToInt()), base * 2))
            return
        }
        val base = _uiState.value.priceBreakdown.finalTotalSom
        val safeOffer = offerSom.coerceIn(max(60, (base * 0.6).roundToInt()), base * 2)
        val offers = listOf(
            DriverBidOffer("bid1", "Эрмек С.", "Hyundai Sonata (белый)", "01KG 412 ABC", 4.95, 4, safeOffer),
            DriverBidOffer("bid2", "Азамат Ж.", "Toyota Camry 55 (чёрный)", "01KG 889 KGA", 4.91, 3, safeOffer + 25),
            DriverBidOffer("bid3", "Айгуль М.", "BYD Qin Plus EV", "01KG 305 EV", 4.98, 5, safeOffer + 15)
        )
        _uiState.update { it.copy(biddingActive = true, userBidSom = safeOffer, bidOffers = offers) }
    }

    fun closeBidding() = _uiState.update { it.copy(biddingActive = false, bidOffers = emptyList()) }

    fun acceptBidOffer(offer: DriverBidOffer) {
        _uiState.update { it.copy(biddingActive = false, bidOffers = emptyList()) }
        requestOrder(customPriceSom = offer.offeredPriceSom, forcedDriver = offer.driverName to "${offer.carModel} • ${offer.plateNumber}")
    }

    // --- HOURLY / COURIER / CARGO / INTERCITY / FOOD ---

    fun setHourlyHours(hours: Int) = _uiState.update { it.copy(hourlyHours = hours.coerceIn(1, 12), selectedExcursion = null) }
    fun selectExcursion(preset: ExcursionPreset) {
        _uiState.update {
            it.copy(selectedExcursion = preset, hourlyHours = preset.hours, dropoffPoint = preset.destination, dropoffAddress = preset.title)
        }
        refreshLiveRoadRoute()
    }

    fun setCourierDetails(recipient: String, comment: String) =
        _uiState.update { it.copy(courierRecipient = recipient, courierComment = comment) }

    fun setCargoOptions(vehicle: String, loaders: Int) =
        _uiState.update { it.copy(cargoVehicle = vehicle, cargoLoaders = loaders.coerceIn(0, 4)) }

    fun selectIntercityRoute(route: IntercityRoute) {
        _uiState.update {
            it.copy(
                selectedIntercityRoute = route,
                selectedIntercityTripId = null,
                occupiedIntercitySeats = emptySet(),
                selectedIntercitySeats = it.selectedIntercitySeats.ifEmpty { setOf(2) }
            )
        }
    }

    fun selectIntercityScheduledTrip(trip: IntercityScheduledTrip) {
        val firstFree = (1..6).firstOrNull { it !in trip.occupiedSeats } ?: 2
        _uiState.update {
            it.copy(
                selectedIntercityTripId = trip.id,
                occupiedIntercitySeats = trip.occupiedSeats,
                selectedIntercitySeats = setOf(firstFree),
                statusBanner = "Выбран рейс ${trip.departureTime} • ${trip.driverName} (${trip.carModel})"
            )
        }
    }

    fun toggleIntercitySeat(seatNumber: Int) {
        if (seatNumber in _uiState.value.occupiedIntercitySeats) return
        _uiState.update { state ->
            val next = state.selectedIntercitySeats.toMutableSet()
            if (seatNumber in next && next.size > 1) next.remove(seatNumber) else next.add(seatNumber)
            state.copy(selectedIntercitySeats = next)
        }
    }

    fun selectFoodVenue(venueId: String) = _uiState.update { it.copy(selectedVenueId = venueId) }

    fun addFoodToCart(venue: FoodVenue, item: FoodMenuItem) {
        _uiState.update { state ->
            val current = state.foodCart.toMutableList()
            val idx = current.indexOfFirst { it.item.id == item.id }
            if (idx >= 0) {
                current[idx] = current[idx].copy(quantity = current[idx].quantity + 1)
            } else {
                current += FoodCartEntry(venue.id, venue.name, item, 1)
            }
            state.copy(foodCart = current)
        }
    }

    fun removeFoodFromCart(itemId: String) {
        _uiState.update { state ->
            val current = state.foodCart.mapNotNull { entry ->
                if (entry.item.id != itemId) entry
                else if (entry.quantity > 1) entry.copy(quantity = entry.quantity - 1) else null
            }
            state.copy(foodCart = current)
        }
    }

    // --- ORDER LIFECYCLE, IN-TRIP EDIT, RIDECHECK & POST-TRIP ---

    fun requestOrder(customPriceSom: Int? = null, forcedDriver: Pair<String, String>? = null) {
        if (FeatureFlags.REAL_RIDES) {
            requestRealRide(customPriceSom)
            return
        }
        val s = _uiState.value
        val price = customPriceSom ?: s.priceBreakdown.finalTotalSom
        val tripId = "trip_${System.currentTimeMillis()}"
        val pin = (1000 + Random.nextInt(9000)).toString()
        val isLady = s.serviceType == ServiceType.TAXI && s.selectedTariff.ladyOnly
        val isSober = s.serviceType == ServiceType.TAXI && s.selectedTariff.soberDriver
        val preferredFav = s.favoriteDrivers.firstOrNull { it !in s.blockedDrivers }
        val (driverName, driverCar) = forcedDriver ?: if (isLady) {
            "Айпери К." to "Kia K5 (белый) • 01KG 521 LDK"
        } else if (isSober) {
            "Руслан А. (Перегон)" to "Ваш автомобиль • Стаж 12 лет"
        } else if (s.serviceType == ServiceType.TAXI && s.selectedTariff.ecoGreen) {
            "Тимур Б." to "BYD Song Plus EV • 01KG 901 EV"
        } else if (preferredFav != null) {
            preferredFav to "Toyota Camry 70 (Избранный ★) • 01KG 707 VIP"
        } else {
            val fallbackName = if ("Нурлан Т." in s.blockedDrivers) "Эрмек С." else "Нурлан Т."
            fallbackName to "Toyota Camry 70 • 01KG 777 KGT"
        }

        val tariffLabel = when (s.serviceType) {
            ServiceType.TAXI -> s.selectedTariff.nameRu
            ServiceType.HOURLY -> s.selectedExcursion?.title ?: "Почасовая (${s.hourlyHours} ч)"
            ServiceType.COURIER -> "Курьер от двери до двери"
            ServiceType.CARGO -> s.cargoVehicle
            ServiceType.INTERCITY -> "${s.selectedIntercityRoute.fromCity} → ${s.selectedIntercityRoute.toCity} (места ${s.selectedIntercitySeats.sorted().joinToString()})"
            ServiceType.FOOD -> s.foodCart.firstOrNull()?.venueName ?: "Доставка еды KG"
        }

        val dropLabel = when (s.serviceType) {
            ServiceType.INTERCITY -> s.selectedIntercityRoute.toCity
            ServiceType.FOOD -> s.pickupAddress
            else -> s.dropoffAddress
        }

        val ride = ActiveRideState(
            tripId = tripId,
            serviceType = s.serviceType,
            tariffName = tariffLabel,
            city = s.selectedCity.name,
            pickupAddress = s.pickupAddress,
            dropoffAddress = dropLabel,
            pickupPoint = s.pickupPoint,
            dropoffPoint = s.dropoffPoint,
            distanceKm = s.routeDistanceKm,
            durationMin = s.routeDurationMin,
            totalPriceSom = price,
            paymentMethod = if (FeatureFlags.ONLINE_PAYMENTS) PaymentMethod.fromCode(s.profile.paymentMethod) else PaymentMethod.CASH,
            status = TripStatus.SEARCHING,
            driverName = driverName,
            driverCar = driverCar,
            driverRating = 4.96,
            driverEtaMin = 4,
            safetyPin = pin,
            progress = 0f,
            businessTrip = s.profile.businessProfileActive,
            splitCount = s.splitCount,
            scheduledLabel = s.reserveTimeLabel,
            pickupSpotNote = s.selectedPickupSpot,
            recipientName = if (s.bookingForOther) s.recipientName.ifBlank { "Гость" } else null,
            recipientPhone = if (s.bookingForOther) s.recipientPhone else null,
            waypoints = s.waypoints,
            waitingSeconds = 0,
            meetAndGreetSignText = if (s.meetAndGreetEnabled) s.meetAndGreetSignText.ifBlank { s.profile.displayName } else null,
            courierParcelPhotoUri = if (s.serviceType == ServiceType.COURIER) s.courierParcelPhotoUri else null
        )

        val welcomeDriverMsg = DriverChatMessage(
            id = "chat_init_$tripId",
            fromPassenger = false,
            originalText = if (s.selectedPickupSpot != null) {
                "Салам! Выезжаю к точке «${s.selectedPickupSpot}». Буду через ~4 мин."
            } else {
                "Салам! Заказ принял, уже еду к точке подачи."
            }
        )

        _uiState.update {
            it.copy(
                activeRide = ride,
                driverChatMessages = listOf(welcomeDriverMsg),
                foodCart = if (s.serviceType == ServiceType.FOOD) emptyList() else it.foodCart
            )
        }
        runRideSimulation(ride)
    }

    private fun runRideSimulation(initialRide: ActiveRideState) {
        rideSimulationJob?.cancel()
        rideSimulationJob = viewModelScope.launch {
            delay(1800)
            // Assigned
            SoundAndNotificationManager.playCue(SoundCue.DRIVER_ASSIGNED, _uiState.value.soundEnabled)
            SoundAndNotificationManager.postNotification(
                appContext,
                "Водитель назначен — ${initialRide.driverName}",
                "${initialRide.driverCar} • PIN: ${initialRide.safetyPin}"
            )
            _uiState.update { state ->
                state.activeRide?.let { r ->
                    state.copy(activeRide = r.copy(status = TripStatus.ASSIGNED, progress = 0.12f, driverEtaMin = 3))
                } ?: state
            }

            delay(2800)
            // Arrived
            SoundAndNotificationManager.playCue(SoundCue.DRIVER_ARRIVED, _uiState.value.soundEnabled)
            SoundAndNotificationManager.postNotification(
                appContext,
                "Автомобиль подан!",
                "Назовите водителю PIN ${initialRide.safetyPin}"
            )
            _uiState.update { state ->
                state.activeRide?.let { r ->
                    state.copy(activeRide = r.copy(status = TripStatus.ARRIVED, progress = 0.25f, driverEtaMin = 0, waitingSeconds = 1))
                } ?: state
            }

            // Live free-waiting countdown ticks while driver waits at pickup
            repeat(5) { tick ->
                delay(1000)
                _uiState.update { state ->
                    state.activeRide?.let { r ->
                        if (r.status == TripStatus.ARRIVED) {
                            state.copy(activeRide = r.copy(waitingSeconds = tick + 2))
                        } else state
                    } ?: state
                }
            }
            // In progress
            _uiState.update { state ->
                state.activeRide?.let { r ->
                    state.copy(activeRide = r.copy(status = TripStatus.IN_PROGRESS, progress = 0.45f))
                } ?: state
            }

            // Animate progress smoothly + trigger RideCheck safety prompt midway
            val steps = listOf(0.60f, 0.72f, 0.88f, 1.0f)
            steps.forEachIndexed { idx, p ->
                delay(2400)
                if (idx == 1) {
                    SoundAndNotificationManager.playCue(SoundCue.RIDE_CHECK_ALERT, _uiState.value.soundEnabled)
                }
                _uiState.update { state ->
                    state.activeRide?.let { r ->
                        state.copy(activeRide = r.copy(progress = p, rideCheckAlert = idx == 1 || r.rideCheckAlert))
                    } ?: state
                }
            }
            completeActiveRide()
        }
    }

    fun dismissRideCheck() {
        _uiState.update { state ->
            state.activeRide?.let { r -> state.copy(activeRide = r.copy(rideCheckAlert = false)) } ?: state
        }
    }

    fun editDestinationInTrip(newAddress: String, newPoint: GeoPoint? = null) {
        _uiState.update { state ->
            val r = state.activeRide ?: return@update state
            val pt = newPoint ?: GeoPoint(r.dropoffPoint.lat + 0.008, r.dropoffPoint.lng + 0.006)
            val newDist = KgGeoCatalog.haversineKm(r.pickupPoint, pt)
            val newDur = KgGeoCatalog.estimateDurationMin(newDist)
            val updatedPrice = max(r.totalPriceSom, (newDist * 26 + 110).roundToInt())
            SoundAndNotificationManager.postNotification(
                appContext,
                "Маршрут обновлён в поездке",
                "Новый адрес: $newAddress ($updatedPrice сом)"
            )
            state.copy(
                dropoffAddress = newAddress,
                dropoffPoint = pt,
                activeRide = r.copy(
                    dropoffAddress = newAddress,
                    dropoffPoint = pt,
                    distanceKm = newDist,
                    durationMin = newDur,
                    totalPriceSom = updatedPrice
                ),
                statusBanner = "Адрес назначения обновлён: $newAddress"
            )
        }
        refreshLiveRoadRoute()
    }

    fun cancelActiveRide(reason: String? = null) {
        rideSimulationJob?.cancel()
        val realId = passengerRideId
        if (realId != null) {
            stopPassengerRideObserver()
            viewModelScope.launch {
                val res = rides.cancelAsPassenger(realId, reason)
                if (res.isFailure) {
                    _uiState.update { it.copy(statusBanner = "Не удалось отменить заказ: проверьте интернет") }
                }
            }
        }
        _uiState.update { it.copy(activeRide = null, statusBanner = "Заказ отменён") }
    }

    fun completeActiveRideNow() {
        // A real ride is finished by the driver, not by the passenger.
        if (passengerRideId != null) {
            _uiState.update { it.copy(statusBanner = "Поездку завершает водитель после прибытия.") }
            return
        }
        rideSimulationJob?.cancel()
        completeActiveRide()
    }

    // --- REAL RIDES (Firestore) — passenger side ---

    private fun requestRealRide(customPriceSom: Int?) {
        val s = _uiState.value
        if (!s.isSignedIn) {
            _uiState.update { it.copy(statusBanner = "Войдите в аккаунт, чтобы заказать такси.") }
            return
        }
        if (s.serviceType == ServiceType.TAXI && s.dropoffAddress.isBlank()) {
            _uiState.update { it.copy(statusBanner = "Укажите, куда едем") }
            return
        }
        if (!FeatureFlags.isServiceEnabled(s.serviceType)) {
            showComingSoon("Эта услуга")
            return
        }
        if (s.activeRide != null) return
        if (!s.pickupConfirmed) {
            _uiState.update {
                it.copy(pickupDialogOpen = true, statusBanner = "Укажите точку подачи — где вас будет ждать водитель")
            }
            return
        }

        val price = customPriceSom ?: s.priceBreakdown.finalTotalSom
        val isIntercity = s.serviceType == ServiceType.INTERCITY
        val dropLabel = if (isIntercity) s.selectedIntercityRoute.toCity else s.dropoffAddress
        val tariffLabel = if (isIntercity) {
            "${s.selectedIntercityRoute.fromCity} → ${s.selectedIntercityRoute.toCity}"
        } else s.selectedTariff.nameRu
        val pin = (1000 + Random.nextInt(9000)).toString()
        val comment = listOfNotNull(
            s.selectedPickupSpot?.let { "Подъезд/место: $it" },
            if (s.bookingForOther) "Для: ${s.recipientName} ${s.recipientPhone}".trim() else null,
            listOfNotNull(
                "тихая поездка".takeIf { s.preferences.quietRide },
                "без кондиционера".takeIf { !s.preferences.climateAc },
                "помочь с багажом".takeIf { s.preferences.luggageHelp },
                "с животным".takeIf { s.preferences.petFriendly }
            ).takeIf { it.isNotEmpty() }?.joinToString(", ", prefix = "Пожелания: "),
            com.example.util.ACCESSIBILITY_OPTIONS.filter { it.first in s.passengerAccessibility }.map { it.second.lowercase() }
                .takeIf { it.isNotEmpty() }?.joinToString(", ", prefix = "Нужна помощь: "),
            s.waypoints.takeIf { it.isNotEmpty() && !isIntercity }?.joinToString("; ", prefix = "Остановки: ") { it.name.take(60) },
            PRIORITY_MARK.takeIf { !isIntercity && s.pickupSpeed == PickupSpeed.PRIORITY },
            WAIT_SAVE_MARK.takeIf { !isIntercity && s.pickupSpeed == PickupSpeed.WAIT_AND_SAVE },
            "PIN $pin"
        ).joinToString(" • ")

        val departure = if (isIntercity) s.intercityDepartureMillis else s.taxiScheduledMillis
        if (departure != null && departure > System.currentTimeMillis() + 30 * 60_000L) {
            _uiState.update { it.copy(statusBanner = "Отправляем предзаказ водителям…") }
            viewModelScope.launch {
                val created = rides.createRide(
                    passengerName = s.profile.displayName,
                    serviceType = s.serviceType.name,
                    city = s.selectedCity.name,
                    pickupAddress = s.pickupAddress,
                    dropoffAddress = dropLabel,
                    pickupLat = s.pickupPoint.lat,
                    pickupLng = s.pickupPoint.lng,
                    dropoffLat = s.dropoffPoint.lat,
                    dropoffLng = s.dropoffPoint.lng,
                    priceSom = price,
                    comment = comment,
                    passengerPhone = s.profile.phone,
                    scheduledAtMillis = departure,
                    seats = if (isIntercity) s.selectedIntercitySeats.size.coerceIn(1, 6) else null,
                    tariffId = if (isIntercity) null else s.selectedTariff.id,
                    tariffName = if (isIntercity) "Межгород" else s.selectedTariff.nameRu,
                    paymentMethod = s.ridePayment,
                    passengerRatingAvg = s.passengerRides.mapNotNull { it.passengerRating }.takeIf { it.isNotEmpty() }?.average(),
                    passengerTrips = s.passengerRides.count { it.status == RideStatus.COMPLETED }
                )
                _uiState.update {
                    created.fold(
                        { _ -> it.copy(intercityDepartureMillis = null, taxiScheduledMillis = null, statusBanner = "Заказ на ${formatDeparture(departure)} создан. Сообщим, когда водитель его возьмёт.") },
                        { e -> it.copy(statusBanner = "Не удалось создать предзаказ: ${e.message ?: "нет интернета"}") }
                    )
                }
            }
            return
        }

        val pending = ActiveRideState(
            tripId = "pending_${System.currentTimeMillis()}",
            serviceType = s.serviceType,
            tariffName = tariffLabel,
            city = s.selectedCity.name,
            pickupAddress = s.pickupAddress,
            dropoffAddress = dropLabel,
            pickupPoint = s.pickupPoint,
            dropoffPoint = s.dropoffPoint,
            distanceKm = s.routeDistanceKm,
            durationMin = s.routeDurationMin,
            totalPriceSom = price,
            paymentMethod = PaymentMethod.CASH,
            status = TripStatus.SEARCHING,
            driverName = "Ищем водителя…",
            driverCar = "",
            driverRating = 0.0,
            driverEtaMin = 0,
            safetyPin = pin,
            progress = 0f,
            pickupSpotNote = s.selectedPickupSpot
        )
        _uiState.update { it.copy(activeRide = pending, driverChatMessages = emptyList(), statusBanner = "Отправляем заказ водителям…") }

        viewModelScope.launch {
            val created = rides.createRide(
                passengerName = s.profile.displayName,
                serviceType = s.serviceType.name,
                city = s.selectedCity.name,
                pickupAddress = s.pickupAddress,
                dropoffAddress = dropLabel,
                pickupLat = s.pickupPoint.lat,
                pickupLng = s.pickupPoint.lng,
                dropoffLat = s.dropoffPoint.lat,
                dropoffLng = s.dropoffPoint.lng,
                priceSom = price,
                comment = comment,
                passengerPhone = s.profile.phone,
                seats = if (isIntercity) s.selectedIntercitySeats.size.coerceIn(1, 6) else null,
                tariffId = if (isIntercity) null else s.selectedTariff.id,
                tariffName = if (isIntercity) "Межгород" else s.selectedTariff.nameRu,
                paymentMethod = s.ridePayment,
                passengerRatingAvg = s.passengerRides.mapNotNull { it.passengerRating }.takeIf { it.isNotEmpty() }?.average(),
                passengerTrips = s.passengerRides.count { it.status == RideStatus.COMPLETED }
            )
            created.onSuccess { id ->
                passengerRideId = id
                startPassengerChat(id)
                _uiState.update { st ->
                    st.copy(
                        activeRide = st.activeRide?.copy(tripId = id),
                        statusBanner = "Заказ отправлен. Ищем водителя рядом…"
                    )
                }
                observePassengerRide(id)
            }.onFailure { e ->
                _uiState.update {
                    it.copy(activeRide = null, statusBanner = "Не удалось создать заказ: ${e.message ?: "нет интернета"}")
                }
            }
        }
    }

    /**
     * Food and groceries: a courier (a driver who takes deliveries) buys what is listed and brings it.
     * The customer pays the courier for the goods and the delivery in cash or by QR.
     */
    fun requestFoodOrder(kind: String, items: String, shop: String, goodsSom: Int, note: String) {
        val s = _uiState.value
        if (!s.isSignedIn) {
            _uiState.update { it.copy(statusBanner = "Войдите в аккаунт, чтобы заказать доставку.") }
            return
        }
        if (items.isBlank()) {
            _uiState.update { it.copy(statusBanner = "Напишите, что купить") }
            return
        }
        if (s.activeRide != null) {
            _uiState.update { it.copy(statusBanner = "Сначала завершите текущий заказ") }
            return
        }
        val fee = FOOD_DELIVERY_FEE_SOM
        val pin = (1000 + Random.nextInt(9000)).toString()
        val shopLabel = shop.trim().ifBlank { "любой магазин рядом" }
        val comment = listOfNotNull(
            "Купить ($kind): " + items.trim().lines().filter { it.isNotBlank() }.joinToString(", ").take(220),
            "Где купить: " + shopLabel.take(60),
            if (goodsSom > 0) "Покупки ≈ $goodsSom сом" else null,
            note.trim().takeIf { it.isNotBlank() }?.take(60),
            "PIN $pin"
        ).joinToString(" • ")
        val pending = ActiveRideState(
            tripId = "pending_" + System.currentTimeMillis(),
            serviceType = ServiceType.FOOD,
            tariffName = "Доставка: $kind",
            city = s.selectedCity.name,
            pickupAddress = shopLabel,
            dropoffAddress = s.pickupAddress,
            pickupPoint = s.pickupPoint,
            dropoffPoint = s.pickupPoint,
            distanceKm = 0.0,
            durationMin = 30,
            totalPriceSom = fee,
            paymentMethod = PaymentMethod.CASH,
            status = TripStatus.SEARCHING,
            driverName = "Ищем курьера…",
            driverCar = "",
            driverRating = 0.0,
            driverEtaMin = 0,
            safetyPin = pin,
            progress = 0f
        )
        _uiState.update { it.copy(activeRide = pending, driverChatMessages = emptyList(), statusBanner = "Отправляем заказ курьерам…") }
        viewModelScope.launch {
            val created = rides.createRide(
                passengerName = s.profile.displayName,
                serviceType = "FOOD",
                city = s.selectedCity.name,
                pickupAddress = shopLabel,
                dropoffAddress = s.pickupAddress,
                pickupLat = s.pickupPoint.lat,
                pickupLng = s.pickupPoint.lng,
                dropoffLat = s.pickupPoint.lat,
                dropoffLng = s.pickupPoint.lng,
                priceSom = fee,
                comment = comment,
                passengerPhone = s.profile.phone,
                tariffName = "Доставка",
                paymentMethod = "CASH",
                passengerRatingAvg = s.passengerRides.mapNotNull { it.passengerRating }.takeIf { it.isNotEmpty() }?.average(),
                passengerTrips = s.passengerRides.count { it.status == RideStatus.COMPLETED }
            )
            created.onSuccess { id ->
                passengerRideId = id
                startPassengerChat(id)
                _uiState.update { st -> st.copy(activeRide = st.activeRide?.copy(tripId = id), statusBanner = "Заказ отправлен. Ищем курьера рядом…") }
                observePassengerRide(id)
            }.onFailure { e ->
                _uiState.update { it.copy(activeRide = null, statusBanner = "Не удалось создать заказ: " + (e.message ?: "нет интернета")) }
            }
        }
    }

    private fun observePassengerRide(rideId: String) {
        passengerRideJob?.cancel()
        var lastStatus = RideStatus.SEARCHING
        passengerRideJob = viewModelScope.launch {
            rides.observeRide(rideId).collect { shared ->
                if (shared == null) return@collect
                val statusChanged = shared.status != lastStatus
                lastStatus = shared.status
                when (shared.status) {
                    RideStatus.CANCELLED -> {
                        stopPassengerRideObserver()
                        _uiState.update { it.copy(activeRide = null, statusBanner = "Водитель отменил заказ. Попробуйте заказать снова.") }
                    }
                    RideStatus.COMPLETED -> {
                        applySharedRideToPassenger(shared)
                        stopPassengerRideObserver()
                        completeActiveRide()
                    }
                    else -> {
                        applySharedRideToPassenger(shared)
                        if (statusChanged) notifyPassengerStatus(shared)
                    }
                }
            }
        }
    }

    private var avatarLoadedFor: String? = null

    private fun loadDriverPhoto(driverId: String) {
        if (avatarLoadedFor == driverId) return
        avatarLoadedFor = driverId
        viewModelScope.launch {
            val photo = rides.fetchDriverAvatar(driverId) ?: return@launch
            _uiState.update { st -> st.activeRide?.let { st.copy(activeRide = it.copy(driverPhoto = photo)) } ?: st }
        }
    }

    private fun applySharedRideToPassenger(shared: SharedRide) {
        shared.driverId?.let { loadDriverPhoto(it) }
        val status = when (shared.status) {
            RideStatus.ASSIGNED -> TripStatus.ASSIGNED
            RideStatus.ARRIVED -> TripStatus.ARRIVED
            RideStatus.IN_PROGRESS -> TripStatus.IN_PROGRESS
            RideStatus.COMPLETED -> TripStatus.COMPLETED
            else -> TripStatus.SEARCHING
        }
        val progress = when (status) {
            TripStatus.ASSIGNED -> 0.12f
            TripStatus.ARRIVED -> 0.25f
            TripStatus.IN_PROGRESS -> 0.6f
            TripStatus.COMPLETED -> 1f
            else -> 0f
        }
        val rq = shared.routeRequest
        if (rq != null && rq.status != "pending" && _uiState.value.passengerRouteRequestStatus == "pending") {
            _uiState.update {
                it.copy(statusBanner = if (rq.status == "accepted") "Водитель согласился: новый маршрут, цена ${rq.priceSom} сом" else "Водитель не может изменить маршрут")
            }
        }
        _uiState.update { it.copy(passengerRouteRequestStatus = rq?.status) }
        _uiState.update { st ->
            val r = st.activeRide ?: return@update st
            st.copy(
                activeRide = r.copy(
                    dropoffAddress = if (rq?.status == "accepted") shared.dropoffAddress else r.dropoffAddress,
                    dropoffPoint = if (rq?.status == "accepted") GeoPoint(shared.dropoffLat, shared.dropoffLng) else r.dropoffPoint,
                    totalPriceSom = if (rq?.status == "accepted") shared.priceSom else r.totalPriceSom,
                    status = status,
                    progress = progress,
                    driverName = shared.driverName ?: r.driverName,
                    driverRating = shared.driverRatingAvg?.takeIf { it > 0 } ?: r.driverRating,
                    driverTrips = shared.driverTrips ?: r.driverTrips,
                    driverDeaf = shared.driverDeaf,
                    driverPayPhone = shared.driverPayPhone?.takeIf { it.isNotBlank() } ?: r.driverPayPhone,
                    driverPoint = if (shared.driverLat != null && shared.driverLng != null) GeoPoint(shared.driverLat, shared.driverLng) else r.driverPoint,
                    driverEtaMin = pickupEtaMinutes(shared, status, r.pickupPoint) ?: r.driverEtaMin,
                    driverCar = listOfNotNull(shared.driverCar, shared.driverPlate)
                        .filter { it.isNotBlank() }.joinToString(" • ").ifBlank { r.driverCar }
                )
            )
        }
    }

    /**
     * Real pickup time from the driver position (shared every ~10 s): straight distance × 1.35
     * for city roads at ~25 km/h. Null when the driver position is not known yet.
     */
    private fun pickupEtaMinutes(shared: SharedRide, status: TripStatus, pickup: GeoPoint): Int? {
        if (status == TripStatus.ARRIVED || status == TripStatus.IN_PROGRESS) return 0
        if (status != TripStatus.ASSIGNED) return null
        val lat = shared.driverLat ?: return null
        val lng = shared.driverLng ?: return null
        val roadKm = KgGeoCatalog.haversineKm(GeoPoint(lat, lng), pickup) * 1.35
        return max(1, kotlin.math.ceil(roadKm / 25.0 * 60.0).toInt())
    }

    private fun notifyPassengerStatus(shared: SharedRide) {
        val sound = _uiState.value.soundEnabled
        val pin = _uiState.value.activeRide?.safetyPin.orEmpty()
        when (shared.status) {
            RideStatus.ASSIGNED -> {
                SoundAndNotificationManager.playCue(SoundCue.DRIVER_ASSIGNED, sound)
                SoundAndNotificationManager.postNotification(
                    appContext,
                    "Водитель едет — ${shared.driverName ?: "водитель"}",
                    listOfNotNull(shared.driverCar, shared.driverPlate).joinToString(" • ") + " • PIN: $pin"
                )
            }
            RideStatus.ARRIVED -> {
                SoundAndNotificationManager.playCue(SoundCue.DRIVER_ARRIVED, sound)
                SoundAndNotificationManager.postNotification(appContext, "Машина подъехала!", "Ваш PIN-код $pin поможет вам и водителю убедиться, что вы нашли друг друга")
            }
            RideStatus.IN_PROGRESS -> _uiState.update { it.copy(statusBanner = "Поездка началась. Хорошей дороги!") }
        }
    }

    private fun RideRepository.ChatMessage.toUi(passengerSide: Boolean): DriverChatMessage {
        val mine = senderId == rides.currentUid
        return DriverChatMessage(
            id = id,
            fromPassenger = if (passengerSide) mine else !mine,
            originalText = text,
            timeLabel = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(createdAtMillis))
        )
    }

    private fun startPassengerChat(rideId: String) {
        passengerChatJob?.cancel()
        var seen = 0
        passengerChatJob = viewModelScope.launch {
            rides.observeMessages(rideId).collect { list ->
                val ui = list.map { it.toUi(passengerSide = true) }
                if (list.size > seen && seen > 0 && list.last().senderId != rides.currentUid) {
                    SoundAndNotificationManager.postNotification(appContext, "Сообщение от водителя", list.last().text)
                }
                seen = list.size
                _uiState.update { it.copy(driverChatMessages = ui) }
            }
        }
    }

    private fun startDriverChat(rideId: String) {
        driverChatJob?.cancel()
        var seen = 0
        driverChatJob = viewModelScope.launch {
            rides.observeMessages(rideId).collect { list ->
                val ui = list.map { it.toUi(passengerSide = false) }
                if (list.size > seen && seen > 0 && list.last().senderId != rides.currentUid) {
                    SoundAndNotificationManager.postNotification(appContext, "Сообщение от пассажира", list.last().text)
                }
                seen = list.size
                _uiState.update { it.copy(driverActiveOrderMessages = ui) }
            }
        }
    }

    private fun stopPassengerRideObserver() {
        passengerChatJob?.cancel()
        passengerChatJob = null
        passengerRideJob?.cancel()
        passengerRideJob = null
        passengerRideId = null
    }

    private fun completeActiveRide() {
        val r = _uiState.value.activeRide ?: return
        SoundAndNotificationManager.playCue(SoundCue.TRIP_COMPLETED, _uiState.value.soundEnabled)
        SoundAndNotificationManager.postNotification(
            appContext,
            "Поездка завершена • ${r.totalPriceSom} сом",
            "Спасибо за поездку с KG Taxi! Оцените водителя ${r.driverName}."
        )
        val entity = LocalTripEntity(
            id = r.tripId,
            serviceType = r.serviceType.name,
            tariffName = r.tariffName,
            city = r.city,
            pickupAddress = r.pickupAddress,
            dropoffAddress = r.dropoffAddress,
            distanceKm = r.distanceKm,
            durationMin = r.durationMin,
            totalPriceSom = r.totalPriceSom,
            paymentMethod = r.paymentMethod.code,
            status = TripStatus.COMPLETED.name,
            driverName = r.driverName,
            driverCar = r.driverCar,
            safetyPin = r.safetyPin,
            rating = 0,
            tipSom = 0,
            businessTrip = r.businessTrip,
            lostItemNote = "",
            createdAtMillis = System.currentTimeMillis()
        )
        _uiState.update { it.copy(activeRide = null, postTripReceipt = entity) }
        viewModelScope.launch {
            dao.upsertTrip(entity)
            // Award 10% cashback with KG Plus (KG One) or 5% standard
            val rate = if ((FeatureFlags.WALLET && _uiState.value.profile.kgOneActive)) 0.10 else 0.05
            val bonusGain = max(10, (r.totalPriceSom * rate).roundToInt())
            val nextProfile = _uiState.value.profile.copy(bonusPoints = _uiState.value.profile.bonusPoints + bonusGain)
            dao.upsertProfile(nextProfile)
            if (_uiState.value.isSignedIn) {
                firestoreSync.syncTripCreate(entity)
                firestoreSync.syncProfile(nextProfile)
            }
        }
        recordOwnerCommissionSplit(
            orderId = r.tripId,
            sourceLabel = "${r.tariffName} (Пассажир)",
            city = r.city,
            grossSom = r.totalPriceSom
        )
    }

    /** Compliments chosen in the rating dialog, sent with the next review. */
    var pendingDriverTags: List<String> = emptyList()

    fun submitPostTripReview(trip: LocalTripEntity, rating: Int, tipSom: Int, lostItemNote: String) {
        val updated = trip.copy(rating = rating, tipSom = tipSom, lostItemNote = lostItemNote)
        _uiState.update {
            it.copy(
                postTripReceipt = null,
                statusBanner = if (lostItemNote.isNotBlank()) "Заявка «Забытые вещи» отправлена водителю ${trip.driverName}" else "Спасибо за оценку ($rating★)!"
            )
        }
        viewModelScope.launch {
            dao.upsertTrip(updated)
            if (_uiState.value.isSignedIn) {
                firestoreSync.syncTripPostUpdate(updated.id, rating, tipSom, lostItemNote)
                if (FeatureFlags.REAL_RIDES && rating in 1..5) rides.rateDriver(updated.id, rating, tipSom, pendingDriverTags)
                pendingDriverTags = emptyList()
            }
        }
    }

    fun dismissPostTripReceipt() = _uiState.update { it.copy(postTripReceipt = null) }

    // --- PROFILE, WALLET, FAMILY, BUSINESS & SAVED PLACES ---

    fun updateProfile(transform: (LocalProfileEntity) -> LocalProfileEntity) {
        val updated = transform(_uiState.value.profile)
        _uiState.update {
            it.copy(
                profile = updated,
                language = AppLanguage.fromCode(updated.language),
                darkTheme = updated.darkTheme,
                soundEnabled = updated.soundEnabled
            )
        }
        viewModelScope.launch {
            dao.upsertProfile(updated)
            if (_uiState.value.isSignedIn) {
                firestoreSync.syncProfile(updated)
            }
        }
    }

    fun addSavedPlace(label: String, address: String, category: String = "FAVORITE") {
        val city = _uiState.value.selectedCity
        val entity = LocalSavedPlaceEntity(
            id = "place_${System.currentTimeMillis()}",
            label = label.trim().ifBlank { "Избранное" },
            address = address.trim().ifBlank { _uiState.value.dropoffAddress },
            city = city.name,
            lat = _uiState.value.dropoffPoint.lat,
            lng = _uiState.value.dropoffPoint.lng,
            category = category,
            rating = 4.9
        )
        viewModelScope.launch {
            dao.upsertSavedPlace(entity)
            if (_uiState.value.isSignedIn) firestoreSync.syncSavedPlace(entity)
        }
    }

    fun deleteSavedPlace(id: String) {
        viewModelScope.launch {
            dao.deleteSavedPlace(id)
            if (_uiState.value.isSignedIn) firestoreSync.deleteSavedPlace(id)
        }
    }

    fun addFamilyMember(name: String, relation: String, phone: String, monthlyLimitSom: Int) {
        val member = LocalFamilyMemberEntity(
            id = "fam_${System.currentTimeMillis()}",
            name = name.trim().ifBlank { "Член семьи" },
            relation = relation.trim().ifBlank { "Teen" },
            phone = phone.trim(),
            monthlyLimitSom = monthlyLimitSom.coerceIn(1000, 500000),
            teenControlEnabled = true
        )
        viewModelScope.launch {
            dao.upsertFamilyMember(member)
            if (_uiState.value.isSignedIn) firestoreSync.syncFamilyMember(member)
        }
    }

    fun removeFamilyMember(id: String) {
        viewModelScope.launch {
            dao.deleteFamilyMember(id)
            if (_uiState.value.isSignedIn) firestoreSync.deleteFamilyMember(id)
        }
    }

    // --- DRIVER CABIN, FUEL CALCULATOR & MAINTENANCE ---

    fun toggleDriverOnline() {
        val goingOnline = !_uiState.value.driverOnline
        if (goingOnline) detectCityByGps()
        if (goingOnline) {
            val rest = DriverWorkPrefs.restUntil(appContext)
            if (rest != null) {
                val t = java.text.SimpleDateFormat("HH:mm", java.util.Locale.US).format(java.util.Date(rest))
                _uiState.update { it.copy(driverRestUntil = rest, statusBanner = "Вы были на линии 12 часов. Отдых до $t.") }
                return
            }
            if (FeatureFlags.REAL_RIDES && photoCheckOverdue()) {
                _uiState.update { it.copy(statusBanner = "Пройдите фотоконтроль во вкладке «Авто» — раз в 30 дней, это 2 минуты.") }
                return
            }
            val limit = _uiState.value.appConfig.debtLimitSom
            val debt = _uiState.value.driverBalance.debtSom
            if (FeatureFlags.REAL_RIDES && limit > 0 && debt > limit) {
                _uiState.update { it.copy(statusBanner = "Долг сервису $debt сом. Оплатите его, чтобы выйти на линию.") }
                return
            }
            DriverWorkPrefs.startOnline(appContext)
        } else {
            DriverWorkPrefs.stopOnline(appContext)
            driverOnlineTickJob?.cancel()
            _uiState.update {
                it.copy(
                    driverOnlineMinutes24h = DriverWorkPrefs.onlineMinutes24h(appContext),
                    driverRestUntil = DriverWorkPrefs.restUntil(appContext),
                    driverHomeModeOn = false,
                    driverLastOrder = false
                )
            }
        }
        _uiState.update {
            val next = !it.driverOnline
            it.copy(
                driverOnline = next,
                statusBanner = if (next) "Вы на линии. Ждём заказы в городе ${it.selectedCity.name}." else "Вы ушли с линии."
            )
        }
        // Keep receiving orders with the screen off while on the line.
        if (_uiState.value.driverOnline) startDriverOnlineTicker()
        if (_uiState.value.driverOnline) com.example.util.DriverOnlineService.start(appContext)
        else com.example.util.DriverOnlineService.stop(appContext)
        if (FeatureFlags.REAL_RIDES) {
            generateDriverIncomingOrders(_uiState.value.selectedCity)
            syncPushToken()
            return
        }
        if (_uiState.value.driverOnline) {
            generateDriverIncomingOrders(_uiState.value.selectedCity)
        }
    }

    /** Counts time on the line; after 12 hours (and no trip in progress) the driver leaves the line. */
    private fun startDriverOnlineTicker() {
        driverOnlineTickJob?.cancel()
        driverOnlineTickJob = viewModelScope.launch {
            while (_uiState.value.driverOnline) {
                val min = DriverWorkPrefs.onlineMinutes24h(appContext)
                _uiState.update { it.copy(driverOnlineMinutes24h = min) }
                if (min >= DriverWorkPrefs.MAX_ONLINE_MIN && _uiState.value.driverAcceptedOrder == null) {
                    toggleDriverOnline()
                    _uiState.update { it.copy(statusBanner = "12 часов на линии. Отдохните 6 часов — это для вашей безопасности.") }
                    SoundAndNotificationManager.postNotification(appContext, "Время отдохнуть", "Вы были на линии 12 часов. Выйти снова можно через 6 часов.")
                    break
                }
                if (min == DriverWorkPrefs.MAX_ONLINE_MIN - 30) {
                    SoundAndNotificationManager.postNotification(appContext, "Осталось 30 минут", "Скоро 12 часов на линии. Потом нужен отдых 6 часов.")
                }
                delay(60_000)
            }
        }
    }

    // --- REAL RIDES (Firestore) — dispatcher board ---

    private fun startDispatcherFeed() {
        dispatcherFeedJob?.cancel()
        dispatcherFeedJob = viewModelScope.launch {
            rides.observeActiveRides().collect { active ->
                val myPoint = _uiState.value.pickupPoint
                val board = active.map { r ->
                    r.toDriverOrder(myPoint).copy(
                        assignedDriverName = r.driverName,
                        tariffName = when (r.status) {
                            RideStatus.SEARCHING -> r.scheduledAtMillis?.let { "Предзаказ ${formatDeparture(it)}" } ?: "Ищет водителя"
                            RideStatus.BOOKED -> "Предзаказ принят ${r.scheduledAtMillis?.let { formatDeparture(it) }.orEmpty()}"
                            RideStatus.ASSIGNED -> "Водитель едет"
                            RideStatus.ARRIVED -> "Водитель на месте"
                            else -> "В поездке"
                        }
                    )
                }
                _uiState.update { it.copy(dispatcherActiveOrders = board) }
            }
        }
    }

    private fun stopDispatcherFeed() {
        dispatcherFeedJob?.cancel()
        dispatcherFeedJob = null
    }

    // --- REAL RIDES (Firestore) — driver side ---

    private fun startDriverOrderFeed(city: KgCity) {
        driverOpenRidesJob?.cancel()
        var knownIds = emptySet<String>()
        driverOpenRidesJob = viewModelScope.launch {
            startDriverPreorders(city.name)
            rides.observeOpenRides(city.name).collect { all ->
                val myTariffs = _uiState.value.driverEnabledTariffs
                val open = all.filterNot { it.isFuturePreorder }
                    .filter { r -> r.serviceType == "INTERCITY" || r.tariffId == null || r.tariffId in myTariffs }
                val myPoint = _uiState.value.pickupPoint
                val st = _uiState.value
                val home = st.driverHomePoint
                val orders = open.map { it.toDriverOrder(myPoint) }
                    .filter { o -> st.driverPickupRadiusKm <= 0 || o.serviceType == ServiceType.INTERCITY || o.pickupDistanceKm <= st.driverPickupRadiusKm }
                    .filter { o -> st.driverMinPassengerRating <= 0.0 || o.passengerTrips <= 0 || o.passengerRating <= 0.0 || o.passengerRating >= st.driverMinPassengerRating }
                    .filter { o -> st.driverMinTripKm <= 0 || o.serviceType == ServiceType.INTERCITY || o.serviceType == ServiceType.FOOD || o.distanceKm >= st.driverMinTripKm }
                    .filter { o -> o.serviceType != ServiceType.FOOD || st.isCourier || st.driverTakesDelivery }
                    // A courier without a car (only the "Курьер" tariff) sees only deliveries.
                    .filter { o -> st.driverAllowedTariffs != setOf("courier") || o.serviceType == ServiceType.FOOD }
                    .filter { o ->
                        // "Подождать": cheaper order, shown to drivers 3 minutes later.
                        val r = open.firstOrNull { it.id == o.id }
                        r == null || r.comment?.contains(WAIT_SAVE_MARK) != true || r.createdAtMillis <= 0L ||
                            System.currentTimeMillis() - r.createdAtMillis > 3 * 60_000L
                    }
                    .sortedByDescending { o -> open.firstOrNull { it.id == o.id }?.comment?.contains(PRIORITY_MARK) == true }
                    .filter { o ->
                        !st.driverHomeModeOn || home == null ||
                            KgGeoCatalog.haversineKm(o.dropoffPoint, home) < KgGeoCatalog.haversineKm(myPoint, home) - 1.0
                    }
                val fresh = orders.map { it.id }.toSet() - knownIds
                knownIds = orders.map { it.id }.toSet()
                _uiState.update { it.copy(driverIncomingOrders = orders.filterNot { o -> o.id == queuedRideId }, driverRadarOrders = orders.filterNot { o -> o.id == queuedRideId }) }
                if (fresh.isNotEmpty() && _uiState.value.driverAcceptedOrder == null) {
                    SoundAndNotificationManager.playCue(SoundCue.DRIVER_ASSIGNED, _uiState.value.soundEnabled)
                    SoundAndNotificationManager.vibrateNewOrder(appContext)
                    val first = orders.firstOrNull { it.id in fresh }
                    if (_uiState.value.driverVoiceAnnounce && first != null) {
                        VoiceAnnouncer.speak(appContext, "Новый заказ. Подача ${first.pickupEtaMin} минут. ${first.grossFareSom} сом.")
                    }
                    SoundAndNotificationManager.postNotification(
                        appContext,
                        "Новый заказ • ${orders.firstOrNull()?.grossFareSom ?: 0} сом",
                        orders.firstOrNull()?.let { "${it.pickupAddress} → ${it.dropoffAddress}" } ?: ""
                    )
                }
            }
        }
    }

    private fun stopDriverOrderFeed() {
        driverOpenRidesJob?.cancel()
        driverOpenRidesJob = null
    }

    private fun SharedRide.toDriverOrder(driverPoint: GeoPoint): DriverIncomingOrder {
        val pickup = GeoPoint(pickupLat, pickupLng)
        val dropoff = GeoPoint(dropoffLat, dropoffLng)
        val tripKm = KgGeoCatalog.haversineKm(pickup, dropoff)
        val toPickupKm = KgGeoCatalog.haversineKm(driverPoint, pickup)
        val split = OwnerPayoutGatewayClient.calculateOrderSplit(
            grossSom = priceSom,
            commissionPercent = _uiState.value.ownerBankConfig.commissionPercent
        )
        val pin = comment?.substringAfter("PIN ", "")?.take(4).orEmpty()
        return DriverIncomingOrder(
            id = id,
            passengerName = passengerName,
            passengerRating = passengerRatingAvg ?: 0.0,
            passengerTrips = passengerTrips ?: 0,
            serviceType = when (serviceType) { "INTERCITY" -> ServiceType.INTERCITY; "FOOD" -> ServiceType.FOOD; else -> ServiceType.TAXI },
            tariffName = tariffName ?: when (serviceType) { "INTERCITY" -> "Межгород"; "FOOD" -> "Доставка"; else -> "Такси" },
            pickupAddress = pickupAddress,
            dropoffAddress = dropoffAddress,
            distanceKm = (tripKm * 10).roundToInt() / 10.0,
            durationMin = max(3, (tripKm / 0.45).roundToInt()),
            grossFareSom = priceSom,
            upfrontNetEarningsSom = split.driverNetSom,
            city = city,
            pickupPoint = pickup,
            dropoffPoint = dropoff,
            pickupEtaMin = max(1, (toPickupKm / 0.4).roundToInt()),
            pickupDistanceKm = (toPickupKm * 10).roundToInt() / 10.0,
            paymentMethodLabel = if (paymentMethod == "CARD") "Оплата по карте" else "Наличные или QR",
            passengerPin = pin.ifBlank { "—" },
            passengerPhone = passengerPhone,
            meetAndGreetSign = comment?.substringBefore(" • PIN")?.takeIf { it.isNotBlank() && !it.startsWith("PIN") },
            passengerWishes = comment?.split(" • ")?.filter {
                it.startsWith("Пожелания:") || it.startsWith("Нужна помощь:") || it.startsWith("Остановки:") ||
                    it.startsWith("Купить") || it.startsWith("Где купить:") || it.startsWith("Покупки") ||
                    it.startsWith(PRIORITY_MARK) || it.startsWith(WAIT_SAVE_MARK)
            }
                ?.joinToString("\n")?.takeIf { it.isNotBlank() }
        )
    }

    /** Sends the driver's GPS position to the ride every 10 seconds while the trip is active. */
    private fun startDriverLocationSharing(rideId: String) {
        driverLocationJob?.cancel()
        driverLocationJob = viewModelScope.launch {
            while (driverRideId == rideId) {
                val gps = withTimeoutOrNull(8_000) {
                    runCatching { LocationAndVoiceHelper.fetchCurrentLocation(appContext, _uiState.value.selectedCity) }.getOrNull()
                }
                if (gps != null) rides.updateDriverLocation(rideId, gps.point.lat, gps.point.lng)
                delay(10_000)
            }
        }
    }

    /** Tells the driver when the passenger cancels the accepted order. */
    private fun watchDriverRide(rideId: String) {
        driverRideWatchJob?.cancel()
        driverRideWatchJob = viewModelScope.launch {
            rides.observeRide(rideId).collect { shared ->
                val req = shared?.routeRequest?.takeIf { it.status == "pending" }
                if (req != null && _uiState.value.driverRouteRequest != req) {
                    SoundAndNotificationManager.vibrateNewOrder(appContext)
                    SoundAndNotificationManager.postNotification(appContext, "Пассажир хочет изменить маршрут", "${req.dropoffAddress} • ${req.priceSom} сом")
                    if (_uiState.value.driverVoiceAnnounce) VoiceAnnouncer.speak(appContext, "Пассажир хочет изменить маршрут")
                }
                _uiState.update { it.copy(driverRouteRequest = req) }
                if (shared?.status == RideStatus.CANCELLED && driverRideId == rideId) {
                    finishDriverRealRide()
                    applyDriverCancel()
                    SoundAndNotificationManager.postNotification(appContext, "Пассажир отменил заказ", shared.pickupAddress)
                    _uiState.update { it.copy(statusBanner = "Пассажир отменил заказ. Вы снова на линии.") }
                }
            }
        }
    }

    private fun finishDriverRealRide() {
        driverChatJob?.cancel()
        driverChatJob = null
        driverRideWatchJob?.cancel()
        driverRideWatchJob = null
        driverRideId = null
        driverLocationJob?.cancel()
        driverLocationJob = null
    }

    /** Pushes the driver's stage change to Firestore. Returns false if it failed (stage must not move). */
    private suspend fun pushDriverStatus(newStatus: String, cancelReason: String? = null): Boolean {
        val id = driverRideId ?: return true
        val res = rides.setStatusAsDriver(id, newStatus, cancelReason)
        if (res.isFailure) {
            _uiState.update { it.copy(statusBanner = "Нет связи с сервером. Попробуйте ещё раз.") }
        }
        return res.isSuccess
    }

    fun acceptDriverOrder(order: DriverIncomingOrder) {
        if (!FeatureFlags.REAL_RIDES) {
            applyAcceptedDriverOrder(order)
            return
        }
        if (driverRideId != null) return
        viewModelScope.launch {
            val res = rides.acceptAsDriver(
                rideId = order.id,
                driverName = _uiState.value.profile.displayName,
                driverCar = verifiedDriverCar,
                driverPlate = verifiedDriverPlate,
                driverPayPhone = verifiedDriverPayPhone,
                driverRatingAvg = _uiState.value.driverRatingAvg,
                driverTrips = _uiState.value.driverHistory.count { it.status == RideStatus.COMPLETED },
                driverDeaf = _uiState.value.driverDeafMode
            )
            res.onSuccess {
                DriverWorkPrefs.countOffer(appContext, accepted = true)
                _uiState.update { it.copy(driverAcceptancePercent = DriverWorkPrefs.acceptanceRate(appContext)) }
                driverRideId = order.id
                applyAcceptedDriverOrder(order)
                startDriverLocationSharing(order.id)
                watchDriverRide(order.id)
                startDriverChat(order.id)
            }.onFailure { e ->
                _uiState.update {
                    it.copy(
                        driverIncomingOrders = it.driverIncomingOrders.filterNot { o -> o.id == order.id },
                        statusBanner = e.message ?: "Не удалось принять заказ"
                    )
                }
            }
        }
    }

    private fun applyAcceptedDriverOrder(order: DriverIncomingOrder) {
        driverWaitingTimerJob?.cancel()
        SoundAndNotificationManager.playCue(SoundCue.DRIVER_ASSIGNED, _uiState.value.soundEnabled)
        val effectiveOrder = if (_uiState.value.driverShiftPass != DriverShiftPass.NONE) {
            order.copy(upfrontNetEarningsSom = order.grossFareSom)
        } else order
        val initPassengerMsg = DriverChatMessage(
            id = "drv_chat_init_${order.id}",
            fromPassenger = true,
            originalText = "Здравствуйте! Жду вас у точки «${order.pickupAddress.substringBefore(",")}»."
        )
        _uiState.update {
            it.copy(
                driverCoffeeBreakActive = false,
                driverAcceptedOrder = effectiveOrder,
                driverOrderStage = DriverOrderStage.EN_ROUTE_TO_PICKUP,
                driverOrderWaitingSeconds = 0,
                driverBackToBackOffer = null,
                driverActiveOrderMessages = if (FeatureFlags.REAL_RIDES) emptyList() else listOf(initPassengerMsg),
                driverActivityScore = (it.driverActivityScore + 2).coerceAtMost(100),
                driverIncomingOrders = it.driverIncomingOrders.filterNot { o -> o.id == order.id },
                statusBanner = "Заказ принят! +2 Активность • Подача ${order.pickupEtaMin} мин"
            )
        }
    }

    fun skipDriverOrder(order: DriverIncomingOrder) {
        DriverWorkPrefs.countOffer(appContext, accepted = false)
        _uiState.update { it.copy(driverAcceptancePercent = DriverWorkPrefs.acceptanceRate(appContext)) }
        _uiState.update {
            val remaining = it.driverIncomingOrders.filterNot { o -> o.id == order.id }
            it.copy(
                driverActivityScore = (it.driverActivityScore - 4).coerceAtLeast(0),
                driverIncomingOrders = remaining,
                statusBanner = "Заказ пропущен"
            )
        }
        if (_uiState.value.driverIncomingOrders.isEmpty()) {
            generateDriverIncomingOrders(_uiState.value.selectedCity)
        }
    }

    fun advanceDriverOrderStage() {
        _uiState.value.driverAcceptedOrder ?: return
        val stage = _uiState.value.driverOrderStage
        if (driverRideId == null || stage == DriverOrderStage.IN_TRIP) {
            applyDriverStageAdvance()
            return
        }
        val next = if (stage == DriverOrderStage.EN_ROUTE_TO_PICKUP) RideStatus.ARRIVED else RideStatus.IN_PROGRESS
        viewModelScope.launch {
            if (pushDriverStatus(next)) applyDriverStageAdvance()
        }
    }

    private fun applyDriverStageAdvance() {
        val currentOrder = _uiState.value.driverAcceptedOrder ?: return
        when (_uiState.value.driverOrderStage) {
            DriverOrderStage.EN_ROUTE_TO_PICKUP -> {
                SoundAndNotificationManager.playCue(SoundCue.DRIVER_ARRIVED, _uiState.value.soundEnabled)
                _uiState.update {
                    it.copy(
                        driverOrderStage = DriverOrderStage.WAITING_AT_PICKUP,
                        driverOrderWaitingSeconds = 0,
                        statusBanner = "Вы на месте! Бесплатное ожидание 3:00 запущено"
                    )
                }
                startDriverWaitingTimer()
            }
            DriverOrderStage.WAITING_AT_PICKUP -> {
                driverWaitingTimerJob?.cancel()
                val city = _uiState.value.selectedCity
                // Propose a realistic Back-to-Back ("По цепочке") next order near dropoff!
                val chainedNext = DriverIncomingOrder(
                    id = "chain_${System.currentTimeMillis()}",
                    passengerName = "Нурбек К.",
                    passengerRating = 4.95,
                    serviceType = ServiceType.TAXI,
                    tariffName = "Комфорт",
                    pickupAddress = "Рядом с ${currentOrder.dropoffAddress.substringBefore(",")}",
                    dropoffAddress = city.landmarks.lastOrNull()?.name ?: "Аэропорт Манас (FRU)",
                    distanceKm = 7.6,
                    durationMin = 15,
                    grossFareSom = 360,
                    upfrontNetEarningsSom = OwnerPayoutGatewayClient.calculateOrderSplit(
                        grossSom = 360,
                        commissionPercent = _uiState.value.ownerBankConfig.commissionPercent,
                        commissionFreeShiftActive = _uiState.value.driverShiftPass != DriverShiftPass.NONE
                    ).driverNetSom,
                    surgeLabel = "x1.3",
                    city = city.name,
                    pickupPoint = currentOrder.dropoffPoint,
                    dropoffPoint = city.landmarks.lastOrNull()?.point ?: city.center,
                    pickupEtaMin = 2,
                    pickupDistanceKm = 0.6,
                    paymentMethodLabel = "MBank",
                    passengerPin = "6194"
                )
                _uiState.update {
                    it.copy(
                        driverOrderStage = DriverOrderStage.IN_TRIP,
                        // Chained orders are made up, so they are offered only in demo mode.
                        driverBackToBackOffer = if (!FeatureFlags.REAL_RIDES && it.driverQueuedNextOrder == null) chainedNext else null,
                        statusBanner = if (FeatureFlags.REAL_RIDES) "Поездка началась. Хорошей дороги!"
                        else "Поездка началась! Доступен заказ по цепочке рядом с высадкой"
                    )
                }
            }
            DriverOrderStage.IN_TRIP -> {
                completeDriverOrder()
            }
        }
    }

    private fun startDriverWaitingTimer() {
        driverWaitingTimerJob?.cancel()
        driverWaitingTimerJob = viewModelScope.launch {
            while (_uiState.value.driverOrderStage == DriverOrderStage.WAITING_AT_PICKUP &&
                _uiState.value.driverAcceptedOrder != null
            ) {
                delay(1000)
                _uiState.update { it.copy(driverOrderWaitingSeconds = it.driverOrderWaitingSeconds + 1) }
            }
        }
    }

    fun cancelDriverAcceptedOrder() = cancelDriverAcceptedOrderWithReason(null)

    fun cancelDriverAcceptedOrderWithReason(reason: String?) {
        if (driverRideId != null) {
            viewModelScope.launch {
                if (pushDriverStatus(RideStatus.CANCELLED, reason)) {
                    finishDriverRealRide()
                    applyDriverCancel()
                }
            }
            return
        }
        applyDriverCancel()
    }

    private fun applyDriverCancel() {
        driverWaitingTimerJob?.cancel()
        val queued = _uiState.value.driverQueuedNextOrder
        _uiState.update {
            it.copy(
                driverAcceptedOrder = queued,
                driverQueuedNextOrder = null,
                driverBackToBackOffer = null,
                driverOrderStage = DriverOrderStage.EN_ROUTE_TO_PICKUP,
                driverOrderWaitingSeconds = 0,
                driverActivityScore = (it.driverActivityScore - 8).coerceAtLeast(0),
                statusBanner = if (queued != null) "Заказ отменён. Едем к следующему пассажиру." else "Заказ отменён"
            )
        }
        if (queued != null) promoteQueuedRide()
        generateDriverIncomingOrders(_uiState.value.selectedCity)
    }

    fun completeDriverOrder() {
        if (driverRideId != null) {
            viewModelScope.launch {
                if (pushDriverStatus(RideStatus.COMPLETED)) {
                    finishDriverRealRide()
                    applyDriverComplete()
                    promoteQueuedRide()
                }
            }
            return
        }
        applyDriverComplete()
    }

    private fun applyDriverComplete() {
        val order = _uiState.value.driverAcceptedOrder ?: return
        val queuedNext = _uiState.value.driverQueuedNextOrder
        driverWaitingTimerJob?.cancel()
        SoundAndNotificationManager.playCue(SoundCue.TRIP_COMPLETED, _uiState.value.soundEnabled)
        val extraWaitMin = ((_uiState.value.driverOrderWaitingSeconds - 180).coerceAtLeast(0) + 59) / 60
        val waitBonus = extraWaitMin * 8
        val newDone = _uiState.value.driverQuestTripsDone + 1
        val questBonus = if (newDone == _uiState.value.driverQuestTarget) 800 else 0
        val shiftFree = _uiState.value.driverShiftPass != DriverShiftPass.NONE
        val split = OwnerPayoutGatewayClient.calculateOrderSplit(
            grossSom = order.grossFareSom,
            commissionPercent = _uiState.value.ownerBankConfig.commissionPercent,
            commissionFreeShiftActive = shiftFree
        )
        val baseNet = split.driverNetSom
        val earned = baseNet + waitBonus + questBonus
        _uiState.update {
            val updatedWeek = it.driverWeeklyEarnings.mapIndexed { idx, pair ->
                if (idx == it.driverWeeklyEarnings.lastIndex) pair.first to (pair.second + earned) else pair
            }
            it.copy(
                driverAcceptedOrder = queuedNext,
                driverQueuedNextOrder = null,
                driverBackToBackOffer = null,
                driverOrderStage = DriverOrderStage.EN_ROUTE_TO_PICKUP,
                driverOrderWaitingSeconds = 0,
                driverDrivingMinutesToday = (it.driverDrivingMinutesToday + order.durationMin).coerceAtMost(12 * 60),
                driverPendingPassengerRating = order.copy(upfrontNetEarningsSom = earned),
                driverTodayEarningsSom = it.driverTodayEarningsSom + earned,
                driverWeeklyEarnings = updatedWeek,
                driverQuestTripsDone = newDone,
                statusBanner = when {
                    queuedNext != null -> "Поездка завершена (+$earned с)! Переход к заказу по цепочке"
                    questBonus > 0 -> "Квест выполнен! +$earned сом!"
                    else -> "Поездка завершена! +$earned сом на баланс"
                }
            )
        }
        recordOwnerCommissionSplit(
            orderId = order.id,
            sourceLabel = "${order.tariffName} (Водитель)",
            city = order.city,
            grossSom = order.grossFareSom,
            commissionFreeShift = shiftFree
        )
        if (queuedNext == null) {
            generateDriverIncomingOrders(_uiState.value.selectedCity)
        }
        if (_uiState.value.driverLastOrder && _uiState.value.driverOnline && queuedNext == null) {
            toggleDriverOnline()
            _uiState.update { it.copy(statusBanner = "Последний заказ выполнен. Вы ушли с линии.") }
        }
    }

    fun acceptBackToBackOrder() {
        val offer = _uiState.value.driverBackToBackOffer ?: return
        _uiState.update {
            it.copy(
                driverQueuedNextOrder = offer,
                driverBackToBackOffer = null,
                driverActivityScore = (it.driverActivityScore + 2).coerceAtMost(100),
                statusBanner = "Заказ по цепочке (+${offer.upfrontNetEarningsSom} с) принят! Начнётся после высадки"
            )
        }
    }

    fun declineBackToBackOrder() {
        _uiState.update {
            it.copy(
                driverBackToBackOffer = null,
                statusBanner = "Заказ по цепочке пропущен"
            )
        }
    }

    fun purchaseDriverShiftPass(pass: DriverShiftPass) {
        if (!FeatureFlags.DRIVER_SHIFT_PASSES) {
            showComingSoon("Абонементы для водителей")
            return
        }
        val prevPass = _uiState.value.driverShiftPass
        if (pass == prevPass) return
        val pct = _uiState.value.ownerBankConfig.commissionPercent
        _uiState.update { state ->
            val cost = if (pass != DriverShiftPass.NONE) pass.costSom else 0
            val nextBalance = (state.driverTodayEarningsSom - cost).coerceAtLeast(0)
            state.copy(
                driverShiftPass = pass,
                driverTodayEarningsSom = nextBalance,
                statusBanner = if (pass == DriverShiftPass.NONE) {
                    "Возвращена стандартная комиссия $pct% с заказа"
                } else {
                    "Куплена ${pass.title} (-${pass.costSom} с) • Комиссия сервиса 0%!"
                }
            )
        }
        if (pass != DriverShiftPass.NONE && pass.costSom > 0) {
            recordOwnerCommissionSplit(
                orderId = "shift_${System.currentTimeMillis()}",
                sourceLabel = "Смена: ${pass.title} (100% владельцу)",
                city = _uiState.value.selectedCity.name,
                grossSom = pass.costSom,
                forceFullOwnerShare = true
            )
        }
    }

    fun toggleDriverCoffeeBreak() {
        if (!FeatureFlags.DEMO_EXTRAS) return
        _uiState.update {
            val next = !it.driverCoffeeBreakActive
            it.copy(
                driverCoffeeBreakActive = next,
                statusBanner = if (next) "Пауза «Кофе-брейк»: заказы приостановлены, линия сохранена" else "Пауза завершена — вы снова принимаете заказы!"
            )
        }
    }

    fun sendDriverSideChatMessage(text: String) {
        val clean = text.trim()
        if (clean.isBlank()) return
        val realId = driverRideId
        if (FeatureFlags.REAL_RIDES && realId != null) {
            viewModelScope.launch {
                if (rides.sendMessage(realId, clean).isFailure) {
                    _uiState.update { it.copy(statusBanner = "Сообщение не отправлено: нет связи.") }
                }
            }
            return
        }
        val drvMsg = DriverChatMessage(
            id = "dc_drv_${System.currentTimeMillis()}",
            fromPassenger = false,
            originalText = clean
        )
        _uiState.update { it.copy(driverActiveOrderMessages = it.driverActiveOrderMessages + drvMsg) }
        viewModelScope.launch {
            delay(1000)
            val reply = when {
                clean.contains("шлагбаум", true) -> "Спасибо, уже иду к шлагбауму!"
                clean.contains("на месте", true) || clean.contains("күтүп", true) -> "Выхожу через 1 минуту."
                else -> "Хорошо, понял вас, спасибо!"
            }
            val passMsg = DriverChatMessage(
                id = "dc_pas_${System.currentTimeMillis()}",
                fromPassenger = true,
                originalText = reply
            )
            _uiState.update { it.copy(driverActiveOrderMessages = it.driverActiveOrderMessages + passMsg) }
        }
    }

    fun translateDriverSideChat() {
        val target = _uiState.value.chatTranslateTargetLang
        val offline = _uiState.value.offlineMode
        val msgs = _uiState.value.driverActiveOrderMessages
        if (msgs.isEmpty() || _uiState.value.chatTranslating) return
        _uiState.update { it.copy(chatTranslating = true) }
        viewModelScope.launch {
            val translated = msgs.map { m ->
                m.copy(translatedText = aiAssistant.translateChatMessage(m.originalText, target, offline))
            }
            _uiState.update {
                it.copy(
                    driverActiveOrderMessages = translated,
                    chatTranslating = false,
                    statusBanner = "Чат с пассажиром переведён на $target"
                )
            }
        }
    }

    fun toggleCarpoolMode() {
        if (!FeatureFlags.DEMO_EXTRAS) return
        _uiState.update {
            val next = !it.carpoolModeEnabled
            it.copy(
                carpoolModeEnabled = next,
                statusBanner = if (next) "Попутчик «Вместе» включён: экономия -30% по пути!" else "Режим «Вместе» выключен"
            )
        }
    }

    fun requestAiTourGuide(topic: String) {
        if (!FeatureFlags.AI_TOUR_GUIDE) {
            showComingSoon("ИИ-гид")
            return
        }
        _uiState.update { it.copy(tourGuideTopic = topic, tourGuideLoading = true) }
        viewModelScope.launch {
            val story = aiAssistant.generateTourGuideStory(
                topic = topic,
                languageCode = _uiState.value.language.code,
                offlineMode = _uiState.value.offlineMode
            )
            _uiState.update { it.copy(tourGuideText = story, tourGuideLoading = false) }
        }
    }

    fun trackFamilyMember(member: LocalFamilyMemberEntity) {
        _uiState.update {
            it.copy(
                statusBanner = "Чтобы видеть поездку ${member.name}, попросите нажать «Поделиться» во время поездки."
            )
        }
    }

    fun submitDriverPassengerRating(stars: Int) {
        val rated = _uiState.value.driverPendingPassengerRating
        if (FeatureFlags.REAL_RIDES && rated != null && stars in 1..5) {
            viewModelScope.launch { rides.ratePassenger(rated.id, stars) }
        }
        _uiState.update {
            it.copy(
                driverPendingPassengerRating = null,
                statusBanner = if (rated != null) "Оценка $stars★ для ${rated.passengerName} отправлена" else null
            )
        }
    }

    fun toggleDriverAutoAccept() {
        if (!FeatureFlags.DEMO_EXTRAS) return
        _uiState.update {
            val next = !it.driverAutoAccept
            it.copy(
                driverAutoAccept = next,
                statusBanner = if (next) "Автоприём заказов включён (+2 к приоритету)" else "Автоприём заказов выключен"
            )
        }
    }

    fun toggleDriverTariff(tariffId: String) {
        _uiState.update { state ->
            // Only tariffs the owner approved for this car can be switched on.
            if (state.driverAllowedTariffs.isNotEmpty() && tariffId !in state.driverAllowedTariffs) {
                return@update state.copy(statusBanner = "Ваше авто не подходит для этого тарифа")
            }
            val current = state.driverEnabledTariffs
            val next = if (tariffId in current && current.size > 1) current - tariffId else current + tariffId
            state.copy(driverEnabledTariffs = next)
        }
    }

    fun passDriverPhotoControl() {
        _uiState.update {
            it.copy(
                driverPhotoControlVerified = true,
                driverPriorityPoints = (it.driverPriorityPoints + 2).coerceAtMost(20),
                statusBanner = "Фотоконтроль авто пройден • +2 к приоритету Яндекс/KG Pro"
            )
        }
    }

    fun updateFuelInputs(distance: String, consumption: String, price: String) {
        _uiState.update {
            it.copy(fuelDistanceKm = distance, fuelConsumptionPer100 = consumption, fuelPriceSom = price)
        }
    }

    fun addMaintenanceLog(vehicle: String, service: String, dueKm: Int, currentKm: Int, liters: Double) {
        val item = LocalMaintenanceEntity(
            id = "maint_${System.currentTimeMillis()}",
            vehicleName = vehicle.ifBlank { "Моё авто" },
            serviceTitle = service.ifBlank { "Плановое ТО" },
            dueMileageKm = dueKm.coerceAtLeast(1000),
            currentMileageKm = currentKm.coerceAtLeast(0),
            fuelLitersPer100Km = liters.coerceIn(2.0, 35.0),
            completed = false
        )
        if (dueKm - currentKm <= 1500) {
            SoundAndNotificationManager.postNotification(
                appContext,
                "Напоминание ТО: ${item.vehicleName}",
                "${item.serviceTitle} через ${max(0, dueKm - currentKm)} км!",
                isHighPriority = false
            )
        }
        viewModelScope.launch {
            dao.upsertMaintenance(item)
            if (_uiState.value.isSignedIn) firestoreSync.syncMaintenance(item)
        }
    }

    fun toggleMaintenanceComplete(item: LocalMaintenanceEntity) {
        val updated = item.copy(completed = !item.completed)
        viewModelScope.launch {
            dao.upsertMaintenance(updated)
            if (_uiState.value.isSignedIn) firestoreSync.syncMaintenance(updated)
        }
    }

    // --- VOICE COMMANDS ---

    fun handleVoiceCommand(spokenText: String) {
        val clean = spokenText.trim()
        if (clean.isBlank()) return
        val lower = clean.lowercase(Locale.getDefault())
        if (_uiState.value.role == AppRole.DRIVER) {
            when {
                lower.contains("принять") || lower.contains("accept") -> {
                    _uiState.value.driverIncomingOrders.firstOrNull()?.let { acceptDriverOrder(it) }
                }
                lower.contains("заверш") || lower.contains("complete") -> completeDriverOrder()
                lower.contains("лини") || lower.contains("online") -> toggleDriverOnline()
                else -> _uiState.update { it.copy(statusBanner = "Голосовая команда: «$clean»") }
            }
            return
        }
        // Passenger Voice-to-Ride AI Intent Parser (Gemini 3.8 Flash + offline fallback)
        _uiState.update { it.copy(voiceOrderParsing = true) }
        viewModelScope.launch {
            val s = _uiState.value
            val intent = aiAssistant.parseVoiceOrderCommand(clean, s.selectedCity.name, s.offlineMode)
            val allLandmarks = KgGeoCatalog.cities.flatMap { it.landmarks }

            val matchedPickup = intent.pickupQuery?.let { q ->
                allLandmarks.firstOrNull {
                    it.name.contains(q, ignoreCase = true) || q.contains(it.name.substringBefore(" "), ignoreCase = true)
                }
            }
            val matchedDropoff = intent.dropoffQuery?.let { q ->
                allLandmarks.firstOrNull {
                    it.name.contains(q, ignoreCase = true) || q.contains(it.name.substringBefore(" "), ignoreCase = true)
                }
            }

            // The city stays the one detected by GPS.

            _uiState.update { cur ->
                val nextPrefs = cur.preferences.copy(
                    quietRide = intent.quietRide ?: cur.preferences.quietRide,
                    climateAc = intent.climateAc ?: cur.preferences.climateAc,
                    luggageHelp = intent.luggageHelp ?: cur.preferences.luggageHelp,
                    petFriendly = intent.petFriendly ?: cur.preferences.petFriendly
                )
                cur.copy(
                    voiceOrderParsing = false,
                    lastVoiceOrderSummary = intent.summary,
                    serviceType = ServiceType.TAXI,
                    selectedTariffId = intent.tariffId ?: cur.selectedTariffId,
                    pickupPoint = matchedPickup?.point ?: cur.pickupPoint,
                    pickupAddress = matchedPickup?.let { "${it.name} • ${it.address}" } ?: intent.pickupQuery ?: cur.pickupAddress,
                    dropoffPoint = matchedDropoff?.point ?: cur.dropoffPoint,
                    dropoffAddress = matchedDropoff?.let { "${it.name} • ${it.address}" } ?: intent.dropoffQuery ?: cur.dropoffAddress,
                    preferences = nextPrefs,
                    statusBanner = intent.summary
                )
            }
            if (intent.businessTrip == true) {
                updateProfile { it.copy(businessProfileActive = true) }
            }
            refreshLiveRoadRoute()
        }
    }

    // --- GEMINI AI ROUTE PLANNER CHAT ---

    fun sendAiMessage(prompt: String) {
        val trimmed = prompt.trim()
        if (trimmed.isBlank() || _uiState.value.aiLoading) return

        val userMsg = AiChatMessage("u_${System.currentTimeMillis()}", true, trimmed)
        val botId = "a_${System.currentTimeMillis() + 1}"
        val botPlaceholder = AiChatMessage(botId, false, "", isStreaming = true)

        _uiState.update {
            it.copy(
                aiMessages = it.aiMessages + userMsg + botPlaceholder,
                aiLoading = true
            )
        }

        viewModelScope.launch {
            val city = _uiState.value.selectedCity.name
            val lang = _uiState.value.language.code
            aiAssistant.streamRouteAdvice(trimmed, city, lang).collect { event ->
                when (event) {
                    is AiStreamEvent.Chunk -> {
                        _uiState.update { state ->
                            state.copy(
                                aiMessages = state.aiMessages.map { m ->
                                    if (m.id == botId) m.copy(text = event.accumulatedText, isStreaming = true) else m
                                }
                            )
                        }
                    }
                    is AiStreamEvent.Complete -> {
                        val suggested = KgGeoCatalog.cities.flatMap { it.landmarks }
                            .firstOrNull { event.fullText.contains(it.name, ignoreCase = true) }?.name
                        _uiState.update { state ->
                            state.copy(
                                aiLoading = false,
                                aiMessages = state.aiMessages.map { m ->
                                    if (m.id == botId) m.copy(
                                        text = event.fullText,
                                        suggestedDestination = suggested,
                                        isStreaming = false
                                    ) else m
                                }
                            )
                        }
                    }
                    is AiStreamEvent.Error -> {
                        _uiState.update { state ->
                            state.copy(
                                aiLoading = false,
                                aiMessages = state.aiMessages.map { m ->
                                    if (m.id == botId) m.copy(text = "⚠️ ${event.message}", isStreaming = false) else m
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Broadcasts to all drivers/passengers need a server (FCM via Cloud Functions).
    // Until then these buttons say so instead of pretending something was sent.
    fun triggerPromoNotification() {
        _uiState.update { it.copy(statusBanner = "Рассылка акций появится после подключения сервера уведомлений.") }
    }

    fun triggerTrafficAlertNotification() {
        _uiState.update { it.copy(statusBanner = "Оповещения водителям появятся после подключения сервера уведомлений.") }
    }

    fun dispatchOrderToDriver(orderId: String) {
        if (FeatureFlags.REAL_RIDES) {
            _uiState.update { it.copy(statusBanner = "Заказ видят все водители на линии в этом городе. Ручное назначение появится позже.") }
            return
        }
        val pool = listOf("Нурлан Т. (Camry 70)", "Бакыт К. (BYD Han EV)", "Айпери К. (Kia K5)", "Эрмек С. (Sonata)")
        val chosen = pool[kotlin.math.abs(orderId.hashCode()) % pool.size]
        SoundAndNotificationManager.playCue(SoundCue.DRIVER_ASSIGNED, _uiState.value.soundEnabled)
        _uiState.update { state ->
            state.copy(
                dispatcherActiveOrders = state.dispatcherActiveOrders.map { o ->
                    if (o.id == orderId) o.copy(assignedDriverName = chosen) else o
                },
                statusBanner = "Диспетчер назначил водителя: $chosen"
            )
        }
    }

    private fun generateNearbyDrivers(city: KgCity) {
        if (FeatureFlags.REAL_RIDES) {
            // No made-up cars on the map. Real driver positions need a drivers-online collection (next step).
            _uiState.update { it.copy(nearbyDrivers = emptyList()) }
            return
        }
        val offsets = listOf(
            Pair(0.005, 0.007) to false,
            Pair(-0.006, 0.004) to true,
            Pair(0.008, -0.006) to false,
            Pair(-0.004, -0.009) to true,
            Pair(0.002, 0.011) to false
        )
        val markers = offsets.mapIndexed { idx, (off, isEv) ->
            NearbyDriverMarker(
                id = "drv_${city.name}_$idx",
                point = GeoPoint(city.center.lat + off.first, city.center.lng + off.second),
                headingDeg = (idx * 72f) % 360f,
                isEv = isEv
            )
        }
        _uiState.update { it.copy(nearbyDrivers = markers) }
    }

    private fun generateDriverIncomingOrders(city: KgCity) {
        if (FeatureFlags.REAL_RIDES) {
            // Real orders come from Firestore; no made-up orders.
            if (_uiState.value.driverOnline && _uiState.value.allowedRoles.contains(AppRole.DRIVER)) {
                startDriverOrderFeed(city)
            } else {
                stopDriverOrderFeed()
                _uiState.update { it.copy(driverIncomingOrders = emptyList(), dispatcherActiveOrders = emptyList()) }
            }
            return
        }
        val lm = city.landmarks
        val p1 = lm.getOrNull(0)?.name ?: "${city.streets.first()}, 12"
        val p2 = lm.getOrNull(1)?.name ?: "${city.streets.last()}, 44"
        val p3 = lm.getOrNull(2)?.name ?: "${city.streets.first()}, 90"
        val pt2 = lm.getOrNull(1)?.point ?: GeoPoint(city.center.lat + 0.012, city.center.lng + 0.014)
        val pt1 = lm.getOrNull(0)?.point ?: city.center
        val pt3 = lm.getOrNull(2)?.point ?: GeoPoint(city.center.lat - 0.009, city.center.lng + 0.011)
        val commPct = _uiState.value.ownerBankConfig.commissionPercent
        val shiftFree = _uiState.value.driverShiftPass != DriverShiftPass.NONE
        val net1 = OwnerPayoutGatewayClient.calculateOrderSplit(380, commPct, shiftFree).driverNetSom
        val net2 = OwnerPayoutGatewayClient.calculateOrderSplit(240, commPct, shiftFree).driverNetSom
        val net3 = OwnerPayoutGatewayClient.calculateOrderSplit(1250, commPct, shiftFree).driverNetSom
        val net4 = OwnerPayoutGatewayClient.calculateOrderSplit(690, commPct, shiftFree).driverNetSom
        val orders = listOf(
            DriverIncomingOrder(
                id = "ord_${System.currentTimeMillis()}_1",
                passengerName = "Алина М.",
                passengerRating = 4.94,
                serviceType = ServiceType.TAXI,
                tariffName = "Комфорт",
                pickupAddress = p1,
                dropoffAddress = p2,
                distanceKm = 8.4,
                durationMin = 17,
                grossFareSom = 380,
                upfrontNetEarningsSom = net1,
                surgeLabel = "x${city.surgeMultiplier}",
                city = city.name,
                pickupPoint = pt1,
                dropoffPoint = pt2,
                pickupEtaMin = 3,
                pickupDistanceKm = 1.1,
                paymentMethodLabel = "MBank",
                passengerPin = "4829"
            ),
            DriverIncomingOrder(
                id = "ord_${System.currentTimeMillis()}_2",
                passengerName = "Дастан Б.",
                passengerRating = 4.88,
                serviceType = ServiceType.COURIER,
                tariffName = "Курьер Экспресс",
                pickupAddress = p3,
                dropoffAddress = "мкр. Джал / $p1",
                distanceKm = 4.1,
                durationMin = 11,
                grossFareSom = 240,
                upfrontNetEarningsSom = net2,
                surgeLabel = null,
                city = city.name,
                pickupPoint = pt3,
                dropoffPoint = pt1,
                pickupEtaMin = 4,
                pickupDistanceKm = 1.6,
                paymentMethodLabel = "Наличные",
                passengerPin = "7310"
            ),
            DriverIncomingOrder(
                id = "ord_${System.currentTimeMillis()}_3",
                passengerName = "Dr. Michael Weber",
                passengerRating = 4.99,
                serviceType = ServiceType.TAXI,
                tariffName = "Бизнес • Meet & Greet",
                pickupAddress = "Аэропорт Манас (FRU) • Выход 1",
                dropoffAddress = "Центр / ЦУМ / Восток-5",
                distanceKm = 28.5,
                durationMin = 36,
                grossFareSom = 1250,
                upfrontNetEarningsSom = net3,
                surgeLabel = "VIP +x1.4",
                city = city.name,
                pickupPoint = GeoPoint(43.0613, 74.4776),
                dropoffPoint = pt2,
                meetAndGreetSign = "DR. MICHAEL WEBER • TK 344",
                pickupEtaMin = 6,
                pickupDistanceKm = 2.9,
                paymentMethodLabel = "Visa •••• 4821",
                passengerPin = "9041"
            )
        )
        val dispatcherList = orders + DriverIncomingOrder(
            id = "disp_${city.name}_3",
            passengerName = "Кайрат О. (B2B)",
            passengerRating = 4.97,
            serviceType = ServiceType.TAXI,
            tariffName = "Бизнес",
            pickupAddress = p2,
            dropoffAddress = p3,
            distanceKm = 11.2,
            durationMin = 21,
            grossFareSom = 690,
            upfrontNetEarningsSom = net4,
            surgeLabel = "x${city.surgeMultiplier}",
            city = city.name,
            dropoffPoint = pt2
        )
        _uiState.update { it.copy(driverIncomingOrders = orders, dispatcherActiveOrders = dispatcherList) }
    }

    // --- IN-TRIP CHAT + AI TRANSLATION, WALLET/QR, REFERRAL, DOCS, SOS, ONBOARDING, REPEAT ---

    fun setChatTranslateLang(langCode: String) {
        _uiState.update { it.copy(chatTranslateTargetLang = langCode) }
    }

    fun sendDriverChatMessage(text: String) {
        val clean = text.trim()
        if (clean.isBlank()) return
        val realId = passengerRideId
        if (FeatureFlags.REAL_RIDES) {
            if (realId == null) {
                _uiState.update { it.copy(statusBanner = "Чат откроется, когда водитель примет заказ.") }
                return
            }
            viewModelScope.launch {
                if (rides.sendMessage(realId, clean).isFailure) {
                    _uiState.update { it.copy(statusBanner = "Сообщение не отправлено: нет связи.") }
                }
            }
            return
        }
        val userMsg = DriverChatMessage(
            id = "cm_${System.currentTimeMillis()}",
            fromPassenger = true,
            originalText = clean
        )
        _uiState.update { it.copy(driverChatMessages = it.driverChatMessages + userMsg) }

        viewModelScope.launch {
            delay(1100)
            val reply = when {
                clean.contains("вход", true) -> "Хорошо, подъезжаю прямо ко входу, включил аварийку."
                clean.contains("шлагбаум", true) -> "Понял вас, жду у шлагбаума!"
                clean.contains("2 мин", true) || clean.contains("выхожу", true) -> "Не торопитесь, я на месте и жду вас."
                else -> "Принял, всё хорошо! Скоро будем на месте."
            }
            val drvMsg = DriverChatMessage(
                id = "cm_drv_${System.currentTimeMillis()}",
                fromPassenger = false,
                originalText = reply
            )
            _uiState.update { it.copy(driverChatMessages = it.driverChatMessages + drvMsg) }
        }
    }

    fun translateAllDriverChat() {
        val target = _uiState.value.chatTranslateTargetLang
        val offline = _uiState.value.offlineMode
        val msgs = _uiState.value.driverChatMessages
        if (msgs.isEmpty() || _uiState.value.chatTranslating) return

        _uiState.update { it.copy(chatTranslating = true) }
        viewModelScope.launch {
            val translated = msgs.map { m ->
                val tr = aiAssistant.translateChatMessage(m.originalText, target, offline)
                m.copy(translatedText = tr)
            }
            _uiState.update {
                it.copy(
                    driverChatMessages = translated,
                    chatTranslating = false,
                    statusBanner = "Сообщения переведены на $target"
                )
            }
        }
    }

    fun topUpWallet(amountSom: Int, method: String) {
        if (!FeatureFlags.WALLET) {
            _uiState.update { it.copy(statusBanner = "Кошелёк скоро появится. Сейчас оплата — наличными водителю.") }
            return
        }
        val next = _uiState.value.walletBalanceSom + amountSom
        val tx = WalletTransaction("wt_${System.currentTimeMillis()}", "Пополнение кошелька", method, amountSom, "Только что")
        _uiState.update {
            it.copy(
                walletBalanceSom = next,
                walletTransactions = listOf(tx) + it.walletTransactions,
                statusBanner = "Кошелёк пополнен на +$amountSom сом ($method)"
            )
        }
        viewModelScope.launch { prefsStore.setWalletBalance(next) }
    }

    fun markElqrPaymentDone(amountSom: Int, title: String) {
        if (!FeatureFlags.ONLINE_PAYMENTS) {
            _uiState.update { it.copy(statusBanner = "Оплата по QR пока недоступна. Оплата — наличными.") }
            return
        }
        val tx = WalletTransaction("qr_${System.currentTimeMillis()}", title, "ELQR • MBank", -amountSom, "Только что")
        _uiState.update {
            it.copy(
                walletTransactions = listOf(tx) + it.walletTransactions,
                statusBanner = "Оплата по ELQR ($amountSom сом) подтверждена!"
            )
        }
    }

    fun claimReferralBonus() {
        if (_uiState.value.referralClaimed) return
        if (!FeatureFlags.WALLET) {
            _uiState.update { it.copy(statusBanner = "Реферальная программа скоро появится.") }
            return
        }
        val nextWallet = _uiState.value.walletBalanceSom + 250
        val tx = WalletTransaction("ref_${System.currentTimeMillis()}", "Бонус «Пригласи друга»", "Реферал", 250, "Только что")
        _uiState.update {
            it.copy(
                referralClaimed = true,
                walletBalanceSom = nextWallet,
                walletTransactions = listOf(tx) + it.walletTransactions,
                statusBanner = "Начислено +250 сом и +250 бонусов по реферальной программе!"
            )
        }
        updateProfile { it.copy(bonusPoints = it.bonusPoints + 250) }
        viewModelScope.launch {
            prefsStore.setReferralClaimed(true)
            prefsStore.setWalletBalance(nextWallet)
        }
    }

    fun renewDriverDocument(docId: String) {
        _uiState.update { state ->
            state.copy(
                driverDocuments = state.driverDocuments.map { d ->
                    if (d.id == docId) d.copy(validUntil = "01.12.2027", daysRemaining = 365) else d
                },
                statusBanner = "Документ продлён и проверен диспетчерской"
            )
        }
    }

    fun triggerEmergencySos(fromActiveRide: Boolean = true) {
        val s = _uiState.value
        val ride = s.activeRide
        val activeRide = if (fromActiveRide) ride else null
        val alertId = "sos_${System.currentTimeMillis()}"
        SoundAndNotificationManager.playCue(SoundCue.RIDE_CHECK_ALERT, s.soundEnabled)
        _uiState.update { it.copy(statusBanner = "SOS: открываю набор ${EmergencyHelper.EMERGENCY_NUMBER}…") }

        viewModelScope.launch {
            // 1. Where are we? Wait at most 3 s for GPS, otherwise use the last known point.
            val gps = withTimeoutOrNull(3_000) {
                runCatching { LocationAndVoiceHelper.fetchCurrentLocation(appContext, s.selectedCity) }.getOrNull()
            }
            val point = gps?.point ?: activeRide?.pickupPoint ?: s.pickupPoint
            val address = gps?.address ?: activeRide?.pickupAddress ?: s.pickupAddress
            val city = activeRide?.city ?: s.selectedCity.name

            // 2. SMS with location to trusted contacts (family members in the profile).
            val contactPhones = _uiState.value.familyMembers.map { it.phone }
            val smsOpened = EmergencyHelper.openSmsToContacts(
                appContext,
                contactPhones,
                EmergencyHelper.buildSosMessage(
                    passengerName = s.profile.displayName,
                    point = point,
                    address = address,
                    carInfo = activeRide?.driverCar,
                    driverName = activeRide?.driverName
                )
            )

            // 3. Dialer with 112 on top, so the call is the first thing the user sees.
            val dialerOpened = EmergencyHelper.openDialer(appContext)

            // 4. Shared alert for dispatchers (works only when signed in and online).
            val cloud = firestoreSync.createSosAlert(
                alertId = alertId,
                lat = point.lat,
                lng = point.lng,
                address = address,
                city = city,
                tripId = activeRide?.tripId,
                driverName = activeRide?.driverName,
                carInfo = activeRide?.driverCar
            )

            val alert = EmergencySosAlert(
                id = alertId,
                passengerName = s.profile.displayName,
                driverName = activeRide?.driverName ?: "—",
                carInfo = activeRide?.driverCar ?: "—",
                city = city,
                locationAddress = address,
                timeLabel = "Только что"
            )
            val parts = buildList {
                add(if (dialerOpened) "набор ${EmergencyHelper.EMERGENCY_NUMBER} открыт" else "позвоните ${EmergencyHelper.EMERGENCY_NUMBER} вручную")
                add(if (smsOpened) "SMS контактам готово" else "нет доверенных контактов в профиле")
                add(if (cloud.isSuccess) "диспетчер уведомлён" else "диспетчер НЕ уведомлён (нет входа или интернета)")
            }
            _uiState.update {
                it.copy(
                    sosAlerts = listOf(alert) + it.sosAlerts,
                    statusBanner = "SOS: " + parts.joinToString(" • ")
                )
            }
        }
    }

    fun resolveSosAlert(alertId: String) {
        _uiState.update { state ->
            state.copy(
                sosAlerts = state.sosAlerts.map { if (it.id == alertId) it.copy(resolved = true) else it },
                statusBanner = "SOS-инцидент обработан и закрыт"
            )
        }
    }

    fun completeOnboarding() {
        _uiState.update { it.copy(showOnboarding = false) }
        viewModelScope.launch { prefsStore.setOnboardingSeen(true) }
    }

    fun reopenOnboarding() {
        _uiState.update { it.copy(showOnboarding = true) }
    }

    fun repeatTripFromHistory(trip: LocalTripEntity) {
        val city = if (cityFromGps) _uiState.value.selectedCity else KgGeoCatalog.cityByName(trip.city)
        val matchedService = ServiceType.entries.firstOrNull { it.name == trip.serviceType } ?: ServiceType.TAXI
        val matchedTariff = KgGeoCatalog.taxiTariffs.firstOrNull {
            it.nameRu.equals(trip.tariffName, ignoreCase = true) || it.nameEn.equals(trip.tariffName, ignoreCase = true)
        }?.id ?: _uiState.value.selectedTariffId
        val targetLandmark = city.landmarks.firstOrNull { trip.dropoffAddress.contains(it.name, ignoreCase = true) }
        _uiState.update {
            it.copy(
                role = AppRole.PASSENGER,
                selectedCity = city,
                serviceType = matchedService,
                selectedTariffId = matchedTariff,
                pickupAddress = trip.pickupAddress,
                dropoffAddress = trip.dropoffAddress,
                dropoffPoint = targetLandmark?.point ?: GeoPoint(city.center.lat + 0.011, city.center.lng - 0.009),
                statusBanner = "Повтор маршрута: ${trip.pickupAddress} → ${trip.dropoffAddress}"
            )
        }
        generateNearbyDrivers(city)
        refreshLiveRoadRoute()
    }

    // --- WEATHER, CURRENCY, MEET & GREET, COURIER PHOTO, LOST & FOUND, FAVORITES, DRIVER PRO ---

    fun refreshCityWeatherAndPasses() {
        val s = _uiState.value
        viewModelScope.launch {
            val info = KgWeatherAndPassesClient.fetchCityWeatherAndAqi(
                cityName = s.selectedCity.name,
                point = s.selectedCity.center,
                offlineMode = s.offlineMode
            )
            val passes = KgWeatherAndPassesClient.buildMountainPasses(info)
            _uiState.update { it.copy(cityWeather = info, mountainPasses = passes) }
        }
    }

    fun setDisplayCurrency(currency: DisplayCurrency) {
        _uiState.update {
            it.copy(
                displayCurrency = currency,
                statusBanner = if (currency == DisplayCurrency.KGS) "Валюта: кыргызский сом (KGS)"
                else "Пересчёт цен в ${currency.code} (${currency.symbol}) включён"
            )
        }
        viewModelScope.launch { prefsStore.setDisplayCurrencyCode(currency.code) }
    }

    fun setBusinessCompanyInn(inn: String) {
        val clean = inn.trim()
        _uiState.update { it.copy(businessCompanyInn = clean) }
        viewModelScope.launch { prefsStore.setBusinessCompanyInn(clean) }
    }

    fun setMeetAndGreet(enabled: Boolean, signText: String) {
        _uiState.update {
            it.copy(
                meetAndGreetEnabled = enabled,
                meetAndGreetSignText = signText,
                statusBanner = if (enabled) "Встреча с табличкой включена (45 мин бесплатного ожидания)" else null
            )
        }
    }

    fun setCourierParcelPhotoUri(uri: String?) {
        _uiState.update {
            it.copy(
                courierParcelPhotoUri = uri,
                statusBanner = if (uri != null) "Фото посылки прикреплено к заказу Курьера" else null
            )
        }
    }

    fun toggleFavoriteDriver(driverName: String) {
        val clean = driverName.trim()
        if (clean.isBlank()) return
        val wasFav = clean in _uiState.value.favoriteDrivers
        _uiState.update {
            it.copy(
                statusBanner = if (wasFav) "$clean удалён из Избранных" else "★ $clean добавлен в Избранные водители (приоритет подачи)"
            )
        }
        viewModelScope.launch { prefsStore.toggleFavoriteDriver(clean) }
    }

    fun toggleBlockedDriver(driverName: String) {
        val clean = driverName.trim()
        if (clean.isBlank()) return
        val wasBlocked = clean in _uiState.value.blockedDrivers
        _uiState.update {
            it.copy(
                statusBanner = if (wasBlocked) "$clean разблокирован" else "$clean больше не будет назначаться на ваши заказы"
            )
        }
        viewModelScope.launch { prefsStore.toggleBlockedDriver(clean) }
    }

    fun submitLostItemTicket(trip: LocalTripEntity, category: String, note: String) {
        val fullNote = "$category: $note"
        val ticket = LostItemTicket(
            id = "lf_${System.currentTimeMillis()}",
            tripId = trip.id,
            driverName = trip.driverName,
            itemCategory = category,
            note = note,
            status = "Отправлено водителю",
            createdLabel = "Только что"
        )
        _uiState.update {
            it.copy(
                lostItemTickets = listOf(ticket) + it.lostItemTickets,
                statusBanner = "Заявка Бюро находок ($category) отправлена водителю ${trip.driverName}"
            )
        }
        viewModelScope.launch {
            dao.upsertTrip(trip.copy(lostItemNote = fullNote))
            if (_uiState.value.isSignedIn) {
                firestoreSync.syncTripPostUpdate(trip.id, trip.rating, trip.tipSom, fullNote)
            }
        }
    }

    fun triggerManualRideCheck() {
        SoundAndNotificationManager.playCue(SoundCue.RIDE_CHECK_ALERT, _uiState.value.soundEnabled)
        _uiState.update { state ->
            state.activeRide?.let { r ->
                state.copy(activeRide = r.copy(rideCheckAlert = true))
            } ?: state
        }
    }

    fun setDriverHomeFilter(enabled: Boolean, district: String) {
        if (!FeatureFlags.DEMO_EXTRAS) return
        _uiState.update {
            it.copy(
                driverHomeFilterEnabled = enabled,
                driverHomeTargetDistrict = district,
                statusBanner = if (enabled) "Фильтр «По пути домой»: $district" else "Фильтр «По пути домой» выключен"
            )
        }
    }

    fun withdrawDriverEarnings(bankName: String, targetAccount: String, amountSom: Int) {
        if (!FeatureFlags.ONLINE_PAYMENTS) {
            _uiState.update { it.copy(statusBanner = "Пассажиры платят вам напрямую (наличными или переводом), выводить ничего не нужно.") }
            return
        }
        if (amountSom <= 0 || amountSom > _uiState.value.driverTodayEarningsSom) return
        val record = DriverPayoutRecord(
            id = "pay_${System.currentTimeMillis()}",
            bankName = bankName,
            targetAccount = targetAccount,
            amountSom = amountSom,
            timeLabel = "Только что"
        )
        _uiState.update {
            it.copy(
                driverTodayEarningsSom = (it.driverTodayEarningsSom - amountSom).coerceAtLeast(0),
                driverPayouts = listOf(record) + it.driverPayouts,
                statusBanner = "Вывод $amountSom сом на $bankName ($targetAccount) выполнен!"
            )
        }
    }

    // --- OWNER BANK DETAILS & AUTOMATED COMMISSION SPLIT ENGINE ---

    fun saveOwnerBankConfig(config: OwnerBankConfig) {
        val error = OwnerPayoutGatewayClient.validateBankConfig(config)
        if (error != null) {
            _uiState.update { it.copy(statusBanner = error) }
            return
        }
        val clean = config.copy(
            accountOrCard = config.accountOrCard.trim(),
            beneficiaryName = config.beneficiaryName.trim(),
            inn = config.inn.filter(Char::isDigit).take(14),
            bik = config.bik.filter(Char::isDigit).take(6)
        )
        _uiState.update {
            it.copy(
                ownerBankConfig = clean,
                statusBanner = "Реквизиты сохранены (${clean.bankName} ${OwnerPayoutGatewayClient.maskAccount(clean.accountOrCard)}) • Комиссия ${clean.commissionPercent}%"
            )
        }
        viewModelScope.launch {
            prefsStore.saveOwnerBankConfig(clean)
        }
        // Recalculate incoming driver order upfront net fares with the new commission rate
        generateDriverIncomingOrders(_uiState.value.selectedCity)

        // If owner already had pending commissions and turned on Auto-Payout, settle them immediately
        val pending = _uiState.value.ownerPendingBalanceSom
        if (clean.autoPayoutEnabled && pending > 0 && (clean.instantPerOrderSplit || pending >= clean.autoPayoutThresholdSom)) {
            settleOwnerBalanceToBank(automatic = true, triggerOrderId = null)
        }
    }

    fun triggerOwnerManualPayout() {
        val state = _uiState.value
        if (!state.ownerBankConfig.isConfigured) {
            _uiState.update { it.copy(statusBanner = "Сначала заполните и сохраните банковские реквизиты владельца") }
            return
        }
        if (state.ownerPendingBalanceSom <= 0) {
            _uiState.update { it.copy(statusBanner = "Нет накопленной комиссии к выводу") }
            return
        }
        settleOwnerBalanceToBank(automatic = false, triggerOrderId = null)
    }

    fun runTestOwnerOrderSplit() {
        if (FeatureFlags.REAL_RIDES) {
            _uiState.update { it.copy(statusBanner = "Тестовые заказы отключены: журнал показывает только настоящие поездки.") }
            return
        }
        val city = _uiState.value.selectedCity.name
        val orderNum = 1000 + Random.nextInt(9000)
        recordOwnerCommissionSplit(
            orderId = "KG-TEST-$orderNum",
            sourceLabel = "Тестовый заказ Комфорт (450 с)",
            city = city,
            grossSom = 450
        )
    }

    private fun recordOwnerCommissionSplit(
        orderId: String,
        sourceLabel: String,
        city: String,
        grossSom: Int,
        forceFullOwnerShare: Boolean = false,
        commissionFreeShift: Boolean = false
    ) {
        if (grossSom <= 0) return
        val state = _uiState.value
        val cfg = state.ownerBankConfig

        val ownerCut: Int
        val driverNet: Int
        val effectivePct: Int
        if (forceFullOwnerShare) {
            ownerCut = grossSom
            driverNet = 0
            effectivePct = 100
        } else {
            val split = OwnerPayoutGatewayClient.calculateOrderSplit(
                grossSom = grossSom,
                commissionPercent = cfg.commissionPercent,
                commissionFreeShiftActive = commissionFreeShift
            )
            ownerCut = split.ownerCommissionSom
            driverNet = split.driverNetSom
            effectivePct = split.commissionPercent
        }

        val maskedDest = if (cfg.isConfigured) {
            "${cfg.bankName} ${OwnerPayoutGatewayClient.maskAccount(cfg.accountOrCard)}"
        } else {
            "Внутренний счёт владельца"
        }

        val willAutoSettleNow = ownerCut > 0 &&
            cfg.isConfigured &&
            cfg.autoPayoutEnabled &&
            (cfg.instantPerOrderSplit || (state.ownerPendingBalanceSom + ownerCut) >= cfg.autoPayoutThresholdSom)

        val entry = OwnerCommissionEntry(
            id = "oc_${System.currentTimeMillis()}_${Random.nextInt(999)}",
            orderId = orderId,
            sourceLabel = sourceLabel,
            city = city,
            grossAmountSom = grossSom,
            commissionPercent = effectivePct,
            ownerCommissionSom = ownerCut,
            driverNetSom = driverNet,
            settledAutomatically = willAutoSettleNow,
            destinationMasked = maskedDest,
            timeLabel = "Только что"
        )

        val nextPending = state.ownerPendingBalanceSom + ownerCut
        _uiState.update {
            it.copy(
                ownerPendingBalanceSom = nextPending,
                ownerCommissionLedger = (listOf(entry) + it.ownerCommissionLedger).take(40),
                statusBanner = if (ownerCut > 0 && !willAutoSettleNow) {
                    "Комиссия владельца +$ownerCut сом ($effectivePct%) зачислена на баланс"
                } else it.statusBanner
            )
        }
        viewModelScope.launch {
            prefsStore.saveOwnerBalances(nextPending, _uiState.value.ownerTotalSettledSom)
        }

        if (willAutoSettleNow) {
            settleOwnerBalanceToBank(automatic = true, triggerOrderId = orderId)
        }
    }

    private fun settleOwnerBalanceToBank(automatic: Boolean, triggerOrderId: String?) {
        val snapshot = _uiState.value
        val cfg = snapshot.ownerBankConfig
        val amount = snapshot.ownerPendingBalanceSom
        if (!cfg.isConfigured || amount <= 0 || snapshot.ownerPayoutBusy) return

        val refCode = "KGT-${if (automatic) "AUTO" else "MAN"}-${10000 + Random.nextInt(89999)}"
        _uiState.update { it.copy(ownerPayoutBusy = true) }

        viewModelScope.launch {
            val result = OwnerPayoutGatewayClient.executeOwnerSettlement(
                config = cfg,
                amountSom = amount,
                referenceCode = refCode,
                orderId = triggerOrderId,
                offlineMode = _uiState.value.offlineMode
            )
            val masked = OwnerPayoutGatewayClient.maskAccount(cfg.accountOrCard)
            val record = OwnerBankSettlementRecord(
                id = "os_${System.currentTimeMillis()}",
                bankName = cfg.bankName,
                destinationMasked = masked,
                amountSom = amount,
                entriesCount = if (triggerOrderId != null && cfg.instantPerOrderSplit) 1 else max(1, _uiState.value.ownerCommissionLedger.count { !it.settledAutomatically }),
                automatic = automatic,
                liveGatewayDelivered = result.deliveredViaLiveGateway,
                referenceCode = result.referenceCode,
                timeLabel = "Только что"
            )
            val nextSettledTotal = _uiState.value.ownerTotalSettledSom + amount
            _uiState.update { cur ->
                cur.copy(
                    ownerPayoutBusy = false,
                    ownerPendingBalanceSom = 0,
                    ownerTotalSettledSom = nextSettledTotal,
                    ownerCommissionLedger = cur.ownerCommissionLedger.map { it.copy(settledAutomatically = true) },
                    ownerSettlements = (listOf(record) + cur.ownerSettlements).take(30),
                    statusBanner = "+$amount сом → ${cfg.bankName} $masked (${if (result.deliveredViaLiveGateway) "Банковский шлюз" else "Авто-реестр"})"
                )
            }
            prefsStore.saveOwnerBalances(0, nextSettledTotal)
            SoundAndNotificationManager.postNotification(
                appContext,
                "Авто-выплата комиссии владельцу: +$amount сом",
                result.statusMessage,
                isHighPriority = false
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopCloudObservers()
        rideSimulationJob?.cancel()
        driverWaitingTimerJob?.cancel()
        routeFetchJob?.cancel()
        addressSearchJob?.cancel()
    }
}

class KgTaxiViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val appCtx = context.applicationContext
        val db = KgTaxiDatabase.getInstance(appCtx)
        val firestoreRepo = FirestoreSyncRepository(appCtx)
        val aiAssistant = AiRouteAssistant()
        val prefsStore = AppPreferencesStore(appCtx)
        return KgTaxiViewModel(appCtx, db.dao(), firestoreRepo, aiAssistant, prefsStore, RideRepository(appCtx)) as T
    }
}
