package com.andere.android.domain.model

enum class ImageQuality {
    Medium,
    High,
    Original,
}

enum class CropMode {
    None,
    TopCenter,
    Center,
    BiggestFace,
    MostFaces,
}

enum class BackgroundTarget {
    Wallpaper,
    LockScreen,
}

enum class BrowseImageSize(val label: String) {
    Small("小"),
    Medium("中"),
    Large("大"),
}

enum class SaveImageVariant {
    Preview,
    Sample,
    High,
}

data class WallpaperRefreshConfig(
    val enabled: Boolean = false,
    val useWallpaperImage: Boolean = false,
    val query: String = "",
    val shuffleCount: Int = 10,
    val intervalMinutes: Long = 180,
    val wifiOnly: Boolean = true,
    val quality: ImageQuality = ImageQuality.High,
    val cropMode: CropMode = CropMode.None,
    val filter: PostFilter = PostFilter(),
    val notificationEnabled: Boolean = true,
    val displayRecordCount: Int = 20,
    val recordRetentionDays: Int = 7,
)

data class ImageCandidate(
    val url: String,
    val width: Int,
    val height: Int,
)

data class SaveImageConfig(
    val directoryUri: String? = null,
    val directoryLabel: String = "Pictures",
)
