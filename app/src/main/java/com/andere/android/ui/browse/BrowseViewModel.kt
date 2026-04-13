package com.andere.android.ui.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.andere.android.data.local.SearchHistoryDao
import com.andere.android.data.local.SearchHistoryEntity
import com.andere.android.data.settings.AppPreferencesRepository
import com.andere.android.domain.model.BackgroundTarget
import com.andere.android.domain.model.BrowseImageSize
import com.andere.android.domain.model.Post
import com.andere.android.domain.model.PostFilter
import com.andere.android.domain.repository.PostRepository
import com.andere.android.domain.usecase.SearchPostsUseCase
import com.andere.android.system.WallpaperRefreshService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class BrowseViewModel(
    private val searchPostsUseCase: SearchPostsUseCase,
    private val postRepository: PostRepository,
    private val searchHistoryDao: SearchHistoryDao,
    private val preferencesRepository: AppPreferencesRepository,
    private val wallpaperRefreshService: WallpaperRefreshService,
) : ViewModel() {
    private val minVisiblePostsForInitialLoad = 12
    private val minVisiblePostsPerAppend = 6

    private val _uiState = MutableStateFlow(BrowseUiState())
    val uiState: StateFlow<BrowseUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val savedFilter = preferencesRepository.browseFilter.first()
            _uiState.update { it.copy(filter = savedFilter) }
            loadPage(reset = true)
        }
        viewModelScope.launch {
            preferencesRepository.browseImageSize.collect { size ->
                _uiState.update { it.copy(imageSize = size) }
            }
        }
    }

    fun updateQuery(query: String) {
        _uiState.update { it.copy(query = query) }
    }

    fun submitSearch() {
        val query = uiState.value.query.trim()

        if (query.isNotBlank()) {
            viewModelScope.launch {
                searchHistoryDao.upsert(SearchHistoryEntity(query = query, updatedAtMillis = System.currentTimeMillis()))
            }
        }
        loadPage(reset = true)
    }

    fun searchWithQuery(query: String) {
        _uiState.update { it.copy(query = query) }
        submitSearch()
    }

    fun loadNextPage() {
        if (uiState.value.isLoading || uiState.value.nextPage == null) return
        loadPage(reset = false)
    }

    fun updateFilter(filter: PostFilter) {
        _uiState.update { it.copy(filter = filter) }
        viewModelScope.launch {
            preferencesRepository.updateBrowseFilter { filter }
        }
        loadPage(reset = true)
    }

    fun applyPost(post: Post, target: BackgroundTarget) {
        viewModelScope.launch {
            _uiState.update { it.copy(isApplyingWallpaper = true, errorMessage = null) }
            runCatching {
                val config = preferencesRepository.wallpaperConfig.first()
                wallpaperRefreshService.applyPost(
                    post = post,
                    quality = config.quality,
                    cropMode = config.cropMode,
                    target = target,
                )
            }.onSuccess {
                _uiState.update { state ->
                    state.copy(
                        isApplyingWallpaper = false,
                        lastAppliedPostId = it.post.id,
                    )
                }
            }.onFailure { error ->
                _uiState.update { state ->
                    state.copy(
                        isApplyingWallpaper = false,
                        errorMessage = error.message,
                    )
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun findPost(postId: Long?): Post? = postId?.let { id ->
        uiState.value.posts.firstOrNull { it.id == id }
    }

    fun ensurePostInList(post: Post) {
        if (uiState.value.posts.none { it.id == post.id }) {
            _uiState.update { it.copy(posts = it.posts + post) }
        }
    }

    suspend fun fetchPostById(postId: Long): Post? {
        return postRepository.searchPosts("id:$postId", 1).posts.firstOrNull()
    }

    private fun loadPage(reset: Boolean) {
        val state = uiState.value
        val query = state.query.trim()
        val page = if (reset) 1 else (state.nextPage ?: return)

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching {
                var nextPageToLoad: Int? = page
                var totalCount = state.totalCount
                var mergedPosts = if (reset) emptyList() else state.posts
                val targetVisibleCount = if (reset) {
                    minVisiblePostsForInitialLoad
                } else {
                    state.posts.size + minVisiblePostsPerAppend
                }
                var safetyCounter = 0

                while (nextPageToLoad != null && mergedPosts.size < targetVisibleCount && safetyCounter < 8) {
                    val result = searchPostsUseCase(
                        query = query,
                        filter = uiState.value.filter,
                        page = nextPageToLoad,
                    )
                    totalCount = result.totalCount
                    mergedPosts = (mergedPosts + result.visiblePosts).distinctBy { it.id }
                    nextPageToLoad = result.nextPage
                    safetyCounter++
                }

                LoadedBrowsePage(
                    posts = mergedPosts,
                    nextPage = nextPageToLoad,
                    totalCount = totalCount,
                )
            }.onSuccess { result ->
                _uiState.update { current ->
                    current.copy(
                        submittedQuery = query,
                        posts = result.posts,
                        nextPage = result.nextPage,
                        totalCount = result.totalCount,
                        isLoading = false,
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "加载图片失败。",
                    )
                }
            }
        }
    }

    companion object {
        fun factory(
            searchPostsUseCase: SearchPostsUseCase,
            postRepository: PostRepository,
            searchHistoryDao: SearchHistoryDao,
            preferencesRepository: AppPreferencesRepository,
            wallpaperRefreshService: WallpaperRefreshService,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return BrowseViewModel(
                    searchPostsUseCase = searchPostsUseCase,
                    postRepository = postRepository,
                    searchHistoryDao = searchHistoryDao,
                    preferencesRepository = preferencesRepository,
                    wallpaperRefreshService = wallpaperRefreshService,
                ) as T
            }
        }
    }
}

private data class LoadedBrowsePage(
    val posts: List<Post>,
    val nextPage: Int?,
    val totalCount: Int,
)

data class BrowseUiState(
    val query: String = "",
    val submittedQuery: String = "",
    val filter: PostFilter = PostFilter(),
    val posts: List<Post> = emptyList(),
    val totalCount: Int = 0,
    val nextPage: Int? = null,
    val isLoading: Boolean = false,
    val isApplyingWallpaper: Boolean = false,
    val errorMessage: String? = null,
    val lastAppliedPostId: Long? = null,
    val imageSize: BrowseImageSize = BrowseImageSize.Small,
)
