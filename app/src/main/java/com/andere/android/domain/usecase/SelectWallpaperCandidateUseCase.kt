package com.andere.android.domain.usecase

import com.andere.android.domain.model.ImageCandidate
import com.andere.android.domain.model.ImageQuality
import com.andere.android.domain.model.Post
import kotlin.random.Random

class SelectWallpaperCandidateUseCase {
    fun select(posts: List<Post>, quality: ImageQuality): Pair<Post, ImageCandidate> {
        require(posts.isNotEmpty()) { "当前搜索条件和筛选条件下没有可用图片。" }

        val post = posts[Random.nextInt(posts.size)]
        return post to candidateFor(post, quality)
    }

    fun candidateFor(post: Post, quality: ImageQuality): ImageCandidate {
        val ordered = when (quality) {
            ImageQuality.Original -> listOf(
                ImageCandidateOrNull(post.fileUrl, post.width, post.height),
                ImageCandidateOrNull(post.jpegUrl, post.jpegWidth, post.jpegHeight),
                ImageCandidateOrNull(post.sampleUrl, post.sampleWidth, post.sampleHeight),
                ImageCandidateOrNull(post.previewUrl, post.previewWidth, post.previewHeight),
            )

            ImageQuality.High -> listOf(
                ImageCandidateOrNull(post.jpegUrl, post.jpegWidth, post.jpegHeight),
                ImageCandidateOrNull(post.fileUrl, post.width, post.height),
                ImageCandidateOrNull(post.sampleUrl, post.sampleWidth, post.sampleHeight),
                ImageCandidateOrNull(post.previewUrl, post.previewWidth, post.previewHeight),
            )

            ImageQuality.Medium -> listOf(
                ImageCandidateOrNull(post.sampleUrl, post.sampleWidth, post.sampleHeight),
                ImageCandidateOrNull(post.jpegUrl, post.jpegWidth, post.jpegHeight),
                ImageCandidateOrNull(post.fileUrl, post.width, post.height),
                ImageCandidateOrNull(post.previewUrl, post.previewWidth, post.previewHeight),
            )
        }

        return ordered.firstOrNull()
            ?: error("找不到可用的图片地址。")
    }

    private fun ImageCandidateOrNull(url: String?, width: Int, height: Int): ImageCandidate? {
        if (url.isNullOrBlank() || width <= 0 || height <= 0) return null
        return ImageCandidate(url = url, width = width, height = height)
    }
}
