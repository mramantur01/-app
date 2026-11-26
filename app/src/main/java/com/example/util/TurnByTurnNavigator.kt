package com.example.util

import com.example.data.model.GeoPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/** One manoeuvre of the route: where it is and what to do there. */
data class NavStep(
    val type: String,
    val modifier: String,
    val street: String,
    val location: GeoPoint,
    val exit: Int = 0
)

/** Route for driving with turns: the line on the map and the list of manoeuvres. */
data class NavRoute(
    val line: List<GeoPoint>,
    val steps: List<NavStep>,
    val distanceM: Double,
    val durationS: Double,
    val target: GeoPoint
)

/** What the driver sees and hears now. */
data class NavGuidance(
    val instruction: String,
    val street: String,
    val distanceM: Int,
    val modifier: String,
    val type: String,
    val remainingKm: Double,
    val remainingMin: Int,
    val arrived: Boolean
)

/**
 * Free turn-by-turn navigation for the driver: OSRM (OpenStreetMap roads) gives the route with
 * manoeuvres, the phone GPS tells how far the next turn is, and the phrase is spoken in Russian.
 */
object TurnByTurnNavigator {

    /** Exact straight-line distance in metres (the app-wide helper rounds for prices and is too coarse here). */
    fun meters(a: GeoPoint, b: GeoPoint): Double {
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLng = Math.toRadians(b.lng - a.lng)
        val h = kotlin.math.sin(dLat / 2).let { it * it } +
            kotlin.math.cos(Math.toRadians(a.lat)) * kotlin.math.cos(Math.toRadians(b.lat)) * kotlin.math.sin(dLng / 2).let { it * it }
        return 2 * 6_371_000.0 * kotlin.math.asin(kotlin.math.sqrt(h.coerceIn(0.0, 1.0)))
    }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(8, TimeUnit.SECONDS).build()
    }

    suspend fun fetchRoute(from: GeoPoint, to: GeoPoint): NavRoute? = withContext(Dispatchers.IO) {
        try {
            val url = "https://router.project-osrm.org/route/v1/driving/" +
                "${from.lng},${from.lat};${to.lng},${to.lat}?overview=full&geometries=geojson&steps=true"
            val req = Request.Builder().url(url).header("User-Agent", "KGTaxiAndroid/1.0").build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                parse(resp.body?.string() ?: return@withContext null, to)
            }
        } catch (_: Exception) {
            null
        }
    }

    internal fun parse(body: String, target: GeoPoint): NavRoute? {
        val root = JSONObject(body)
        if (root.optString("code") != "Ok") return null
        val route = root.optJSONArray("routes")?.optJSONObject(0) ?: return null
        val coords = route.optJSONObject("geometry")?.optJSONArray("coordinates") ?: return null
        val line = (0 until coords.length()).mapNotNull { i ->
            val p = coords.optJSONArray(i) ?: return@mapNotNull null
            GeoPoint(p.optDouble(1), p.optDouble(0))
        }
        val steps = mutableListOf<NavStep>()
        val legs = route.optJSONArray("legs")
        for (l in 0 until (legs?.length() ?: 0)) {
            val arr = legs!!.optJSONObject(l).optJSONArray("steps") ?: continue
            for (i in 0 until arr.length()) {
                val st = arr.optJSONObject(i) ?: continue
                val m = st.optJSONObject("maneuver") ?: continue
                val loc = m.optJSONArray("location") ?: continue
                steps += NavStep(
                    type = m.optString("type"),
                    modifier = m.optString("modifier"),
                    street = st.optString("name"),
                    location = GeoPoint(loc.optDouble(1), loc.optDouble(0)),
                    exit = m.optInt("exit", 0)
                )
            }
        }
        if (line.size < 2) return null
        return NavRoute(line, steps, route.optDouble("distance"), route.optDouble("duration"), target)
    }

    /** Distance from the driver to the route line, in metres. */
    fun offRouteMeters(route: NavRoute, pos: GeoPoint): Double =
        route.line.minOf { meters(it, pos) }

    /** Index of the next manoeuvre ahead of the driver. */
    fun nextStepIndex(route: NavRoute, pos: GeoPoint, current: Int): Int {
        var idx = current.coerceIn(0, route.steps.lastIndex.coerceAtLeast(0))
        // Skip the start and the manoeuvres already passed (the driver is close to or beyond them).
        while (idx < route.steps.lastIndex) {
            val step = route.steps[idx]
            val d = meters(step.location, pos)
            val nextD = meters(route.steps[idx + 1].location, pos)
            if (step.type == "depart" || d < 25.0 || nextD < d - 5.0 && d < 120.0) idx++ else break
        }
        return idx
    }

    fun guidance(route: NavRoute, pos: GeoPoint, stepIdx: Int): NavGuidance {
        val toTargetM = meters(pos, route.target)
        val step = route.steps.getOrNull(stepIdx)
        val distM = step?.let { meters(it.location, pos) } ?: toTargetM
        // Remaining distance along the route: share of the route left, by the nearest point of the line.
        val nearest = route.line.indices.minByOrNull { meters(route.line[it], pos) } ?: 0
        var restKm = 0.0
        for (i in nearest until route.line.lastIndex) restKm += meters(route.line[i], route.line[i + 1]) / 1000.0
        val share = if (route.distanceM > 0) (restKm * 1000.0 / route.distanceM).coerceIn(0.0, 1.0) else 1.0
        // The passenger may stand off the road: the end of the road route counts as arriving too.
        val toRouteEndM = meters(pos, route.line.last())
        val arrived = toTargetM < 40.0 || toRouteEndM < 35.0
        return NavGuidance(
            instruction = if (arrived) "Вы на месте" else phrase(step),
            street = step?.street.orEmpty(),
            distanceM = distM.roundToInt(),
            modifier = step?.modifier.orEmpty(),
            type = step?.type.orEmpty(),
            remainingKm = ((restKm * 10).roundToInt() / 10.0),
            remainingMin = ((route.durationS * share) / 60.0).roundToInt().coerceAtLeast(if (arrived) 0 else 1),
            arrived = arrived
        )
    }

    fun phrase(step: NavStep?): String {
        if (step == null) return "Двигайтесь по маршруту"
        val dir = when (step.modifier) {
            "left" -> "налево"
            "right" -> "направо"
            "slight left" -> "плавно налево"
            "slight right" -> "плавно направо"
            "sharp left" -> "резко налево"
            "sharp right" -> "резко направо"
            "uturn" -> "на разворот"
            else -> "прямо"
        }
        return when (step.type) {
            "arrive" -> "Вы прибудете на место"
            "depart" -> "Начните движение"
            "roundabout", "rotary", "roundabout turn" ->
                if (step.exit > 0) "На круге — ${step.exit}-й съезд" else "Проезжайте круг"
            "fork" -> if (dir == "прямо") "На развилке держитесь прямо" else "На развилке держитесь $dir"
            "merge" -> "Перестройтесь $dir"
            "on ramp" -> "Съезд на дорогу $dir"
            "off ramp" -> "Съезд $dir"
            "continue", "new name" -> if (dir == "прямо") "Продолжайте прямо" else "Держитесь $dir"
            else -> when (step.modifier) {
                "uturn" -> "Развернитесь"
                "straight", "" -> "Продолжайте прямо"
                else -> "Поверните $dir"
            }
        }
    }

    fun distanceLabel(m: Int): String = when {
        m >= 1000 -> String.format(java.util.Locale("ru"), "%.1f км", m / 1000.0)
        m >= 100 -> "${(m / 50) * 50} м"
        else -> "${(m / 10).coerceAtLeast(1) * 10} м"
    }

    /** What to say: "Через 300 метров поверните направо на улицу Ленина". */
    fun spoken(g: NavGuidance, near: Boolean): String {
        if (g.arrived) return "Вы на месте"
        val street = g.street.takeIf { it.isNotBlank() && !g.instruction.startsWith("Вы") }?.let { " на $it" }.orEmpty()
        val action = g.instruction.replaceFirstChar { it.lowercase() }
        return if (near) g.instruction + street else "Через ${spokenDistance(g.distanceM)} $action$street"
    }

    private fun spokenDistance(m: Int): String = when {
        m >= 1000 -> String.format(java.util.Locale("ru"), "%.1f километра", m / 1000.0)
        else -> "${(m / 50).coerceAtLeast(1) * 50} метров"
    }
}
