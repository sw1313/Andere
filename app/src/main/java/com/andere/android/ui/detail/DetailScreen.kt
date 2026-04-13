package com.andere.android.ui.detail

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import com.andere.android.domain.model.BackgroundTarget
import com.andere.android.domain.model.Post
import com.andere.android.domain.model.SaveImageVariant
import com.andere.android.ui.displayName
import com.andere.android.ui.tagTypeColor
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun DetailScreen(
    posts: List<Post>,
    initialIndex: Int,
    viewModel: DetailViewModel,
    onBack: () -> Unit,
    onApply: (Post, BackgroundTarget) -> Unit,
    onOpenPostInBrowser: (Post) -> Unit,
    onOpenSourceInBrowser: (String?) -> Unit,
    onTagClick: (String) -> Unit,
    onNearEnd: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var showSaveDialog by remember { mutableStateOf(false) }
    var showFullscreen by remember { mutableStateOf(false) }
    var tagActionMenuFor by remember { mutableStateOf<String?>(null) }
    var translationFor by remember { mutableStateOf<String?>(null) }
    var translationDraft by remember { mutableStateOf("") }

    val postCount = posts.size
    val pagerState = rememberPagerState(initialPage = initialIndex) { postCount }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            val post = posts.getOrNull(page) ?: return@collect
            viewModel.bindPost(post)
            if (page >= posts.size - 5) onNearEnd()
            val loader = context.imageLoader
            for (ahead in 1..6) {
                val p = posts.getOrNull(page + ahead) ?: break
                val url = p.sampleUrl ?: p.jpegUrl ?: p.fileUrl ?: p.previewUrl ?: continue
                loader.enqueue(ImageRequest.Builder(context).data(url).build())
            }
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            viewModel.consumeMessage()
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
        }
    }

    BackHandler(enabled = showFullscreen) {
        showFullscreen = false
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        val currentPost = posts.getOrNull(pagerState.currentPage)
                        Text(if (currentPost != null) "#${currentPost.id}" else "详情")
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    },
                )
            },
        ) { innerPadding ->
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                beyondViewportPageCount = 0,
                key = { posts.getOrNull(it)?.id ?: it },
            ) { page ->
                val post = posts.getOrNull(page) ?: return@HorizontalPager
                PostDetailPage(
                    post = post,
                    state = state,
                    onImageClick = { showFullscreen = true },
                    onFavoriteToggle = { viewModel.toggleFavorite(post) },
                    onSave = { showSaveDialog = true },
                    onApplyWallpaper = { onApply(post, BackgroundTarget.Wallpaper) },
                    onApplyLockScreen = { onApply(post, BackgroundTarget.LockScreen) },
                    onOpenPost = { onOpenPostInBrowser(post) },
                    onOpenSource = { onOpenSourceInBrowser(normalizeSourceUrl(it)) },
                    onTagClick = onTagClick,
                    onTagLongPress = { tagActionMenuFor = it },
                    onBack = onBack,
                )
            }
        }

        FullscreenImageViewer(
            posts = posts,
            initialIndex = pagerState.currentPage,
            visible = showFullscreen,
            onDismiss = { showFullscreen = false },
            onPageChanged = { newIndex ->
                coroutineScope.launch { pagerState.scrollToPage(newIndex) }
            },
        )
    }

    if (showSaveDialog) {
        val currentPost = posts.getOrNull(pagerState.currentPage)
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("保存图片") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SaveVariantButton("预览图") {
                        currentPost?.let { viewModel.saveImage(it, SaveImageVariant.Preview) }
                        showSaveDialog = false
                    }
                    SaveVariantButton("Sample") {
                        currentPost?.let { viewModel.saveImage(it, SaveImageVariant.Sample) }
                        showSaveDialog = false
                    }
                    SaveVariantButton("高清") {
                        currentPost?.let { viewModel.saveImage(it, SaveImageVariant.High) }
                        showSaveDialog = false
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                OutlinedButton(onClick = { showSaveDialog = false }) {
                    Text("取消")
                }
            },
        )
    }

    val menuTag = tagActionMenuFor
    if (menuTag != null) {
        AlertDialog(
            onDismissRequest = { tagActionMenuFor = null },
            title = { Text("标签：$menuTag") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = {
                        viewModel.addTagToWallpaperSearch(menuTag)
                        tagActionMenuFor = null
                    }) { Text("设为壁纸搜索标签") }
                    TextButton(onClick = {
                        viewModel.addTagToLockscreenSearch(menuTag)
                        tagActionMenuFor = null
                    }) { Text("设为锁屏搜索标签") }
                    TextButton(onClick = {
                        viewModel.addTagToWallpaperBlacklist(menuTag)
                        tagActionMenuFor = null
                    }) { Text("设为壁纸黑名单标签") }
                    TextButton(onClick = {
                        viewModel.addTagToLockscreenBlacklist(menuTag)
                        tagActionMenuFor = null
                    }) { Text("设为锁屏黑名单标签") }
                    TextButton(onClick = {
                        translationDraft = state.resolvedTags.find { t -> t.name == menuTag }?.zhName?.takeIf { it.isNotBlank() }
                            ?: menuTag
                        translationFor = menuTag
                        tagActionMenuFor = null
                    }) { Text("翻译或备注") }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { tagActionMenuFor = null }) { Text("取消") }
            },
        )
    }

    val zhTag = translationFor
    if (zhTag != null) {
        AlertDialog(
            onDismissRequest = { translationFor = null },
            title = { Text("翻译或备注") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("标签：$zhTag", style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(
                        value = translationDraft,
                        onValueChange = { v -> translationDraft = v },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("中文或备注") },
                        minLines = 2,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.saveTagTranslation(zhTag, translationDraft)
                    translationFor = null
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { translationFor = null }) { Text("取消") }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun PostDetailPage(
    post: Post,
    state: DetailUiState,
    onImageClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onSave: () -> Unit,
    onApplyWallpaper: () -> Unit,
    onApplyLockScreen: () -> Unit,
    onOpenPost: () -> Unit,
    onOpenSource: (String) -> Unit,
    onTagClick: (String) -> Unit,
    onTagLongPress: (String) -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AsyncImage(
            model = post.sampleUrl ?: post.jpegUrl ?: post.fileUrl ?: post.previewUrl,
            contentDescription = post.tags,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onImageClick),
            contentScale = ContentScale.FillWidth,
        )

        Text("上传者：${post.author.ifBlank { post.creatorId ?: "未知" }}")
        Text("图片大小：${post.width} x ${post.height}")
        Text("分数：${post.score}")
        Text("上传日期：${formatTimestamp(post.createdAtEpochSeconds)}")
        Text("评级：${post.rating.displayName()}")
        post.source?.takeIf { it.isNotBlank() }?.let {
            Text("图源：${normalizeSourceUrl(it)}", style = MaterialTheme.typography.bodyMedium)
        }
        Text("保存目录：${state.saveConfig.directoryLabel}", style = MaterialTheme.typography.bodySmall)

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.isFavorited) {
                Button(onClick = onFavoriteToggle) { Text("取消收藏") }
            } else {
                OutlinedButton(onClick = onFavoriteToggle) { Text("收藏") }
            }
            Button(onClick = onSave) { Text("保存") }
            Button(onClick = onApplyWallpaper) { Text("设为壁纸") }
            Button(onClick = onApplyLockScreen) { Text("设为锁屏") }
            OutlinedButton(onClick = onOpenPost) { Text("打开原贴") }
            post.source?.takeIf { it.isNotBlank() }?.let { src ->
                OutlinedButton(onClick = { onOpenSource(src) }) { Text("打开图源") }
            }
            OutlinedButton(onClick = onBack) { Text("返回") }
        }

        Text("标签", style = MaterialTheme.typography.titleMedium)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.resolvedTags.forEach { resolved ->
                val color = tagTypeColor(resolved.type)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                    modifier = Modifier.combinedClickable(
                        onClick = { onTagClick(resolved.name) },
                        onLongClick = { onTagLongPress(resolved.name) },
                    ),
                ) {
                    Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                        if (resolved.zhName != null) {
                            Text(resolved.zhName, color = color, style = MaterialTheme.typography.bodyMedium)
                            Text(resolved.name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Text(resolved.name, color = color, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SaveVariantButton(label: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(label) }
}

private fun normalizeSourceUrl(source: String): String {
    if (source.startsWith("https://i.pximg.net/img-original/img/")) {
        val illustId = source.substringAfterLast('/').substringBefore('_')
        return "https://www.pixiv.net/member_illust.php?mode=medium&illust_id=$illustId"
    }
    return source
}

private fun formatTimestamp(epochSeconds: Long): String {
    if (epochSeconds <= 0) return "未知"
    return Instant.ofEpochSecond(epochSeconds)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("yyyy/M/d HH:mm:ss"))
}
