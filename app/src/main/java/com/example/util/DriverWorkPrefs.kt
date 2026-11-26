package com.example.util

import android.content.Context
import android.speech.tts.TextToSpeech
import com.example.data.model.GeoPoint
import java.util.Locale

/**
 * Driver's own work settings kept on the phone: pickup radius, voice, night map,
 * home address and "Домой" uses, and time on the line (rest rule: 12 h on the line,
 * then 6 h of rest).
 */
object DriverWorkPrefs {
    const val MAX_ONLINE_MIN = 12 * 60
    const val REST_MIN = 6 * 60
    const val HOME_USES_PER_DAY = 2

    private const val PREFS = "driver_work"
    private fun p(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun radiusKm(c: Context): Int = p(c).getInt("radius", 0)
    fun setRadiusKm(c: Context, km: Int) = p(c).edit().putInt("radius", km).apply()

    /** Minimal passenger rating (0 = any). New passengers without trips always pass. */
    fun minRating(c: Context): Double = p(c).getFloat("min_rating", 0f).toDouble()
    fun setMinRating(c: Context, v: Double) = p(c).edit().putFloat("min_rating", v.toFloat()).apply()

    /** The driver also takes food and grocery deliveries (works as a courier). */
    fun takesDelivery(c: Context): Boolean = p(c).getBoolean("takes_delivery_v2", false)
    fun setTakesDelivery(c: Context, on: Boolean) = p(c).edit().putBoolean("takes_delivery_v2", on).apply()

    /** Minimal trip length in km (0 = any). */
    fun minTripKm(c: Context): Int = p(c).getInt("min_trip_km", 0)
    fun setMinTripKm(c: Context, km: Int) = p(c).edit().putInt("min_trip_km", km).apply()

    /** Deaf or hard of hearing driver: passengers write instead of calling, new orders flash the screen. */
    fun deaf(c: Context): Boolean = p(c).getBoolean("deaf", false)
    fun setDeaf(c: Context, on: Boolean) = p(c).edit().putBoolean("deaf", on).apply()

    /** Offers accepted / skipped, for the acceptance rate. */
    fun countOffer(c: Context, accepted: Boolean) {
        val key = if (accepted) "offers_accepted" else "offers_skipped"
        p(c).edit().putInt(key, p(c).getInt(key, 0) + 1).apply()
    }
    fun acceptanceRate(c: Context): Int? {
        val a = p(c).getInt("offers_accepted", 0)
        val s = p(c).getInt("offers_skipped", 0)
        return if (a + s == 0) null else a * 100 / (a + s)
    }

    fun voice(c: Context): Boolean = p(c).getBoolean("voice", true)
    fun setVoice(c: Context, on: Boolean) = p(c).edit().putBoolean("voice", on).apply()

    fun nightMap(c: Context): Boolean = p(c).getBoolean("night_map", true)
    fun setNightMap(c: Context, on: Boolean) = p(c).edit().putBoolean("night_map", on).apply()

    fun home(c: Context): Pair<GeoPoint, String>? {
        val pr = p(c)
        if (!pr.contains("home_lat")) return null
        return GeoPoint(pr.getFloat("home_lat", 0f).toDouble(), pr.getFloat("home_lng", 0f).toDouble()) to
            pr.getString("home_addr", "").orEmpty()
    }

    fun setHome(c: Context, point: GeoPoint, address: String) = p(c).edit()
        .putFloat("home_lat", point.lat.toFloat()).putFloat("home_lng", point.lng.toFloat())
        .putString("home_addr", address).apply()

    /** How many times "Домой" can still be turned on today. */
    fun homeUsesLeft(c: Context): Int {
        val pr = p(c)
        val today = dayKey()
        val used = if (pr.getInt("home_day", 0) == today) pr.getInt("home_used", 0) else 0
        return (HOME_USES_PER_DAY - used).coerceAtLeast(0)
    }

    fun useHome(c: Context) {
        val pr = p(c)
        val today = dayKey()
        val used = if (pr.getInt("home_day", 0) == today) pr.getInt("home_used", 0) else 0
        pr.edit().putInt("home_day", today).putInt("home_used", used + 1).apply()
    }

    // --- time on the line ---

    fun startOnline(c: Context, now: Long = System.currentTimeMillis()) {
        p(c).edit().putLong("online_since", now).apply()
    }

    fun stopOnline(c: Context, now: Long = System.currentTimeMillis()) {
        val pr = p(c)
        val since = pr.getLong("online_since", 0L)
        if (since <= 0L) return
        val sessions = loadSessions(pr.getString("sessions", "").orEmpty()) + (since to now)
        pr.edit().putString("sessions", saveSessions(sessions, now)).putLong("online_since", 0L).apply()
        if (onlineMinutes24h(c, now) >= MAX_ONLINE_MIN) {
            pr.edit().putLong("rest_until", now + REST_MIN * 60_000L).apply()
        }
    }

    /** Minutes on the line in the last 24 hours, including the current session. */
    fun onlineMinutes24h(c: Context, now: Long = System.currentTimeMillis()): Int {
        val pr = p(c)
        val from = now - 24 * 3600_000L
        var ms = loadSessions(pr.getString("sessions", "").orEmpty())
            .sumOf { (s, e) -> (e - maxOf(s, from)).coerceAtLeast(0L) }
        val since = pr.getLong("online_since", 0L)
        if (since > 0L) ms += now - maxOf(since, from)
        return (ms / 60_000L).toInt()
    }

    /** Time until which the driver must rest, or null. */
    fun restUntil(c: Context, now: Long = System.currentTimeMillis()): Long? =
        p(c).getLong("rest_until", 0L).takeIf { it > now }

    private fun loadSessions(s: String): List<Pair<Long, Long>> =
        s.split(';').mapNotNull { part ->
            val a = part.split('-')
            if (a.size != 2) null else a[0].toLongOrNull()?.let { st -> a[1].toLongOrNull()?.let { en -> st to en } }
        }

    private fun saveSessions(list: List<Pair<Long, Long>>, now: Long): String =
        list.filter { it.second > now - 24 * 3600_000L }.joinToString(";") { "${it.first}-${it.second}" }

    private fun dayKey(): Int {
        val cal = java.util.Calendar.getInstance()
        return cal.get(java.util.Calendar.YEAR) * 1000 + cal.get(java.util.Calendar.DAY_OF_YEAR)
    }

    fun isNight(now: Long = System.currentTimeMillis()): Boolean {
        val h = java.util.Calendar.getInstance().apply { timeInMillis = now }.get(java.util.Calendar.HOUR_OF_DAY)
        return h >= 20 || h < 6
    }
}

/** Speaks short phrases for the driver ("Новый заказ…"). */
object VoiceAnnouncer {
    private var tts: TextToSpeech? = null
    private var ready = false

    fun speak(context: Context, text: String) {
        val engine = tts
        if (engine != null && ready) {
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "kg_${System.currentTimeMillis()}")
            return
        }
        if (engine != null) return
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("ru", "RU")
                ready = true
                tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "kg_first")
            }
        }
    }
}
