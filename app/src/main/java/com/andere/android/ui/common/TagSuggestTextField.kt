package com.andere.android.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.andere.android.data.local.TagSuggestionService
import com.andere.android.data.local.TagTranslationRepository
import com.andere.android.domain.model.TagSuggestion
import com.andere.android.ui.tagTypeColor
import kotlinx.coroutines.delay

@Composable
fun TagSuggestTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    tagSuggestionService: TagSuggestionService,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    tagTranslationRepository: TagTranslationRepository? = null,
    onTagSelected: (() -> Unit)? = null,
    extraTrailingIcon: (@Composable () -> Unit)? = null,
) {
    var textFieldValue by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    var suggestions by remember { mutableStateOf<List<TagSuggestion>>(emptyList()) }
    var suppressSuggestions by remember { mutableStateOf(false) }
    var isFocused by remember { mutableStateOf(false) }
    var fieldHeightPx by remember { mutableStateOf(0) }
    var translations by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    if (value != textFieldValue.text) {
        textFieldValue = TextFieldValue(value, TextRange(value.length))
    }

    LaunchedEffect(value, tagTranslationRepository) {
        if (tagTranslationRepository == null) { translations = emptyMap(); return@LaunchedEffect }
        val endsWithSpace = value.endsWith(" ")
        val tags = value.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }
        val confirmed = if (endsWithSpace) tags else tags.dropLast(1)
        val map = HashMap<String, String>(confirmed.size)
        for (tag in confirmed) {
            val entry = tagTranslationRepository.lookup(tag)
            if (entry != null && entry.zhName.isNotBlank()) map[tag] = entry.zhName
        }
        translations = map
    }

    val visualTransformation = remember(translations) {
        if (translations.isEmpty()) VisualTransformation.None
        else TagDisplayTransformation(translations)
    }

    LaunchedEffect(value, isFocused) {
        if (!isFocused) {
            suggestions = emptyList()
            return@LaunchedEffect
        }
        if (suppressSuggestions) {
            suppressSuggestions = false
            return@LaunchedEffect
        }
        val keyword = value.trim().split("\\s+".toRegex()).lastOrNull().orEmpty()
        if (keyword.isBlank()) {
            suggestions = emptyList()
            return@LaunchedEffect
        }
        delay(300)
        suggestions = tagSuggestionService.search(keyword)
    }

    Box(modifier = modifier) {
        OutlinedTextField(
            value = textFieldValue,
            onValueChange = { tfv ->
                textFieldValue = tfv
                onValueChange(tfv.text)
            },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { state ->
                    isFocused = state.isFocused
                    if (!state.isFocused) suggestions = emptyList()
                }
                .onGloballyPositioned { fieldHeightPx = it.size.height },
            label = { Text(label) },
            singleLine = singleLine,
            visualTransformation = visualTransformation,
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    extraTrailingIcon?.invoke()
                    if (value.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                suppressSuggestions = true
                                suggestions = emptyList()
                                textFieldValue = TextFieldValue("", TextRange.Zero)
                                onValueChange("")
                            },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "清空", modifier = Modifier.size(20.dp))
                        }
                    }
                }
            },
        )

        if (suggestions.isNotEmpty() && value.isNotBlank() && isFocused) {
            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(0, fieldHeightPx),
                onDismissRequest = { suggestions = emptyList() },
                properties = PopupProperties(focusable = false),
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    shape = MaterialTheme.shapes.medium,
                    shadowElevation = 8.dp,
                    tonalElevation = 2.dp,
                ) {
                    LazyColumn(modifier = Modifier.heightIn(max = 260.dp)) {
                        items(suggestions, key = { it.name }) { item ->
                            SuggestionItem(
                                item = item,
                                onClick = {
                                    val parts = value.trim().split("\\s+".toRegex()).toMutableList()
                                    if (parts.isNotEmpty()) parts[parts.lastIndex] = item.name
                                    else parts.add(item.name)
                                    val newText = parts.joinToString(" ") + " "
                                    suppressSuggestions = true
                                    suggestions = emptyList()
                                    textFieldValue = TextFieldValue(newText, TextRange(newText.length))
                                    onValueChange(newText)
                                    onTagSelected?.invoke()
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

private class TagDisplayTransformation(
    private val translations: Map<String, String>,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val src = text.text
        if (src.isEmpty()) return TransformedText(text, OffsetMapping.Identity)

        val buf = StringBuilder(src.length * 2)
        val o2t = IntArray(src.length + 1)
        val t2oList = mutableListOf<Int>()

        var i = 0
        while (i < src.length) {
            if (src[i] == ' ') {
                o2t[i] = buf.length
                t2oList.add(i)
                buf.append(' ')
                i++
                continue
            }

            val start = i
            while (i < src.length && src[i] != ' ') i++
            val tag = src.substring(start, i)
            val zh = translations[tag]

            if (zh != null) {
                for (c in zh) { t2oList.add(start); buf.append(c) }
                t2oList.add(start); buf.append('(')
                for (j in tag.indices) {
                    o2t[start + j] = buf.length
                    t2oList.add(start + j)
                    buf.append(tag[j])
                }
                t2oList.add(if (i > 0) i - 1 else 0); buf.append(')')
            } else {
                for (j in tag.indices) {
                    o2t[start + j] = buf.length
                    t2oList.add(start + j)
                    buf.append(tag[j])
                }
            }
        }
        o2t[src.length] = buf.length

        val t2o = t2oList.toIntArray()
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int =
                o2t[offset.coerceIn(0, src.length)]
            override fun transformedToOriginal(offset: Int): Int {
                if (offset >= t2o.size) return src.length
                return t2o[offset.coerceIn(0, t2o.lastIndex)]
            }
        }
        return TransformedText(AnnotatedString(buf.toString()), mapping)
    }
}

@Composable
private fun SuggestionItem(item: TagSuggestion, onClick: () -> Unit) {
    val color = tagTypeColor(item.type)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (item.zhName != null) {
            Text(text = item.zhName, color = color, style = MaterialTheme.typography.bodyMedium)
            Text(text = item.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text(text = item.name, color = color, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
