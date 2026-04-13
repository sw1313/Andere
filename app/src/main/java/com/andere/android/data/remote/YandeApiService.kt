package com.andere.android.data.remote

import com.andere.android.domain.model.PostPage
import com.andere.android.domain.model.TagSuggestion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

class YandeApiService(
    private val okHttpClient: OkHttpClient,
    private val parser: YandeXmlParser,
    private val host: String,
) {
    suspend fun searchPosts(query: String, page: Int): PostPage {
        return withContext(Dispatchers.IO) {
            val url = host.toHttpUrl().newBuilder()
                .addPathSegment("post.xml")
                .addQueryParameter("tags", query)
                .addQueryParameter("page", page.toString())
                .build()

            val request = Request.Builder().url(url).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("Yande 图片列表请求失败：${response.code}")
                parser.parsePostPage(response.body?.string().orEmpty(), page)
            }
        }
    }

    suspend fun searchTags(keyword: String): List<TagSuggestion> {
        if (keyword.isBlank()) return emptyList()

        return withContext(Dispatchers.IO) {
            val url = host.toHttpUrl().newBuilder()
                .addPathSegment("tag.xml")
                .addQueryParameter("order", "count")
                .addQueryParameter("limit", "10")
                .addQueryParameter("name", keyword)
                .build()

            val request = Request.Builder().url(url).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("Yande 标签请求失败：${response.code}")
                parser.parseTagSuggestions(response.body?.string().orEmpty())
            }
        }
    }
}
