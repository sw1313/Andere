package com.andere.android.system

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class NetworkImageDownloader(
    private val okHttpClient: OkHttpClient,
) {
    suspend fun downloadBytes(url: String): ByteArray {
        return withContext(Dispatchers.IO) {
            val request = Request.Builder().url(url).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("图片下载失败：${response.code}")
                response.body?.bytes() ?: error("图片响应内容为空。")
            }
        }
    }
}
