package com.andere.android.data.repository

import com.andere.android.data.remote.YandeApiService
import com.andere.android.domain.model.PostPage
import com.andere.android.domain.model.TagSuggestion
import com.andere.android.domain.repository.PostRepository

class DefaultPostRepository(
    private val apiService: YandeApiService,
) : PostRepository {
    override suspend fun searchPosts(query: String, page: Int): PostPage {
        return apiService.searchPosts(query = query, page = page)
    }

    override suspend fun searchTags(keyword: String): List<TagSuggestion> {
        return apiService.searchTags(keyword)
    }
}
