package com.watermelon.ui.tv

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import com.watermelon.common.model.UserIntent
import com.watermelon.ui.theme.PlayerColors
import com.watermelon.ui.theme.WatermelonShapes
import com.watermelon.ui.theme.WatermelonSpacing

/**
 * Explicit D-pad controls for the TV player. Transport and subtitle actions are visible as
 * individual focus stops, rather than hidden behind directional key handling. This keeps default
 * Compose focus traversal intact while retaining support for hardware media keys from remotes.
 */
@Composable
fun TvPlayerControls(
    isPlaying: Boolean,
    hasPreviousTrack: Boolean,
    hasNextTrack: Boolean,
    hasSubtitles: Boolean,
    onIntent: (UserIntent) -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onSubtitleNudge: (Long) -> Unit,
    onSeek: (direction: Int) -> Unit,
    showAutoSync: Boolean = false,
    autoSyncStatusLabel: String? = null,
    onAutoSync: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val playPauseFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { playPauseFocus.requestFocus() }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(WatermelonSpacing.lg)
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.MediaPlayPause -> {
                        onIntent(if (isPlaying) UserIntent.Pause else UserIntent.Resume)
                        true
                    }
                    Key.MediaPlay -> {
                        onIntent(UserIntent.Resume)
                        true
                    }
                    Key.MediaPause -> {
                        onIntent(UserIntent.Pause)
                        true
                    }
                    Key.MediaNext -> {
                        if (hasNextTrack) onSkipNext()
                        true
                    }
                    Key.MediaPrevious -> {
                        if (hasPreviousTrack) onSkipPrevious()
                        true
                    }
                    Key.MediaRewind -> {
                        onSeek(-1)
                        true
                    }
                    Key.MediaFastForward -> {
                        onSeek(+1)
                        true
                    }
                    else -> false
                }
            },
        verticalArrangement = Arrangement.spacedBy(WatermelonSpacing.sm)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(WatermelonSpacing.md)
        ) {
            if (hasPreviousTrack) {
                TvFocusableButton(label = "Previous", onClick = onSkipPrevious)
            }
            TvFocusableButton(label = "Rewind 10 sec", onClick = { onSeek(-1) })
            TvFocusableButton(
                label = if (isPlaying) "Pause" else "Play",
                onClick = { onIntent(if (isPlaying) UserIntent.Pause else UserIntent.Resume) },
                modifier = Modifier.focusRequester(playPauseFocus)
            )
            TvFocusableButton(label = "Forward 10 sec", onClick = { onSeek(+1) })
            if (hasNextTrack) {
                TvFocusableButton(label = "Next", onClick = onSkipNext)
            }
        }
        if (hasSubtitles) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(WatermelonSpacing.md)
            ) {
                TvFocusableButton(
                    label = "Subtitles −100 ms",
                    onClick = { onSubtitleNudge(-100L) }
                )
                TvFocusableButton(
                    label = "Subtitles +100 ms",
                    onClick = { onSubtitleNudge(+100L) }
                )
                if (showAutoSync) {
                    TvFocusableButton(
                        label = "Auto Sync",
                        onClick = onAutoSync
                    )
                }
            }
            if (showAutoSync && autoSyncStatusLabel != null) {
                Text(
                    text = autoSyncStatusLabel,
                    color = PlayerColors.current.textPrimary.copy(alpha = 0.78f)
                )
            }
        }
    }
}

/**
 * Shared focus treatment for TV controls: a visible border plus a small scale transition. The
 * effect does not rely on color alone and leaves button semantics intact for accessibility.
 */
@Composable
private fun TvFocusableButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, label = "tvButtonFocusScale")

    Button(
        onClick = onClick,
        interactionSource = interaction,
        colors = ButtonDefaults.buttonColors(
            containerColor = PlayerColors.current.sheetBackground,
            contentColor = PlayerColors.current.textPrimary
        ),
        modifier = modifier
            .scale(scale)
            .border(
                width = if (focused) 3.dp else 0.dp,
                color = if (focused) PlayerColors.current.iconFocus else Color.Transparent,
                shape = RoundedCornerShape(WatermelonShapes.Radius.control)
            )
    ) {
        Text(label)
    }
}
