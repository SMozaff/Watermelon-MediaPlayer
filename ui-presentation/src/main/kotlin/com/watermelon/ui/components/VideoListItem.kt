package com.watermelon.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.watermelon.common.model.MediaItem
import com.watermelon.ui.R
import com.watermelon.ui.WatermelonIcons
import com.watermelon.ui.theme.WatermelonColors
import com.watermelon.ui.theme.WatermelonGlass
import com.watermelon.ui.theme.WatermelonShapes
import com.watermelon.ui.theme.WatermelonSpacing
import com.watermelon.ui.theme.WatermelonTypography

/** Video list's own size axis — SMALL (compact row with extra metadata: resolution, file
 *  size, date added) and LARGE (big, simple row: name + duration only). Deliberately only
 *  2 values, unlike folders/playlists' [ItemSize] below, which keeps 3 (SMALL/MEDIUM/LARGE)
 *  — the two screens' sizing scales are independent by design. */
enum class VideoItemSize(val label: String) { TINY(""), SMALL(""), MEDIUM(""), LARGE(""), XLARGE("") }

/** Folders/playlists' own size axis (used by FolderListItem/FolderBrowserScreen) — kept at
 *  3 values, unlike the video list's [VideoItemSize] above. Small rows there also show
 *  extra metadata (item count, duration, last modified — see FolderListItem's
 *  smallMetaText). */
enum class ItemSize(val label: String) { TINY(""), SMALL(""), MEDIUM(""), LARGE(""), XLARGE("") }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoListItem(
    item: MediaItem,
    itemSize: VideoItemSize,
    isGrid: Boolean,
    isScrollingFast: Boolean,
    isSelected: Boolean,
    selectionActive: Boolean,
    showThumbnails: Boolean = true,
    showDurations: Boolean = true,
    showFileSize: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onContextMenuClick: () -> Unit = {},
    // media-tools entry points (real bug fix -- see file-level note below for what was
    // wrong before). Nullable/optional, same pattern as VideoListScreen previously used,
    // so callers that haven't wired media-tools yet just don't show these items.
    onExtractAudio: ((MediaItem) -> Unit)? = null,
    onTrimVideo: ((MediaItem) -> Unit)? = null,
    onCompressVideo: ((MediaItem) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // FIXED REAL BUG: the context menu (Extract Audio/Trim/Compress) used to be a
    // DropdownMenu rendered at VideoListScreen's root level, entirely disconnected from
    // the 3-dot button that was supposed to open it. Compose's DropdownMenu anchors to
    // whatever's directly above it in its own parent's layout -- placed at screen-root
    // scope, it had no real anchor relationship to the tapped item, so it rendered
    // somewhere wrong (effectively invisible/unreachable) instead of near the button.
    // Confirmed via user report: tapping the 3-dot icon showed nothing at all.
    // Fix: menu state + the actual DropdownMenu now live HERE, inside the same Box as the
    // 3-dot button that triggers it, so Compose anchors it correctly.
    var showMenu by remember { mutableStateOf(false) }
    val thumbH: Dp = when (itemSize) {
        VideoItemSize.TINY -> if (isGrid) 56.dp else 40.dp
        VideoItemSize.SMALL -> if (isGrid) 76.dp else 52.dp
        VideoItemSize.MEDIUM -> if (isGrid) 108.dp else 68.dp
        VideoItemSize.LARGE -> if (isGrid) 148.dp else 88.dp
        VideoItemSize.XLARGE -> if (isGrid) 196.dp else 112.dp
    }
    val textStyle = when (itemSize) {
        VideoItemSize.TINY -> WatermelonTypography.typography.labelSmall
        VideoItemSize.SMALL -> WatermelonTypography.typography.bodyMedium
        VideoItemSize.MEDIUM -> WatermelonTypography.typography.bodyMedium
        VideoItemSize.LARGE -> WatermelonTypography.typography.bodyLarge
        VideoItemSize.XLARGE -> WatermelonTypography.typography.titleMedium
    }

    // Match the thumbnail frame to the video's actual display aspect ratio. This keeps
    // portrait phone videos portrait instead of placing them inside a forced 16:9 box.
    // Clamp extreme/malformed ratios so one unusual file cannot distort the list row.
    val thumbnailAspectRatio = if (item.width > 0 && item.height > 0) {
        (item.width.toFloat() / item.height.toFloat()).coerceIn(0.5625f, 1.7778f)
    } else {
        16f / 9f
    }

    val selectedBorder = if (isSelected) {
        Modifier
            .background(WatermelonColors.Accent.copy(alpha = 0.12f), WatermelonShapes.control)
            .border(2.dp, WatermelonColors.Accent, WatermelonShapes.control)
    } else {
        Modifier
            .background(WatermelonGlass.controlSurface(), WatermelonShapes.control)
            .border(1.dp, WatermelonGlass.borderColor(), WatermelonShapes.control)
    }

    val clickModifier = Modifier
        .clip(WatermelonShapes.control)
        .then(selectedBorder)
        .combinedClickable(onClick = onClick, onLongClick = onLongClick)

    if (isGrid) {
        Column(
            modifier = modifier.then(clickModifier).padding(WatermelonSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(WatermelonSpacing.xs)
        ) {
            Box {
                VideoPreview(
                    item = item,
                    showThumbnails = showThumbnails,
                    isScrollingFast = isScrollingFast,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(thumbH)
                        .clip(WatermelonShapes.small)
                )

                if (!selectionActive) {
                    Box {
                        androidx.compose.material3.IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(WatermelonSpacing.xs)
                                .size(48.dp)
                        ) {
                            WatermelonGlyph(
                                icon = WatermelonIcons.MoreVert,
                                contentDescription = "More options",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        VideoItemContextMenu(
                            expanded = showMenu,
                            onDismiss = { showMenu = false },
                            item = item,
                            onExtractAudio = onExtractAudio,
                            onTrimVideo = onTrimVideo,
                            onCompressVideo = onCompressVideo,
                        )
                    }
                    androidx.compose.material3.IconButton(
                        onClick = onClick,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(48.dp)
                            .background(WatermelonGlass.controlSurface(), androidx.compose.foundation.shape.CircleShape)
                            .border(1.dp, WatermelonGlass.borderColor(), androidx.compose.foundation.shape.CircleShape)
                    ) {
                        WatermelonGlyph(
                            icon = WatermelonIcons.Play,
                            contentDescription = "Play",
                            tint = WatermelonColors.Palette.PaperWhite
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.displayName,
                    style = textStyle,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (item.lastPlayedAt == null) {
                    StatusBadge.New(compact = true)
                }
            }

            if (showDurations) {
                Text(
                    text = formatDuration(item.durationMs),
                    style = WatermelonTypography.timecode,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        Row(
            modifier = modifier.then(clickModifier)
                .fillMaxWidth()
                .padding(horizontal = WatermelonSpacing.sm, vertical = WatermelonSpacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                VideoPreview(
                    item = item,
                    showThumbnails = showThumbnails,
                    isScrollingFast = isScrollingFast,
                    modifier = Modifier
                        .width(thumbH * thumbnailAspectRatio)
                        .height(thumbH)
                        .clip(WatermelonShapes.small)
                )

                if (!selectionActive) {
                    androidx.compose.material3.IconButton(
                        onClick = onClick,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(48.dp)
                    ) {
                        WatermelonGlyph(
                            icon = WatermelonIcons.Play,
                            contentDescription = "Play",
                            tint = WatermelonColors.Palette.PaperWhite.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            Spacer(Modifier.width(WatermelonSpacing.md))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.displayName,
                        style = textStyle,
                    color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (item.lastPlayedAt == null) {
                        StatusBadge.New(modifier = Modifier.padding(start = WatermelonSpacing.xs))
                    }
                }
                // Parent folder name — list view only (grid rows are too compact for it,
                // and the spec explicitly calls out grid as unaffected). Shown regardless
                // of Small/Large: Large appends it after duration since that's its only
                // metadata line; Small appends it to its existing resolution/size/date line.
                val folderSuffix = if (!isGrid) item.parentFolder else ""
                val primaryMeta = buildList {
                    if (showDurations) add(formatDuration(item.durationMs))
                    if (itemSize == VideoItemSize.LARGE && folderSuffix.isNotBlank()) add(folderSuffix)
                }.joinToString(" · ")
                if (primaryMeta.isNotBlank()) {
                    Text(
                        text = primaryMeta,
                        style = WatermelonTypography.timecode,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (itemSize == VideoItemSize.SMALL) {
                    Text(
                        text = buildList {
                            val detail = formatDetailLine(item, showFileSize)
                            if (detail.isNotBlank()) add(detail)
                            if (folderSuffix.isNotBlank()) add(folderSuffix)
                        }.joinToString(" · "),
                        style = WatermelonTypography.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (!selectionActive) {
                Box {
                    androidx.compose.material3.IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(48.dp)
                    ) {
                        WatermelonGlyph(
                            icon = WatermelonIcons.MoreVert,
                            contentDescription = "More options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    VideoItemContextMenu(
                        expanded = showMenu,
                        onDismiss = { showMenu = false },
                        item = item,
                        onExtractAudio = onExtractAudio,
                        onTrimVideo = onTrimVideo,
                        onCompressVideo = onCompressVideo,
                    )
                }
            }
        }
    }
}

/**
 * The actual per-item context menu (Extract Audio/Trim/Compress), factored out so both the
 * grid and list layout variants of VideoListItem can render it identically. Must be called
 * from inside a Box alongside the IconButton that triggers it -- DropdownMenu anchors to
 * its position in the composition tree, so this can't be hoisted up to VideoListScreen's
 * root scope (that was the original bug: see VideoListItem's file-level fix note).
 */
@Composable
internal fun VideoItemContextMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    item: MediaItem,
    onExtractAudio: ((MediaItem) -> Unit)?,
    onTrimVideo: ((MediaItem) -> Unit)?,
    onCompressVideo: ((MediaItem) -> Unit)?,
) {
    if (expanded) {
        androidx.compose.material3.DropdownMenu(
            expanded = true,
            onDismissRequest = onDismiss,
            containerColor = WatermelonGlass.surfaceFor(WatermelonGlass.Role.Popup),
            shape = WatermelonShapes.card,
            border = BorderStroke(1.dp, WatermelonGlass.borderFor(WatermelonGlass.Role.Popup))
        ) {
            onExtractAudio?.let { action ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text("Extract Audio", color = MaterialTheme.colorScheme.onSurface) },
                    onClick = { onDismiss(); action(item) }
                )
            }
            onTrimVideo?.let { action ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text("Trim", color = MaterialTheme.colorScheme.onSurface) },
                    onClick = { onDismiss(); action(item) }
                )
            }
            onCompressVideo?.let { action ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text("Compress", color = MaterialTheme.colorScheme.onSurface) },
                    onClick = { onDismiss(); action(item) }
                )
            }
        }
    }
}

@Composable
private fun VideoPreview(
    item: MediaItem,
    showThumbnails: Boolean,
    isScrollingFast: Boolean,
    modifier: Modifier = Modifier
) {
    if (showThumbnails) {
        VelocityGuardImage(
            uri = item.uri,
            durationMs = item.durationMs,
            isScrollingFast = isScrollingFast,
            modifier = modifier
        )
    } else {
        Box(
            modifier = modifier.background(WatermelonGlass.cardSurface()),
            contentAlignment = Alignment.Center
        ) {
            WatermelonGlyph(
                icon = WatermelonIcons.VideoUnavailable,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

private fun formatDuration(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

/** "1920x1080 · 245 MB · Jan 3, 2026" — the extra detail line shown on Small rows. Any
 *  piece with no known value (0) is dropped rather than shown as "0x0" or "Jan 1, 1970",
 *  since those are more confusing than just omitting the field for a file the indexer
 *  hasn't fully resolved yet. */
private fun formatDetailLine(
    item: com.watermelon.common.model.MediaItem,
    showFileSize: Boolean
): String {
    val parts = mutableListOf<String>()
    if (item.width > 0 && item.height > 0) parts += "${item.width}x${item.height}"
    if (showFileSize && item.fileSize > 0) parts += formatFileSize(item.fileSize)
    if (item.dateAdded > 0) parts += formatDateAdded(item.dateAdded)
    return parts.joinToString(" · ")
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

private fun formatDateAdded(epochMs: Long): String {
    val sdf = java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(epochMs))
}
