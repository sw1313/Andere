package com.andere.android

import android.content.Context
import com.andere.android.data.local.AppDatabase
import com.andere.android.data.local.TagSuggestionService
import com.andere.android.data.local.TagTranslationRepository
import com.andere.android.data.remote.YandeApiService
import com.andere.android.data.remote.YandeRequestInterceptor
import com.andere.android.data.remote.YandeXmlParser
import com.andere.android.data.repository.DefaultPostRepository
import com.andere.android.data.settings.AppPreferencesRepository
import com.andere.android.domain.repository.PostRepository
import com.andere.android.domain.usecase.FilterPostsUseCase
import com.andere.android.domain.usecase.SearchPostsUseCase
import com.andere.android.domain.usecase.SelectWallpaperCandidateUseCase
import com.andere.android.data.remote.TagSyncService
import com.andere.android.system.NetworkImageDownloader
import com.andere.android.system.ImageSaveService
import com.andere.android.system.WallpaperApplier
import com.andere.android.system.WallpaperImageProcessor
import com.andere.android.system.WallpaperRefreshService
import com.andere.android.system.WallpaperScheduler
import com.andere.android.data.remote.YandeDns
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor

class AppContainer(
    private val context: Context,
) {
    private val yandeHost = "https://yande.re"

    val yandeDns = YandeDns()

    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(yandeDns)
            .addInterceptor(YandeRequestInterceptor(yandeHost))
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
            .build()
    }

    private val xmlParser by lazy { YandeXmlParser() }
    private val apiService by lazy { YandeApiService(okHttpClient, xmlParser, yandeHost) }
    private val database by lazy { AppDatabase.build(context) }

    val preferencesRepository by lazy { AppPreferencesRepository(context) }
    val postRepository: PostRepository by lazy { DefaultPostRepository(apiService) }
    val filterPostsUseCase by lazy { FilterPostsUseCase() }
    val searchPostsUseCase by lazy { SearchPostsUseCase(postRepository, filterPostsUseCase) }
    val selectWallpaperCandidateUseCase by lazy { SelectWallpaperCandidateUseCase() }
    val imageDownloader by lazy { NetworkImageDownloader(okHttpClient) }
    val imageSaveService by lazy { ImageSaveService(context, imageDownloader) }
    val wallpaperImageProcessor by lazy { WallpaperImageProcessor() }
    val wallpaperApplier by lazy { WallpaperApplier(context) }
    val wallpaperScheduler by lazy { WallpaperScheduler(context) }
    val wallpaperRefreshService by lazy {
        WallpaperRefreshService(
            context = context,
            preferencesRepository = preferencesRepository,
            postRepository = postRepository,
            filterPostsUseCase = filterPostsUseCase,
            selectWallpaperCandidateUseCase = selectWallpaperCandidateUseCase,
            imageDownloader = imageDownloader,
            wallpaperImageProcessor = wallpaperImageProcessor,
            wallpaperApplier = wallpaperApplier,
            wallpaperRecordDao = database.wallpaperRecordDao(),
        )
    }

    val tagTranslationRepository by lazy { TagTranslationRepository(context) }
    val tagSyncService by lazy { TagSyncService(tagTranslationRepository) }
    val tagSuggestionService by lazy { TagSuggestionService(postRepository, tagTranslationRepository) }
    val wallpaperRecordDao get() = database.wallpaperRecordDao()
    val searchHistoryDao get() = database.searchHistoryDao()
    val favoritePostDao get() = database.favoritePostDao()
}
