package com.andere.android.system

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.andere.android.AndereApplication
import com.andere.android.R
import com.andere.android.domain.model.BackgroundTarget
import com.andere.android.domain.model.WallpaperRefreshConfig
import kotlinx.coroutines.flow.first

class WallpaperRefreshWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val targetName = inputData.getString(WallpaperScheduler.KEY_TARGET) ?: BackgroundTarget.Wallpaper.name
        val target = runCatching { BackgroundTarget.valueOf(targetName) }.getOrDefault(BackgroundTarget.Wallpaper)
        Log.d(TAG, "doWork() target=$target, runAttemptCount=$runAttemptCount")

        val container = (applicationContext as? AndereApplication)?.appContainer
        if (container == null) {
            Log.e(TAG, "AppContainer not available")
            return Result.failure()
        }

        val config = when (target) {
            BackgroundTarget.Wallpaper -> container.preferencesRepository.wallpaperConfig.first()
            BackgroundTarget.LockScreen -> container.preferencesRepository.lockscreenConfig.first()
        }
        Log.d(TAG, "config: enabled=${config.enabled}, query='${config.query}', interval=${config.intervalMinutes}")

        if (!config.enabled) {
            Log.d(TAG, "$target refresh disabled, skipping")
            return Result.success()
        }

        cleanupOldRecords(container, config)

        return try {
            val result = container.wallpaperRefreshService.refresh(config, target)
            Log.d(TAG, "Refresh success, target=$target, postId=${result.post.id}")
            if (config.notificationEnabled) {
                showNotification(result.post.id, target)
            }
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Refresh failed (attempt $runAttemptCount): ${e.message}", e)
            if (runAttemptCount >= MAX_RETRIES) {
                Log.w(TAG, "Max retries reached, giving up")
                Result.failure()
            } else {
                Result.retry()
            }
        }
    }

    private suspend fun cleanupOldRecords(
        container: com.andere.android.AppContainer,
        config: WallpaperRefreshConfig,
    ) {
        val retentionMillis = config.recordRetentionDays.toLong() * 24 * 60 * 60 * 1000
        val cutoff = System.currentTimeMillis() - retentionMillis
        container.wallpaperRecordDao.deleteOlderThan(cutoff)
        Log.d(TAG, "Cleaned records older than ${config.recordRetentionDays} days")
    }

    private fun showNotification(postId: Long, target: BackgroundTarget) {
        val context = applicationContext
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "壁纸自动更换",
                NotificationManager.IMPORTANCE_LOW,
            )
            nm.createNotificationChannel(channel)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val perm = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            if (perm != PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "POST_NOTIFICATIONS not granted, skip notification")
                return
            }
        }

        val label = if (target == BackgroundTarget.Wallpaper) "壁纸" else "锁屏"
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("${label}已更换")
            .setContentText("已自动更换$label (ID: $postId)")
            .setAutoCancel(true)
            .build()

        val notifId = if (target == BackgroundTarget.Wallpaper) NOTIFICATION_ID_WP else NOTIFICATION_ID_LS
        NotificationManagerCompat.from(context).notify(notifId, notification)
    }

    companion object {
        private const val TAG = "WallpaperRefreshWorker"
        private const val MAX_RETRIES = 3
        private const val CHANNEL_ID = "wallpaper_refresh"
        private const val NOTIFICATION_ID_WP = 1001
        private const val NOTIFICATION_ID_LS = 1002
    }
}
