package com.watermelon.ui.components

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import com.watermelon.ui.R
import com.watermelon.ui.WatermelonIcons
import com.watermelon.ui.components.WatermelonGlyph
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.watermelon.common.model.FolderNode
import com.watermelon.ui.theme.WatermelonShapes
import com.watermelon.ui.theme.WatermelonGlass
import com.watermelon.ui.theme.WatermelonSpacing

/**
 * Folder item for list and grid layouts. List mode intentionally uses the folder icon;
 * representative thumbnails are reserved for the grid media presentation.
 * Shows an initial-letter icon, folder name (⭐ if unplayed files exist), file count,
 * and total playtime. Size differences are deliberately dramatic to be tangible.
 */
@Composable
fun FolderListItem(
    folder: FolderNode,
    onClick: (FolderNode) -> Unit,
    modifier: Modifier = Modifier,
    itemSize: ItemSize = ItemSize.MEDIUM,
    isGrid: Boolean = false,
    showDurations: Boolean = true,
    showFileSize: Boolean = false,
    isScrollingFast: Boolean = false,
    // Optional shared interaction source: null (default) preserves prior behavior exactly —
    // clickable() creates and owns its own source internally. Callers that need to read this
    // row's real focus/press state externally (e.g. TvFolderBrowserScreen drawing a focus
    // ring) pass their own source in, so the ring reflects the same interactions clickable()
    // itself reacts to instead of a second, disconnected one.
    interactionSource: MutableInteractionSource? = null
) {
    // Size-dependent values — gaps are large so the difference is obvious.
    val iconDp: Dp = when (itemSize) {
        ItemSize.TINY   -> if (isGrid) 30.dp else 24.dp
        ItemSize.SMALL  -> if (isGrid) 40.dp else 32.dp
        ItemSize.MEDIUM -> if (isGrid) 56.dp else 44.dp
        ItemSize.LARGE  -> if (isGrid) 72.dp else 56.dp
        ItemSize.XLARGE -> if (isGrid) 92.dp else 72.dp
    }
    // Base spacing grid steps (Team 0 tokens); LARGE nudges up half a step beyond `lg`
    // since the size scale is deliberately dramatic (see class doc) and the grid tops
    // out at `lg` for a "standard" step.
    val hPad: Dp = when (itemSize) {
        ItemSize.TINY   -> WatermelonSpacing.xs
        ItemSize.SMALL  -> WatermelonSpacing.sm
        ItemSize.MEDIUM -> WatermelonSpacing.md
        ItemSize.LARGE  -> WatermelonSpacing.lg
        ItemSize.XLARGE -> WatermelonSpacing.lg
    }
    val vPad: Dp = when (itemSize) {
        ItemSize.TINY   -> WatermelonSpacing.xs
        ItemSize.SMALL  -> WatermelonSpacing.xs + WatermelonSpacing.xs / 2
        ItemSize.MEDIUM -> WatermelonSpacing.sm + WatermelonSpacing.xs
        ItemSize.LARGE  -> WatermelonSpacing.lg - WatermelonSpacing.xs / 2
        ItemSize.XLARGE -> WatermelonSpacing.lg
    }

    val metaText = buildList {
        add("${folder.itemCount} files")
        if (showDurations) {
            add(if (folder.totalDurationMs > 0L) formatDuration(folder.totalDurationMs) else "--:--")
        }
        if (showFileSize && folder.totalSizeBytes > 0L) {
            add(formatFileSize(folder.totalSizeBytes))
        }
    }.joinToString(" · ")
    val smallMetaText = if (itemSize == ItemSize.TINY || itemSize == ItemSize.SMALL && folder.lastModifiedAt > 0L) {
        "$metaText · ${formatLastModified(folder.lastModifiedAt)}"
    } else {
        metaText
    }

    val clickMod = if (interactionSource != null) {
        modifier
            .clip(WatermelonShapes.card)
            .background(WatermelonGlass.cardSurface())
            .border(1.dp, WatermelonGlass.borderColor(), WatermelonShapes.card)
            .clickable(interactionSource = interactionSource, indication = LocalIndication.current) {
                onClick(folder)
            }
    } else {
        modifier
            .clip(WatermelonShapes.card)
            .background(WatermelonGlass.cardSurface())
            .border(1.dp, WatermelonGlass.borderColor(), WatermelonShapes.card)
            .clickable { onClick(folder) }
    }

    if (isGrid) {
        Column(
            modifier = clickMod.fillMaxWidth().padding(hPad, vPad),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(WatermelonSpacing.sm)
        ) {
            FolderThumbnailOrIcon(folder = folder, size = iconDp, isScrollingFast = isScrollingFast)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text      = folder.displayName,
                    color     = MaterialTheme.colorScheme.onSurface,
                    style     = when (itemSize) {
                        ItemSize.TINY   -> MaterialTheme.typography.labelSmall
                        ItemSize.SMALL  -> MaterialTheme.typography.bodySmall
                        ItemSize.MEDIUM -> MaterialTheme.typography.bodyMedium
                        ItemSize.LARGE  -> MaterialTheme.typography.bodyLarge
                        ItemSize.XLARGE -> MaterialTheme.typography.titleMedium
                    },
                    maxLines  = 2,
                    overflow  = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Medium
                )
                if (folder.hasNewFiles) {
                    StatusBadge.New(
                        compact = true,
                        modifier = Modifier.padding(start = WatermelonSpacing.xs / 2)
                    )
                }
            }
            Text(
                text  = smallMetaText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    } else {
        Row(
            modifier = clickMod.fillMaxWidth().padding(hPad, vPad),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FolderIcon(size = iconDp, isPlaylist = folder.isPlaylist)
            Spacer(Modifier.width(hPad))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text       = folder.displayName,
                        color      = MaterialTheme.colorScheme.onSurface,
                        style      = when (itemSize) {
                            ItemSize.TINY   -> MaterialTheme.typography.bodySmall
                            ItemSize.SMALL  -> MaterialTheme.typography.bodyMedium
                            ItemSize.MEDIUM -> MaterialTheme.typography.bodyLarge
                            ItemSize.LARGE  -> MaterialTheme.typography.titleMedium
                            ItemSize.XLARGE -> MaterialTheme.typography.titleLarge
                        },
                        maxLines   = 1,
                        overflow   = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.SemiBold,
                        modifier   = Modifier.weight(1f, fill = false)
                    )
                    if (folder.hasNewFiles) {
                        StatusBadge.New(
                            modifier = Modifier.padding(start = WatermelonSpacing.xs)
                        )
                    }
                }
                Text(
                    text  = smallMetaText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun FolderThumbnailOrIcon(
    folder: FolderNode,
    size: Dp,
    isScrollingFast: Boolean,
) {
    val thumbnailUri = folder.thumbnailUri
    if (!folder.isPlaylist && !thumbnailUri.isNullOrBlank()) {
        Box(
            modifier = Modifier
                .width(size * 1.6f)
                .height(size)
                .clip(WatermelonShapes.small)
                .background(WatermelonGlass.controlSurface()),
        ) {
            VelocityGuardImage(
                uri = thumbnailUri,
                durationMs = folder.totalDurationMs,
                isScrollingFast = isScrollingFast,
                modifier = Modifier.fillMaxSize(),
            )
        }
    } else {
        FolderIcon(size = size, isPlaylist = folder.isPlaylist)
    }
}

@Composable
private fun FolderIcon(size: Dp, isPlaylist: Boolean) {
    if (isPlaylist) {
        WatermelonGlyph(
            icon = WatermelonIcons.Playlist,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(size)
        )
        return
    }
    Icon(
        painter            = painterResource(R.drawable.ic_folder),
        contentDescription = null,
        tint               = Color.Unspecified,
        modifier          = Modifier.size(size)
    )
}

private fun formatDuration(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

private fun formatFileSize(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> "%.1f GB".format(gb)
        mb >= 1.0 -> "%.0f MB".format(mb)
        else -> "%.0f KB".format(kb)
    }
}

private fun formatLastModified(epochMs: Long): String {
    val sdf = java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(epochMs))
}
