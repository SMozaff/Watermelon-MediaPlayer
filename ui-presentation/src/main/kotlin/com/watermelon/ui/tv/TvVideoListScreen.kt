package com.watermelon.ui.tv

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
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
 * D-Pad-optimised video catalogue for Android TV. It serves both the All Videos destination and
 * folder/playlist contents, but is intentionally a separate composition from the phone
 * [com.watermelon.ui.screens.VideoListScreen]. The large 16:9 preview, high-contrast focus ring,
 * and explicit SELECT instruction make every row legible and actionable from a ten-foot viewing
 * distance without introducing touch-only controls or long-press behaviour.
 *
 * Batch operations remain intentionally out of scope for TV: a remote's SELECT action must open
 * the item immediately and consistently. Secondary actions can be added later as an explicit,
 * separately focusable overflow surface rather than borrowing the phone screen's long-press
 * model.
 */
@Composable
fun TvVideoListScreen(
    viewModel: VideoListViewModel,
    title: String,
    onVideoClick: (MediaItem) -> Unit,
    showThumbnails: Boolean = true,
    modifier: Modifier = Modifier
) {
    val videos by viewModel.videos.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = WatermelonSpacing.xl + WatermelonSpacing.md,
                    top = WatermelonSpacing.md,
                    bottom = WatermelonSpacing.xs
                )
        )
        Text(
            text = "Use the D-pad to browse. Press SELECT to play.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(
                horizontal = WatermelonSpacing.xl + WatermelonSpacing.md,
                bottom = WatermelonSpacing.sm
            )
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
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.025f else 1f,
        label = "tvVideoRowFocusScale"
    )

    Surface(
        onClick = onClick,
        interactionSource = interaction,
        shape = WatermelonShapes.card,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .border(
                width = if (focused) 3.dp else 0.dp,
                color = if (focused) MaterialTheme.colorScheme.secondary else Color.Transparent,
                shape = WatermelonShapes.card
            )
    ) {
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
                    text = if (showThumbnails) {
                        "${formatTvDuration(item.durationMs)} · SELECT to play"
                    } else {
                        "${formatTvDuration(item.durationMs)} · Preview disabled · SELECT to play"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = WatermelonSpacing.xs)
                )
            }
        }
    }
}

private fun formatTvDuration(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}
