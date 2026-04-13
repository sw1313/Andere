package com.andere.android.domain.usecase

import com.andere.android.domain.model.Post
import com.andere.android.domain.model.PostFilter
import com.andere.android.domain.repository.PostRepository

class SearchPostsUseCase(
    private val repository: PostRepository,
    private val filterPosts: FilterPostsUseCase,
) {
    suspend operator fun invoke(
        query: String,
        filter: PostFilter,
        page: Int,
    ): SearchResult {
        val response = repository.searchPosts(query = query, page = page)
        val filtered = filterPosts(response.posts, filter)
        return SearchResult(
            visiblePosts = filtered,
            fetchedPosts = response.posts,
            totalCount = response.totalCount,
            nextPage = response.nextPage,
        )
    }
}

data class SearchResult(
    val visiblePosts: List<Post>,
    val fetchedPosts: List<Post>,
    val totalCount: Int,
    val nextPage: Int?,
)
