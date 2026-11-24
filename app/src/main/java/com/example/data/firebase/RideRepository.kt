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

    val ACTIVE = setOf(SEARCHING, ASSIGNED, ARRIVED, IN_PROGRESS)
}

/** One order in the shared `rides` collection, seen by the passenger, the driver and dispatchers. */
data class SharedRide(
    val id: String,
    val passengerId: String,
    val passengerName: String,
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
    val driverLng: Double?
) {
    val isActive: Boolean get() = status in RideStatus.ACTIVE

    companion object {
        fun from(snap: DocumentSnapshot): SharedRide? {
            if (!snap.exists()) return null
            return SharedRide(
                id = snap.id,
                passengerId = snap.getString("passengerId") ?: return null,
                passengerName = snap.getString("passengerName") ?: "Пассажир",
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
                driverLng = snap.getDouble("driverLng")
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
        comment: String?
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
            "paymentMethod" to "CASH",
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (!comment.isNullOrBlank()) payload["comment"] = comment.take(200)
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

    /** All active rides (searching or on the way), newest first — the dispatcher's board. */
    fun observeActiveRides(): Flow<List<SharedRide>> {
        if (currentUid == null) return emptyFlow()
        return rides
            .whereIn("status", RideStatus.ACTIVE.toList())
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

    suspend fun cancelAsPassenger(rideId: String): Result<Unit> =
        runWrite(OperationType.UPDATE, "rides/$rideId") {
            rides.document(rideId).update(
                mapOf("status" to RideStatus.CANCELLED, "updatedAt" to FieldValue.serverTimestamp())
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
        driverPayPhone: String = ""
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
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }.await()
            Unit
        }
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
