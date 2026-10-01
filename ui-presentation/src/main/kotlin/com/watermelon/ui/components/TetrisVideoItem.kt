package com.watermelon.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.watermelon.common.model.MediaItem
import com.watermelon.ui.WatermelonIcons
import com.watermelon.ui.theme.WatermelonColors
import com.watermelon.ui.theme.WatermelonShapes
import com.watermelon.ui.theme.WatermelonTypography
import com.watermelon.ui.theme.WatermelonGlass

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TetrisVideoItem(
    item: MediaItem,
    isScrollingFast: Boolean,
    isSelected: Boolean,
    selectionActive: Boolean,
    showThumbnails: Boolean = true,
    showDurations: Boolean = true,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onExtractAudio: ((MediaItem) -> Unit)? = null,
    onTrimVideo: ((MediaItem) -> Unit)? = null,
    onCompressVideo: ((MediaItem) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var showMenu by remember { mutableStateOf(false) }
    val ratio = if (item.width > 0 && item.height > 0) {
        (item.width.toFloat() / item.height.toFloat()).coerceIn(0.5625f, 1.7778f)
    } else 16f / 9f

    val borderModifier = if (isSelected) {
        Modifier.background(WatermelonColors.Accent.copy(alpha = 0.16f), WatermelonShapes.small)
            .border(2.dp, WatermelonColors.Accent, WatermelonShapes.small)
    } else {
        Modifier.background(WatermelonGlass.cardSurface(), WatermelonShapes.small)
            .border(1.dp, WatermelonGlass.borderColor(), WatermelonShapes.small)
    }

    Column(
        modifier = modifier.clip(WatermelonShapes.small).then(borderModifier)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().aspectRatio(ratio)
                .clip(WatermelonShapes.small)
                .background(WatermelonGlass.cardSurface()),
        ) {
            if (showThumbnails) {
                VelocityGuardImage(
                    uri = item.uri,
                    durationMs = item.durationMs,
                    isScrollingFast = isScrollingFast,
                    modifier = Modifier.matchParentSize(),
                )
            } else {
                Box(Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                    WatermelonGlyph(
                        icon = WatermelonIcons.VideoUnavailable,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }

            if (showDurations) {
                Text(
                    text = formatTetrisDuration(item.durationMs),
                    style = WatermelonTypography.timecode,
                    color = WatermelonColors.Palette.PaperWhite,
                    modifier = Modifier.align(Alignment.BottomEnd)
                        .background(WatermelonGlass.scrim.copy(alpha = 0.72f), RoundedCornerShape(6.dp)),
                )
            }

            if (!selectionActive) {
                androidx.compose.material3.IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.align(Alignment.TopEnd).size(34.dp),
                ) {
                    WatermelonGlyph(
                        icon = WatermelonIcons.MoreVert,
                        contentDescription = "More options",
                        tint = WatermelonColors.Palette.PaperWhite,
                    )
                }
                androidx.compose.material3.IconButton(
                    onClick = onClick,
                    modifier = Modifier.align(Alignment.Center).size(46.dp),
                ) {
                    WatermelonGlyph(
                        icon = WatermelonIcons.Play,
                        contentDescription = "Play",
                        tint = WatermelonColors.Palette.PaperWhite,
                    )
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = item.displayName,
                style = WatermelonTypography.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (item.lastPlayedAt == null) StatusBadge.New(compact = true)
        }

        if (showMenu) {
            VideoItemContextMenu(
                expanded = true,
                onDismiss = { showMenu = false },
                item = item,
                onExtractAudio = onExtractAudio,
                onTrimVideo = onTrimVideo,
                onCompressVideo = onCompressVideo,
            )
        }
    }
}

private fun formatTetrisDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%d:%02d".format(minutes, seconds)
}
