package com.example.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

/**
 * Keeps the app alive while the driver is on the line: an ongoing notification "Вы на линии"
 * and a partial wake lock, so new orders arrive (with sound and vibration) even with the screen off.
 * Started when the driver presses GO, stopped on "Уйти с линии".
 */
class DriverOnlineService : Service() {
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification(this)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (_: Exception) {
            // Location permission missing: stop quietly, the app still works while it is open.
            stopSelf()
            return START_NOT_STICKY
        }
        if (wakeLock == null) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "KGTaxi:DriverOnline").apply {
                setReferenceCounted(false)
                acquire(12 * 60 * 60 * 1000L) // at most 12 hours of a shift
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "kg_taxi_driver_line"
        private const val NOTIFICATION_ID = 4242

        fun start(context: Context) {
            runCatching {
                ContextCompat.startForegroundService(context, Intent(context, DriverOnlineService::class.java))
            }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, DriverOnlineService::class.java)) }
        }

        private fun buildNotification(context: Context): Notification {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "KG Taxi — Водитель на линии", NotificationManager.IMPORTANCE_LOW).apply {
                        description = "Показывается, пока вы на линии и получаете заказы"
                    }
                )
            }
            val open = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Вы на линии")
                .setContentText("KG Taxi принимает заказы. Чтобы закончить — «Уйти с линии».")
                .setOngoing(true)
                .setContentIntent(open)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .build()
        }
    }
}
