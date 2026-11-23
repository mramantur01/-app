package com.example.util

import com.example.data.model.GeoPoint
import com.example.data.model.KgGeoCatalog
import com.example.data.model.KgPlace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.roundToInt

data class RoadRouteResult(
    val polyline: List<GeoPoint>,
    val distanceKm: Double,
    val durationMin: Int,
    val fromLiveOsrm: Boolean
)

object KyrgyzstanRoutingClient {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Fetches real road-following route geometry, distance (km), and duration (min)
     * across Kyrgyzstan using OSRM (OpenStreetMap road network). Falls back smoothly
     * to a curved geodesic route when offline or unreachable.
     */
    suspend fun fetchRoadRoute(
        pickup: GeoPoint,
        dropoff: GeoPoint,
        offlineMode: Boolean,
        waypoints: List<GeoPoint> = emptyList()
    ): RoadRouteResult = withContext(Dispatchers.IO) {
        val stops = buildList {
            add(pickup)
            addAll(waypoints)
            add(dropoff)
        }
        if (!offlineMode) {
            try {
                val coordPath = stops.joinToString(";") { "${it.lng},${it.lat}" }
                val url = "https://router.project-osrm.org/route/v1/driving/$coordPath?overview=full&geometries=geojson"
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", "KGTaxiAndroid/1.0")
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string()
                        if (!body.isNullOrBlank()) {
                            parseOsrmResponse(body)?.let { return@withContext it }
                        }
                    }
                }
            } catch (_: Exception) {
                // Graceful fallback to offline route computation below
            }
        }
        buildOfflineFallbackRoute(pickup, dropoff, waypoints)
    }

    internal fun parseOsrmResponse(jsonBody: String): RoadRouteResult? {
        val distMatch = Regex(""""distance"\s*:\s*([0-9]+(?:\.[0-9]+)?)""").find(jsonBody) ?: return null
        val durMatch = Regex(""""duration"\s*:\s*([0-9]+(?:\.[0-9]+)?)""").find(jsonBody) ?: return null
        val coordsBlock = Regex(""""coordinates"\s*:\s*\[(.*?)\]\s*\]""", RegexOption.DOT_MATCHES_ALL)
            .find(jsonBody)?.groupValues?.getOrNull(1) ?: return null

        val pairRegex = Regex("""\[\s*(-?[0-9]+(?:\.[0-9]+)?)\s*,\s*(-?[0-9]+(?:\.[0-9]+)?)\s*\]""")
        val points = pairRegex.findAll("$coordsBlock]").mapNotNull { m ->
            val lng = m.groupValues[1].toDoubleOrNull() ?: return@mapNotNull null
            val lat = m.groupValues[2].toDoubleOrNull() ?: return@mapNotNull null
            GeoPoint(lat, lng)
        }.toList()

        if (points.size < 2) return null

        val distanceMeters = distMatch.groupValues[1].toDoubleOrNull() ?: return null
        val durationSec = durMatch.groupValues[1].toDoubleOrNull() ?: return null
        val km = max(0.3, ((distanceMeters / 1000.0) * 10.0).roundToInt() / 10.0)
        val min = max(3, (durationSec / 60.0).roundToInt())
        return RoadRouteResult(
            polyline = points,
            distanceKm = km,
            durationMin = min,
            fromLiveOsrm = true
        )
    }

    fun buildOfflineFallbackRoute(
        pickup: GeoPoint,
        dropoff: GeoPoint,
        waypoints: List<GeoPoint> = emptyList()
    ): RoadRouteResult {
        val stops = buildList {
            add(pickup)
            addAll(waypoints)
            add(dropoff)
        }
        var totalKm = 0.0
        val poly = mutableListOf<GeoPoint>()
        for (i in 0 until stops.lastIndex) {
            val a = stops[i]
            val b = stops[i + 1]
            totalKm += KgGeoCatalog.haversineKm(a, b)
            poly += a
            poly += GeoPoint((a.lat + b.lat) / 2.0 + 0.0014, (a.lng + b.lng) / 2.0 - 0.0012)
        }
        poly += stops.last()
        val roundedKm = max(0.8, (totalKm * 10.0).roundToInt() / 10.0)
        val dur = KgGeoCatalog.estimateDurationMin(roundedKm) + waypoints.size * 3
        return RoadRouteResult(
            polyline = poly,
            distanceKm = roundedKm,
            durationMin = dur,
            fromLiveOsrm = false
        )
    }

    /**
     * Live address autocomplete in Kyrgyzstan via OpenStreetMap Nominatim.
     */
    suspend fun searchKyrgyzstanAddresses(
        query: String,
        cityName: String,
        offlineMode: Boolean
    ): List<KgPlace> = withContext(Dispatchers.IO) {
        if (offlineMode || query.trim().length < 3) return@withContext emptyList()
        try {
            val encoded = URLEncoder.encode("$query, $cityName, Kyrgyzstan", "UTF-8")
            val url = "https://nominatim.openstreetmap.org/search?format=json&countrycodes=kg&limit=5&q=$encoded"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "KGTaxiAndroid/1.0")
                .header("Accept-Language", "ru,ky,en")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext emptyList()
                val body = resp.body?.string() ?: return@withContext emptyList()
                val arr = JSONArray(body)
                val results = mutableListOf<KgPlace>()
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    val lat = item.optString("lat").toDoubleOrNull() ?: continue
                    val lng = item.optString("lon").toDoubleOrNull() ?: continue
                    val displayName = item.optString("display_name").ifBlank { continue }
                    val shortName = displayName.substringBefore(",").trim()
                    val cleanAddr = displayName.split(",").take(3).joinToString(", ").trim()
                    results += KgPlace(
                        id = "nom_${item.optString("place_id", "$i")}",
                        name = shortName,
                        address = cleanAddr,
                        city = cityName,
                        point = GeoPoint(lat, lng),
                        category = "Адрес",
                        rating = 4.7,
                        reviewCount = 120,
                        priceLevel = 1
                    )
                }
                results
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
