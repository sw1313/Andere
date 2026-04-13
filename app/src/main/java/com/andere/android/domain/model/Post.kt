package com.andere.android.domain.model

data class Post(
    val id: Long,
    val tags: String,
    val createdAtEpochSeconds: Long,
    val creatorId: String?,
    val author: String,
    val source: String?,
    val score: Int,
    val fileExtension: String?,
    val fileUrl: String?,
    val previewUrl: String?,
    val sampleUrl: String?,
    val jpegUrl: String?,
    val width: Int,
    val height: Int,
    val previewWidth: Int,
    val previewHeight: Int,
    val sampleWidth: Int,
    val sampleHeight: Int,
    val jpegWidth: Int,
    val jpegHeight: Int,
    val sampleFileSize: Int,
    val rating: Rating,
    val isShownInIndex: Boolean,
    val isHeld: Boolean,
) {
    val preferredWidth: Double = width.toDouble().coerceAtLeast(1.0)
    val preferredHeight: Double = height.toDouble().coerceAtLeast(1.0)
    val preferredRatio: Double = preferredWidth / preferredHeight

    val browseThumbnailUrl: String? get() = previewUrl

    fun tagList(): List<String> = tags.split(' ').filter { it.isNotBlank() }
}

enum class Rating(val wireValue: String) {
    Safe("s"),
    Questionable("q"),
    Explicit("e");

    companion object {
        fun fromWireValue(value: String?): Rating = entries.firstOrNull { it.wireValue == value } ?: Safe
    }
}
