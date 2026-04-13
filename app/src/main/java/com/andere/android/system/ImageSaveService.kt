package com.andere.android.system

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import com.andere.android.domain.model.Post
import com.andere.android.domain.model.SaveImageConfig
import com.andere.android.domain.model.SaveImageVariant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class ImageSaveService(
    private val context: Context,
    private val imageDownloader: NetworkImageDownloader,
) {
    suspend fun savePost(
        post: Post,
        variant: SaveImageVariant,
        config: SaveImageConfig,
    ): String = withContext(Dispatchers.IO) {
        val source = resolveSource(post, variant)
        val fileName = buildFileName(post, source.extension)
        val bytes = imageDownloader.downloadBytes(source.url)

        if (!config.directoryUri.isNullOrBlank()) {
            saveToTreeUri(bytes, fileName, source.mimeType, Uri.parse(config.directoryUri))
        } else {
            saveToPictures(bytes, fileName, source.mimeType)
        }

        fileName
    }

    private fun resolveSource(post: Post, variant: SaveImageVariant): SaveSource {
        return when (variant) {
            SaveImageVariant.Preview -> SaveSource(
                url = post.previewUrl ?: error("没有可保存的预览图。"),
                extension = "jpg",
                mimeType = "image/jpeg",
            )

            SaveImageVariant.Sample -> SaveSource(
                url = post.sampleUrl ?: post.previewUrl ?: error("没有可保存的 Sample 图。"),
                extension = "jpg",
                mimeType = "image/jpeg",
            )

            SaveImageVariant.High -> {
                val highUrl = post.jpegUrl ?: post.fileUrl ?: error("没有可保存的高清图。")
                val extension = if (!post.jpegUrl.isNullOrBlank()) {
                    "jpg"
                } else {
                    post.fileExtension?.ifBlank { null } ?: inferExtensionFromUrl(highUrl)
                }
                SaveSource(
                    url = highUrl,
                    extension = extension,
                    mimeType = mimeTypeForExtension(extension),
                )
            }
        }
    }

    private fun buildFileName(post: Post, extension: String): String {
        val baseName = post.fileUrl
            ?.substringAfterLast('/')
            ?.substringBeforeLast('.')
            ?.let { URLDecoder.decode(it, StandardCharsets.UTF_8.name()) }
            ?.replace("/", "")
            ?.takeIf { it.isNotBlank() }
            ?: "yande.re_${post.id}"
        return "$baseName.$extension"
    }

    private fun saveToTreeUri(bytes: ByteArray, fileName: String, mimeType: String, treeUri: Uri) {
        val contentResolver = context.contentResolver
        val parentDocumentUri = DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri),
        )
        val documentUri = DocumentsContract.createDocument(contentResolver, parentDocumentUri, mimeType, fileName)
            ?: error("无法在所选目录中创建文件。")
        contentResolver.openOutputStream(documentUri)?.use { output ->
            output.write(bytes)
        } ?: error("无法写入所选目录。")
    }

    private fun saveToPictures(bytes: ByteArray, fileName: String, mimeType: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Andere")
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("无法在 Pictures 中创建文件。")
            context.contentResolver.openOutputStream(uri)?.use { output ->
                output.write(bytes)
            } ?: error("无法写入 Pictures。")
            return
        }

        @Suppress("DEPRECATION")
        val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val targetDir = File(picturesDir, "Andere").apply { mkdirs() }
        val outputFile = File(targetDir, fileName)
        outputFile.outputStream().use { it.write(bytes) }
    }

    private fun inferExtensionFromUrl(url: String): String =
        url.substringAfterLast('.', "jpg").substringBefore('?').lowercase()

    private fun mimeTypeForExtension(extension: String): String = when (extension.lowercase()) {
        "png" -> "image/png"
        "webp" -> "image/webp"
        else -> "image/jpeg"
    }

    private data class SaveSource(
        val url: String,
        val extension: String,
        val mimeType: String,
    )
}
