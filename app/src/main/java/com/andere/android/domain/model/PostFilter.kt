package com.andere.android.domain.model

import java.time.LocalDate
import java.time.format.DateTimeFormatter

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
    val sortOrder: Int = 0,   // 0=按时间, 1=按评分
    val timeRange: Int = 0,   // 0=不限, 1=今天, 2=本周, 3=本月, 4=今年
) {
    fun buildMetaTags(): String {
        val parts = mutableListOf<String>()
        if (sortOrder == 1) parts.add("order:score")
        val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        when (timeRange) {
            1 -> parts.add("date:>=${LocalDate.now().format(fmt)}")
            2 -> parts.add("date:>=${LocalDate.now().minusDays(7).format(fmt)}")
            3 -> parts.add("date:>=${LocalDate.now().minusDays(30).format(fmt)}")
            4 -> parts.add("date:>=${LocalDate.now().minusDays(365).format(fmt)}")
        }
        return parts.joinToString(" ")
    }

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
