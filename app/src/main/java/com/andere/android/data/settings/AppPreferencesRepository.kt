package com.andere.android.data.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.andere.android.domain.model.BrowseImageSize
import com.andere.android.domain.model.CropMode
import com.andere.android.domain.model.ImageQuality
import com.andere.android.domain.model.PostFilter
import com.andere.android.domain.model.SaveImageConfig
import com.andere.android.domain.model.WallpaperRefreshConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "prpr_settings")

class AppPreferencesRepository(
    private val context: Context,
) {
    private val wpKeys = ConfigKeys("wp_")
    private val lsKeys = ConfigKeys("ls_")
    private val browseKeys = FilterKeys("browse_")
    private val wpSaveKeys = SaveKeys("wp_")
    private val lsSaveKeys = SaveKeys("ls_")

    val wallpaperConfig: Flow<WallpaperRefreshConfig> =
        context.dataStore.data.map { mapConfig(it, wpKeys) }
    val lockscreenConfig: Flow<WallpaperRefreshConfig> =
        context.dataStore.data.map { mapConfig(it, lsKeys) }
    val wallpaperSaveImageConfig: Flow<SaveImageConfig> =
        context.dataStore.data.map { mapSaveImageConfig(it, wpSaveKeys) }
    val lockscreenSaveImageConfig: Flow<SaveImageConfig> =
        context.dataStore.data.map { mapSaveImageConfig(it, lsSaveKeys) }
    val saveImageConfig: Flow<SaveImageConfig> = wallpaperSaveImageConfig
    val browseFilter: Flow<PostFilter> =
        context.dataStore.data.map { mapFilter(it, browseKeys) }

    private val autoSyncTagsKey = booleanPreferencesKey("auto_sync_tags")
    private val githubPatKey = stringPreferencesKey("github_pat")

    val autoSyncTags: Flow<Boolean> = context.dataStore.data.map { it[autoSyncTagsKey] ?: false }

    suspend fun setAutoSyncTags(enabled: Boolean) {
        context.dataStore.edit { it[autoSyncTagsKey] = enabled }
    }

    val githubPat: Flow<String> = context.dataStore.data.map { it[githubPatKey] ?: "" }

    suspend fun setGithubPat(pat: String) {
        context.dataStore.edit { it[githubPatKey] = pat }
    }

    private val browseImageSizeKey = stringPreferencesKey("browse_image_size")

    val browseImageSize: Flow<BrowseImageSize> =
        context.dataStore.data.map { enumOrDefault(it[browseImageSizeKey], BrowseImageSize.Small) }

    suspend fun setBrowseImageSize(size: BrowseImageSize) {
        context.dataStore.edit { it[browseImageSizeKey] = size.name }
    }

    suspend fun updateWallpaperConfig(transform: (WallpaperRefreshConfig) -> WallpaperRefreshConfig) {
        context.dataStore.edit { prefs ->
            writeConfig(prefs, wpKeys, transform(mapConfig(prefs, wpKeys)))
        }
    }

    suspend fun updateLockscreenConfig(transform: (WallpaperRefreshConfig) -> WallpaperRefreshConfig) {
        context.dataStore.edit { prefs ->
            writeConfig(prefs, lsKeys, transform(mapConfig(prefs, lsKeys)))
        }
    }

    suspend fun updateSaveImageConfig(transform: (SaveImageConfig) -> SaveImageConfig) {
        updateWallpaperSaveImageConfig(transform)
    }

    suspend fun updateWallpaperSaveImageConfig(transform: (SaveImageConfig) -> SaveImageConfig) {
        context.dataStore.edit { preferences ->
            val next = transform(mapSaveImageConfig(preferences, wpSaveKeys))
            writeSaveImageConfig(preferences, wpSaveKeys, next)
        }
    }

    suspend fun updateLockscreenSaveImageConfig(transform: (SaveImageConfig) -> SaveImageConfig) {
        context.dataStore.edit { preferences ->
            val next = transform(mapSaveImageConfig(preferences, lsSaveKeys))
            writeSaveImageConfig(preferences, lsSaveKeys, next)
        }
    }

    suspend fun updateBrowseFilter(transform: (PostFilter) -> PostFilter) {
        context.dataStore.edit { prefs ->
            writeFilter(prefs, browseKeys, transform(mapFilter(prefs, browseKeys)))
        }
    }

    private fun mapConfig(prefs: Preferences, k: ConfigKeys): WallpaperRefreshConfig =
        WallpaperRefreshConfig(
            enabled = prefs[k.enabled] ?: false,
            useWallpaperImage = prefs[k.useWallpaperImage] ?: false,
            query = prefs[k.query] ?: "",
            shuffleCount = prefs[k.shuffleCount] ?: 10,
            intervalMinutes = prefs[k.intervalMinutes] ?: 180L,
            wifiOnly = prefs[k.wifiOnly] ?: true,
            quality = enumOrDefault(prefs[k.quality], ImageQuality.High),
            cropMode = enumOrDefault(prefs[k.cropMode], CropMode.None),
            filter = PostFilter(
                allowSafe = prefs[k.allowSafe] ?: true,
                allowQuestionable = prefs[k.allowQuestionable] ?: false,
                allowExplicit = prefs[k.allowExplicit] ?: false,
                allowHorizontal = prefs[k.allowHorizontal] ?: true,
                allowVertical = prefs[k.allowVertical] ?: false,
                useScreenOrientation = prefs[k.useScreenOrientation] ?: false,
                allowHidden = prefs[k.allowHidden] ?: true,
                allowHeld = prefs[k.allowHeld] ?: true,
                tagBlacklist = prefs[k.tagBlacklist] ?: "",
            ),
            notificationEnabled = prefs[k.notificationEnabled] ?: true,
            displayRecordCount = prefs[k.displayRecordCount] ?: 20,
            recordRetentionDays = prefs[k.recordRetentionDays] ?: 7,
        )

    private fun mapFilter(prefs: Preferences, k: FilterKeys): PostFilter =
        PostFilter(
            allowSafe = prefs[k.allowSafe] ?: true,
            allowQuestionable = prefs[k.allowQuestionable] ?: false,
            allowExplicit = prefs[k.allowExplicit] ?: false,
            allowHorizontal = prefs[k.allowHorizontal] ?: true,
            allowVertical = prefs[k.allowVertical] ?: false,
            useScreenOrientation = prefs[k.useScreenOrientation] ?: false,
            allowHidden = prefs[k.allowHidden] ?: true,
            allowHeld = prefs[k.allowHeld] ?: true,
            tagBlacklist = prefs[k.tagBlacklist] ?: "",
        )

    private fun writeConfig(prefs: androidx.datastore.preferences.core.MutablePreferences, k: ConfigKeys, c: WallpaperRefreshConfig) {
        prefs[k.enabled] = c.enabled
        prefs[k.useWallpaperImage] = c.useWallpaperImage
        prefs[k.query] = c.query
        prefs[k.shuffleCount] = c.shuffleCount
        prefs[k.intervalMinutes] = c.intervalMinutes
        prefs[k.wifiOnly] = c.wifiOnly
        prefs[k.quality] = c.quality.name
        prefs[k.cropMode] = c.cropMode.name
        prefs[k.allowSafe] = c.filter.allowSafe
        prefs[k.allowQuestionable] = c.filter.allowQuestionable
        prefs[k.allowExplicit] = c.filter.allowExplicit
        prefs[k.allowHorizontal] = c.filter.allowHorizontal
        prefs[k.allowVertical] = c.filter.allowVertical
        prefs[k.useScreenOrientation] = c.filter.useScreenOrientation
        prefs[k.allowHidden] = c.filter.allowHidden
        prefs[k.allowHeld] = c.filter.allowHeld
        prefs[k.tagBlacklist] = c.filter.tagBlacklist
        prefs[k.notificationEnabled] = c.notificationEnabled
        prefs[k.displayRecordCount] = c.displayRecordCount
        prefs[k.recordRetentionDays] = c.recordRetentionDays
    }

    private fun writeFilter(
        prefs: androidx.datastore.preferences.core.MutablePreferences,
        k: FilterKeys,
        filter: PostFilter,
    ) {
        prefs[k.allowSafe] = filter.allowSafe
        prefs[k.allowQuestionable] = filter.allowQuestionable
        prefs[k.allowExplicit] = filter.allowExplicit
        prefs[k.allowHorizontal] = filter.allowHorizontal
        prefs[k.allowVertical] = filter.allowVertical
        prefs[k.useScreenOrientation] = filter.useScreenOrientation
        prefs[k.allowHidden] = filter.allowHidden
        prefs[k.allowHeld] = filter.allowHeld
        prefs[k.tagBlacklist] = filter.tagBlacklist
    }

    private fun mapSaveImageConfig(preferences: Preferences, keys: SaveKeys): SaveImageConfig = SaveImageConfig(
        directoryUri = preferences[keys.directoryUri]?.takeIf { it.isNotBlank() },
        directoryLabel = preferences[keys.directoryLabel] ?: "Pictures",
    )

    private fun writeSaveImageConfig(
        preferences: androidx.datastore.preferences.core.MutablePreferences,
        keys: SaveKeys,
        config: SaveImageConfig,
    ) {
        preferences[keys.directoryUri] = config.directoryUri ?: ""
        preferences[keys.directoryLabel] = config.directoryLabel
    }

    private fun <T : Enum<T>> enumOrDefault(value: String?, fallback: T): T {
        if (value.isNullOrBlank()) return fallback
        return runCatching { java.lang.Enum.valueOf(fallback.declaringJavaClass, value) }.getOrDefault(fallback)
    }

    private class ConfigKeys(prefix: String) {
        val enabled = booleanPreferencesKey("${prefix}enabled")
        val useWallpaperImage = booleanPreferencesKey("${prefix}use_wallpaper_image")
        val query = stringPreferencesKey("${prefix}query")
        val shuffleCount = intPreferencesKey("${prefix}shuffle_count")
        val intervalMinutes = longPreferencesKey("${prefix}interval_minutes")
        val wifiOnly = booleanPreferencesKey("${prefix}wifi_only")
        val quality = stringPreferencesKey("${prefix}quality")
        val cropMode = stringPreferencesKey("${prefix}crop_mode")
        val allowSafe = booleanPreferencesKey("${prefix}filter_safe")
        val allowQuestionable = booleanPreferencesKey("${prefix}filter_questionable")
        val allowExplicit = booleanPreferencesKey("${prefix}filter_explicit")
        val allowHorizontal = booleanPreferencesKey("${prefix}filter_horizontal")
        val allowVertical = booleanPreferencesKey("${prefix}filter_vertical")
        val useScreenOrientation = booleanPreferencesKey("${prefix}filter_use_screen_orientation")
        val allowHidden = booleanPreferencesKey("${prefix}filter_hidden")
        val allowHeld = booleanPreferencesKey("${prefix}filter_held")
        val tagBlacklist = stringPreferencesKey("${prefix}filter_blacklist")
        val notificationEnabled = booleanPreferencesKey("${prefix}notification_enabled")
        val displayRecordCount = intPreferencesKey("${prefix}display_record_count")
        val recordRetentionDays = intPreferencesKey("${prefix}record_retention_days")
    }

    private class FilterKeys(prefix: String) {
        val allowSafe = booleanPreferencesKey("${prefix}filter_safe")
        val allowQuestionable = booleanPreferencesKey("${prefix}filter_questionable")
        val allowExplicit = booleanPreferencesKey("${prefix}filter_explicit")
        val allowHorizontal = booleanPreferencesKey("${prefix}filter_horizontal")
        val allowVertical = booleanPreferencesKey("${prefix}filter_vertical")
        val useScreenOrientation = booleanPreferencesKey("${prefix}filter_use_screen_orientation")
        val allowHidden = booleanPreferencesKey("${prefix}filter_hidden")
        val allowHeld = booleanPreferencesKey("${prefix}filter_held")
        val tagBlacklist = stringPreferencesKey("${prefix}filter_blacklist")
    }

    private class SaveKeys(prefix: String) {
        val directoryUri = stringPreferencesKey("${prefix}save_directory_uri")
        val directoryLabel = stringPreferencesKey("${prefix}save_directory_label")
    }
}
