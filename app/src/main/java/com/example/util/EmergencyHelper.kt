package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.example.data.model.GeoPoint
import java.util.Locale

/**
 * Real emergency actions for the SOS button.
 *
 * - Opens the phone dialer with the emergency number pre-filled (no CALL_PHONE permission needed;
 *   the user presses the green button themselves, so a pocket tap never calls by accident).
 * - Opens the SMS app with the user's location for their trusted contacts.
 *
 * 112 is the single emergency number in Kyrgyzstan and works from any mobile phone,
 * also without a SIM card. 102 is the police.
 */
object EmergencyHelper {
    const val EMERGENCY_NUMBER = "112"
    const val POLICE_NUMBER = "102"
    private const val TAG = "EmergencyHelper"

    fun mapsLink(point: GeoPoint): String =
        String.format(Locale.US, "https://maps.google.com/?q=%.6f,%.6f", point.lat, point.lng)

    fun buildSosMessage(
        passengerName: String,
        point: GeoPoint,
        address: String,
        carInfo: String?,
        driverName: String?
    ): String = buildString {
        append("SOS! ")
        append(passengerName.ifBlank { "Пассажир KG Taxi" })
        append(" нуждается в помощи.\n")
        append("Где: ").append(address).append('\n')
        append(mapsLink(point)).append('\n')
        if (!carInfo.isNullOrBlank()) append("Машина: ").append(carInfo).append('\n')
        if (!driverName.isNullOrBlank()) append("Водитель: ").append(driverName).append('\n')
        append("Экстренная служба: ").append(EMERGENCY_NUMBER)
    }

    /** Opens the dialer with [number] filled in. Returns false if no dialer app exists. */
    fun openDialer(context: Context, number: String = EMERGENCY_NUMBER): Boolean = try {
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    } catch (e: Exception) {
        Log.w(TAG, "No dialer available", e)
        false
    }

    /**
     * Opens the SMS app addressed to [phones] with [message] filled in.
     * Returns false if there is nobody to send to or no SMS app.
     */
    fun openSmsToContacts(context: Context, phones: List<String>, message: String): Boolean {
        val cleaned = phones
            .map { it.filter { ch -> ch.isDigit() || ch == '+' } }
            .filter { it.length >= 6 }
            .distinct()
        if (cleaned.isEmpty()) return false
        return try {
            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + cleaned.joinToString(";")))
                .putExtra("sms_body", message)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.w(TAG, "No SMS app available", e)
            false
        }
    }
}
