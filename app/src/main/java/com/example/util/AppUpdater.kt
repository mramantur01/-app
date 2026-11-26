package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * Updates for installs from the website: downloads the new APK inside the app and opens
 * the Android installer ("Обновить" → one tap). Data stays, the app is replaced in place.
 */
object AppUpdater {
    private const val WORK_NAME = "kgtaxi_update_check"

    /** Android 8+: the user must allow KG Taxi to install apps once. */
    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()

    fun openInstallPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }

    /** Downloads the APK; [onProgress] gets 0..100. Returns the file or null on error. */
    suspend fun download(context: Context, info: UpdateInfo, onProgress: (Int) -> Unit): File? = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "KG-Taxi-${info.versionCode}.apk")
        try {
            var url = URL(info.url)
            var conn: HttpURLConnection
            var redirects = 0
            while (true) {
                conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 30000
                    instanceFollowRedirects = true
                }
                val code = conn.responseCode
                if (code in 300..399 && redirects < 5) {
                    url = URL(url, conn.getHeaderField("Location"))
                    redirects++
                    continue
                }
                if (code != 200) return@withContext null
                break
            }
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: -1L
            var done = 0L
            var last = -1
            conn.inputStream.use { input ->
                file.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0) {
                            val p = (done * 100 / total).toInt()
                            if (p != last) { last = p; onProgress(p) }
                        }
                    }
                }
            }
            // A broken download is smaller than the real APK (about 25 MB).
            if (total > 0 && done != total) return@withContext null
            if (done < 1_000_000) return@withContext null
            file
        } catch (_: Exception) {
            file.delete()
            null
        }
    }

    /** Opens the Android installer for the downloaded APK. */
    fun install(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /** Checks the website once a day and shows a notification when a new version is out. */
    fun scheduleDailyCheck(context: Context) {
        val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(1, TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}

class UpdateCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val info = UpdateChecker.check(applicationContext) ?: return Result.success()
        val prefs = applicationContext.getSharedPreferences("updates", Context.MODE_PRIVATE)
        if (prefs.getInt("notified", 0) == info.versionCode) return Result.success()
        SoundAndNotificationManager.postNotification(
            applicationContext,
            "Новая версия KG Taxi ${info.versionName}",
            "Откройте приложение и нажмите «Обновить». ${info.notes}".trim(),
            isHighPriority = false
        )
        prefs.edit().putInt("notified", info.versionCode).apply()
        return Result.success()
    }
}
