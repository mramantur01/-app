package com.example.util

import android.content.Context
import android.os.Build
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(val versionCode: Int, val versionName: String, val url: String, val notes: String)

/**
 * For installs from the website (not Google Play): reads version.json from the site and
 * tells if a newer APK is there. Google Play installs update through Play, so they are skipped.
 */
object UpdateChecker {
    const val VERSION_URL = "https://mramantur-site.vercel.app/version.json"
    const val SITE_URL = "https://mramantur-site.vercel.app/"

    private fun installedFromPlay(context: Context): Boolean = try {
        val installer = if (Build.VERSION.SDK_INT >= 30) {
            context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getInstallerPackageName(context.packageName)
        }
        installer == "com.android.vending"
    } catch (_: Exception) {
        false
    }

    suspend fun check(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        if (installedFromPlay(context)) return@withContext null
        try {
            val conn = (URL(VERSION_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 6000
                readTimeout = 6000
                useCaches = false
            }
            if (conn.responseCode != 200) return@withContext null
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val code = json.optInt("versionCode", 0)
            if (code <= BuildConfig.VERSION_CODE) return@withContext null
            UpdateInfo(
                versionCode = code,
                versionName = json.optString("versionName", ""),
                url = json.optString("url", SITE_URL),
                notes = json.optString("notes", "")
            )
        } catch (_: Exception) {
            null
        }
    }
}
