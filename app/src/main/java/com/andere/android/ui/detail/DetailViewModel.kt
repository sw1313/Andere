package com.andere.android.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.andere.android.data.local.FavoritePostDao
import com.andere.android.data.local.FavoritePostEntity
import com.andere.android.data.local.TagTranslationRepository
import com.andere.android.data.settings.AppPreferencesRepository
import com.andere.android.domain.model.BackgroundTarget
import com.andere.android.domain.model.Post
import com.andere.android.domain.model.SaveImageConfig
import com.andere.android.domain.model.SaveImageVariant
import com.andere.android.system.ImageSaveService
import com.andere.android.system.WallpaperScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ResolvedTag(val name: String, val type: Int, val zhName: String?)

class DetailViewModel(
    private val favoritePostDao: FavoritePostDao,
    private val preferencesRepository: AppPreferencesRepository,
    private val imageSaveService: ImageSaveService,
    private val tagTranslationRepository: TagTranslationRepository,
    private val wallpaperScheduler: WallpaperScheduler,
) : ViewModel() {
    private val _uiState = MutableStateFlow(DetailUiState())
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    private var boundPost: Post? = null

    init {
        viewModelScope.launch {
            preferencesRepository.saveImageConfig.collect { config ->
                _uiState.update { it.copy(saveConfig = config) }
            }
        }
    }

    fun bindPost(post: Post) {
        boundPost = post
        viewModelScope.launch {
            _uiState.update { it.copy(resolvedTags = emptyList(), isFavorited = false) }
            val isFav = favoritePostDao.findByPostId(post.id) != null
            val resolved = post.tagList().map { tag ->
                val entry = tagTranslationRepository.lookup(tag)
                ResolvedTag(name = tag, type = entry?.type ?: 0, zhName = entry?.zhName)
            }.sortedByDescending { it.type }
            _uiState.update {
                it.copy(isFavorited = isFav, resolvedTags = resolved)
            }
        }
    }

    fun toggleFavorite(post: Post) {
        viewModelScope.launch {
            val existing = favoritePostDao.findByPostId(post.id)
            if (existing == null) {
                favoritePostDao.upsert(
                    FavoritePostEntity(
                        postId = post.id,
                        previewUrl = post.browseThumbnailUrl,
                        width = post.width,
                        height = post.height,
                        tags = post.tags,
                        author = post.author,
                        createdAtEpochSeconds = post.createdAtEpochSeconds,
                        savedAtMillis = System.currentTimeMillis(),
                    ),
                )
                _uiState.update { it.copy(isFavorited = true, message = "已加入本地收藏。") }
            } else {
                favoritePostDao.deleteByPostId(post.id)
                _uiState.update { it.copy(isFavorited = false, message = "已取消本地收藏。") }
            }
        }
    }

    fun saveImage(post: Post, variant: SaveImageVariant) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, message = null) }
            runCatching {
                imageSaveService.savePost(post, variant, _uiState.value.saveConfig)
            }.onSuccess { fileName ->
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        message = "已保存：$fileName",
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        message = error.message ?: "保存失败。",
                    )
                }
            }
        }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }

    fun addTagToWallpaperSearch(tag: String) {
        viewModelScope.launch {
            preferencesRepository.updateWallpaperConfig { cfg ->
                cfg.copy(query = appendSpaceSeparatedUnique(cfg.query, tag))
            }
            wallpaperScheduler.syncSchedule(preferencesRepository.wallpaperConfig.first(), BackgroundTarget.Wallpaper)
            _uiState.update { it.copy(message = "已加入壁纸搜索标签。") }
        }
    }

    fun addTagToLockscreenSearch(tag: String) {
        viewModelScope.launch {
            preferencesRepository.updateLockscreenConfig { cfg ->
                cfg.copy(query = appendSpaceSeparatedUnique(cfg.query, tag))
            }
            wallpaperScheduler.syncSchedule(preferencesRepository.lockscreenConfig.first(), BackgroundTarget.LockScreen)
            _uiState.update { it.copy(message = "已加入锁屏搜索标签。") }
        }
    }

    fun addTagToWallpaperBlacklist(tag: String) {
        viewModelScope.launch {
            preferencesRepository.updateWallpaperConfig { cfg ->
                val next = appendSpaceSeparatedUnique(cfg.filter.tagBlacklist, tag)
                cfg.copy(filter = cfg.filter.copy(tagBlacklist = next))
            }
            wallpaperScheduler.syncSchedule(preferencesRepository.wallpaperConfig.first(), BackgroundTarget.Wallpaper)
            _uiState.update { it.copy(message = "已加入壁纸黑名单标签。") }
        }
    }

    fun addTagToLockscreenBlacklist(tag: String) {
        viewModelScope.launch {
            preferencesRepository.updateLockscreenConfig { cfg ->
                val next = appendSpaceSeparatedUnique(cfg.filter.tagBlacklist, tag)
                cfg.copy(filter = cfg.filter.copy(tagBlacklist = next))
            }
            wallpaperScheduler.syncSchedule(preferencesRepository.lockscreenConfig.first(), BackgroundTarget.LockScreen)
            _uiState.update { it.copy(message = "已加入锁屏黑名单标签。") }
        }
    }

    fun saveTagTranslation(tag: String, zhText: String) {
        viewModelScope.launch {
            runCatching {
                tagTranslationRepository.saveUserOverride(tag, zhText)
                boundPost?.let { p -> refreshResolvedTagsOnly(p) }
            }.onSuccess {
                _uiState.update { it.copy(message = "已写入本机汉化库。") }
            }.onFailure { e ->
                _uiState.update { it.copy(message = e.message ?: "保存失败。") }
            }
        }
    }

    private suspend fun refreshResolvedTagsOnly(post: Post) {
        val resolved = post.tagList().map { t ->
            val entry = tagTranslationRepository.lookup(t)
            ResolvedTag(name = t, type = entry?.type ?: 0, zhName = entry?.zhName)
        }.sortedByDescending { it.type }
        _uiState.update { it.copy(resolvedTags = resolved) }
    }

    private fun appendSpaceSeparatedUnique(current: String, rawTag: String): String {
        val tag = rawTag.trim()
        if (tag.isEmpty()) return current.trim()
        val existingLower = current.split(' ', '\t', '\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { it.lowercase() }
            .toSet()
        if (tag.lowercase() in existingLower) return current.trim()
        return if (current.isBlank()) tag else "${current.trim()} $tag"
    }

    companion object {
        fun factory(
            favoritePostDao: FavoritePostDao,
            preferencesRepository: AppPreferencesRepository,
            imageSaveService: ImageSaveService,
            tagTranslationRepository: TagTranslationRepository,
            wallpaperScheduler: WallpaperScheduler,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return DetailViewModel(
                    favoritePostDao = favoritePostDao,
                    preferencesRepository = preferencesRepository,
                    imageSaveService = imageSaveService,
                    tagTranslationRepository = tagTranslationRepository,
                    wallpaperScheduler = wallpaperScheduler,
                ) as T
            }
        }
    }
}

data class DetailUiState(
    val isFavorited: Boolean = false,
    val isSaving: Boolean = false,
    val saveConfig: SaveImageConfig = SaveImageConfig(),
    val resolvedTags: List<ResolvedTag> = emptyList(),
    val message: String? = null,
)
