package com.example.util

import android.content.Context

/**
 * Passenger's close people (family and trusted contacts), kept on the phone.
 * [shareTrips]: the app offers to send them every trip (where, who drives, the route).
 */
data class ClosePerson(val name: String, val phone: String, val shareTrips: Boolean)

object PassengerPeople {
    private const val PREFS = "passenger_people"
    private const val KEY = "people"
    const val MAX = 5

    fun load(c: Context): List<ClosePerson> =
        c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "").orEmpty()
            .split('\n').mapNotNull { line ->
                val p = line.split('\t')
                if (p.size != 3 || p[1].isBlank()) null else ClosePerson(p[0], p[1], p[2] == "1")
            }

    fun save(c: Context, list: List<ClosePerson>) {
        val text = list.take(MAX).joinToString("\n") { p ->
            listOf(p.name.replace('\t', ' ').replace('\n', ' '), p.phone.replace('\t', ' ').replace('\n', ' '), if (p.shareTrips) "1" else "0")
                .joinToString("\t")
        }
        c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, text).apply()
    }
}

/** Special needs a passenger can tell the driver about. */
val ACCESSIBILITY_OPTIONS = listOf(
    "wheelchair" to "Инвалидное кресло",
    "guide_dog" to "Собака-поводырь",
    "hearing" to "Плохо слышу — пишите в чат",
    "vision" to "Плохо вижу — помогите найти машину",
    "child" to "Еду с ребёнком"
)
