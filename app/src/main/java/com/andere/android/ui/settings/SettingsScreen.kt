package com.andere.android.ui.settings

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.andere.android.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import coil.compose.AsyncImage
import com.andere.android.data.local.TagSuggestionService
import com.andere.android.data.local.TagTranslationRepository
import com.andere.android.data.local.WallpaperRecordEntity
import com.andere.android.domain.model.BackgroundTarget
import com.andere.android.domain.model.BrowseImageSize
import com.andere.android.domain.model.CropMode
import com.andere.android.domain.model.ImageQuality
import com.andere.android.domain.model.SaveImageConfig
import com.andere.android.domain.model.WallpaperRefreshConfig
import com.andere.android.system.WallpaperScheduler
import com.andere.android.ui.common.TagSuggestTextField
import com.andere.android.ui.displayName
import com.andere.android.ui.targetNameDisplayName
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    tagSuggestionService: TagSuggestionService,
    tagTranslationRepository: TagTranslationRepository? = null,
    onOpenRecord: (Long) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var showWorkLogSheet by remember { mutableStateOf(false) }
    val tabIcons = if (state.isLockscreenSupported)
        listOf(R.drawable.ic_settings, R.drawable.ic_photo, R.drawable.ic_lock)
    else
        listOf(R.drawable.ic_settings, R.drawable.ic_photo)
    val tabLabels = if (state.isLockscreenSupported)
        listOf("常规", "壁纸", "锁屏")
    else
        listOf("常规", "壁纸")
    var titleTapCount by remember { mutableStateOf(0) }
    var showPatDialog by remember { mutableStateOf(false) }
    val pagerState = rememberPagerState(pageCount = { tabLabels.size })
    val coroutineScope = rememberCoroutineScope()
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val folderPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        val pickedLabel = treeUriLabel(uri)
        when (tabLabels.getOrNull(pagerState.currentPage)) {
            "锁屏" -> viewModel.updateLsSaveConfig {
                it.copy(directoryUri = uri.toString(), directoryLabel = pickedLabel)
            }
            else -> viewModel.updateWpSaveConfig {
                it.copy(directoryUri = uri.toString(), directoryLabel = pickedLabel)
            }
        }
    }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { snackbarHostState.showSnackbar(it) }
    }

    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                viewModel.refreshRecentRecords()
                delay(2_000)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = {
                Text(
                    "设置",
                    modifier = Modifier.clickable(
                        indication = null,
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    ) {
                        titleTapCount++
                        if (titleTapCount >= 5) {
                            titleTapCount = 0
                            if (state.hasGithubPat) {
                                viewModel.saveGithubPat("")
                            } else {
                                showPatDialog = true
                            }
                        }
                    },
                )
            })
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (tabLabels.size > 1) {
                PrimaryTabRow(selectedTabIndex = pagerState.currentPage) {
                    tabLabels.forEachIndexed { index, title ->
                        Tab(
                            selected = pagerState.currentPage == index,
                            onClick = { coroutineScope.launch { pagerState.animateScrollToPage(index) } },
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(title)
                                    Spacer(Modifier.width(4.dp))
                                    Icon(
                                        painter = painterResource(tabIcons[index]),
                                        contentDescription = title,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            },
                        )
                    }
                }
            }

            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                when (tabLabels.getOrNull(page)) {
                    "常规" -> GeneralSection(
                        autoSyncTags = state.autoSyncTags,
                        onToggleAutoSync = viewModel::setAutoSyncTags,
                        isSyncingTags = state.isSyncingTags,
                        onSyncTags = viewModel::syncTags,
                        hasGithubPat = state.hasGithubPat,
                        isUploadingTags = state.isUploadingTags,
                        onUploadTags = viewModel::uploadTags,
                        tagSyncMessage = state.tagSyncMessage,
                        onDismissMessage = viewModel::dismissTagSyncMessage,
                        browseImageSize = state.browseImageSize,
                        onBrowseImageSizeChanged = viewModel::setBrowseImageSize,
                    )
                    "壁纸" -> TargetConfigSection(
                        label = "壁纸",
                        config = state.wpConfig,
                        isRefreshing = state.refreshingTarget == BackgroundTarget.Wallpaper,
                        onUpdateConfig = viewModel::updateWpConfig,
                        onToggleEnabled = { enabled ->
                            if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            viewModel.toggleWpEnabled(enabled)
                        },
                        onRefreshNow = { viewModel.refreshNow(BackgroundTarget.Wallpaper) },
                        tagSuggestionService = tagSuggestionService,
                        tagTranslationRepository = tagTranslationRepository,
                        saveConfig = state.wpSaveConfig,
                        onUpdateSaveConfig = viewModel::updateWpSaveConfig,
                        onPickFolder = { folderPickerLauncher.launch(null) },
                        recentRecords = state.wpRecords,
                        onOpenRecord = onOpenRecord,
                        onClearRecords = { viewModel.clearRecords(BackgroundTarget.Wallpaper) },
                        onShowWorkLogs = { showWorkLogSheet = true },
                    )
                    "锁屏" -> TargetConfigSection(
                        label = "锁屏",
                        showUseWallpaperImageToggle = true,
                        useWallpaperImage = state.lsConfig.useWallpaperImage,
                        onToggleUseWallpaperImage = { checked ->
                            viewModel.updateLsConfig { it.copy(useWallpaperImage = checked) }
                        },
                        config = state.lsConfig,
                        isRefreshing = state.refreshingTarget == BackgroundTarget.LockScreen,
                        onUpdateConfig = viewModel::updateLsConfig,
                        onToggleEnabled = { enabled ->
                            if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            viewModel.toggleLsEnabled(enabled)
                        },
                        onRefreshNow = { viewModel.refreshNow(BackgroundTarget.LockScreen) },
                        tagSuggestionService = tagSuggestionService,
                        tagTranslationRepository = tagTranslationRepository,
                        saveConfig = state.lsSaveConfig,
                        onUpdateSaveConfig = viewModel::updateLsSaveConfig,
                        onPickFolder = { folderPickerLauncher.launch(null) },
                        recentRecords = state.lsRecords,
                        onOpenRecord = onOpenRecord,
                        onClearRecords = { viewModel.clearRecords(BackgroundTarget.LockScreen) },
                        onShowWorkLogs = { showWorkLogSheet = true },
                    )
                }
            }
        }
    }

    if (showWorkLogSheet) {
        WorkManagerLogSheet(
            wpLastRecord = state.wpRecords.firstOrNull(),
            lsLastRecord = state.lsRecords.firstOrNull(),
            onDismiss = { showWorkLogSheet = false },
        )
    }

    if (showPatDialog) {
        var patInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showPatDialog = false },
            title = { Text("输入密钥") },
            text = {
                OutlinedTextField(
                    value = patInput,
                    onValueChange = { patInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("GitHub Personal Access Token") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (patInput.isNotBlank()) {
                        viewModel.saveGithubPat(patInput.trim())
                    }
                    showPatDialog = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showPatDialog = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun GeneralSection(
    autoSyncTags: Boolean,
    onToggleAutoSync: (Boolean) -> Unit,
    isSyncingTags: Boolean,
    onSyncTags: () -> Unit,
    hasGithubPat: Boolean,
    isUploadingTags: Boolean,
    onUploadTags: () -> Unit,
    tagSyncMessage: String?,
    onDismissMessage: () -> Unit,
    browseImageSize: BrowseImageSize,
    onBrowseImageSizeChanged: (BrowseImageSize) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("浏览", style = MaterialTheme.typography.titleMedium)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("图片尺寸", modifier = Modifier.weight(1f))
            BrowseImageSize.entries.forEach { size ->
                FilterChip(
                    selected = browseImageSize == size,
                    onClick = { onBrowseImageSizeChanged(size) },
                    label = { Text(size.label) },
                )
            }
        }

        HorizontalDivider()

        Text("标签翻译同步", style = MaterialTheme.typography.titleMedium)
        Text(
            "从 GitHub 仓库下载最新的标签中文翻译库。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Button(
            onClick = onSyncTags,
            enabled = !isSyncingTags,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isSyncingTags) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                Spacer(Modifier.width(8.dp))
                Text("同步中…")
            } else {
                Text("手动同步标签翻译")
            }
        }

        LabeledSwitch("自动同步", autoSyncTags, onToggleAutoSync)
        if (autoSyncTags) {
            Text(
                "每隔 1 小时自动同步一次。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (hasGithubPat) {
            HorizontalDivider()
            Text("上传翻译库", style = MaterialTheme.typography.titleMedium)
            Text(
                "将本机全部翻译（含手动修改）上传到 GitHub 仓库。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onUploadTags,
                enabled = !isUploadingTags,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isUploadingTags) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text("上传中…")
                } else {
                    Text("上传所有标签翻译")
                }
            }
        }

        tagSyncMessage?.let { msg ->
            LaunchedEffect(msg) {
                delay(4_000)
                onDismissMessage()
            }
            Surface(
                shape = MaterialTheme.shapes.small,
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    msg,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TargetConfigSection(
    label: String,
    showUseWallpaperImageToggle: Boolean = false,
    useWallpaperImage: Boolean = false,
    onToggleUseWallpaperImage: (Boolean) -> Unit = {},
    config: WallpaperRefreshConfig,
    isRefreshing: Boolean,
    onUpdateConfig: ((WallpaperRefreshConfig) -> WallpaperRefreshConfig) -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onRefreshNow: () -> Unit,
    tagSuggestionService: TagSuggestionService,
    tagTranslationRepository: TagTranslationRepository? = null,
    saveConfig: SaveImageConfig,
    onUpdateSaveConfig: ((SaveImageConfig) -> SaveImageConfig) -> Unit,
    onPickFolder: () -> Unit,
    recentRecords: List<WallpaperRecordEntity>,
    onOpenRecord: (Long) -> Unit,
    onClearRecords: () -> Unit,
    onShowWorkLogs: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (showUseWallpaperImageToggle) {
            LabeledSwitch("使用和壁纸同一图片", useWallpaperImage, onToggleUseWallpaperImage)
        }

        if (showUseWallpaperImageToggle && useWallpaperImage) {
            Text(
                "开启后，锁屏会跟随壁纸一起更新，锁屏自己的定时刷新和其他设置会暂时隐藏。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        LabeledSwitch("启用定时刷新", config.enabled, onToggleEnabled)
        LabeledSwitch("仅在非计费网络下刷新", config.wifiOnly) { checked -> onUpdateConfig { it.copy(wifiOnly = checked) } }
        LabeledSwitch("更换后发送通知", config.notificationEnabled) { checked -> onUpdateConfig { it.copy(notificationEnabled = checked) } }

        TagSuggestTextField(
            value = config.query,
            onValueChange = { text -> onUpdateConfig { it.copy(query = text) } },
            label = "搜索标签",
            tagSuggestionService = tagSuggestionService,
            tagTranslationRepository = tagTranslationRepository,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = config.shuffleCount.toString(),
                onValueChange = { text ->
                    text.toIntOrNull()?.let { v -> onUpdateConfig { it.copy(shuffleCount = v.coerceIn(1, 20)) } }
                },
                modifier = Modifier.weight(1f),
                label = { Text("随机范围") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            OutlinedTextField(
                value = config.intervalMinutes.toString(),
                onValueChange = { text ->
                    text.toLongOrNull()?.let { v -> onUpdateConfig { it.copy(intervalMinutes = v.coerceAtLeast(15)) } }
                },
                modifier = Modifier.weight(1f),
                label = { Text("间隔（分钟）") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        }

        EnumSelector("图片质量", ImageQuality.entries, config.quality, { s -> onUpdateConfig { it.copy(quality = s) } }, { it.displayName() })
        EnumSelector("裁切方式", CropMode.entries, config.cropMode, { s -> onUpdateConfig { it.copy(cropMode = s) } }, { it.displayName() })

        Text("筛选", style = MaterialTheme.typography.titleMedium)
        MultiSelectFilterGroup(
            label = "评级",
            options = listOf("安全" to config.filter.allowSafe, "可疑" to config.filter.allowQuestionable, "明显" to config.filter.allowExplicit),
            onToggle = { l, c ->
                onUpdateConfig { cfg ->
                    cfg.copy(filter = when (l) {
                        "安全" -> cfg.filter.copy(allowSafe = c)
                        "可疑" -> cfg.filter.copy(allowQuestionable = c)
                        else -> cfg.filter.copy(allowExplicit = c)
                    })
                }
            },
        )
        MultiSelectFilterGroup(
            label = "长阔比",
            options = listOf(
                "根据屏幕方向" to config.filter.useScreenOrientation,
                "横向" to (config.filter.allowHorizontal && !config.filter.useScreenOrientation),
                "纵向" to (config.filter.allowVertical && !config.filter.useScreenOrientation),
            ),
            onToggle = { l, c ->
                onUpdateConfig { cfg ->
                    cfg.copy(filter = when (l) {
                        "根据屏幕方向" -> if (c) cfg.filter.copy(useScreenOrientation = true, allowHorizontal = false, allowVertical = false)
                        else cfg.filter.copy(useScreenOrientation = false, allowHorizontal = true)
                        "横向" -> cfg.filter.copy(allowHorizontal = c, useScreenOrientation = false)
                        else -> cfg.filter.copy(allowVertical = c, useScreenOrientation = false)
                    })
                }
            },
        )
        FilterRow("显示被隐藏的图片", config.filter.allowHidden) { checked -> onUpdateConfig { it.copy(filter = it.filter.copy(allowHidden = checked)) } }
        FilterRow("显示被拦截的图片", config.filter.allowHeld) { checked -> onUpdateConfig { it.copy(filter = it.filter.copy(allowHeld = checked)) } }
        TagSuggestTextField(
            value = config.filter.tagBlacklist,
            onValueChange = { text -> onUpdateConfig { it.copy(filter = it.filter.copy(tagBlacklist = text)) } },
            label = "黑名单标签",
            tagSuggestionService = tagSuggestionService,
            tagTranslationRepository = tagTranslationRepository,
            modifier = Modifier.fillMaxWidth(),
        )

        Button(
            onClick = onRefreshNow,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isRefreshing) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Text("立即刷新$label")
            }
        }
        OutlinedButton(
            onClick = onShowWorkLogs,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("查看 WorkManager 日志")
        }

        HorizontalDivider()

        Text("保存设置", style = MaterialTheme.typography.titleMedium)
        Text("当前目录：${saveConfig.directoryLabel}", style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onPickFolder) { Text("选择目录") }
            OutlinedButton(onClick = { onUpdateSaveConfig { it.copy(directoryUri = null, directoryLabel = "Pictures") } }) { Text("恢复默认") }
        }

        HorizontalDivider()

        Text("最近记录", style = MaterialTheme.typography.titleMedium)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = config.displayRecordCount.toString(),
                onValueChange = { text ->
                    text.toIntOrNull()?.let { v -> onUpdateConfig { it.copy(displayRecordCount = v.coerceIn(1, 100)) } }
                },
                modifier = Modifier.weight(1f),
                label = { Text("显示条数") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            OutlinedTextField(
                value = config.recordRetentionDays.toString(),
                onValueChange = { text ->
                    text.toIntOrNull()?.let { v -> onUpdateConfig { it.copy(recordRetentionDays = v.coerceIn(1, 365)) } }
                },
                modifier = Modifier.weight(1f),
                label = { Text("去重保留（天）") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        }
        OutlinedButton(onClick = onClearRecords) { Text("清空历史记录") }

        val recordListState = rememberLazyListState()
        val latestRecordId = recentRecords.firstOrNull()?.id
        LaunchedEffect(latestRecordId) {
            if (latestRecordId != null) {
                recordListState.scrollToItem(0)
            }
        }
        LazyRow(state = recordListState, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(recentRecords, key = { it.id }) { record ->
                Card(modifier = Modifier.width(140.dp).clickable { onOpenRecord(record.postId) }) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        AsyncImage(
                            model = record.previewUrl,
                            contentDescription = record.postId.toString(),
                            modifier = Modifier.fillMaxWidth().height(180.dp),
                            contentScale = ContentScale.Crop,
                        )
                        Text("图片 ${record.postId}", style = MaterialTheme.typography.bodySmall)
                        Text(targetNameDisplayName(record.target), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

private fun treeUriLabel(uri: android.net.Uri): String {
    val documentId = DocumentsContract.getTreeDocumentId(uri)
    return documentId.substringAfter(':', documentId).ifBlank { "Pictures" }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkManagerLogSheet(
    wpLastRecord: WallpaperRecordEntity?,
    lsLastRecord: WallpaperRecordEntity?,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val workManager = remember(context) { WorkManager.getInstance(context) }
    val now by produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            delay(1_000)
            value = System.currentTimeMillis()
        }
    }
    val logEntries by remember(workManager) { workManagerLogFlow(workManager) }
        .collectAsState(initial = emptyList())
    val targetSummaries = remember(logEntries, wpLastRecord, lsLastRecord, now) {
        buildWorkLogSummaries(
            entries = logEntries,
            latestRecords = mapOf(
                BackgroundTarget.Wallpaper to wpLastRecord,
                BackgroundTarget.LockScreen to lsLastRecord,
            ),
            now = now,
        )
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("WorkManager 日志", style = MaterialTheme.typography.titleLarge)
            Text(
                "显示最近实际刷新时间、当前任务是否还活着，以及下次可运行时间",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (targetSummaries.isEmpty()) {
                Text("当前没有已注册的 WorkManager 任务。")
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(targetSummaries, key = { it.target.name }) { summary ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            tonalElevation = 1.dp,
                            shape = MaterialTheme.shapes.medium,
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    summary.targetLabel,
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    "上次实际刷新：${summary.lastRefreshText}",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    "WorkManager 存活：${if (summary.hasAliveWork) "在" else "不在"}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Text(
                                    summary.overviewText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                summary.works.forEach { entry ->
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        tonalElevation = 0.dp,
                                        shape = MaterialTheme.shapes.small,
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(10.dp),
                                            verticalArrangement = Arrangement.spacedBy(2.dp),
                                        ) {
                                            Text(
                                                entry.kindLabel,
                                                style = MaterialTheme.typography.labelLarge,
                                            )
                                            Text(
                                                "状态：${entry.stateLabel}",
                                                style = MaterialTheme.typography.bodySmall,
                                            )
                                            Text(
                                                "计时：${entry.timingText}",
                                                style = MaterialTheme.typography.bodySmall,
                                            )
                                            Text(
                                                "下次可运行：${entry.nextRunText}",
                                                style = MaterialTheme.typography.bodySmall,
                                            )
                                            Text(
                                                "周期：${entry.scheduleText}",
                                                style = MaterialTheme.typography.bodySmall,
                                            )
                                            Text(
                                                "重试次数：${entry.runAttemptCount}  Work ID：${entry.workId}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class WorkLogEntry(
    val key: String,
    val target: BackgroundTarget,
    val targetLabel: String,
    val kindLabel: String,
    val stateLabel: String,
    val isTracked: Boolean,
    val isAlive: Boolean,
    val runAttemptCount: Int,
    val workId: String,
    val nextScheduleTimeMillis: Long?,
    val repeatIntervalMillis: Long?,
    val scheduleText: String,
    val timingText: String,
    val nextRunText: String,
)

private data class WorkLogSummary(
    val target: BackgroundTarget,
    val targetLabel: String,
    val lastRefreshText: String,
    val hasAliveWork: Boolean,
    val overviewText: String,
    val works: List<WorkLogEntry>,
)

private fun workManagerLogFlow(workManager: WorkManager): Flow<List<WorkLogEntry>> {
    val names = listOf(
        BackgroundTarget.Wallpaper to WallpaperScheduler.periodicWorkName(BackgroundTarget.Wallpaper),
        BackgroundTarget.Wallpaper to WallpaperScheduler.immediateWorkName(BackgroundTarget.Wallpaper),
        BackgroundTarget.LockScreen to WallpaperScheduler.periodicWorkName(BackgroundTarget.LockScreen),
        BackgroundTarget.LockScreen to WallpaperScheduler.immediateWorkName(BackgroundTarget.LockScreen),
    )
    val flows = names.map { (target, workName) ->
        workManager.getWorkInfosForUniqueWorkFlow(workName).map { infos ->
            buildWorkLogEntries(target, workName, infos)
        }
    }
    return combine(flows) { groups -> groups.flatMap { it } }
}

private fun buildWorkLogEntries(
    target: BackgroundTarget,
    workName: String,
    infos: List<WorkInfo>,
): List<WorkLogEntry> {
    val targetLabel = if (target == BackgroundTarget.Wallpaper) "壁纸" else "锁屏"
    val kindLabel = if ("periodic" in workName) "定时任务" else "即时任务"
    val refreshedAt = formatWorkLogTime(System.currentTimeMillis())
    if (infos.isEmpty()) {
        return listOf(
            WorkLogEntry(
                key = "$workName-empty",
                target = target,
                targetLabel = targetLabel,
                kindLabel = kindLabel,
                stateLabel = "未注册",
                isTracked = false,
                isAlive = false,
                runAttemptCount = 0,
                workId = "-",
                nextScheduleTimeMillis = null,
                repeatIntervalMillis = null,
                scheduleText = "未注册",
                timingText = "未开始计时",
                nextRunText = "-",
            ),
        )
    }
    val info = infos.maxWithOrNull(
        compareBy<WorkInfo>(
            { workStatePriority(it.state) },
            { it.generation },
            { it.runAttemptCount },
        ),
    ) ?: return emptyList()
    val nextScheduleTimeMillis = info.nextScheduleTimeMillis.takeUnless { it == Long.MAX_VALUE }
    val repeatIntervalMillis = info.periodicityInfo?.repeatIntervalMillis
    return listOf(
        WorkLogEntry(
            key = "${workName}-${info.id}",
            target = target,
            targetLabel = targetLabel,
            kindLabel = kindLabel,
            stateLabel = workStateLabel(info.state),
            isTracked = true,
            isAlive = info.state in setOf(WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING, WorkInfo.State.BLOCKED),
            runAttemptCount = info.runAttemptCount,
            workId = info.id.toString(),
            nextScheduleTimeMillis = nextScheduleTimeMillis,
            repeatIntervalMillis = repeatIntervalMillis,
            scheduleText = formatScheduleText(info),
            timingText = "已于 $refreshedAt 更新状态",
            nextRunText = nextScheduleTimeMillis?.let(::formatWorkLogTime) ?: "-",
        ),
    )
}

private fun workStateLabel(state: WorkInfo.State): String = when (state) {
    WorkInfo.State.ENQUEUED -> "已排队"
    WorkInfo.State.RUNNING -> "运行中"
    WorkInfo.State.SUCCEEDED -> "已成功"
    WorkInfo.State.FAILED -> "失败"
    WorkInfo.State.BLOCKED -> "阻塞中"
    WorkInfo.State.CANCELLED -> "已取消"
}

private fun buildWorkLogSummaries(
    entries: List<WorkLogEntry>,
    latestRecords: Map<BackgroundTarget, WallpaperRecordEntity?>,
    now: Long,
): List<WorkLogSummary> {
    val entriesByTarget = entries.groupBy { it.target }
    return listOf(BackgroundTarget.Wallpaper, BackgroundTarget.LockScreen).mapNotNull { target ->
        val works = entriesByTarget[target].orEmpty().sortedBy { it.kindLabel }
        if (works.isEmpty() && latestRecords[target] == null) {
            return@mapNotNull null
        }
        val targetLabel = if (target == BackgroundTarget.Wallpaper) "壁纸" else "锁屏"
        val lastRefreshText = latestRecords[target]?.createdAtMillis?.let {
            "${formatWorkLogTime(it)} (${formatRelativeDuration(now - it)}前)"
        } ?: "暂无成功刷新记录"
        val hasAliveWork = works.any { it.isAlive }
        val trackedCount = works.count { it.isTracked }
        val overviewText = when {
            hasAliveWork -> "当前有 ${works.count { it.isAlive }} 个任务仍在 WorkManager 队列里。"
            trackedCount > 0 -> "当前没有活动任务，WorkManager 里保留了最近一次状态。"
            else -> "当前还没有向 WorkManager 注册任务。"
        }
        WorkLogSummary(
            target = target,
            targetLabel = targetLabel,
            lastRefreshText = lastRefreshText,
            hasAliveWork = hasAliveWork,
            overviewText = overviewText,
            works = works.map { entry -> entry.enrichTiming(now) },
        )
    }
}

private fun WorkLogEntry.enrichTiming(now: Long): WorkLogEntry {
    if (!isTracked) return this
    val timingText = when {
        isAlive && repeatIntervalMillis != null && nextScheduleTimeMillis != null && nextScheduleTimeMillis > now -> {
            val remaining = nextScheduleTimeMillis - now
            val elapsed = (repeatIntervalMillis - remaining).coerceAtLeast(0L)
            "已计时 ${formatRelativeDuration(elapsed)}，距可运行还剩 ${formatRelativeDuration(remaining)}"
        }
        isAlive && repeatIntervalMillis != null && nextScheduleTimeMillis != null && nextScheduleTimeMillis <= now ->
            "本轮已计满 ${formatRelativeDuration(repeatIntervalMillis)}，超时等待 ${formatRelativeDuration(now - nextScheduleTimeMillis)}"
        isAlive && nextScheduleTimeMillis != null && nextScheduleTimeMillis > now ->
            "等待执行中，距可运行还剩 ${formatRelativeDuration(nextScheduleTimeMillis - now)}"
        isAlive && nextScheduleTimeMillis != null && nextScheduleTimeMillis <= now ->
            "已到可运行时间，额外等待 ${formatRelativeDuration(now - nextScheduleTimeMillis)}"
        isAlive && stateLabel == "运行中" ->
            "任务正在执行中"
        isAlive ->
            "仍在 WorkManager 跟踪中"
        else ->
            "任务已结束"
    }
    val nextRunText = nextScheduleTimeMillis?.let { scheduledAt ->
        val base = formatWorkLogTime(scheduledAt)
        if (scheduledAt > now) "$base (${formatRelativeDuration(scheduledAt - now)}后)"
        else "$base (已过 ${formatRelativeDuration(now - scheduledAt)})"
    } ?: "-"
    return copy(
        timingText = timingText,
        nextRunText = nextRunText,
    )
}

private fun formatScheduleText(info: WorkInfo): String {
    val periodicityInfo = info.periodicityInfo ?: return if (info.initialDelayMillis > 0) {
        "一次性，初始延迟 ${formatRelativeDuration(info.initialDelayMillis)}"
    } else {
        "一次性任务"
    }
    return "每 ${formatRelativeDuration(periodicityInfo.repeatIntervalMillis)}，弹性窗口 ${formatRelativeDuration(periodicityInfo.flexIntervalMillis)}"
}

private fun workStatePriority(state: WorkInfo.State): Int = when (state) {
    WorkInfo.State.RUNNING -> 5
    WorkInfo.State.ENQUEUED -> 4
    WorkInfo.State.BLOCKED -> 3
    WorkInfo.State.SUCCEEDED -> 2
    WorkInfo.State.FAILED -> 1
    WorkInfo.State.CANCELLED -> 0
}

private fun formatRelativeDuration(durationMillis: Long): String {
    val totalSeconds = abs(durationMillis) / 1_000
    val days = totalSeconds / 86_400
    val hours = (totalSeconds % 86_400) / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return when {
        days > 0 -> "${days}天${hours}小时"
        hours > 0 -> "${hours}小时${minutes}分钟"
        minutes > 0 -> "${minutes}分钟${seconds}秒"
        else -> "${seconds}秒"
    }
}

private fun formatWorkLogTime(timeMillis: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timeMillis))

@Composable
private fun LabeledSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun FilterRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> EnumSelector(label: String, options: List<T>, selected: T, onSelect: (T) -> Unit, optionLabel: (T) -> String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                if (option == selected) Button(onClick = { onSelect(option) }) { Text(optionLabel(option)) }
                else OutlinedButton(onClick = { onSelect(option) }) { Text(optionLabel(option)) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MultiSelectFilterGroup(label: String, options: List<Pair<String, Boolean>>, onToggle: (String, Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (text, selected) -> FilterChip(selected = selected, onClick = { onToggle(text, !selected) }, label = { Text(text) }) }
        }
    }
}
