package com.andere.android.data.local

import com.andere.android.domain.model.TagSuggestion
import com.andere.android.domain.repository.PostRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class TagSuggestionService(
    private val postRepository: PostRepository,
    private val tagTranslationRepository: TagTranslationRepository,
) {
    suspend fun search(keyword: String, limit: Int = 12): List<TagSuggestion> {
        if (keyword.isBlank()) return emptyList()

        val isAscii = keyword.all { it.code < 128 }
        return if (isAscii) {
            searchByEnglish(keyword, limit)
        } else {
            searchByChinese(keyword, limit)
        }
    }

    private suspend fun searchByEnglish(keyword: String, limit: Int): List<TagSuggestion> {
        val remoteTags = runCatching {
            postRepository.searchTags(keyword)
        }.getOrDefault(emptyList())

        return remoteTags.take(limit).map { tag ->
            val local = tagTranslationRepository.lookup(tag.name)
            tag.copy(zhName = local?.zhName)
        }
    }

    private suspend fun searchByChinese(keyword: String, limit: Int): List<TagSuggestion> {
        val localMatches = tagTranslationRepository.search(keyword, limit = limit)
        if (localMatches.isEmpty()) return emptyList()

        return coroutineScope {
            localMatches.map { entry ->
                async {
                    runCatching {
                        val results = postRepository.searchTags(entry.name)
                        results.firstOrNull { it.name == entry.name }?.copy(zhName = entry.zhName)
                    }.getOrNull()
                }
            }.awaitAll().filterNotNull()
        }
    }
}
