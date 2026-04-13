package com.andere.android.system

import android.app.WallpaperManager
import android.content.Context
import android.os.Build
import com.andere.android.domain.model.BackgroundTarget
import java.io.ByteArrayOutputStream

class WallpaperApplier(
    private val context: Context,
) {
    private val wallpaperManager: WallpaperManager = WallpaperManager.getInstance(context)

    fun isLockscreenSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N

    fun apply(
        bitmap: android.graphics.Bitmap,
        target: BackgroundTarget,
        alsoApplyToLockScreen: Boolean = false,
    ) {
        val stream = ByteArrayOutputStream()
        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 96, stream)
        val bytes = stream.toByteArray()

        when (target) {
            BackgroundTarget.Wallpaper -> {
                if (isLockscreenSupported()) {
                    val flags = if (alsoApplyToLockScreen) {
                        WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
                    } else {
                        WallpaperManager.FLAG_SYSTEM
                    }
                    wallpaperManager.setStream(bytes.inputStream(), null, true, flags)
                } else {
                    wallpaperManager.setStream(bytes.inputStream())
                }
            }
            BackgroundTarget.LockScreen -> {
                if (!isLockscreenSupported()) {
                    error("当前设备不支持单独设置锁屏壁纸。")
                }
                wallpaperManager.setStream(bytes.inputStream(), null, true, WallpaperManager.FLAG_LOCK)
            }
        }
    }
}
