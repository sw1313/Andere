package com.andere.android.domain.model

data class PostFilter(
    val allowSafe: Boolean = true,
    val allowQuestionable: Boolean = false,
    val allowExplicit: Boolean = false,
    val allowHorizontal: Boolean = true,
    val allowVertical: Boolean = false,
    val useScreenOrientation: Boolean = false,
    val allowHidden: Boolean = true,
    val allowHeld: Boolean = true,
    val tagBlacklist: String = "",
) {
    fun resolveOrientation(isLandscape: Boolean): PostFilter {
        if (!useScreenOrientation) return this
        return copy(
            allowHorizontal = isLandscape,
            allowVertical = !isLandscape,
            useScreenOrientation = false,
        )
    }

    fun matches(post: Post): Boolean {
        val ratingFilterActive = allowSafe || allowQuestionable || allowExplicit
        val ratingAllowed = when (post.rating) {
            Rating.Safe -> allowSafe
            Rating.Questionable -> allowQuestionable
            Rating.Explicit -> allowExplicit
        }
        val ratioAllowed = (post.width >= post.height && allowHorizontal) || (post.width < post.height && allowVertical)
        val blacklist = tagBlacklist
            .split(' ')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { it.lowercase() }
            .toSet()
        val blacklistAllowed = post.tagList().all { tag -> tag.lowercase() !in blacklist }

        return (!ratingFilterActive || ratingAllowed) &&
            ratioAllowed &&
            (post.isShownInIndex || allowHidden) &&
            (!post.isHeld || allowHeld) &&
            blacklistAllowed
    }
}
