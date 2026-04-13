package com.andere.android.ui

import androidx.compose.ui.graphics.Color
import com.andere.android.domain.model.BackgroundTarget
import com.andere.android.domain.model.CropMode
import com.andere.android.domain.model.ImageQuality
import com.andere.android.domain.model.Rating
import com.andere.android.domain.model.SaveImageVariant

fun Rating.displayName(): String = when (this) {
    Rating.Safe -> "安全"
    Rating.Questionable -> "可疑"
    Rating.Explicit -> "明显"
}

fun ImageQuality.displayName(): String = when (this) {
    ImageQuality.Medium -> "中等"
    ImageQuality.High -> "高清"
    ImageQuality.Original -> "原图"
}

fun CropMode.displayName(): String = when (this) {
    CropMode.None -> "不裁切"
    CropMode.TopCenter -> "顶部居中"
    CropMode.Center -> "居中"
    CropMode.BiggestFace -> "最大人脸"
    CropMode.MostFaces -> "最多人脸"
}

fun BackgroundTarget.displayName(): String = when (this) {
    BackgroundTarget.Wallpaper -> "壁纸"
    BackgroundTarget.LockScreen -> "锁屏"
}

fun targetNameDisplayName(value: String): String = runCatching {
    BackgroundTarget.valueOf(value).displayName()
}.getOrDefault(value)

fun SaveImageVariant.displayName(): String = when (this) {
    SaveImageVariant.Preview -> "预览图"
    SaveImageVariant.Sample -> "Sample"
    SaveImageVariant.High -> "高清"
}

/**
 * Yande/Danbooru tag type colors matching UWP TagTypeColorConverter.
 * 0=General(None), 1=Artist, 3=Copyright, 4=Character, 5=Circle, 6=Faults/Meta
 */
fun tagTypeColor(type: Int): Color = when (type) {
    1 -> Color(0xFFCA5010)    // Artist
    3 -> Color(0xFFC239B3)    // Copyright
    4 -> Color(0xFF10893E)    // Character
    5 -> Color(0xFF2D7D9A)    // Circle
    6 -> Color(0xFFE81123)    // Faults / Meta
    else -> Color(0xFF767676) // General / None
}
