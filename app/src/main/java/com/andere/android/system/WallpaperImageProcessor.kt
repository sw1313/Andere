package com.andere.android.system

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import com.andere.android.domain.model.CropMode
import java.io.ByteArrayInputStream
import kotlin.math.max
import kotlin.math.min

class WallpaperImageProcessor {
    fun decodeAndCrop(bytes: ByteArray, cropMode: CropMode, targetAspectRatio: Float): Bitmap {
        val bitmap = BitmapFactory.decodeStream(ByteArrayInputStream(bytes))
            ?: error("Failed to decode wallpaper image.")

        if (cropMode == CropMode.None || targetAspectRatio <= 0f) return bitmap

        val cropRect = when (cropMode) {
            CropMode.None -> Rect(0, 0, bitmap.width, bitmap.height)
            CropMode.Center -> centerCrop(bitmap.width, bitmap.height, targetAspectRatio)
            CropMode.TopCenter,
            CropMode.BiggestFace,
            CropMode.MostFaces,
                -> topCenterCrop(bitmap.width, bitmap.height, targetAspectRatio)
        }

        return Bitmap.createBitmap(bitmap, cropRect.left, cropRect.top, cropRect.width(), cropRect.height())
    }

    private fun centerCrop(width: Int, height: Int, aspectRatio: Float): Rect {
        val imageRatio = width.toFloat() / height.toFloat()
        return if (imageRatio > aspectRatio) {
            val targetWidth = (height * aspectRatio).toInt()
            val left = (width - targetWidth) / 2
            Rect(left, 0, left + targetWidth, height)
        } else {
            val targetHeight = (width / aspectRatio).toInt()
            val top = (height - targetHeight) / 2
            Rect(0, top, width, top + targetHeight)
        }
    }

    private fun topCenterCrop(width: Int, height: Int, aspectRatio: Float): Rect {
        val imageRatio = width.toFloat() / height.toFloat()
        return if (imageRatio > aspectRatio) {
            val targetWidth = (height * aspectRatio).toInt()
            val left = (width - targetWidth) / 2
            Rect(left, 0, left + targetWidth, height)
        } else {
            val targetHeight = min(height, max(1, (width / aspectRatio).toInt()))
            Rect(0, 0, width, targetHeight)
        }
    }
}
