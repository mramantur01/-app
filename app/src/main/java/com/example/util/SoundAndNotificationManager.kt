package com.example.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.R

enum class SoundCue {
    DRIVER_ASSIGNED,
    DRIVER_ARRIVED,
    TRIP_COMPLETED,
    RIDE_CHECK_ALERT
}

object SoundAndNotificationManager {
    private const val CHANNEL_TRIPS = "kg_taxi_trips"
    private const val CHANNEL_ALERTS = "kg_taxi_alerts"
    private var nextNotificationId = 1001

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val tripChannel = NotificationChannel(
                CHANNEL_TRIPS,
                "KG Taxi — Поездки и заказы",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Уведомления о назначении водителя, подаче и маршруте"
            }
            val alertChannel = NotificationChannel(
                CHANNEL_ALERTS,
                "KG Taxi — Пробки, скидки и ТО",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Оповещения о пробках, акциях и техобслуживании авто"
            }
            nm.createNotificationChannel(tripChannel)
            nm.createNotificationChannel(alertChannel)
        }
    }

    /** Strong vibration for a new order, felt even when the phone is on silent. */
    fun vibrateNewOrder(context: Context) {
        try {
            val pattern = longArrayOf(0, 400, 200, 400, 200, 600)
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                val vm = context.getSystemService(android.os.VibratorManager::class.java)
                vm?.defaultVibrator?.vibrate(android.os.VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                val v = context.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                v?.vibrate(android.os.VibrationEffect.createWaveform(pattern, -1))
            }
        } catch (_: Exception) {
            // No vibrator: nothing to do.
        }
    }

    fun playCue(cue: SoundCue, soundEnabled: Boolean) {
        if (!soundEnabled) return
        try {
            val toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 85)
            when (cue) {
                SoundCue.DRIVER_ASSIGNED -> toneGen.startTone(ToneGenerator.TONE_PROP_ACK, 260)
                SoundCue.DRIVER_ARRIVED -> toneGen.startTone(ToneGenerator.TONE_PROP_BEEP2, 420)
                SoundCue.TRIP_COMPLETED -> toneGen.startTone(ToneGenerator.TONE_CDMA_CONFIRM, 450)
                SoundCue.RIDE_CHECK_ALERT -> toneGen.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 500)
            }
        } catch (e: Exception) {
            Log.w("SoundManager", "Audio cue skipped: ${e.message}")
        }
    }

    fun postNotification(
        context: Context,
        title: String,
        body: String,
        isHighPriority: Boolean = true
    ) {
        ensureChannels(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }
        try {
            val channelId = if (isHighPriority) CHANNEL_TRIPS else CHANNEL_ALERTS
            val builder = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(if (isHighPriority) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)

            NotificationManagerCompat.from(context).notify(nextNotificationId++, builder.build())
        } catch (e: SecurityException) {
            Log.w("SoundManager", "Notification permission missing: ${e.message}")
        }
    }
}
