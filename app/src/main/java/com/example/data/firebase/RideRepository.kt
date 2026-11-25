package com.example.data.firebase

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/** Statuses of a shared ride, same strings as in firestore.rules. */
object RideStatus {
    const val SEARCHING = "SEARCHING"
    const val ASSIGNED = "ASSIGNED"
    const val ARRIVED = "ARRIVED"
    const val IN_PROGRESS = "IN_PROGRESS"
    const val COMPLETED = "COMPLETED"
    const val CANCELLED = "CANCELLED"
    /** Intercity pre-order taken by a driver; the trip starts later (BOOKED -> ASSIGNED). */
    const val BOOKED = "BOOKED"

    val ACTIVE = setOf(SEARCHING, ASSIGNED, ARRIVED, IN_PROGRESS)
}

/** One order in the shared `rides` collection, seen by the passenger, the driver and dispatchers. */
data class SharedRide(
    val id: String,
    val passengerId: String,
    val passengerName: String,
    val passengerPhone: String = "",
    val status: String,
    val driverId: String?,
    val driverName: String?,
    val driverCar: String?,
    val driverPlate: String?,
    /** Phone number for a direct MBank / O!Деньги transfer to the driver. */
    val driverPayPhone: String?,
    val serviceType: String,
    val city: String,
    val pickupAddress: String,
    val dropoffAddress: String,
    val pickupLat: Double,
    val pickupLng: Double,
    val dropoffLat: Double,
    val dropoffLng: Double,
    val priceSom: Int,
    val comment: String?,
    val driverLat: Double?,
    val driverLng: Double?,
    val createdAtMillis: Long = 0L,
    /** Stars the passenger gave the driver (1..5), null if not rated. */
    val driverRating: Int? = null,
    /** Stars the driver gave the passenger (1..5). */
    val passengerRating: Int? = null,
    /** Driver's average rating at the moment of accepting. */
    val driverRatingAvg: Double? = null,
    val cancelReason: String? = null,
    /** Departure time of an intercity pre-order, null for "right now" orders. */
    val scheduledAtMillis: Long? = null,
    val seats: Int? = null,
    val tariffId: String? = null,
    val tariffName: String? = null,
    /** "CASH" = cash or QR / transfer to the driver, "CARD" = card linked in the app. */
    val paymentMethod: String = "CASH",
    val passengerRatingAvg: Double? = null,
    val passengerTrips: Int? = null,
    val driverTrips: Int? = null
) {
    val isActive: Boolean get() = status in RideStatus.ACTIVE

    /** A pre-order that leaves more than 30 minutes from now (not for the "now" order feed). */
    val isFuturePreorder: Boolean
        get() = scheduledAtMillis != null && scheduledAtMillis > System.currentTimeMillis() + 30 * 60_000L

    companion object {
        fun from(snap: DocumentSnapshot): SharedRide? {
            if (!snap.exists()) return null
            return SharedRide(
                id = snap.id,
                passengerId = snap.getString("passengerId") ?: return null,
                passengerName = snap.getString("passengerName") ?: "Пассажир",
                passengerPhone = snap.getString("passengerPhone").orEmpty(),
                status = snap.getString("status") ?: return null,
                driverId = snap.getString("driverId"),
                driverName = snap.getString("driverName"),
                driverCar = snap.getString("driverCar"),
                driverPlate = snap.getString("driverPlate"),
                driverPayPhone = snap.getString("driverPayPhone"),
                serviceType = snap.getString("serviceType") ?: "TAXI",
                city = snap.getString("city") ?: "",
                pickupAddress = snap.getString("pickupAddress") ?: "",
                dropoffAddress = snap.getString("dropoffAddress") ?: "",
                pickupLat = snap.getDouble("pickupLat") ?: 0.0,
                pickupLng = snap.getDouble("pickupLng") ?: 0.0,
                dropoffLat = snap.getDouble("dropoffLat") ?: 0.0,
                dropoffLng = snap.getDouble("dropoffLng") ?: 0.0,
                priceSom = snap.getLong("priceSom")?.toInt() ?: 0,
                comment = snap.getString("comment"),
                driverLat = snap.getDouble("driverLat"),
                driverLng = snap.getDouble("driverLng"),
                createdAtMillis = snap.getTimestamp("createdAt")?.toDate()?.time ?: 0L,
                driverRating = snap.getLong("driverRating")?.toInt(),
                passengerRating = snap.getLong("passengerRating")?.toInt(),
                driverRatingAvg = snap.getDouble("driverRatingAvg"),
                cancelReason = snap.getString("cancelReason"),
                scheduledAtMillis = snap.getTimestamp("scheduledAt")?.toDate()?.time,
                seats = snap.getLong("seats")?.toInt(),
                tariffId = snap.getString("tariffId"),
                tariffName = snap.getString("tariffName"),
                paymentMethod = snap.getString("paymentMethod") ?: "CASH",
                passengerRatingAvg = snap.getDouble("passengerRatingAvg"),
                passengerTrips = snap.getLong("passengerTrips")?.toInt(),
                driverTrips = snap.getLong("driverTrips")?.toInt()
            )
        }
    }
}

/**
 * Real ride orders through Firestore. Every write here matches a rule in firestore.rules:
 * a passenger creates and cancels, a driver (role "driver") accepts and moves the trip forward.
 */
class RideRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val rides get() = db.collection("rides")

    val currentUid: String? get() = auth.currentUser?.uid

    /** Creates an order. Returns its id. Only cash payment is allowed by the rules for now. */
    suspend fun createRide(
        passengerName: String,
        serviceType: String,
        city: String,
        pickupAddress: String,
        dropoffAddress: String,
        pickupLat: Double,
        pickupLng: Double,
        dropoffLat: Double,
        dropoffLng: Double,
        priceSom: Int,
        comment: String?,
        passengerPhone: String = "",
        scheduledAtMillis: Long? = null,
        seats: Int? = null,
        tariffId: String? = null,
        tariffName: String? = null,
        paymentMethod: String = "CASH",
        passengerRatingAvg: Double? = null,
        passengerTrips: Int = 0
    ): Result<String> {
        val uid = currentUid ?: return Result.failure(IllegalStateException("Войдите в аккаунт, чтобы заказать"))
        val doc = rides.document()
        val payload = hashMapOf<String, Any?>(
            "passengerId" to uid,
            "passengerName" to passengerName.ifBlank { "Пассажир" }.take(80),
            "status" to RideStatus.SEARCHING,
            "driverId" to null,
            "serviceType" to if (serviceType == "INTERCITY") "INTERCITY" else "TAXI",
            "city" to city.ifBlank { "Бишкек" }.take(50),
            "pickupAddress" to pickupAddress.ifBlank { "Точка на карте" }.take(200),
            "dropoffAddress" to dropoffAddress.ifBlank { "Точка на карте" }.take(200),
            "pickupLat" to pickupLat,
            "pickupLng" to pickupLng,
            "dropoffLat" to dropoffLat,
            "dropoffLng" to dropoffLng,
            "priceSom" to priceSom.coerceIn(1, 100000),
            "paymentMethod" to if (paymentMethod == "CARD") "CARD" else "CASH",
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (!comment.isNullOrBlank()) payload["comment"] = comment.take(200)
        if (passengerPhone.isNotBlank()) payload["passengerPhone"] = passengerPhone.take(20)
        if (scheduledAtMillis != null) {
            payload["scheduledAt"] = com.google.firebase.Timestamp(java.util.Date(scheduledAtMillis))
        }
        if (seats != null) payload["seats"] = seats.coerceIn(1, 6)
        if (!tariffId.isNullOrBlank()) payload["tariffId"] = tariffId.take(30)
        payload["passengerTrips"] = passengerTrips.coerceIn(0, 100000)
        if (passengerRatingAvg != null) payload["passengerRatingAvg"] = passengerRatingAvg.coerceIn(0.0, 5.0)
        if (!tariffName.isNullOrBlank()) payload["tariffName"] = tariffName.take(30)
        return runWrite(OperationType.CREATE, "rides/${doc.id}") {
            doc.set(payload).await()
            doc.id
        }
    }

    /** Live updates of one ride (for the passenger and the assigned driver). */
    fun observeRide(rideId: String): Flow<SharedRide?> =
        rides.document(rideId).snapshots()
            .map { SharedRide.from(it) }
            .catch { e ->
                if (e is CancellationException) throw e
                handleFirestoreError(e, OperationType.GET, "rides/$rideId")
                emit(null)
            }

    /** Open orders in a city, newest first — the driver's order feed. */
    fun observeOpenRides(city: String): Flow<List<SharedRide>> {
        if (currentUid == null) return emptyFlow()
        return rides
            .whereEqualTo("status", RideStatus.SEARCHING)
            .whereEqualTo("city", city)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(30)
            .snapshots()
            .map { qs -> qs.documents.mapNotNull { SharedRide.from(it) } }
            .catch { e ->
                if (e is CancellationException) throw e
                handleFirestoreError(e, OperationType.LIST, "rides?status=SEARCHING&city=$city")
                emit(emptyList())
            }
    }

    /** One-time read of open pre-orders in a city (for the background alert check). */
    suspend fun fetchOpenPreorders(city: String): List<SharedRide> {
        if (currentUid == null) return emptyList()
        return rides
            .whereEqualTo("status", RideStatus.SEARCHING)
            .whereEqualTo("city", city)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(30)
            .get()
            .await()
            .documents
            .mapNotNull { SharedRide.from(it) }
            .filter { it.scheduledAtMillis != null }
    }

    /** All active rides (searching or on the way), newest first — the dispatcher's board. */
    fun observeActiveRides(): Flow<List<SharedRide>> {
        if (currentUid == null) return emptyFlow()
        return rides
            .whereIn("status", (RideStatus.ACTIVE + RideStatus.BOOKED).toList())
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(100)
            .snapshots()
            .map { qs -> qs.documents.mapNotNull { SharedRide.from(it) } }
            .catch { e ->
                if (e is CancellationException) throw e
                handleFirestoreError(e, OperationType.LIST, "rides?status in ACTIVE")
                emit(emptyList())
            }
    }

    /** My last rides (as passenger or as driver), newest first — for history and earnings. */
    fun observeMyRides(asDriver: Boolean, limit: Long = 100): Flow<List<SharedRide>> {
        val uid = currentUid ?: return emptyFlow()
        val field = if (asDriver) "driverId" else "passengerId"
        return rides
            .whereEqualTo(field, uid)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(limit)
            .snapshots()
            .map { qs -> qs.documents.mapNotNull { SharedRide.from(it) } }
            .catch { e ->
                if (e is CancellationException) throw e
                handleFirestoreError(e, OperationType.LIST, "rides?$field=me")
                emit(emptyList())
            }
    }

    suspend fun cancelAsPassenger(rideId: String, reason: String? = null): Result<Unit> =
        runWrite(OperationType.UPDATE, "rides/$rideId") {
            val data = mutableMapOf<String, Any>("status" to RideStatus.CANCELLED, "updatedAt" to FieldValue.serverTimestamp())
            if (!reason.isNullOrBlank()) data["cancelReason"] = reason.take(100)
            rides.document(rideId).update(data).await()
        }

    /** Passenger rates the driver once after a completed ride. */
    suspend fun rateDriver(rideId: String, stars: Int, tipSom: Int): Result<Unit> =
        runWrite(OperationType.UPDATE, "rides/$rideId") {
            rides.document(rideId).update(
                mapOf(
                    "driverRating" to stars.coerceIn(1, 5),
                    "tipSom" to tipSom.coerceIn(0, 100000),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }

    /** Driver rates the passenger once after a completed ride. */
    suspend fun ratePassenger(rideId: String, stars: Int): Result<Unit> =
        runWrite(OperationType.UPDATE, "rides/$rideId") {
            rides.document(rideId).update(
                mapOf("passengerRating" to stars.coerceIn(1, 5), "updatedAt" to FieldValue.serverTimestamp())
            ).await()
        }

    /**
     * A driver takes an open order. Runs in a transaction so two drivers can't take the same order:
     * the second one gets "Заказ уже принят другим водителем".
     */
    suspend fun acceptAsDriver(
        rideId: String,
        driverName: String,
        driverCar: String,
        driverPlate: String,
        driverPayPhone: String = "",
        driverRatingAvg: Double? = null,
        driverTrips: Int = 0
    ): Result<Unit> {
        val uid = currentUid ?: return Result.failure(IllegalStateException("Войдите в аккаунт"))
        val ref = rides.document(rideId)
        return runWrite(OperationType.UPDATE, "rides/$rideId") {
            db.runTransaction { tx ->
                val snap = tx.get(ref)
                if (snap.getString("status") != RideStatus.SEARCHING || snap.getString("driverId") != null) {
                    throw IllegalStateException("Заказ уже принят другим водителем")
                }
                tx.update(
                    ref,
                    mapOf(
                        "status" to RideStatus.ASSIGNED,
                        "driverId" to uid,
                        "driverName" to driverName.ifBlank { "Водитель" }.take(80),
                        "driverCar" to driverCar.take(80),
                        "driverPlate" to driverPlate.take(20),
                        "driverPayPhone" to driverPayPhone.take(20),
                        "driverRatingAvg" to (driverRatingAvg ?: 0.0),
                        "driverTrips" to driverTrips.coerceIn(0, 100000),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }.await()
            Unit
        }
    }

    /** A driver books an intercity pre-order for later. Same race protection as [acceptAsDriver]. */
    suspend fun bookPreorder(
        rideId: String,
        driverName: String,
        driverCar: String,
        driverPlate: String,
        driverPayPhone: String = "",
        driverRatingAvg: Double? = null,
        driverTrips: Int = 0
    ): Result<Unit> {
        val uid = currentUid ?: return Result.failure(IllegalStateException("Войдите в аккаунт"))
        val ref = rides.document(rideId)
        return runWrite(OperationType.UPDATE, "rides/$rideId") {
            db.runTransaction { tx ->
                val snap = tx.get(ref)
                if (snap.getString("status") != RideStatus.SEARCHING || snap.getString("driverId") != null) {
                    throw IllegalStateException("Предзаказ уже взял другой водитель")
                }
                tx.update(
                    ref,
                    mapOf(
                        "status" to RideStatus.BOOKED,
                        "driverId" to uid,
                        "driverName" to driverName.ifBlank { "Водитель" }.take(80),
                        "driverCar" to driverCar.take(80),
                        "driverPlate" to driverPlate.take(20),
                        "driverPayPhone" to driverPayPhone.take(20),
                        "driverRatingAvg" to (driverRatingAvg ?: 0.0),
                        "driverTrips" to driverTrips.coerceIn(0, 100000),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }.await()
            Unit
        }
    }

    /** The booked driver gives the pre-order back to other drivers. */
    suspend fun releasePreorder(rideId: String): Result<Unit> =
        runWrite(OperationType.UPDATE, "rides/$rideId") {
            rides.document(rideId).update(
                mapOf(
                    "status" to RideStatus.SEARCHING,
                    "driverId" to null,
                    "driverName" to null,
                    "driverCar" to null,
                    "driverPlate" to null,
                    "driverPayPhone" to null,
                    "driverRatingAvg" to null,
                    "driverTrips" to null,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }

    /** Driver face photo for the passenger (null if the driver has none yet). */
    suspend fun fetchDriverAvatar(driverId: String): String? = try {
        db.collection("driver_avatars").document(driverId).get().await().getString("data")
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    /** The assigned driver moves the trip: ARRIVED, IN_PROGRESS, COMPLETED or CANCELLED. */
    suspend fun setStatusAsDriver(rideId: String, newStatus: String): Result<Unit> =
        runWrite(OperationType.UPDATE, "rides/$rideId") {
            rides.document(rideId).update(
                mapOf("status" to newStatus, "updatedAt" to FieldValue.serverTimestamp())
            ).await()
        }

    /** The assigned driver shares where the car is, so the passenger sees it move. */
    suspend fun updateDriverLocation(rideId: String, lat: Double, lng: Double): Result<Unit> =
        runWrite(OperationType.UPDATE, "rides/$rideId") {
            rides.document(rideId).update(
                mapOf("driverLat" to lat, "driverLng" to lng, "updatedAt" to FieldValue.serverTimestamp())
            ).await()
        }

    // --- Chat between the passenger and the driver of one ride: rides/{rideId}/messages ---

    data class ChatMessage(val id: String, val senderId: String, val text: String, val createdAtMillis: Long)

    fun observeMessages(rideId: String): Flow<List<ChatMessage>> =
        rides.document(rideId).collection("messages")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .limit(200)
            .snapshots()
            .map { qs ->
                qs.documents.mapNotNull { d ->
                    ChatMessage(
                        id = d.id,
                        senderId = d.getString("senderId") ?: return@mapNotNull null,
                        text = d.getString("text") ?: return@mapNotNull null,
                        createdAtMillis = d.getTimestamp("createdAt")?.toDate()?.time ?: System.currentTimeMillis()
                    )
                }
            }
            .catch { e ->
                if (e is CancellationException) throw e
                handleFirestoreError(e, OperationType.LIST, "rides/$rideId/messages")
                emit(emptyList())
            }

    suspend fun sendMessage(rideId: String, text: String): Result<Unit> {
        val uid = currentUid ?: return Result.failure(IllegalStateException("Войдите в аккаунт"))
        val clean = text.trim().take(500)
        if (clean.isEmpty()) return Result.success(Unit)
        return runWrite(OperationType.CREATE, "rides/$rideId/messages") {
            rides.document(rideId).collection("messages").add(
                mapOf(
                    "senderId" to uid,
                    "text" to clean,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Unit
        }
    }

    private suspend fun <T> runWrite(op: OperationType, path: String, block: suspend () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val root = generateSequence<Throwable>(e) { it.cause }.firstOrNull { it is IllegalStateException }
            if (root == null) handleFirestoreError(e, op, path)
            Result.failure(root ?: e)
        }
}
