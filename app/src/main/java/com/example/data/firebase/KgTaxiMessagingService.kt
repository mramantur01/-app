package com.example.data.firebase

import android.content.Context
import com.example.R
import com.example.util.SoundAndNotificationManager
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

/**
 * Receives push notifications sent by the server (functions/index.js):
 * a new order for drivers on the line, ride status changes and chat messages for the passenger.
 * They arrive even when the app is closed.
 */
class KgTaxiMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        // Saved the next time the app runs (it needs the signed-in user).
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_TOKEN, token).apply()
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title ?: message.data["title"] ?: "KG Taxi"
        val body = message.notification?.body ?: message.data["body"] ?: return
        SoundAndNotificationManager.ensureChannels(applicationContext)
        SoundAndNotificationManager.postNotification(applicationContext, title, body)
    }

    companion object {
        private const val PREFS = "kg_push"
        private const val KEY_TOKEN = "token"
    }
}

/**
 * Keeps device_tokens/{uid} up to date: the push token, the city and whether the driver is on the line.
 * The server uses it to find which drivers to tell about a new order.
 */
object PushTokenRegistry {

    suspend fun update(context: Context, city: String, driverOnline: Boolean) {
        val uid = Firebase.auth.currentUser?.uid ?: return
        try {
            val token = FirebaseMessaging.getInstance().token.await()
            val db = FirebaseFirestore.getInstance(
                context.applicationContext.getString(R.string.firestore_database_id)
            )
            db.collection("device_tokens").document(uid).set(
                mapOf(
                    "userId" to uid,
                    "token" to token,
                    "city" to city.take(50),
                    "driverOnline" to driverOnline,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // No Play services / no network: pushes just won't arrive; the app still works.
        }
    }
}
