package com.andere.android.system

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.andere.android.domain.model.BackgroundTarget
import com.andere.android.domain.model.WallpaperRefreshConfig
import java.util.concurrent.TimeUnit

class WallpaperScheduler(
    private val context: Context,
) {
    fun syncSchedule(config: WallpaperRefreshConfig, target: BackgroundTarget) {
        val workManager = WorkManager.getInstance(context)
        val periodicName = periodicWorkName(target)
        val immediateName = immediateWorkName(target)

        if (target == BackgroundTarget.LockScreen && config.useWallpaperImage) {
            workManager.cancelUniqueWork(periodicName)
            workManager.cancelUniqueWork(immediateName)
            Log.d(TAG, "LockScreen refresh follows wallpaper, cancelled dedicated work")
            return
        }

        if (!config.enabled) {
            workManager.cancelUniqueWork(periodicName)
            workManager.cancelUniqueWork(immediateName)
            Log.d(TAG, "${target.name} refresh disabled, cancelled")
            return
        }

        val constraints = buildConstraints(config)
        val interval = config.intervalMinutes.coerceAtLeast(15)
        val data = workDataOf(KEY_TARGET to target.name)

        val periodicRequest = PeriodicWorkRequestBuilder<WallpaperRefreshWorker>(
                interval, TimeUnit.MINUTES,
                5, TimeUnit.MINUTES,
            )
            .setConstraints(constraints)
            .setInputData(data)
            .build()

        workManager.enqueueUniquePeriodicWork(
            periodicName,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodicRequest,
        )
        Log.d(TAG, "${target.name} periodic refresh scheduled every $interval min")
    }

    fun triggerImmediate(config: WallpaperRefreshConfig, target: BackgroundTarget) {
        if (target == BackgroundTarget.LockScreen && config.useWallpaperImage) return
        if (!config.enabled) return

        val constraints = buildConstraints(config)
        val data = workDataOf(KEY_TARGET to target.name)
        val immediateRequest = OneTimeWorkRequestBuilder<WallpaperRefreshWorker>()
            .setConstraints(constraints)
            .setInputData(data)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            immediateWorkName(target),
            ExistingWorkPolicy.REPLACE,
            immediateRequest,
        )
        Log.d(TAG, "${target.name} immediate refresh enqueued")
    }

    private fun buildConstraints(config: WallpaperRefreshConfig): Constraints =
        Constraints.Builder()
            .setRequiredNetworkType(if (config.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .build()

    companion object {
        private const val TAG = "WallpaperScheduler"
        const val KEY_TARGET = "target"

        fun periodicWorkName(target: BackgroundTarget): String =
            "periodic_${target.name.lowercase()}_refresh"

        fun immediateWorkName(target: BackgroundTarget): String =
            "immediate_${target.name.lowercase()}_refresh"
    }
}
