package com.andere.android.ui.browse

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.andere.android.domain.model.BrowseImageSize
import com.andere.android.data.local.TagSuggestionService
import com.andere.android.data.local.TagTranslationRepository
import com.andere.android.domain.model.Post
import com.andere.android.domain.model.PostFilter
import com.andere.android.ui.common.TagSuggestTextField

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun BrowseScreen(
    viewModel: BrowseViewModel,
    tagSuggestionService: TagSuggestionService,
    tagTranslationRepository: TagTranslationRepository? = null,
    onOpenDetail: (Post) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showFilterSheet by remember { mutableStateOf(false) }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { snackbarHostState.showSnackbar(it) }
    }

    LaunchedEffect(state.posts.size, state.nextPage, state.isLoading) {
        if (!state.isLoading && state.nextPage != null && state.posts.size < 18) {
            viewModel.loadNextPage()
        }
    }

    val gridListState = rememberLazyListState()
    var headerExpanded by remember { mutableStateOf(true) }

    LaunchedEffect(gridListState) {
        var prevIndex = gridListState.firstVisibleItemIndex
        var prevOffset = gridListState.firstVisibleItemScrollOffset
        snapshotFlow {
            gridListState.firstVisibleItemIndex to gridListState.firstVisibleItemScrollOffset
        }.collect { (index, offset) ->
            if (index == 0 && offset == 0) headerExpanded = true
            else {
                val scrollingDown = index > prevIndex || (index == prevIndex && offset > prevOffset)
                if (scrollingDown && index > 0) headerExpanded = false
            }
            prevIndex = index
            prevOffset = offset
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
        ) {
            AnimatedVisibility(
                visible = headerExpanded,
                enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
            ) {
                Column {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        TagSuggestTextField(
                            value = state.query,
                            onValueChange = viewModel::updateQuery,
                            label = "搜索标签",
                            tagSuggestionService = tagSuggestionService,
                            tagTranslationRepository = tagTranslationRepository,
                            modifier = Modifier.weight(1f),
                            onTagSelected = viewModel::submitSearch,
                            extraTrailingIcon = {
                                IconButton(
                                    onClick = { showFilterSheet = true },
                                    modifier = Modifier.size(36.dp),
                                ) {
                                    Icon(Icons.Default.FilterList, contentDescription = "筛选", modifier = Modifier.size(20.dp))
                                }
                            },
                        )
                        Button(
                            onClick = viewModel::submitSearch,
                            modifier = Modifier.padding(top = 8.dp),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                        ) {
                            Text("搜索", style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    Row(
                        modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (state.isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        }
                        Text(
                            text = if (state.isLoading) "加载中…" else "已加载 ${state.posts.size} / ${state.totalCount}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = !headerExpanded,
                enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(Unit) {
                            detectVerticalDragGestures { _, dragAmount ->
                                if (dragAmount > 0) headerExpanded = true
                            }
                        }
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .width(32.dp)
                            .height(4.dp)
                            .background(
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                RoundedCornerShape(2.dp),
                            ),
                    )
                }
            }

            JustifiedPostGrid(
                posts = state.posts,
                imageSize = state.imageSize,
                listState = gridListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) { _, row ->
                if (row.posts.any { it.id in state.posts.takeLast(8).map(Post::id).toSet() }) {
                    if (!state.isLoading && state.nextPage != null) {
                        viewModel.loadNextPage()
                    }
                }
                JustifiedRow(
                    row = row,
                    imageSize = state.imageSize,
                    onOpenDetail = onOpenDetail,
                )
            }
        }
    }

    if (showFilterSheet) {
        FilterSheet(
            initialFilter = state.filter,
            tagSuggestionService = tagSuggestionService,
            tagTranslationRepository = tagTranslationRepository,
            onDismiss = { showFilterSheet = false },
            onApply = {
                viewModel.updateFilter(it)
                showFilterSheet = false
            },
        )
    }
}

@Composable
private fun JustifiedPostGrid(
    posts: List<Post>,
    imageSize: BrowseImageSize = BrowseImageSize.Small,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    rowContent: @Composable (Int, JustifiedRowData) -> Unit,
) {
    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val spacing = 6.dp
        val (targetH, minH, maxH) = when (imageSize) {
            BrowseImageSize.Small -> Triple(180.dp, 90.dp, 320.dp)
            BrowseImageSize.Medium -> Triple(280.dp, 160.dp, 480.dp)
            BrowseImageSize.Large -> Triple(400.dp, 240.dp, 640.dp)
        }
        val rows = remember(posts, maxWidth, imageSize) {
            val widthPx = with(density) { maxWidth.roundToPx() }
            buildJustifiedRows(
                posts = posts,
                containerWidthPx = widthPx,
                spacingPx = with(density) { spacing.roundToPx() },
                targetRowHeightPx = with(density) { targetH.roundToPx() },
                minRowHeightPx = with(density) { minH.roundToPx() },
                maxRowHeightPx = with(density) { maxH.roundToPx() },
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(spacing),
        ) {
            itemsIndexed(rows, key = { _, row -> row.key }) { index, row ->
                rowContent(index, row)
            }
        }
    }
}

@Composable
private fun JustifiedRow(
    row: JustifiedRowData,
    imageSize: BrowseImageSize = BrowseImageSize.Small,
    onOpenDetail: (Post) -> Unit,
) {
    val density = LocalDensity.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(with(density) { row.heightPx.toDp() }),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        row.posts.forEach { post ->
            val ratio = post.preferredRatio.toFloat().coerceAtLeast(0.4f)
            val imageUrl = when (imageSize) {
                BrowseImageSize.Small -> post.previewUrl
                BrowseImageSize.Medium, BrowseImageSize.Large -> post.sampleUrl ?: post.previewUrl
            }
            Box(
                modifier = Modifier
                    .weight(ratio)
                    .fillMaxSize()
                    .clickable { onOpenDetail(post) },
            ) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = post.tags,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterSheet(
    initialFilter: PostFilter,
    tagSuggestionService: TagSuggestionService,
    tagTranslationRepository: TagTranslationRepository?,
    onDismiss: () -> Unit,
    onApply: (PostFilter) -> Unit,
) {
    var filter by remember(initialFilter) { mutableStateOf(initialFilter) }
    ModalBottomSheet(
        onDismissRequest = {
            if (filter != initialFilter) {
                onApply(filter)
            }
            onDismiss()
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TagSuggestTextField(
                value = filter.tagBlacklist,
                onValueChange = { filter = filter.copy(tagBlacklist = it) },
                label = "黑名单标签",
                tagSuggestionService = tagSuggestionService,
                tagTranslationRepository = tagTranslationRepository,
                modifier = Modifier.fillMaxWidth(),
            )

            Text("评级", style = MaterialTheme.typography.titleMedium)
            FilterCheckRow("安全", filter.allowSafe) { filter = filter.copy(allowSafe = it) }
            FilterCheckRow("可疑", filter.allowQuestionable) { filter = filter.copy(allowQuestionable = it) }
            FilterCheckRow("明显", filter.allowExplicit) { filter = filter.copy(allowExplicit = it) }

            Text("长宽比", style = MaterialTheme.typography.titleMedium)
            FilterCheckRow("横向", filter.allowHorizontal) { filter = filter.copy(allowHorizontal = it) }
            FilterCheckRow("纵向", filter.allowVertical) { filter = filter.copy(allowVertical = it) }

            Text("可见性", style = MaterialTheme.typography.titleMedium)
            FilterCheckRow("显示隐藏图", filter.allowHidden) { filter = filter.copy(allowHidden = it) }
            FilterCheckRow("显示暂挂图", filter.allowHeld) { filter = filter.copy(allowHeld = it) }

            SingleSelectFilterGroup(
                label = "排序",
                options = listOf("按时间", "按评分"),
                selectedIndex = filter.sortOrder,
                onSelect = { filter = filter.copy(sortOrder = it) },
            )

            SingleSelectFilterGroup(
                label = "时间范围",
                options = listOf("不限", "今天", "本周", "本月", "今年"),
                selectedIndex = filter.timeRange,
                onSelect = { filter = filter.copy(timeRange = it) },
            )

            Text("收起面板后自动应用筛选", style = MaterialTheme.typography.bodySmall)
        }
    }
}

private data class JustifiedRowData(
    val posts: List<Post>,
    val heightPx: Int,
    val isLastRow: Boolean,
) {
    val key: String = posts.joinToString(separator = "-") { it.id.toString() }
}

private fun buildJustifiedRows(
    posts: List<Post>,
    containerWidthPx: Int,
    spacingPx: Int,
    targetRowHeightPx: Int,
    minRowHeightPx: Int,
    maxRowHeightPx: Int,
): List<JustifiedRowData> {
    if (posts.isEmpty() || containerWidthPx <= 0) return emptyList()

    val rows = mutableListOf<JustifiedRowData>()
    val current = mutableListOf<Post>()
    var aspectSum = 0.0

    fun flushRow(isLast: Boolean) {
        if (current.isEmpty()) return
        val gaps = (current.size - 1).coerceAtLeast(0) * spacingPx
        val rowHeightPx = if (isLast) {
            targetRowHeightPx.coerceIn(minRowHeightPx, maxRowHeightPx)
        } else {
            ((containerWidthPx - gaps) / aspectSum).toInt().coerceIn(minRowHeightPx, maxRowHeightPx)
        }
        rows += JustifiedRowData(
            posts = current.toList(),
            heightPx = rowHeightPx,
            isLastRow = isLast,
        )
        current.clear()
        aspectSum = 0.0
    }

    posts.forEach { post ->
        current += post
        aspectSum += post.preferredRatio.coerceAtLeast(0.4)
        val gaps = (current.size - 1).coerceAtLeast(0) * spacingPx
        val estimatedHeight = ((containerWidthPx - gaps) / aspectSum).toInt()
        if (current.size > 1 && estimatedHeight <= targetRowHeightPx) {
            flushRow(isLast = false)
        }
    }

    flushRow(isLast = true)
    return rows
}

@Composable
private fun FilterCheckRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SingleSelectFilterGroup(
    label: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    Text(label, style = MaterialTheme.typography.titleMedium)
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEachIndexed { index, text ->
            FilterChip(selected = index == selectedIndex, onClick = { onSelect(index) }, label = { Text(text) })
        }
    }
}
