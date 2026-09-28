package com.watermelon.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.watermelon.common.model.MediaItem

private data class TetrisRow(val items: List<MediaItem>, val aspectSum: Float)

private fun buildTetrisRows(items: List<MediaItem>, targetWidthDp: Float, targetRowHeightDp: Float = 150f): List<TetrisRow> {
    if (items.isEmpty()) return emptyList()
    val rows = mutableListOf<TetrisRow>()
    val current = mutableListOf<MediaItem>()
    var aspectSum = 0f
    val targetWidth = targetWidthDp.coerceAtLeast(1f)
    val gap = 10f
    fun ratio(item: MediaItem): Float =
        if (item.width > 0 && item.height > 0) {
            (item.width.toFloat() / item.height.toFloat()).coerceIn(0.5625f, 1.7778f)
        } else 16f / 9f

    for (item in items) {
        val nextSum = aspectSum + ratio(item)
        val nextWidth = nextSum * targetRowHeightDp + (current.size + 1) * gap
        if (current.isNotEmpty() && nextWidth > targetWidth) {
            rows += TetrisRow(current.toList(), aspectSum)
            current.clear()
            aspectSum = 0f
        }
        current += item
        aspectSum += ratio(item)
    }
    if (current.isNotEmpty()) rows += TetrisRow(current.toList(), aspectSum)
    return rows
}

@Composable
fun TetrisVideoLayout(
    items: List<MediaItem>,
    isScrollingFast: Boolean,
    isSelected: (MediaItem) -> Boolean,
    selectionActive: Boolean,
    showThumbnails: Boolean,
    showDurations: Boolean,
    onClick: (MediaItem) -> Unit,
    onLongClick: (MediaItem) -> Unit,
    onExtractAudio: ((MediaItem) -> Unit)?,
    onTrimVideo: ((MediaItem) -> Unit)?,
    onCompressVideo: ((MediaItem) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier) {
        val rows = buildTetrisRows(items, maxWidth.value)
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            rows.forEachIndexed { rowIndex, row ->
            item(key = "tetris-$rowIndex-${row.items.firstOrNull()?.uri.orEmpty()}") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    row.items.forEach { item ->
                        val ratio = if (item.width > 0 && item.height > 0) {
                            (item.width.toFloat() / item.height.toFloat()).coerceIn(0.5625f, 1.7778f)
                        } else 16f / 9f
                        TetrisVideoItem(
                            item = item,
                            isScrollingFast = isScrollingFast,
                            isSelected = isSelected(item),
                            selectionActive = selectionActive,
                            showThumbnails = showThumbnails,
                            showDurations = showDurations,
                            onClick = { onClick(item) },
                            onLongClick = { onLongClick(item) },
                            onExtractAudio = onExtractAudio,
                            onTrimVideo = onTrimVideo,
                            onCompressVideo = onCompressVideo,
                            modifier = Modifier.weight(ratio),
                        )
                    }
                }
            }
        }
    }
}
