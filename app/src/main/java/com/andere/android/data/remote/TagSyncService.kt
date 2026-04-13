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
        val request = Request.Builder()
            .url(RAW_URL)
            .header("Cache-Control", "no-cache")
            .get()
            .build()
        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) throw Exception("下载失败: HTTP ${response.code}")

        val body = response.body?.string() ?: throw Exception("下载失败: 响应为空")
        val arr = JSONArray(body)
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
        private const val RAW_URL = "https://raw.githubusercontent.com/$REPO/main/tags.json"
        private const val API_CONTENTS_URL = "https://api.github.com/repos/$REPO/contents/tags.json"
    }
}
