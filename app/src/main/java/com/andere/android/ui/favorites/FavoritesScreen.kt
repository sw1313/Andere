package com.andere.android.ui.favorites

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.andere.android.data.local.FavoritePostEntity

@Composable
fun FavoritesScreen(
    viewModel: FavoritesViewModel,
    onOpenFavorite: (Long) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    if (state.favorites.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("收藏", style = MaterialTheme.typography.headlineSmall)
            Text("还没有本地收藏。", style = MaterialTheme.typography.bodyMedium)
        }
        return
    }

    FavoriteGrid(
        favorites = state.favorites,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 24.dp),
    ) { row ->
        FavoriteRow(
            row = row,
            onOpenFavorite = onOpenFavorite,
        )
    }
}

@Composable
private fun FavoriteGrid(
    favorites: List<FavoritePostEntity>,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    rowContent: @Composable (FavoriteRowData) -> Unit,
) {
    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val spacing = 6.dp
        val rows = androidx.compose.runtime.remember(favorites, maxWidth) {
            val widthPx = with(density) { maxWidth.roundToPx() }
            buildFavoriteRows(
                favorites = favorites,
                containerWidthPx = widthPx,
                spacingPx = with(density) { spacing.roundToPx() },
                targetRowHeightPx = with(density) { 180.dp.roundToPx() },
                minRowHeightPx = with(density) { 120.dp.roundToPx() },
                maxRowHeightPx = with(density) { 240.dp.roundToPx() },
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(spacing),
        ) {
            itemsIndexed(rows, key = { _, row -> row.key }) { _, row ->
                rowContent(row)
            }
        }
    }
}

@Composable
private fun FavoriteRow(
    row: FavoriteRowData,
    onOpenFavorite: (Long) -> Unit,
) {
    val density = LocalDensity.current
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(with(density) { row.heightPx.toDp() }),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        row.favorites.forEach { favorite ->
            val ratio = favorite.aspectRatio.toFloat().coerceAtLeast(0.4f)
            Box(
                modifier = Modifier
                    .weight(ratio)
                    .fillMaxSize()
                    .clickable { onOpenFavorite(favorite.postId) },
            ) {
                AsyncImage(
                    model = favorite.previewUrl,
                    contentDescription = favorite.tags,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }
        }
    }
}

private data class FavoriteRowData(
    val favorites: List<FavoritePostEntity>,
    val heightPx: Int,
) {
    val key: String = favorites.joinToString(separator = "-") { it.postId.toString() }
}

private val FavoritePostEntity.aspectRatio: Double
    get() = width.toDouble().coerceAtLeast(1.0) / height.toDouble().coerceAtLeast(1.0)

private fun buildFavoriteRows(
    favorites: List<FavoritePostEntity>,
    containerWidthPx: Int,
    spacingPx: Int,
    targetRowHeightPx: Int,
    minRowHeightPx: Int,
    maxRowHeightPx: Int,
): List<FavoriteRowData> {
    if (favorites.isEmpty() || containerWidthPx <= 0) return emptyList()

    val rows = mutableListOf<FavoriteRowData>()
    val current = mutableListOf<FavoritePostEntity>()
    var aspectSum = 0.0

    fun flushRow(isLast: Boolean) {
        if (current.isEmpty()) return
        val gaps = (current.size - 1).coerceAtLeast(0) * spacingPx
        val rowHeightPx = if (isLast) {
            targetRowHeightPx.coerceIn(minRowHeightPx, maxRowHeightPx)
        } else {
            ((containerWidthPx - gaps) / aspectSum).toInt().coerceIn(minRowHeightPx, maxRowHeightPx)
        }
        rows += FavoriteRowData(
            favorites = current.toList(),
            heightPx = rowHeightPx,
        )
        current.clear()
        aspectSum = 0.0
    }

    favorites.forEach { favorite ->
        current += favorite
        aspectSum += favorite.aspectRatio.coerceAtLeast(0.4)
        val gaps = (current.size - 1).coerceAtLeast(0) * spacingPx
        val estimatedHeight = ((containerWidthPx - gaps) / aspectSum).toInt()
        if (current.size > 1 && estimatedHeight <= targetRowHeightPx) {
            flushRow(isLast = false)
        }
    }

    flushRow(isLast = true)
    return rows
}
