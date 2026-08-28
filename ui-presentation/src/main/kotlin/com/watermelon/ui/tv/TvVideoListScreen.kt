package com.watermelon.ui.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.watermelon.common.model.MediaItem
import com.watermelon.ui.WatermelonIcons
import com.watermelon.ui.components.VelocityGuardImage
import com.watermelon.ui.components.WatermelonGlyph
import com.watermelon.ui.theme.WatermelonShapes
import com.watermelon.ui.theme.WatermelonSpacing
import com.watermelon.ui.viewmodel.VideoListViewModel

/**
 * D-pad-optimised Android TV catalogue for all videos, folders, and playlists. Each media item
 * has a large preview, a single SELECT action to open it, and shared focus treatment. Phone-only
 * long-press and batch operations remain intentionally excluded: adding them as hidden gestures
 * would make the TV path less discoverable and less reliable.
 */
@Composable
fun TvVideoListScreen(
    viewModel: VideoListViewModel,
    title: String,
    onVideoClick: (MediaItem) -> Unit,
    showThumbnails: Boolean = true,
    showDurations: Boolean = true,
    showFileSize: Boolean = false,
    modifier: Modifier = Modifier
) {
    val videos by viewModel.videos.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        TvScreenHeader(
            title = title,
            supportingText = "Use the D-pad to browse. Press SELECT to play."
        )

        if (videos.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = WatermelonSpacing.xl + WatermelonSpacing.md),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "No videos here yet",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Press BACK to return to the library.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = WatermelonSpacing.xs)
                )
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = WatermelonSpacing.xl + WatermelonSpacing.md,
                    vertical = WatermelonSpacing.xs
                ),
            verticalArrangement = Arrangement.spacedBy(WatermelonSpacing.sm)
        ) {
            items(videos, key = { it.uri }) { item ->
                TvVideoRow(
                    item = item,
                    showThumbnails = showThumbnails,
                    showDurations = showDurations,
                    showFileSize = showFileSize,
                    onClick = { onVideoClick(item) }
                )
            }
        }
    }
}

@Composable
private fun TvVideoRow(
    item: MediaItem,
    showThumbnails: Boolean,
    showDurations: Boolean,
    showFileSize: Boolean,
    onClick: () -> Unit
) {
    TvFocusableSurface(onClick = onClick) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(WatermelonSpacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(224.dp)
                    .aspectRatio(16f / 9f)
                    .clip(WatermelonShapes.card)
            ) {
                if (showThumbnails) {
                    VelocityGuardImage(
                        uri = item.uri,
                        durationMs = item.durationMs,
                        isScrollingFast = false,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        WatermelonGlyph(
                            icon = WatermelonIcons.VideoUnavailable,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(52.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.width(WatermelonSpacing.lg))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = buildTvVideoDetail(item, showThumbnails, showDurations, showFileSize),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = WatermelonSpacing.xs)
                )
            }
        }
    }
}

private fun buildTvVideoDetail(
    item: MediaItem,
    showThumbnails: Boolean,
    showDurations: Boolean,
    showFileSize: Boolean
): String {
    val details = buildList {
        if (showDurations) add(formatTvDuration(item.durationMs))
        if (showFileSize && item.fileSize > 0L) add(formatTvFileSize(item.fileSize))
        if (!showThumbnails) add("Preview disabled")
        add("SELECT to play")
    }
    return details.joinToString(" · ")
}

private fun formatTvDuration(ms: Long): String {
    val seconds = (ms / 1000).coerceAtLeast(0)
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val remainder = seconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, remainder)
    else "%d:%02d".format(minutes, remainder)
}

private fun formatTvFileSize(bytes: Long): String = when {
    bytes >= 1024L * 1024L * 1024L -> "%.1f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
    bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
