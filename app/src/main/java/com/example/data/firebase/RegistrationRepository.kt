package com.example.data.firebase

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/** Photo slots of the registration. */
enum class RegPhoto(val id: String, val title: String) {
    PASSPORT("passport", "Паспорт (ID-карта) — лицевая сторона"),
    PASSPORT_BACK("passport_back", "Паспорт (ID-карта) — обратная сторона"),
    SELFIE_DOC("selfie_passport", "Селфи с паспортом в руке: видно лицо и паспорт"),
    DRIVER_FACE("driver_face", "Ваше фото (лицо) — его увидят пассажиры"),
    LICENSE("license", "Водительское удостоверение — лицевая сторона"),
    LICENSE_BACK("license_back", "Водительское удостоверение — обратная сторона"),
    CAR_FRONT("car_front", "Авто спереди (виден номер)"),
    CAR_BACK("car_back", "Авто сзади (виден номер)"),
    CAR_LEFT("car_left", "Авто слева"),
    CAR_RIGHT("car_right", "Авто справа"),
    CAR_INTERIOR("car_interior", "Салон");

    companion object {
        val PASSENGER = listOf(PASSPORT, PASSPORT_BACK, SELFIE_DOC)
        val DRIVER_DOCS = listOf(DRIVER_FACE, PASSPORT, PASSPORT_BACK, SELFIE_DOC, LICENSE, LICENSE_BACK)
        val CAR = listOf(CAR_FRONT, CAR_BACK, CAR_LEFT, CAR_RIGHT, CAR_INTERIOR)
    }
}

/** What we know about this user's registration. */
data class RegistrationInfo(
    /** "passenger", "driver", or null when the user hasn't registered yet. */
    val type: String? = null,
    val fullName: String = "",
    /** Driver application status: "pending", "approved", "rejected" (null for passengers). */
    val driverStatus: String? = null,
    val rejectReason: String? = null,
    val phone: String = ""
)

data class DriverRegistration(
    val fullName: String,
    val phone: String,
    val city: String,
    val passportNumber: String,
    val licenseNumber: String,
    val experienceYears: Int,
    val payPhone: String,
    val carMake: String,
    val carModel: String,
    val carYear: Int,
    val carColor: String,
    val plate: String,
    val tariffs: List<String>
)

/**
 * Registration of passengers (passport) and drivers (passport, licence, car, photo check).
 * Photos are stored as small JPEGs inside Firestore documents (no Cloud Storage needed):
 * registrations/{uid}/photos/{kind}. Only the owner and admins can read them (firestore.rules).
 */
class RegistrationRepository(
    private val appContext: Context,
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(
        appContext.getString(R.string.firestore_database_id)
    ),
    private val auth: FirebaseAuth = Firebase.auth
) {
    private val uid get() = auth.currentUser?.uid

    suspend fun fetch(): RegistrationInfo {
        val id = uid ?: return RegistrationInfo()
        return try {
            val reg = db.collection("registrations").document(id).get().await()
            if (!reg.exists()) return RegistrationInfo()
            val type = reg.getString("type")
            var status: String? = null
            var reason: String? = null
            if (type == "driver") {
                val app = db.collection("driver_applications").document(id).get().await()
                if (app.exists()) {
                    status = app.getString("status") ?: "pending"
                    reason = app.getString("rejectReason")
                }
            }
            RegistrationInfo(type, reg.getString("fullName").orEmpty(), status, reason, reg.getString("phone").orEmpty())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, "registrations/$id")
            // Unknown: don't force the registration screen on a network error.
            RegistrationInfo(type = "unknown")
        }
    }

    /** Shrinks a camera photo to max 1280 px and JPEG ~60% so it fits a Firestore document. */
    suspend fun encodePhoto(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            appContext.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2560) sample *= 2
            val bmp = appContext.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return@withContext null
            val maxSide = 1280f
            val scale = minOf(1f, maxSide / maxOf(bmp.width, bmp.height))
            val scaled = if (scale < 1f) {
                Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true)
            } else bmp
            var quality = 60
            var bytes: ByteArray
            do {
                val out = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
                bytes = out.toByteArray()
                quality -= 10
            } while (bytes.size > 450_000 && quality >= 20)
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun savePhotos(id: String, photos: Map<RegPhoto, String>) {
        photos.forEach { (kind, data) ->
            db.collection("registrations").document(id).collection("photos").document(kind.id).set(
                mapOf("kind" to kind.id, "data" to data, "createdAt" to FieldValue.serverTimestamp())
            ).await()
        }
    }

    suspend fun registerPassenger(
        fullName: String,
        phone: String,
        passportNumber: String,
        photos: Map<RegPhoto, String>
    ): Result<Unit> {
        val id = uid ?: return Result.failure(IllegalStateException("Войдите в аккаунт"))
        return runCatchingFs("registrations/$id") {
            savePhotos(id, photos)
            db.collection("registrations").document(id).set(
                mapOf(
                    "userId" to id,
                    "type" to "passenger",
                    "fullName" to fullName.trim().take(80),
                    "phone" to phone.trim().take(20),
                    "passportNumber" to passportNumber.trim().uppercase().take(20),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
    }

    suspend fun registerDriver(reg: DriverRegistration, photos: Map<RegPhoto, String>): Result<Unit> {
        val id = uid ?: return Result.failure(IllegalStateException("Войдите в аккаунт"))
        return runCatchingFs("driver_applications/$id") {
            savePhotos(id, photos)
            val car = "${reg.carMake} ${reg.carModel} ${reg.carYear}, ${reg.carColor.lowercase()}"
            db.collection("driver_applications").document(id).set(
                mapOf(
                    "userId" to id,
                    "fullName" to reg.fullName.trim().take(80),
                    "phone" to reg.phone.trim().take(20),
                    "city" to reg.city.trim().take(50),
                    "car" to car.take(80),
                    "plate" to reg.plate.trim().uppercase().take(20),
                    "licenseNumber" to reg.licenseNumber.trim().uppercase().take(30),
                    "experienceYears" to reg.experienceYears.coerceIn(0, 60),
                    "payPhone" to reg.payPhone.trim().take(20),
                    "passportNumber" to reg.passportNumber.trim().uppercase().take(20),
                    "carMake" to reg.carMake.take(40),
                    "carModel" to reg.carModel.take(40),
                    "carYear" to reg.carYear,
                    "carColor" to reg.carColor.take(30),
                    "tariffs" to reg.tariffs.take(25),
                    "status" to "pending",
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            db.collection("registrations").document(id).set(
                mapOf(
                    "userId" to id,
                    "type" to "driver",
                    "fullName" to reg.fullName.trim().take(80),
                    "phone" to reg.phone.trim().take(20),
                    "passportNumber" to reg.passportNumber.trim().uppercase().take(20),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
    }

    private suspend fun runCatchingFs(path: String, block: suspend () -> Unit): Result<Unit> =
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
