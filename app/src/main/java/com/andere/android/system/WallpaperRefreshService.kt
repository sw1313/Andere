package com.andere.android.system

import android.content.Context
import com.andere.android.data.local.WallpaperRecordDao
import com.andere.android.data.local.WallpaperRecordEntity
import com.andere.android.data.settings.AppPreferencesRepository
import com.andere.android.domain.model.BackgroundTarget
import com.andere.android.domain.model.CropMode
import com.andere.android.domain.model.ImageQuality
import com.andere.android.domain.model.Post
import com.andere.android.domain.model.WallpaperRefreshConfig
import com.andere.android.domain.repository.PostRepository
import com.andere.android.domain.usecase.FilterPostsUseCase
import com.andere.android.domain.usecase.SelectWallpaperCandidateUseCase
import kotlinx.coroutines.flow.first

class WallpaperRefreshService(
    private val context: Context,
    private val preferencesRepository: AppPreferencesRepository,
    private val postRepository: PostRepository,
    private val filterPostsUseCase: FilterPostsUseCase,
    private val selectWallpaperCandidateUseCase: SelectWallpaperCandidateUseCase,
    private val imageDownloader: NetworkImageDownloader,
    private val wallpaperImageProcessor: WallpaperImageProcessor,
    private val wallpaperApplier: WallpaperApplier,
    private val wallpaperRecordDao: WallpaperRecordDao,
) {
    suspend fun refresh(config: WallpaperRefreshConfig, target: BackgroundTarget): WallpaperRefreshResult {
        val isLandscape = ScreenOrientationHelper.isCurrentLandscape(context)
        val resolvedFilter = config.filter.resolveOrientation(isLandscape)

        val metaTags = resolvedFilter.buildMetaTags()
        val finalQuery = if (metaTags.isEmpty()) config.query else "${config.query} $metaTags".trim()

        val retentionMillis = config.recordRetentionDays.toLong() * 24 * 60 * 60 * 1000
        val sinceMillis = System.currentTimeMillis() - retentionMillis
        val recentIds = wallpaperRecordDao.postIdsSinceByTarget(target.name, sinceMillis).toSet()

        val targetCount = config.shuffleCount.coerceAtLeast(1)
        val candidates = mutableListOf<Post>()
        val seenIds = mutableSetOf<Long>()
        var freshCount = 0
        var page = 1
        var nextPage: Int? = 1

        while (nextPage != null && freshCount < targetCount) {
            val postPage = postRepository.searchPosts(finalQuery, page)
            val filtered = filterPostsUseCase(postPage.posts, resolvedFilter)
            for (incoming in filtered) {
                if (incoming.id in seenIds) continue
                seenIds.add(incoming.id)
                candidates.add(incoming)
                if (incoming.id !in recentIds) freshCount++
            }
            if (freshCount >= targetCount) break
            nextPage = postPage.nextPage
            page = nextPage ?: page
        }

        val preferredCandidates = candidates.filterNot { it.id in recentIds }
        val selectionPool = if (preferredCandidates.isNotEmpty()) preferredCandidates else candidates

        val (post, _) = selectWallpaperCandidateUseCase.select(
            posts = selectionPool,
            quality = config.quality,
        )
        return applyPost(post, config.quality, config.cropMode, target)
    }

    suspend fun applyPost(
        post: Post,
        quality: ImageQuality,
        cropMode: CropMode,
        target: BackgroundTarget,
    ): WallpaperRefreshResult {
        val imageCandidate = selectWallpaperCandidateUseCase.candidateFor(post, quality)
        val bytes = imageDownloader.downloadBytes(imageCandidate.url)
        val metrics = context.resources.displayMetrics
        val aspectRatio = metrics.widthPixels.toFloat() / metrics.heightPixels.toFloat()
        val bitmap = wallpaperImageProcessor.decodeAndCrop(bytes, cropMode, aspectRatio)
        val applyToLockScreen = target == BackgroundTarget.Wallpaper &&
            wallpaperApplier.isLockscreenSupported() &&
            preferencesRepository.lockscreenConfig.first().useWallpaperImage
        wallpaperApplier.apply(bitmap, target, alsoApplyToLockScreen = applyToLockScreen)

        val createdAtMillis = System.currentTimeMillis()
        val record = WallpaperRecordEntity(
            postId = post.id,
            previewUrl = post.browseThumbnailUrl,
            createdAtMillis = createdAtMillis,
            target = target.name,
        )
        wallpaperRecordDao.insert(record)
        if (applyToLockScreen) {
            wallpaperRecordDao.insert(
                WallpaperRecordEntity(
                    postId = post.id,
                    previewUrl = post.browseThumbnailUrl,
                    createdAtMillis = createdAtMillis,
                    target = BackgroundTarget.LockScreen.name,
                ),
            )
        }

        return WallpaperRefreshResult(post = post, record = record)
    }

    fun isLockscreenSupported(): Boolean = wallpaperApplier.isLockscreenSupported()
}

data class WallpaperRefreshResult(
    val post: Post,
    val record: WallpaperRecordEntity,
)
