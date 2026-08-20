package com.watermelon.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.watermelon.ui.WatermelonIcons
import com.watermelon.ui.theme.WatermelonShapes
import com.watermelon.ui.theme.WatermelonSpacing

private val MiniPlayerHeight = 64.dp
private val CompactMiniPlayerWidth = 360.dp

/**
 * Docked mini-player that switches to an essentials-only layout on narrow widths and enlarged
 * text. The compact variant retains the video surface, readable media title, Play/Pause, and
 * Close, while previous/next/mute remain available from the restored full player.
 */
@Composable
fun MiniPlayerBar(
    visible: Boolean,
    title: String,
    isPlaying: Boolean,
    isMuted: Boolean,
    progressFraction: Float,
    hasNext: Boolean,
    hasPrevious: Boolean,
    videoSurface: @Composable (Modifier) -> Unit,
    onRestore: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onMuteToggle: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(animationSpec = tween(220)) { -it } + fadeIn(tween(220)),
        exit = slideOutVertically(animationSpec = tween(180)) { -it } + fadeOut(tween(180)),
        modifier = modifier,
    ) {
        BoxWithConstraints {
            val fontScale = LocalConfiguration.current.fontScale
            val compact = maxWidth <= CompactMiniPlayerWidth || fontScale >= 1.3f
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .semantics { contentDescription = "Open full player for $title" }
                    .clickable(onClick = onRestore),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(MiniPlayerHeight)
                        .padding(horizontal = WatermelonSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(vertical = WatermelonSpacing.xs)
                            .aspectRatio(16f / 9f)
                            .clip(WatermelonShapes.card),
                    ) {
                        videoSurface(Modifier.fillMaxSize())
                    }

                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = if (compact) 2 else 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = WatermelonSpacing.sm),
                    )

                    if (!compact) {
                        IconButton(onClick = onPrevious, enabled = hasPrevious) {
                            WatermelonIcon(
                                WatermelonIcons.SkipPrevious,
                                contentDescription = "Previous",
                                tint = if (hasPrevious) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                            )
                        }
                    }
                    IconButton(onClick = onPlayPause) {
                        WatermelonIcon(
                            if (isPlaying) WatermelonIcons.Pause else WatermelonIcons.Play,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                        )
                    }
                    if (!compact) {
                        IconButton(onClick = onNext, enabled = hasNext) {
                            WatermelonIcon(
                                WatermelonIcons.SkipNext,
                                contentDescription = "Next",
                                tint = if (hasNext) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                            )
                        }
                        IconButton(onClick = onMuteToggle) {
                            WatermelonIcon(
                                if (isMuted) WatermelonIcons.VolumeMute else WatermelonIcons.VolumeHigh,
                                contentDescription = if (isMuted) "Unmute" else "Mute",
                            )
                        }
                    }
                    IconButton(onClick = onClose) {
                        WatermelonIcon(WatermelonIcons.Close, contentDescription = "Close player")
                    }
                }

                LinearProgressIndicator(
                    progress = { progressFraction.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                    color = MaterialTheme.colorScheme.secondary,
                    trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
                )
            }
        }
    }
}
