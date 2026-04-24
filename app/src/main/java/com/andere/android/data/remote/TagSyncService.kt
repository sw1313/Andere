package com.andere.android.data.remote

import android.util.Base64
import com.andere.android.data.local.TagEntry
import com.andere.android.data.local.TagTranslationRepository
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class TagSyncService(
    private val tagTranslationRepository: TagTranslationRepository,
) {
    private val httpClient = OkHttpClient.Builder().build()

    suspend fun download(): Int {
        val remote = fetchRemoteEntries()
        tagTranslationRepository.replaceAllBundled(remote)
        return remote.size
    }

    suspend fun uploadThenDownload(pat: String): String {
        val remote = fetchRemoteEntries()
        val local = tagTranslationRepository.exportAll()

        val merged = LinkedHashMap<String, TagEntry>()
        for (e in remote) merged[e.name] = e
        for (e in local) merged[e.name] = e
        val mergedList = merged.values.toList()

        uploadEntries(mergedList, pat)
        tagTranslationRepository.replaceAllBundled(mergedList)
        tagTranslationRepository.clearUserOverrides()

        return "同步完成，共 ${mergedList.size} 条"
    }

    suspend fun upload(pat: String): String {
        val remote = fetchRemoteEntries()
        val local = tagTranslationRepository.exportAll()

        val merged = LinkedHashMap<String, TagEntry>()
        for (e in remote) merged[e.name] = e
        for (e in local) merged[e.name] = e
        val mergedList = merged.values.toList()

        uploadEntries(mergedList, pat)
        tagTranslationRepository.replaceAllBundled(mergedList)
        tagTranslationRepository.clearUserOverrides()

        return "上传成功，共 ${mergedList.size} 条"
    }

    private fun fetchRemoteEntries(): List<TagEntry> {
        val errors = mutableListOf<String>()
        for (url in DOWNLOAD_URLS) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("Cache-Control", "no-cache")
                    .get()
                    .build()
                val response = httpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    errors.add("$url -> HTTP ${response.code}")
                    continue
                }
                val body = response.body?.string() ?: continue
                return parseEntries(body)
            } catch (e: Exception) {
                errors.add("$url -> ${e.message}")
            }
        }
        throw Exception("所有下载源均失败:\n${errors.joinToString("\n")}")
    }

    private fun parseEntries(json: String): List<TagEntry> {
        val arr = JSONArray(json)
        val entries = mutableListOf<TagEntry>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            entries.add(
                TagEntry(
                    name = obj.getString("name"),
                    type = obj.optInt("type", 0),
                    zhName = obj.optString("zh", ""),
                ),
            )
        }
        return entries
    }

    private fun uploadEntries(entries: List<TagEntry>, pat: String) {
        val arr = JSONArray()
        for (entry in entries) {
            val obj = JSONObject()
            obj.put("name", entry.name)
            obj.put("type", entry.type)
            if (entry.zhName.isNotBlank()) obj.put("zh", entry.zhName)
            arr.put(obj)
        }
        val contentBytes = arr.toString(1).toByteArray(Charsets.UTF_8)
        val contentBase64 = Base64.encodeToString(contentBytes, Base64.NO_WRAP)

        val sha = fetchCurrentSha(pat)

        val payload = JSONObject().apply {
            put("message", "Update tags.json")
            put("content", contentBase64)
            if (sha != null) put("sha", sha)
        }

        val request = Request.Builder()
            .url(API_CONTENTS_URL)
            .header("Authorization", "Bearer $pat")
            .header("Accept", "application/vnd.github+json")
            .put(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            val errBody = response.body?.string().orEmpty()
            throw Exception("上传失败: HTTP ${response.code}\n$errBody")
        }
    }

    private fun fetchCurrentSha(pat: String): String? {
        val request = Request.Builder()
            .url(API_CONTENTS_URL)
            .header("Authorization", "Bearer $pat")
            .header("Accept", "application/vnd.github+json")
            .get()
            .build()
        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) return null
        val body = response.body?.string() ?: return null
        val sha = JSONObject(body).optString("sha", "")
        return sha.ifBlank { null }
    }

    companion object {
        private const val REPO = "sw1313/danbooru-tags-translation"
        private const val FILE_PATH = "tags.json"
        private const val API_CONTENTS_URL = "https://api.github.com/repos/$REPO/contents/$FILE_PATH"
        private val DOWNLOAD_URLS = listOf(
            "https://raw.githubusercontent.com/$REPO/main/$FILE_PATH",
            "https://cdn.jsdelivr.net/gh/$REPO@main/$FILE_PATH",
            "https://mirror.ghproxy.com/https://raw.githubusercontent.com/$REPO/main/$FILE_PATH",
            "https://cdn.staticaly.com/gh/$REPO/main/$FILE_PATH",
        )
    }
}
