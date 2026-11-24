package com.example.util

import java.util.Calendar
import java.util.Locale

/** "сегодня 18:00", "завтра 08:00" or "26.11 08:00" for an intercity departure. */
fun formatDeparture(millis: Long, now: Long = System.currentTimeMillis()): String {
    val c = Calendar.getInstance().apply { timeInMillis = millis }
    val today = Calendar.getInstance().apply { timeInMillis = now }
    val time = String.format(Locale.US, "%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
    return when (daysBetween(today, c)) {
        0 -> "сегодня $time"
        1 -> "завтра $time"
        2 -> "послезавтра $time"
        else -> String.format(
            Locale.US, "%02d.%02d %s",
            c.get(Calendar.DAY_OF_MONTH), c.get(Calendar.MONTH) + 1, time
        )
    }
}

/** Millis of the given day offset (0 = today) at hour:00. */
fun departureMillis(dayOffset: Int, hour: Int, now: Long = System.currentTimeMillis()): Long =
    Calendar.getInstance().apply {
        timeInMillis = now
        add(Calendar.DAY_OF_YEAR, dayOffset)
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

private fun daysBetween(a: Calendar, b: Calendar): Int {
    fun startOfDay(c: Calendar) = (c.clone() as Calendar).apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    return Math.round((startOfDay(b) - startOfDay(a)) / (24.0 * 60 * 60 * 1000)).toInt()
}
