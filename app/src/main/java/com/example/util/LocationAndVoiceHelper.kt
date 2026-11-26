package com.example.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.location.Geocoder
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.speech.RecognizerIntent
import com.example.data.model.GeoPoint
import com.example.data.model.KgCity
import com.example.data.model.KgGeoCatalog
import com.example.data.model.KgSettlements
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Locale

data class GpsLookupResult(
    val point: GeoPoint,
    val address: String,
    val matchedCityName: String,
    val fromRealGps: Boolean
)

object LocationAndVoiceHelper {

    fun isNetworkAvailable(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
        val active = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(active) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    @SuppressLint("MissingPermission")
    suspend fun fetchCurrentLocation(
        context: Context,
        activeCity: KgCity
    ): GpsLookupResult = withContext(Dispatchers.IO) {
        val fused = LocationServices.getFusedLocationProviderClient(context)
        val loc = try {
            val tokenSource = CancellationTokenSource()
            fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, tokenSource.token).await()
                ?: fused.lastLocation.await()
        } catch (_: Exception) {
            null
        }

        if (loc != null) {
            val rawPoint = GeoPoint(loc.latitude, loc.longitude)
            // Check if GPS coordinate is inside Kyrgyzstan bounding box (lat 39.1..43.3, lng 69.2..80.3)
            val insideKg = rawPoint.lat in 39.1..43.3 && rawPoint.lng in 69.2..80.3
            // The nearest settlement of Kyrgyzstan (city, district centre, urban-type settlement…).
            val targetCity = if (insideKg) {
                KgSettlements.nearest(rawPoint)?.let { KgSettlements.cityFor(it) } ?: activeCity
            } else {
                activeCity
            }
            val effectivePoint = if (insideKg) rawPoint else activeCity.center
            val address = reverseGeocode(context, targetCity, effectivePoint)
            GpsLookupResult(
                point = effectivePoint,
                address = address,
                matchedCityName = targetCity.name,
                fromRealGps = insideKg
            )
        } else {
            val fallbackPoint = activeCity.center
            GpsLookupResult(
                point = fallbackPoint,
                address = KgGeoCatalog.reverseGeocodeFallback(activeCity, fallbackPoint),
                matchedCityName = activeCity.name,
                fromRealGps = false
            )
        }
    }

    @Suppress("DEPRECATION")
    suspend fun reverseGeocode(context: Context, city: KgCity, point: GeoPoint): String =
        withContext(Dispatchers.IO) {
            try {
                if (Geocoder.isPresent()) {
                    val geocoder = Geocoder(context, Locale("ru", "KG"))
                    val results = geocoder.getFromLocation(point.lat, point.lng, 1)
                    val first = results?.firstOrNull()
                    val thoroughfare = first?.thoroughfare
                    val feature = first?.subThoroughfare ?: first?.featureName
                    if (!thoroughfare.isNullOrBlank()) {
                        return@withContext if (!feature.isNullOrBlank() && feature != thoroughfare) {
                            "$thoroughfare, $feature, ${city.name}"
                        } else {
                            "$thoroughfare, ${city.name}"
                        }
                    }
                }
            } catch (_: Exception) {
                // Fallback to curated Kyrgyzstan street model below
            }
            KgGeoCatalog.reverseGeocodeFallback(city, point)
        }

    fun buildVoiceRecognizerIntent(langCode: String, prompt: String): Intent {
        val locale = when (langCode.uppercase()) {
            "EN" -> "en-US"
            "KG" -> "ky-KG"
            else -> "ru-RU"
        }
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale)
            putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
        }
    }
}
