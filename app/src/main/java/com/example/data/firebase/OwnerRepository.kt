package com.example.data.firebase

import android.content.Context
import com.example.R
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/** One driver application as the owner sees it. */
data class OwnerApplication(
    val uid: String,
    val fullName: String,
    val phone: String,
    val city: String,
    val car: String,
    val plate: String,
    val licenseNumber: String,
    val passportNumber: String,
    val experienceYears: Int,
    val payPhone: String,
    val tariffs: List<String>,
    val status: String,
    val createdAtMillis: Long,
    val birthDate: String = "",
    val workType: String = "",
    val insuranceUntil: String = ""
)

data class OwnerDriver(
    val uid: String,
    val name: String,
    val car: String,
    val plate: String,
    val tariffs: List<String>,
    val blocked: Boolean
)

data class OwnerDayStats(
    val ridesTotal: Int = 0,
    val ridesDone: Int = 0,
    val ridesCancelled: Int = 0,
    val revenueSom: Int = 0,
    /** Best drivers of the period: name to completed trips and revenue. */
    val topDrivers: List<Triple<String, Int, Int>> = emptyList(),
    /** Why orders were cancelled in the period: reason to count. */
    val cancelReasons: List<Pair<String, Int>> = emptyList(),
    /** Rows for the spreadsheet export. */
    val rows: List<OwnerRideRow> = emptyList()
)

data class OwnerRideRow(
    val createdAtMillis: Long,
    val city: String,
    val from: String,
    val to: String,
    val tariff: String,
    val priceSom: Int,
    val status: String,
    val payment: String,
    val passenger: String,
    val driver: String
)

/**
 * Owner (role "admin") tools: review driver applications with their photos,
 * approve or reject, list drivers, see today's numbers. Every write matches firestore.rules.
 */
class OwnerRepository(context: Context) {
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(
        context.applicationContext.getString(R.string.firestore_database_id)
    )

    fun observeApplications(): Flow<List<OwnerApplication>> =
        db.collection("driver_applications")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(100)
            .snapshots()
            .map { qs ->
                qs.documents.map { d ->
                    @Suppress("UNCHECKED_CAST")
                    OwnerApplication(
                        uid = d.id,
                        fullName = d.getString("fullName").orEmpty(),
                        phone = d.getString("phone").orEmpty(),
                        city = d.getString("city").orEmpty(),
                        car = d.getString("car").orEmpty(),
                        plate = d.getString("plate").orEmpty(),
                        licenseNumber = d.getString("licenseNumber").orEmpty(),
                        passportNumber = d.getString("passportNumber").orEmpty(),
                        experienceYears = d.getLong("experienceYears")?.toInt() ?: 0,
                        payPhone = d.getString("payPhone").orEmpty(),
                        tariffs = (d.get("tariffs") as? List<String>).orEmpty(),
                        status = d.getString("status") ?: "pending",
                        createdAtMillis = d.getTimestamp("createdAt")?.toDate()?.time ?: 0L,
                        birthDate = d.getString("birthDate").orEmpty(),
                        workType = d.getString("workType").orEmpty(),
                        insuranceUntil = d.getString("insuranceUntil").orEmpty()
                    )
                }
            }
            .catch { e ->
                if (e is CancellationException) throw e
                handleFirestoreError(e, OperationType.LIST, "driver_applications")
                emit(emptyList())
            }

    /** Base64 JPEGs of one applicant, by photo kind id. */
    suspend fun fetchPhotos(uid: String): Map<String, String> = try {
        db.collection("registrations").document(uid).collection("photos").get().await()
            .documents.mapNotNull { d -> d.getString("data")?.let { d.id to it } }.toMap()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.LIST, "registrations/$uid/photos")
        emptyMap()
    }

    suspend fun approve(app: OwnerApplication): Result<Unit> = write("roles/${app.uid}") {
        val face = runCatching {
            db.collection("registrations").document(app.uid).collection("photos").document("driver_face").get().await().getString("data")
        }.getOrNull()
        val batch = db.batch()
        if (!face.isNullOrBlank()) {
            batch.set(
                db.collection("driver_avatars").document(app.uid),
                mapOf("data" to face, "updatedAt" to FieldValue.serverTimestamp())
            )
        }
        batch.set(
            db.collection("roles").document(app.uid),
            mapOf(
                "role" to "driver",
                "name" to app.fullName,
                "car" to app.car,
                "plate" to app.plate,
                "payPhone" to app.payPhone.ifBlank { app.phone },
                "tariffs" to app.tariffs,
                "blocked" to false,
                "approvedAt" to FieldValue.serverTimestamp()
            )
        )
        batch.update(
            db.collection("driver_applications").document(app.uid),
            mapOf("status" to "approved", "reviewedAt" to FieldValue.serverTimestamp())
        )
        batch.commit().await()
    }

    suspend fun reject(uid: String, reason: String): Result<Unit> = write("driver_applications/$uid") {
        db.collection("driver_applications").document(uid).update(
            mapOf(
                "status" to "rejected",
                "rejectReason" to reason.take(200),
                "reviewedAt" to FieldValue.serverTimestamp()
            )
        ).await()
    }

    fun observeDrivers(): Flow<List<OwnerDriver>> =
        db.collection("roles").whereEqualTo("role", "driver").limit(300).snapshots()
            .map { qs ->
                qs.documents.map { d ->
                    @Suppress("UNCHECKED_CAST")
                    OwnerDriver(
                        uid = d.id,
                        name = d.getString("name").orEmpty(),
                        car = d.getString("car").orEmpty(),
                        plate = d.getString("plate").orEmpty(),
                        tariffs = (d.get("tariffs") as? List<String>).orEmpty(),
                        blocked = d.getBoolean("blocked") == true
                    )
                }
            }
            .catch { e ->
                if (e is CancellationException) throw e
                handleFirestoreError(e, OperationType.LIST, "roles?role=driver")
                emit(emptyList())
            }

    /** Blocks or unblocks a driver: a blocked driver keeps the role record but can't take orders. */
    /** Debt of one driver: his completed rides and what he already paid. */
    suspend fun fetchDriverBalance(uid: String, commissionPercent: Int, goalTrips: Int, goalBonusSom: Int): com.example.data.model.DriverBalance? = try {
        val rides = db.collection("rides").whereEqualTo("driverId", uid).whereEqualTo("status", RideStatus.COMPLETED)
            .limit(2000).get().await().documents.mapNotNull { SharedRide.from(it) }
        val paid = db.collection("driver_balances").document(uid).get().await().getLong("paidSom")?.toInt() ?: 0
        com.example.data.model.DriverBalance.compute(rides, commissionPercent, goalTrips, goalBonusSom, paid)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.LIST, "rides?driverId=$uid")
        null
    }

    /** Owner got money from the driver: add it to what the driver has paid. */
    suspend fun addDriverPayment(uid: String, amountSom: Int): Result<Unit> = write("driver_balances/$uid") {
        db.collection("driver_balances").document(uid).set(
            mapOf("paidSom" to FieldValue.increment(amountSom.toLong()), "updatedAt" to FieldValue.serverTimestamp()),
            com.google.firebase.firestore.SetOptions.merge()
        ).await()
    }

    suspend fun setBlocked(uid: String, blocked: Boolean): Result<Unit> = write("roles/$uid") {
        db.collection("roles").document(uid).update(
            mapOf("role" to if (blocked) "driver_blocked" else "driver", "blocked" to blocked)
        ).await()
    }

    fun observeBlockedDrivers(): Flow<List<OwnerDriver>> =
        db.collection("roles").whereEqualTo("role", "driver_blocked").limit(300).snapshots()
            .map { qs ->
                qs.documents.map { d ->
                    @Suppress("UNCHECKED_CAST")
                    OwnerDriver(
                        uid = d.id,
                        name = d.getString("name").orEmpty(),
                        car = d.getString("car").orEmpty(),
                        plate = d.getString("plate").orEmpty(),
                        tariffs = (d.get("tariffs") as? List<String>).orEmpty(),
                        blocked = true
                    )
                }
            }
            .catch { e ->
                if (e is CancellationException) throw e
                emit(emptyList())
            }

    /** Rides created since [fromMillis] (today), for the owner's numbers. */
    fun observeRidesSince(fromMillis: Long): Flow<OwnerDayStats> =
        db.collection("rides")
            .whereGreaterThanOrEqualTo("createdAt", Timestamp(java.util.Date(fromMillis)))
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(1000)
            .snapshots()
            .map { qs ->
                val docs = qs.documents
                val done = docs.filter { it.getString("status") == RideStatus.COMPLETED }
                OwnerDayStats(
                    ridesTotal = docs.size,
                    ridesDone = done.size,
                    ridesCancelled = docs.count { it.getString("status") == RideStatus.CANCELLED },
                    revenueSom = done.sumOf { (it.getLong("priceSom") ?: 0L).toInt() },
                    topDrivers = done.groupBy { it.getString("driverName").orEmpty().ifBlank { "Без имени" } }
                        .map { (name, list) -> Triple(name, list.size, list.sumOf { (it.getLong("priceSom") ?: 0L).toInt() }) }
                        .sortedByDescending { it.second }
                        .take(5),
                    cancelReasons = docs.filter { it.getString("status") == RideStatus.CANCELLED }
                        .groupingBy { it.getString("cancelReason")?.takeIf { r -> r.isNotBlank() } ?: "Без причины" }
                        .eachCount().toList().sortedByDescending { it.second },
                    rows = docs.map { d ->
                        OwnerRideRow(
                            createdAtMillis = d.getTimestamp("createdAt")?.toDate()?.time ?: 0L,
                            city = d.getString("city").orEmpty(),
                            from = d.getString("pickupAddress").orEmpty(),
                            to = d.getString("dropoffAddress").orEmpty(),
                            tariff = d.getString("tariffName") ?: if (d.getString("serviceType") == "INTERCITY") "Межгород" else "Такси",
                            priceSom = (d.getLong("priceSom") ?: 0L).toInt(),
                            status = d.getString("status").orEmpty(),
                            payment = if (d.getString("paymentMethod") == "CARD") "Карта" else "Наличные/QR",
                            passenger = d.getString("passengerName").orEmpty(),
                            driver = d.getString("driverName").orEmpty()
                        )
                    }
                )
            }
            .catch { e ->
                if (e is CancellationException) throw e
                handleFirestoreError(e, OperationType.LIST, "rides?createdAt>=today")
                emit(OwnerDayStats())
            }

    private suspend fun write(path: String, block: suspend () -> Unit): Result<Unit> =
        try {
            block()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
}
