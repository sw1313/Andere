package com.andere.android.data.remote

import android.util.Xml
import com.andere.android.domain.model.Post
import com.andere.android.domain.model.PostPage
import com.andere.android.domain.model.Rating
import com.andere.android.domain.model.TagSuggestion
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader

class YandeXmlParser {
    fun parsePostPage(xml: String, page: Int): PostPage {
        val parser = Xml.newPullParser()
        parser.setInput(StringReader(xml))

        var count = 0
        var offset = 0
        val posts = mutableListOf<Post>()

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            when (parser.name) {
                "posts" -> {
                    count = parser.getAttributeInt("count")
                    offset = parser.getAttributeInt("offset")
                }
                "post" -> posts += parsePost(parser)
            }
        }

        val uniquePosts = posts.distinctBy { it.id }
        val nextPage = if (uniquePosts.isEmpty() || offset + posts.size >= count) null else page + 1
        return PostPage(posts = uniquePosts, totalCount = count, nextPage = nextPage)
    }

    fun parseTagSuggestions(xml: String): List<TagSuggestion> {
        val parser = Xml.newPullParser()
        parser.setInput(StringReader(xml))
        val tags = mutableListOf<TagSuggestion>()

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "tag") {
                tags += TagSuggestion(
                    name = parser.getAttributeValue(null, "name").orEmpty(),
                    count = parser.getAttributeInt("count"),
                    type = parser.getAttributeInt("type"),
                )
            }
        }

        return tags
    }

    private fun parsePost(parser: XmlPullParser): Post = Post(
        id = parser.getAttributeLong("id"),
        tags = parser.getAttributeValue(null, "tags").orEmpty(),
        createdAtEpochSeconds = parser.getAttributeLong("created_at"),
        creatorId = parser.getAttributeValue(null, "creator_id"),
        author = parser.getAttributeValue(null, "author").orEmpty(),
        source = parser.getAttributeValue(null, "source"),
        score = parser.getAttributeInt("score"),
        fileExtension = parser.getAttributeValue(null, "file_ext"),
        fileUrl = parser.getAttributeValue(null, "file_url"),
        previewUrl = parser.getAttributeValue(null, "preview_url"),
        sampleUrl = parser.getAttributeValue(null, "sample_url"),
        jpegUrl = parser.getAttributeValue(null, "jpeg_url"),
        width = parser.getAttributeInt("width"),
        height = parser.getAttributeInt("height"),
        previewWidth = parser.getAttributeInt("preview_width"),
        previewHeight = parser.getAttributeInt("preview_height"),
        sampleWidth = parser.getAttributeInt("sample_width"),
        sampleHeight = parser.getAttributeInt("sample_height"),
        jpegWidth = parser.getAttributeInt("jpeg_width"),
        jpegHeight = parser.getAttributeInt("jpeg_height"),
        sampleFileSize = parser.getAttributeInt("sample_file_size"),
        rating = Rating.fromWireValue(parser.getAttributeValue(null, "rating")),
        isShownInIndex = parser.getAttributeBoolean("is_shown_in_index", true),
        isHeld = parser.getAttributeBoolean("is_held", false),
    )

    private fun XmlPullParser.getAttributeInt(name: String): Int =
        getAttributeValue(null, name)?.toIntOrNull() ?: 0

    private fun XmlPullParser.getAttributeLong(name: String): Long =
        getAttributeValue(null, name)?.toLongOrNull() ?: 0L

    private fun XmlPullParser.getAttributeBoolean(name: String, default: Boolean): Boolean =
        when (getAttributeValue(null, name)?.lowercase()) {
            "true", "1" -> true
            "false", "0" -> false
            else -> default
        }
}
