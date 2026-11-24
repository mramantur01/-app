package com.example.data.firebase

import android.content.Context
import com.example.R
import com.example.data.local.LocalFamilyMemberEntity
import com.example.data.local.LocalMaintenanceEntity
import com.example.data.local.LocalProfileEntity
import com.example.data.local.LocalSavedPlaceEntity
import com.example.data.local.LocalTripEntity
import com.example.data.model.FamilyMemberDto
import com.example.data.model.SavedPlaceDto
import com.example.data.model.TripRecordDto
import com.example.data.model.UserProfileDto
import com.example.data.model.VehicleMaintenanceDto
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class FirestoreSyncRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    // Always resolve named database from R.string.firestore_database_id
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        val uid = auth.currentUser?.uid
        check(!uid.isNullOrBlank()) { "User must be signed in with Google before accessing Firestore." }
        return uid
    }

    fun observeProfile(userId: String): Flow<UserProfileDto?> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Mismatched userId for profile observer." }
        val path = "users/$uid"
        emitAll(
            db.collection("users").document(uid)
                .snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(UserProfileDto::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    } else null
                }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(fsErr ?: error, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    fun observeTrips(userId: String): Flow<List<TripRecordDto>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Mismatched userId for trips observer." }
        val path = "users/$uid/trips"
        emitAll(
            db.collection("users").document(uid).collection("trips")
                .snapshots()
                .map { snap ->
                    snap.toObjects(TripRecordDto::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(fsErr ?: error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeSavedPlaces(userId: String): Flow<List<SavedPlaceDto>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Mismatched userId for saved_places observer." }
        val path = "users/$uid/saved_places"
        emitAll(
            db.collection("users").document(uid).collection("saved_places")
                .snapshots()
                .map { snap ->
                    snap.toObjects(SavedPlaceDto::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(fsErr ?: error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeFamilyMembers(userId: String): Flow<List<FamilyMemberDto>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Mismatched userId for family observer." }
        val path = "users/$uid/family_members"
        emitAll(
            db.collection("users").document(uid).collection("family_members")
                .snapshots()
                .map { snap ->
                    snap.toObjects(FamilyMemberDto::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(fsErr ?: error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeMaintenanceLogs(userId: String): Flow<List<VehicleMaintenanceDto>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Mismatched userId for maintenance observer." }
        val path = "users/$uid/maintenance_logs"
        emitAll(
            db.collection("users").document(uid).collection("maintenance_logs")
                .snapshots()
                .map { snap ->
                    snap.toObjects(VehicleMaintenanceDto::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(fsErr ?: error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun syncProfile(profile: LocalProfileEntity): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.success(Unit)
        val path = "users/$uid"
        return try {
            val docRef = db.collection("users").document(uid)
            val snap = docRef.get().await()
            val safeName = profile.displayName.trim().ifBlank { "Пользователь KG Taxi" }.take(80)
            val safePayment = if (profile.paymentMethod in setOf("CASH", "MBANK", "ODENGI", "ELCART", "CARD")) profile.paymentMethod else "MBANK"
            val safeLang = if (profile.language in setOf("RU", "KG", "EN")) profile.language else "RU"
            val safeCity = profile.preferredCity.trim().ifBlank { "Бишкек" }.take(50)

            if (!snap.exists()) {
                val createPayload = mapOf<String, Any?>(
                    "userId" to uid,
                    "displayName" to safeName,
                    "phone" to profile.phone.take(30),
                    "email" to profile.email.take(120),
                    "homeAddress" to profile.homeAddress.take(160),
                    "workAddress" to profile.workAddress.take(160),
                    "paymentMethod" to safePayment,
                    "kgOneActive" to profile.kgOneActive,
                    "businessProfileActive" to profile.businessProfileActive,
                    "companyName" to profile.companyName.take(100),
                    "companyEmail" to profile.companyEmail.take(120),
                    "bonusPoints" to profile.bonusPoints.coerceIn(0, 1000000),
                    "preferredCity" to safeCity,
                    "language" to safeLang,
                    "darkTheme" to profile.darkTheme,
                    "soundEnabled" to profile.soundEnabled,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                docRef.set(createPayload).await()
            } else {
                val updatePayload = mapOf<String, Any>(
                    "displayName" to safeName,
                    "phone" to profile.phone.take(30),
                    "email" to profile.email.take(120),
                    "homeAddress" to profile.homeAddress.take(160),
                    "workAddress" to profile.workAddress.take(160),
                    "paymentMethod" to safePayment,
                    "kgOneActive" to profile.kgOneActive,
                    "businessProfileActive" to profile.businessProfileActive,
                    "companyName" to profile.companyName.take(100),
                    "companyEmail" to profile.companyEmail.take(120),
                    "bonusPoints" to profile.bonusPoints.coerceIn(0, 1000000),
                    "preferredCity" to safeCity,
                    "language" to safeLang,
                    "darkTheme" to profile.darkTheme,
                    "soundEnabled" to profile.soundEnabled,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(updatePayload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun syncTripCreate(trip: LocalTripEntity): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.success(Unit)
        val safeId = sanitizeId(trip.id)
        val path = "users/$uid/trips/$safeId"
        return try {
            val docRef = db.collection("users").document(uid).collection("trips").document(safeId)
            val payload = mapOf<String, Any?>(
                "userId" to uid,
                "serviceType" to trip.serviceType,
                "tariffName" to trip.tariffName.ifBlank { "Такси" }.take(60),
                "city" to trip.city.ifBlank { "Бишкек" }.take(50),
                "pickupAddress" to trip.pickupAddress.ifBlank { "Бишкек" }.take(200),
                "dropoffAddress" to trip.dropoffAddress.ifBlank { "Бишкек" }.take(200),
                "distanceKm" to trip.distanceKm.coerceIn(0.0, 5000.0),
                "durationMin" to trip.durationMin.coerceIn(0, 2000),
                "totalPriceSom" to trip.totalPriceSom.coerceIn(0, 1000000),
                "paymentMethod" to trip.paymentMethod.ifBlank { "MBANK" }.take(30),
                "status" to trip.status,
                "driverName" to trip.driverName.take(80),
                "driverCar" to trip.driverCar.take(80),
                "safetyPin" to trip.safetyPin.ifBlank { "4829" }.take(6),
                "rating" to trip.rating.coerceIn(0, 5),
                "tipSom" to trip.tipSom.coerceIn(0, 100000),
                "businessTrip" to trip.businessTrip,
                "lostItemNote" to trip.lostItemNote.take(300),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    /**
     * Reads the user's role from `roles/{uid}` ("driver", "dispatcher", "admin").
     * Returns "passenger" when there is no role document, the user is signed out, or the read fails.
     * Roles can only be written from the Firebase console (see firestore.rules).
     */
    suspend fun fetchRole(): String = fetchRoleInfo().role

    /**
     * Role plus the verified car, written by the owner when approving a driver:
     * roles/{uid} = { role: "driver", car: "Toyota Camry 70, белый", plate: "01KG 123 ABC" }.
     */
    suspend fun fetchRoleInfo(): RoleInfo {
        val uid = auth.currentUser?.uid ?: return RoleInfo()
        return try {
            val snap = db.collection("roles").document(uid).get().await()
            RoleInfo(
                role = (snap.getString("role") ?: "passenger").lowercase(),
                car = snap.getString("car").orEmpty(),
                plate = snap.getString("plate").orEmpty(),
                payPhone = snap.getString("payPhone").orEmpty(),
                tariffs = (snap.get("tariffs") as? List<*>)?.filterIsInstance<String>().orEmpty()
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, "roles/$uid")
            RoleInfo()
        }
    }

    /** What a person fills in to become a KG Taxi driver. Stored in driver_applications/{uid}. */
    data class DriverApplication(
        val fullName: String,
        val phone: String,
        val city: String,
        val car: String,
        val plate: String,
        val licenseNumber: String,
        val experienceYears: Int,
        val payPhone: String
    )

    /** Saves (or updates while still pending) the signed-in user's driver application. */
    suspend fun submitDriverApplication(app: DriverApplication): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Войдите в аккаунт"))
        val path = "driver_applications/$uid"
        return try {
            db.collection("driver_applications").document(uid).set(
                mapOf(
                    "userId" to uid,
                    "fullName" to app.fullName.trim().take(80),
                    "phone" to app.phone.trim().take(20),
                    "city" to app.city.trim().take(50),
                    "car" to app.car.trim().take(80),
                    "plate" to app.plate.trim().uppercase().take(20),
                    "licenseNumber" to app.licenseNumber.trim().take(30),
                    "experienceYears" to app.experienceYears.coerceIn(0, 60),
                    "payPhone" to app.payPhone.trim().take(20),
                    "status" to "pending",
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    /** "pending", "approved", "rejected", or null when there is no application. */
    suspend fun fetchDriverApplicationStatus(): String? {
        val uid = auth.currentUser?.uid ?: return null
        return try {
            val snap = db.collection("driver_applications").document(uid).get().await()
            if (snap.exists()) snap.getString("status") ?: "pending" else null
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    data class RoleInfo(
        val role: String = "passenger",
        val car: String = "",
        val plate: String = "",
        val payPhone: String = "",
        /** Tariffs approved for the driver car by the owner. */
        val tariffs: List<String> = emptyList()
    )

    /**
     * Writes an SOS alert to the shared `sos_alerts` collection so that dispatchers see it.
     * Only the sender can create it; only dispatchers/admins can read and resolve it (see firestore.rules).
     */
    suspend fun createSosAlert(
        alertId: String,
        lat: Double,
        lng: Double,
        address: String,
        city: String,
        tripId: String?,
        driverName: String?,
        carInfo: String?
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("not signed in"))
        val safeId = sanitizeId(alertId)
        val path = "sos_alerts/$safeId"
        return try {
            val payload = mapOf<String, Any?>(
                "userId" to uid,
                "lat" to lat.coerceIn(-90.0, 90.0),
                "lng" to lng.coerceIn(-180.0, 180.0),
                "address" to address.take(200),
                "city" to city.take(50),
                "tripId" to tripId?.take(80),
                "driverName" to driverName?.take(80),
                "carInfo" to carInfo?.take(80),
                "resolved" to false,
                "createdAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }
            db.collection("sos_alerts").document(safeId).set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun syncTripPostUpdate(tripId: String, rating: Int, tipSom: Int, lostItemNote: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.success(Unit)
        val safeId = sanitizeId(tripId)
        val path = "users/$uid/trips/$safeId"
        return try {
            val docRef = db.collection("users").document(uid).collection("trips").document(safeId)
            docRef.update(
                mapOf(
                    "rating" to rating.coerceIn(0, 5),
                    "tipSom" to tipSom.coerceIn(0, 100000),
                    "lostItemNote" to lostItemNote.take(300),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun syncSavedPlace(place: LocalSavedPlaceEntity): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.success(Unit)
        val safeId = sanitizeId(place.id)
        val path = "users/$uid/saved_places/$safeId"
        return try {
            val docRef = db.collection("users").document(uid).collection("saved_places").document(safeId)
            val snap = docRef.get().await()
            if (!snap.exists()) {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "label" to place.label.ifBlank { "Место" }.take(60),
                        "address" to place.address.ifBlank { "Бишкек" }.take(200),
                        "city" to place.city.ifBlank { "Бишкек" }.take(50),
                        "lat" to place.lat.coerceIn(-90.0, 90.0),
                        "lng" to place.lng.coerceIn(-180.0, 180.0),
                        "category" to if (place.category in setOf("HOME", "WORK", "FAVORITE", "ROUTE")) place.category else "FAVORITE",
                        "rating" to place.rating.coerceIn(0.0, 5.0),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.update(
                    mapOf(
                        "label" to place.label.ifBlank { "Место" }.take(60),
                        "address" to place.address.ifBlank { "Бишкек" }.take(200),
                        "city" to place.city.ifBlank { "Бишкек" }.take(50),
                        "lat" to place.lat.coerceIn(-90.0, 90.0),
                        "lng" to place.lng.coerceIn(-180.0, 180.0),
                        "rating" to place.rating.coerceIn(0.0, 5.0),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteSavedPlace(placeId: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.success(Unit)
        val safeId = sanitizeId(placeId)
        val path = "users/$uid/saved_places/$safeId"
        return try {
            db.collection("users").document(uid).collection("saved_places").document(safeId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun syncFamilyMember(member: LocalFamilyMemberEntity): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.success(Unit)
        val safeId = sanitizeId(member.id)
        val path = "users/$uid/family_members/$safeId"
        return try {
            val docRef = db.collection("users").document(uid).collection("family_members").document(safeId)
            val snap = docRef.get().await()
            if (!snap.exists()) {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "name" to member.name.ifBlank { "Член семьи" }.take(80),
                        "relation" to member.relation.ifBlank { "Teen" }.take(40),
                        "phone" to member.phone.take(30),
                        "monthlyLimitSom" to member.monthlyLimitSom.coerceIn(0, 500000),
                        "teenControlEnabled" to member.teenControlEnabled,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.update(
                    mapOf(
                        "name" to member.name.ifBlank { "Член семьи" }.take(80),
                        "relation" to member.relation.ifBlank { "Teen" }.take(40),
                        "phone" to member.phone.take(30),
                        "monthlyLimitSom" to member.monthlyLimitSom.coerceIn(0, 500000),
                        "teenControlEnabled" to member.teenControlEnabled,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteFamilyMember(memberId: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.success(Unit)
        val safeId = sanitizeId(memberId)
        val path = "users/$uid/family_members/$safeId"
        return try {
            db.collection("users").document(uid).collection("family_members").document(safeId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun syncMaintenance(item: LocalMaintenanceEntity): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.success(Unit)
        val safeId = sanitizeId(item.id)
        val path = "users/$uid/maintenance_logs/$safeId"
        return try {
            val docRef = db.collection("users").document(uid).collection("maintenance_logs").document(safeId)
            val snap = docRef.get().await()
            if (!snap.exists()) {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "vehicleName" to item.vehicleName.ifBlank { "Авто" }.take(80),
                        "serviceTitle" to item.serviceTitle.ifBlank { "ТО" }.take(100),
                        "dueMileageKm" to item.dueMileageKm.coerceIn(0, 2000000),
                        "currentMileageKm" to item.currentMileageKm.coerceIn(0, 2000000),
                        "fuelLitersPer100Km" to item.fuelLitersPer100Km.coerceIn(0.0, 50.0),
                        "completed" to item.completed,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.update(
                    mapOf(
                        "vehicleName" to item.vehicleName.ifBlank { "Авто" }.take(80),
                        "serviceTitle" to item.serviceTitle.ifBlank { "ТО" }.take(100),
                        "dueMileageKm" to item.dueMileageKm.coerceIn(0, 2000000),
                        "currentMileageKm" to item.currentMileageKm.coerceIn(0, 2000000),
                        "fuelLitersPer100Km" to item.fuelLitersPer100Km.coerceIn(0.0, 50.0),
                        "completed" to item.completed,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    private fun sanitizeId(raw: String): String =
        raw.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(120).ifBlank { "doc_${System.currentTimeMillis()}" }
}
