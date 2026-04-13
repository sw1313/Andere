package com.andere.android.domain.repository

import com.andere.android.domain.model.PostPage
import com.andere.android.domain.model.TagSuggestion

interface PostRepository {
    suspend fun searchPosts(query: String, page: Int): PostPage
    suspend fun searchTags(keyword: String): List<TagSuggestion>
}
