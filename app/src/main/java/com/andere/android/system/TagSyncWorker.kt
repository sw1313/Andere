package com.andere.android.system

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.andere.android.AndereApplication
import kotlinx.coroutines.flow.first

class TagSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as AndereApplication).appContainer
        return try {
            val pat = container.preferencesRepository.githubPat.first()
            if (pat.isNotBlank()) {
                val msg = container.tagSyncService.uploadThenDownload(pat)
                Log.d(TAG, "Tag sync (upload+download): $msg")
            } else {
                val count = container.tagSyncService.download()
                Log.d(TAG, "Tag sync (download only): $count entries")
            }
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Tag sync failed", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "TagSyncWorker"
        const val UNIQUE_WORK_NAME = "tag_sync_periodic"
    }
}
