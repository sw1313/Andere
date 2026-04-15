package com.andere.android.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.andere.android.data.local.WallpaperRecordDao
import com.andere.android.data.local.WallpaperRecordEntity
import com.andere.android.data.remote.TagSyncService
import com.andere.android.data.settings.AppPreferencesRepository
import com.andere.android.domain.model.BackgroundTarget
import com.andere.android.domain.model.BrowseImageSize
import com.andere.android.domain.model.PostFilter
import com.andere.android.domain.model.SaveImageConfig
import com.andere.android.domain.model.WallpaperRefreshConfig
import com.andere.android.system.WallpaperRefreshService
import com.andere.android.system.WallpaperScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ExperimentalCoroutinesApi

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModel(
    private val preferencesRepository: AppPreferencesRepository,
    private val wallpaperScheduler: WallpaperScheduler,
    private val wallpaperRefreshService: WallpaperRefreshService,
    private val wallpaperRecordDao: WallpaperRecordDao,
    private val tagSyncService: TagSyncService,
    private val onTagSyncScheduleChanged: (Boolean) -> Unit,
) : ViewModel() {
    private data class SettingsBaseState(
        val wpConfig: WallpaperRefreshConfig,
        val lsConfig: WallpaperRefreshConfig,
        val wpSaveConfig: SaveImageConfig,
        val lsSaveConfig: SaveImageConfig,
        val autoSyncTags: Boolean,
        val hasGithubPat: Boolean,
        val browseImageSize: BrowseImageSize = BrowseImageSize.Small,
        val useBuiltInHosts: Boolean = false,
    )

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val baseFlow = combine(
                preferencesRepository.wallpaperConfig,
                preferencesRepository.lockscreenConfig,
                preferencesRepository.wallpaperSaveImageConfig,
                preferencesRepository.lockscreenSaveImageConfig,
            ) { wpConfig, lsConfig, wpSaveConfig, lsSaveConfig ->
                SettingsBaseState(
                    wpConfig = wpConfig,
                    lsConfig = lsConfig,
                    wpSaveConfig = wpSaveConfig,
                    lsSaveConfig = lsSaveConfig,
                    autoSyncTags = false,
                    hasGithubPat = false,
                )
            }.combine(preferencesRepository.autoSyncTags) { base, auto ->
                base.copy(autoSyncTags = auto)
            }.combine(preferencesRepository.githubPat) { base, pat ->
                base.copy(hasGithubPat = pat.isNotBlank())
            }.combine(preferencesRepository.browseImageSize) { base, size ->
                base.copy(browseImageSize = size)
            }.combine(preferencesRepository.useBuiltInHosts) { base, hosts ->
                base.copy(useBuiltInHosts = hosts)
            }
            val wpRecordsFlow = preferencesRepository.wallpaperConfig
                .map { it.displayRecordCount.coerceIn(1, 100) }
                .distinctUntilChanged()
                .flatMapLatest { limit ->
                    wallpaperRecordDao.latestByTargetFlow(BackgroundTarget.Wallpaper.name, limit)
                }
            val lsRecordsFlow = preferencesRepository.lockscreenConfig
                .map { it.displayRecordCount.coerceIn(1, 100) }
                .distinctUntilChanged()
                .flatMapLatest { limit ->
                    wallpaperRecordDao.latestByTargetFlow(BackgroundTarget.LockScreen.name, limit)
                }
            combine(baseFlow, wpRecordsFlow, lsRecordsFlow) { base, wpRecords, lsRecords ->
                _uiState.value.copy(
                    wpConfig = base.wpConfig,
                    lsConfig = base.lsConfig,
                    wpSaveConfig = base.wpSaveConfig,
                    lsSaveConfig = base.lsSaveConfig,
                    wpRecords = wpRecords,
                    lsRecords = lsRecords,
                    isLockscreenSupported = wallpaperRefreshService.isLockscreenSupported(),
                    autoSyncTags = base.autoSyncTags,
                    hasGithubPat = base.hasGithubPat,
                    browseImageSize = base.browseImageSize,
                    useBuiltInHosts = base.useBuiltInHosts,
                )
            }.collect { _uiState.value = it }
        }
    }

    fun updateWpConfig(transform: (WallpaperRefreshConfig) -> WallpaperRefreshConfig) {
        viewModelScope.launch {
            preferencesRepository.updateWallpaperConfig(transform)
            val config = preferencesRepository.wallpaperConfig.first()
            wallpaperScheduler.syncSchedule(config, BackgroundTarget.Wallpaper)
        }
    }

    fun updateLsConfig(transform: (WallpaperRefreshConfig) -> WallpaperRefreshConfig) {
        viewModelScope.launch {
            preferencesRepository.updateLockscreenConfig(transform)
            val config = preferencesRepository.lockscreenConfig.first()
            wallpaperScheduler.syncSchedule(config, BackgroundTarget.LockScreen)
        }
    }

    fun toggleWpEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.updateWallpaperConfig { it.copy(enabled = enabled) }
            wallpaperScheduler.syncSchedule(preferencesRepository.wallpaperConfig.first(), BackgroundTarget.Wallpaper)
        }
    }

    fun toggleLsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.updateLockscreenConfig { it.copy(enabled = enabled) }
            wallpaperScheduler.syncSchedule(preferencesRepository.lockscreenConfig.first(), BackgroundTarget.LockScreen)
        }
    }

    fun updateWpSaveConfig(transform: (SaveImageConfig) -> SaveImageConfig) {
        viewModelScope.launch {
            preferencesRepository.updateWallpaperSaveImageConfig(transform)
        }
    }

    fun updateLsSaveConfig(transform: (SaveImageConfig) -> SaveImageConfig) {
        viewModelScope.launch {
            preferencesRepository.updateLockscreenSaveImageConfig(transform)
        }
    }

    fun refreshNow(target: BackgroundTarget) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(refreshingTarget = target)
            val config = when (target) {
                BackgroundTarget.Wallpaper -> _uiState.value.wpConfig
                BackgroundTarget.LockScreen -> _uiState.value.lsConfig
            }
            runCatching {
                wallpaperRefreshService.refresh(config, target)
            }.onSuccess {
                _uiState.value = _uiState.value.copy(refreshingTarget = null)
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    refreshingTarget = null,
                    errorMessage = error.message,
                )
            }
        }
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun clearRecords(target: BackgroundTarget) {
        viewModelScope.launch {
            wallpaperRecordDao.deleteByTarget(target.name)
        }
    }

    fun refreshRecentRecords() {
        viewModelScope.launch {
            val wpLimit = _uiState.value.wpConfig.displayRecordCount.coerceIn(1, 100)
            val lsLimit = _uiState.value.lsConfig.displayRecordCount.coerceIn(1, 100)
            val wpRecords = wallpaperRecordDao.latestByTarget(BackgroundTarget.Wallpaper.name, limit = wpLimit)
            val lsRecords = wallpaperRecordDao.latestByTarget(BackgroundTarget.LockScreen.name, limit = lsLimit)
            _uiState.value = _uiState.value.copy(
                wpRecords = wpRecords,
                lsRecords = lsRecords,
            )
        }
    }

    fun syncTags() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSyncingTags = true, tagSyncMessage = null)
            val pat = preferencesRepository.githubPat.first()
            runCatching {
                withContext(Dispatchers.IO) {
                    if (pat.isNotBlank()) {
                        tagSyncService.uploadThenDownload(pat)
                    } else {
                        "同步完成，共 ${tagSyncService.download()} 条"
                    }
                }
            }.onSuccess { msg ->
                _uiState.value = _uiState.value.copy(isSyncingTags = false, tagSyncMessage = msg)
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    isSyncingTags = false,
                    tagSyncMessage = e.message ?: "同步失败",
                )
            }
        }
    }

    fun uploadTags() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isUploadingTags = true, tagSyncMessage = null)
            val pat = preferencesRepository.githubPat.first()
            runCatching { withContext(Dispatchers.IO) { tagSyncService.upload(pat) } }
                .onSuccess { msg ->
                    _uiState.value = _uiState.value.copy(isUploadingTags = false, tagSyncMessage = msg)
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isUploadingTags = false,
                        tagSyncMessage = e.message ?: "上传失败",
                    )
                }
        }
    }

    fun setAutoSyncTags(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setAutoSyncTags(enabled)
            onTagSyncScheduleChanged(enabled)
        }
    }

    fun saveGithubPat(pat: String) {
        viewModelScope.launch { preferencesRepository.setGithubPat(pat) }
    }

    fun setBrowseImageSize(size: BrowseImageSize) {
        viewModelScope.launch { preferencesRepository.setBrowseImageSize(size) }
    }

    fun setUseBuiltInHosts(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setUseBuiltInHosts(enabled) }
    }

    fun dismissTagSyncMessage() {
        _uiState.value = _uiState.value.copy(tagSyncMessage = null)
    }

    companion object {
        fun factory(
            preferencesRepository: AppPreferencesRepository,
            wallpaperScheduler: WallpaperScheduler,
            wallpaperRefreshService: WallpaperRefreshService,
            wallpaperRecordDao: WallpaperRecordDao,
            tagSyncService: TagSyncService,
            onTagSyncScheduleChanged: (Boolean) -> Unit,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SettingsViewModel(
                    preferencesRepository = preferencesRepository,
                    wallpaperScheduler = wallpaperScheduler,
                    wallpaperRefreshService = wallpaperRefreshService,
                    wallpaperRecordDao = wallpaperRecordDao,
                    tagSyncService = tagSyncService,
                    onTagSyncScheduleChanged = onTagSyncScheduleChanged,
                ) as T
            }
        }
    }
}

data class SettingsUiState(
    val wpConfig: WallpaperRefreshConfig = WallpaperRefreshConfig(filter = PostFilter()),
    val lsConfig: WallpaperRefreshConfig = WallpaperRefreshConfig(filter = PostFilter()),
    val wpSaveConfig: SaveImageConfig = SaveImageConfig(),
    val lsSaveConfig: SaveImageConfig = SaveImageConfig(),
    val wpRecords: List<WallpaperRecordEntity> = emptyList(),
    val lsRecords: List<WallpaperRecordEntity> = emptyList(),
    val refreshingTarget: BackgroundTarget? = null,
    val isLockscreenSupported: Boolean = false,
    val errorMessage: String? = null,
    val autoSyncTags: Boolean = false,
    val hasGithubPat: Boolean = false,
    val isSyncingTags: Boolean = false,
    val isUploadingTags: Boolean = false,
    val tagSyncMessage: String? = null,
    val browseImageSize: BrowseImageSize = BrowseImageSize.Small,
    val useBuiltInHosts: Boolean = false,
)
