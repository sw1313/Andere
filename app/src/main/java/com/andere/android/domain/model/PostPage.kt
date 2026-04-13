package com.andere.android.domain.model

data class PostPage(
    val posts: List<Post>,
    val totalCount: Int,
    val nextPage: Int?,
)

data class TagSuggestion(
    val name: String,
    val count: Int = 0,
    val type: Int = 0,
    val zhName: String? = null,
)
