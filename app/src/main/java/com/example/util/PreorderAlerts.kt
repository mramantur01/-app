package com.example.util

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.firebase.RideRepository
import com.example.data.firebase.SharedRide
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Driver picks week days (Mon…Sun). When a new intercity pre-order for one of those days
 * appears in the driver's city, a notification is shown — while the app is open (live list)
 * and in the background (check every 15 minutes).
 */
object PreorderAlerts {
    private const val PREFS = "preorder_alerts"
    private const val KEY_DAYS = "days"
    private const val KEY_SEEN = "seen"
    private const val KEY_CITY = "city"
    private const val KEY_READY = "ready"
    private const val WORK_NAME = "preorder_alerts_check"

    fun days(context: Context): Set<Int> =
        prefs(context).getStringSet(KEY_DAYS, emptySet()).orEmpty().mapNotNull { it.toIntOrNull() }.toSet()

    fun setDays(context: Context, days: Set<Int>) {
        prefs(context).edit().putStringSet(KEY_DAYS, days.map { it.toString() }.toSet()).apply()
        schedule(context)
    }

    fun setCity(context: Context, city: String) {
        prefs(context).edit().putString(KEY_CITY, city).apply()
    }

    /** Start or stop the background check depending on whether any day is chosen. */
    fun schedule(context: Context) {
        val wm = WorkManager.getInstance(context)
        if (days(context).isEmpty()) {
            wm.cancelUniqueWork(WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<PreorderCheckWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        wm.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Show a notification for each new pre-order on a chosen day. Each pre-order is announced once. */
    @Synchronized
    fun check(context: Context, preorders: List<SharedRide>) {
        val p = prefs(context)
        val days = days(context)
        val seen = p.getStringSet(KEY_SEEN, emptySet()).orEmpty().toMutableSet()
        val ready = p.getBoolean(KEY_READY, false)
        val now = System.currentTimeMillis()
        val fresh = preorders.filter { r ->
            val at = r.scheduledAtMillis ?: return@filter false
            at > now && r.id !in seen && dayOf(at) in days
        }
        // The first time: remember what is already there, so we don't send a pile of old ones.
        if (ready && days.isNotEmpty()) {
            fresh.take(5).forEach { r ->
                val whenText = r.scheduledAtMillis?.let { formatDeparture(it) }.orEmpty()
                SoundAndNotificationManager.postNotification(
                    context,
                    "Новый предзаказ: $whenText",
                    "${r.city} → ${r.dropoffAddress} • ${r.priceSom} сом. Откройте KG Taxi, чтобы взять заказ."
                )
            }
        }
        seen += preorders.map { it.id }
        // Keep the list short.
        val trimmed = if (seen.size > 300) seen.toList().takeLast(200).toSet() else seen
        p.edit().putStringSet(KEY_SEEN, trimmed).putBoolean(KEY_READY, true).apply()
    }

    internal fun city(context: Context): String? = prefs(context).getString(KEY_CITY, null)

    private fun dayOf(millis: Long): Int = Calendar.getInstance().apply { timeInMillis = millis }.get(Calendar.DAY_OF_WEEK)

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/** Background check for new pre-orders on the driver's chosen days. */
class PreorderCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        if (PreorderAlerts.days(ctx).isEmpty()) return Result.success()
        val city = PreorderAlerts.city(ctx) ?: return Result.success()
        val list = runCatching { RideRepository(ctx).fetchOpenPreorders(city) }.getOrNull() ?: return Result.retry()
        PreorderAlerts.check(ctx, list)
        return Result.success()
    }
}
