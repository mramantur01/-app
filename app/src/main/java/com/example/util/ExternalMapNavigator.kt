package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.data.model.GeoPoint

object ExternalMapNavigator {

    /**
     * Opens real turn-by-turn navigation in Google Maps, 2GIS, or Yandex Navigator
     * (whichever map service is installed, falling back to browser Google Maps).
     */
    fun openTurnByTurnNavigation(
        context: Context,
        destination: GeoPoint,
        label: String,
        preferredProvider: String = "GOOGLE"
    ) {
        val encodedLabel = Uri.encode(label)
        val primaryUri = when (preferredProvider.uppercase()) {
            "2GIS" -> Uri.parse("dgis://2gis.ru/routeSearch/rsType/car/to/${destination.lng},${destination.lat}")
            "YANDEX" -> Uri.parse("yandexnavi://build_route_on_map?lat_to=${destination.lat}&lon_to=${destination.lng}")
            else -> Uri.parse("google.navigation:q=${destination.lat},${destination.lng}")
        }
        val primaryIntent = Intent(Intent.ACTION_VIEW, primaryUri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (primaryIntent.resolveActivity(context.packageManager) != null) {
            context.startActivity(primaryIntent)
            return
        }

        // Fallback to universal geo: intent or HTTPS Google Maps directions
        val geoIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("geo:${destination.lat},${destination.lng}?q=${destination.lat},${destination.lng}($encodedLabel)")
        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }

        if (geoIntent.resolveActivity(context.packageManager) != null) {
            context.startActivity(geoIntent)
        } else {
            val webIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://www.google.com/maps/dir/?api=1&destination=${destination.lat},${destination.lng}&travelmode=driving")
            ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            context.startActivity(webIntent)
        }
    }
}
