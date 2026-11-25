package com.example.data.firebase

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.auth
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

/** Price of one tariff set by the owner. */
data class TariffPrice(val baseSom: Int, val perKmSom: Int)

/**
 * Settings the owner changes from the owner panel without a new app version:
 * tariff prices, commission and the first-ride promo code. Stored in config/app.
 */
data class AppConfig(
    val commissionPercent: Int? = null,
    val tariffs: Map<String, TariffPrice> = emptyMap(),
    val promoCode: String = "",
    val promoPercent: Int = 0,
    val promoActive: Boolean = false,
    /** Intercity seat price by route id (owner panel). */
    val intercity: Map<String, Int> = emptyMap(),
    /** Discount for a friend's first ride by an invite code. */
    val referralPercent: Int = 20,
    /** Daily goal for drivers: this many trips in a day gives [goalBonusSom]. 0 = off. */
    val goalTrips: Int = 0,
    val goalBonusSom: Int = 0,
    /** A driver who owes more than this cannot go on the line. 0 = no limit. */
    val debtLimitSom: Int = 0
)

/** A message to support or a complaint about a ride. */
data class SupportTicket(
    val id: String,
    val userId: String,
    val name: String,
    val phone: String,
    val text: String,
    val rideId: String?,
    val status: String,
    val createdAtMillis: Long
)

private fun db(context: Context): FirebaseFirestore =
    FirebaseFirestore.getInstance(context.applicationContext.getString(R.string.firestore_database_id))

class AppConfigRepository(context: Context) {
    private val doc = db(context).collection("config").document("app")

    fun observe(): Flow<AppConfig> =
        doc.snapshots()
            .map { snap ->
                @Suppress("UNCHECKED_CAST")
                val tariffs = (snap.get("tariffs") as? Map<String, Map<String, Any?>>).orEmpty()
                    .mapNotNull { (id, m) ->
                        val base = (m["baseSom"] as? Number)?.toInt() ?: return@mapNotNull null
                        val perKm = (m["perKmSom"] as? Number)?.toInt() ?: return@mapNotNull null
                        id to TariffPrice(base, perKm)
                    }.toMap()
                AppConfig(
                    commissionPercent = snap.getLong("commissionPercent")?.toInt(),
                    tariffs = tariffs,
                    promoCode = snap.getString("promoCode").orEmpty(),
                    promoPercent = snap.getLong("promoPercent")?.toInt() ?: 0,
                    promoActive = snap.getBoolean("promoActive") == true,
                    intercity = (snap.get("intercity") as? Map<*, *>).orEmpty()
                        .mapNotNull { (k, v) -> (k as? String)?.let { id -> (v as? Number)?.toInt()?.let { id to it } } }.toMap(),
                    referralPercent = snap.getLong("referralPercent")?.toInt() ?: 20,
                    goalTrips = snap.getLong("goalTrips")?.toInt() ?: 0,
                    goalBonusSom = snap.getLong("goalBonusSom")?.toInt() ?: 0,
                    debtLimitSom = snap.getLong("debtLimitSom")?.toInt() ?: 0
                )
            }
            .catch { e ->
                if (e is CancellationException) throw e
                emit(AppConfig())
            }

    suspend fun save(config: AppConfig): Result<Unit> = try {
        doc.set(
            mapOf(
                "commissionPercent" to (config.commissionPercent ?: 14).coerceIn(0, 50),
                "tariffs" to config.tariffs.mapValues { (_, p) ->
                    mapOf("baseSom" to p.baseSom.coerceIn(0, 5000), "perKmSom" to p.perKmSom.coerceIn(0, 500))
                },
                "promoCode" to config.promoCode.trim().uppercase().take(20),
                "promoPercent" to config.promoPercent.coerceIn(0, 90),
                "promoActive" to config.promoActive,
                "intercity" to config.intercity.mapValues { (_, v) -> v.coerceIn(0, 50000) },
                "referralPercent" to config.referralPercent.coerceIn(0, 90),
                "goalTrips" to config.goalTrips.coerceIn(0, 100),
                "goalBonusSom" to config.goalBonusSom.coerceIn(0, 100000),
                "debtLimitSom" to config.debtLimitSom.coerceIn(0, 1000000),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.WRITE, "config/app")
        Result.failure(e)
    }
}

class SupportRepository(context: Context, private val auth: FirebaseAuth = Firebase.auth) {
    private val col = db(context).collection("support_tickets")

    suspend fun send(name: String, phone: String, text: String, rideId: String?): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Войдите в аккаунт"))
        return try {
            val data = mutableMapOf<String, Any>(
                "userId" to uid,
                "name" to name.take(80).ifBlank { "Пользователь" },
                "phone" to phone.take(20),
                "text" to text.trim().take(1000),
                "status" to "open",
                "createdAt" to FieldValue.serverTimestamp()
            )
            if (!rideId.isNullOrBlank()) data["rideId"] = rideId.take(60)
            col.add(data).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "support_tickets")
            Result.failure(e)
        }
    }

    /** All tickets, newest first (owner only). */
    fun observeAll(): Flow<List<SupportTicket>> =
        col.orderBy("createdAt", Query.Direction.DESCENDING).limit(100).snapshots()
            .map { qs ->
                qs.documents.map { d ->
                    SupportTicket(
                        id = d.id,
                        userId = d.getString("userId").orEmpty(),
                        name = d.getString("name").orEmpty(),
                        phone = d.getString("phone").orEmpty(),
                        text = d.getString("text").orEmpty(),
                        rideId = d.getString("rideId"),
                        status = d.getString("status") ?: "open",
                        createdAtMillis = d.getTimestamp("createdAt")?.toDate()?.time ?: 0L
                    )
                }
            }
            .catch { e ->
                if (e is CancellationException) throw e
                handleFirestoreError(e, OperationType.LIST, "support_tickets")
                emit(emptyList())
            }

    suspend fun resolve(id: String): Result<Unit> = try {
        col.document(id).update(mapOf("status" to "resolved", "resolvedAt" to FieldValue.serverTimestamp())).await()
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        handleFirestoreError(e, OperationType.UPDATE, "support_tickets/$id")
        Result.failure(e)
    }
}

/** Live status of the signed-in user's driver application: "pending" / "approved" / "rejected" / null. */
class DriverApplicationWatcher(context: Context, private val auth: FirebaseAuth = Firebase.auth) {
    private val db = db(context)

    fun observe(): Flow<Pair<String?, String?>> {
        val uid = auth.currentUser?.uid ?: return emptyFlow()
        return db.collection("driver_applications").document(uid).snapshots()
            .map { s -> if (s.exists()) (s.getString("status") ?: "pending") to s.getString("rejectReason") else null to null }
            .catch { e ->
                if (e is CancellationException) throw e
                emit(null to null)
            }
    }
}

/**
 * Deletes the user's account and personal data (required by Google Play):
 * profile and its subcollections, registration with document photos, driver application,
 * push token, then the Firebase Auth user. Ride records stay for trip history of the other party.
 */
class AccountDeletionRepository(context: Context, private val auth: FirebaseAuth = Firebase.auth) {
    private val db = db(context)

    suspend fun deleteEverything(): Result<Unit> {
        val user = auth.currentUser ?: return Result.failure(IllegalStateException("Войдите в аккаунт"))
        val uid = user.uid
        return try {
            val userDoc = db.collection("users").document(uid)
            for (sub in listOf("trips", "saved_places", "family_members", "maintenance_logs")) {
                runCatching {
                    userDoc.collection(sub).get().await().documents.forEach { it.reference.delete().await() }
                }
            }
            runCatching { userDoc.delete().await() }
            val reg = db.collection("registrations").document(uid)
            runCatching { reg.collection("photos").get().await().documents.forEach { it.reference.delete().await() } }
            runCatching { reg.delete().await() }
            runCatching { db.collection("driver_applications").document(uid).delete().await() }
            runCatching { db.collection("device_tokens").document(uid).delete().await() }
            user.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseAuthRecentLoginRequiredException) {
            Result.failure(IllegalStateException("Для удаления войдите в аккаунт заново и повторите."))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}


/**
 * "Пригласи друга": every user has a code (KG + 6 letters of the account id).
 * referrals/{code} = { uid } so a friend's code can be checked when they enter it.
 */
class ReferralRepository(context: Context, private val auth: FirebaseAuth = Firebase.auth) {
    private val col = db(context).collection("referrals")

    fun myCode(): String? = auth.currentUser?.uid?.let { uid ->
        "KG" + uid.filter { it.isLetterOrDigit() }.take(6).uppercase()
    }

    /** Registers my code once so friends can use it. */
    suspend fun ensureMyCode() {
        val uid = auth.currentUser?.uid ?: return
        val code = myCode() ?: return
        try {
            val doc = col.document(code)
            if (!doc.get().await().exists()) {
                doc.set(mapOf("uid" to uid, "createdAt" to FieldValue.serverTimestamp())).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    /** Owner uid of the code, or null if there is no such code. */
    suspend fun ownerOf(code: String): String? = try {
        col.document(code.trim().uppercase()).get().await().getString("uid")
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }
}
