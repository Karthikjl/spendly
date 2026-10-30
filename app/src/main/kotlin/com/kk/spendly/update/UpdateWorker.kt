package com.kk.spendly.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.kk.spendly.BuildConfig
import com.kk.spendly.R
import com.kk.spendly.ui.MainActivity
import java.util.concurrent.TimeUnit

/** Once a day, when online, checks for a newer release and posts one notification per new version. */
class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        // Test builds are a different app (and signed differently), so they must never offer to "update" to a release.
        if (BuildConfig.DEV_BUILD) return Result.success()
        val info = Updater.fetchLatest() ?: return Result.success()
        if (Updater.isNewer(info.version, Updater.installedVersion(applicationContext))) {
            UpdateNotifier.notifyAvailable(applicationContext, info)
        }
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "spendly-update-check"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<UpdateWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}

object UpdateNotifier {
    private const val CHANNEL = "app_updates"
    private const val NOTIFICATION_ID = 7001
    private const val PREFS = "updates"
    private const val KEY_NOTIFIED = "notified_version"

    /** Posts the "update available" notification once per version, and only if the system will show it. */
    fun notifyAvailable(context: Context, info: ReleaseInfo) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_NOTIFIED, null) == info.version) return
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "App updates", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Tells you when a new version of Spendly is available"
            },
        )
        val open = PendingIntent.getActivity(
            context, NOTIFICATION_ID,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val summary = info.highlight ?: "Tap to update."
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notif)
            .setColor(0xFF6C5CE7.toInt())
            .setContentTitle("Spendly ${info.version} is available")
            .setContentText(summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(info.notes.take(400).ifEmpty { "Tap to update." }))
            .addAction(0, "Update now", open)
            .setContentIntent(open)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            prefs.edit().putString(KEY_NOTIFIED, info.version).apply()
        } catch (_: SecurityException) {
            // Permission was revoked between the check and the post; try again on the next daily run.
        }
    }
}
