package com.andere.android

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.andere.android.domain.model.BackgroundTarget
import com.andere.android.system.TagSyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class AndereApplication : Application(), ImageLoaderFactory {
    lateinit var appContainer: AppContainer
        private set

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        appContainer = AppContainer(this)
        applicationScope.launch {
            val repo = appContainer.preferencesRepository
            val scheduler = appContainer.wallpaperScheduler

            appContainer.yandeDns.enabled = repo.useBuiltInHosts.first()

            scheduler.syncSchedule(repo.wallpaperConfig.first(), BackgroundTarget.Wallpaper)
            scheduler.syncSchedule(repo.lockscreenConfig.first(), BackgroundTarget.LockScreen)

            syncTagSchedule(repo.autoSyncTags.first())

            repo.useBuiltInHosts.collect { appContainer.yandeDns.enabled = it }
        }
    }

    fun syncTagSchedule(enabled: Boolean) {
        val wm = WorkManager.getInstance(this)
        if (!enabled) {
            wm.cancelUniqueWork(TagSyncWorker.UNIQUE_WORK_NAME)
            return
        }
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<TagSyncWorker>(
                1, TimeUnit.HOURS,
                5, TimeUnit.MINUTES,
            )
            .setConstraints(constraints)
            .build()
        wm.enqueueUniquePeriodicWork(
            TagSyncWorker.UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    override fun newImageLoader(): ImageLoader {
        val imageClient = okhttp3.OkHttpClient.Builder()
            .dns(appContainer.yandeDns)
            .addInterceptor(com.andere.android.data.remote.YandeRequestInterceptor("https://yande.re"))
            .build()
        return ImageLoader.Builder(this)
            .okHttpClient(imageClient)
            .crossfade(true)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(128L * 1024 * 1024)
                    .build()
            }
            .build()
    }
}
