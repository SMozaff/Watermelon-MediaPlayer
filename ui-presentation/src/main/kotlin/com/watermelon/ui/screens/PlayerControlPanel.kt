package com.watermelon.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.watermelon.common.model.RepeatMode
import com.watermelon.ui.R
import com.watermelon.ui.WatermelonIcons
import com.watermelon.ui.components.WatermelonGlyph
import com.watermelon.ui.theme.PlayerColors
import com.watermelon.ui.theme.WatermelonSpacing

private val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

/** The first stage of the player menu: choose an intent before seeing detailed controls. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerActionsSheet(
    onQuickTools: () -> Unit,
    onFileActions: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        SheetTitle("Player actions")
        SheetAction(
            label = "Quick tools",
            detail = "Speed, subtitles, aspect ratio, sleep timer, screenshot, PiP, and background play",
            onClick = onQuickTools,
        )
        SheetAction(
            label = "File actions",
            detail = "Favourite, playlist, share, media tools, and device deletion",
            onClick = onFileActions,
        )
        SheetBottomSpace()
    }
}

/** Playback-affecting tools grouped separately from file-management actions. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickToolsSheet(
    currentSpeed: Float,
    isMuted: Boolean,
    currentRatio: VideoRatio,
    currentOrientation: ScreenOrientation,
    tunerSeekBarEnabled: Boolean,
    tunerSeekStepSeconds: Int,
    repeatMode: RepeatMode,
    isShuffled: Boolean,
    isPiP: Boolean,
    canUsePip: Boolean,
    isBackground: Boolean,
    hasSubtitleTrack: Boolean,
    onSpeedChange: (Float) -> Unit,
    onMuteToggle: () -> Unit,
    onRatioChange: (VideoRatio) -> Unit,
    onOrientationChange: (ScreenOrientation) -> Unit,
    onTunerSeekBarEnabledChange: (Boolean) -> Unit,
    onRepeat: () -> Unit,
    onShuffle: () -> Unit,
    onScreenshot: () -> Unit,
    onSleepTimer: () -> Unit,
    onPip: () -> Unit,
    onBackground: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        SheetTitle("Quick tools")

        SheetSectionLabel("Playback speed")
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = WatermelonSpacing.md),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SPEEDS.forEach { speed ->
                TextButton(onClick = { onSpeedChange(speed) }) {
                    Text(
                        text = formatSpeed(speed),
                        color = if (speed == currentSpeed) {
                            PlayerColors.current.iconActive
                        } else {
                            PlayerColors.current.textPrimary
                        },
                        fontWeight = if (speed == currentSpeed) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }

        SheetDivider()
        SheetSectionLabel("Aspect ratio")
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = WatermelonSpacing.md),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            VideoRatio.values().forEach { ratio ->
                TextButton(onClick = { onRatioChange(ratio) }) {
                    Text(
                        ratio.label,
                        color = if (ratio == currentRatio) {
                            PlayerColors.current.iconActive
                        } else {
                            PlayerColors.current.textPrimary
                        },
                        fontWeight = if (ratio == currentRatio) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }

        SheetDivider()
        SheetSectionLabel("Orientation")
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = WatermelonSpacing.md),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            ScreenOrientation.values().forEach { orientation ->
                IconButton(onClick = { onOrientationChange(orientation) }) {
                    androidx.compose.material3.Icon(
                        painter = painterResource(orientation.iconRes),
                        contentDescription = orientation.name.lowercase().replaceFirstChar { it.uppercase() },
                        tint = if (orientation == currentOrientation) {
                            PlayerColors.current.iconActive
                        } else {
                            Color.Unspecified
                        },
                    )
                }
            }
        }

        SheetDivider()
        SheetSectionLabel("Seeking")
        SheetAction(
            label = if (tunerSeekBarEnabled) "Use standard seek bar" else "Use tuner seek",
            detail = if (tunerSeekBarEnabled) {
                "Switch to a conventional progress bar with direct positioning."
            } else {
                "Switch to the tuner dial using $tunerSeekStepSeconds second detents."
            },
            onClick = { onTunerSeekBarEnabledChange(!tunerSeekBarEnabled) },
        )

        SheetDivider()
        SheetAction(
            label = if (isMuted) "Unmute" else "Mute",
            detail = "Toggle device media volume",
            onClick = onMuteToggle,
        )
        SheetAction(
            label = "Repeat: ${repeatMode.name.lowercase().replaceFirstChar { it.uppercase() }}",
            detail = "Change repeat mode",
            onClick = onRepeat,
        )
        SheetAction(
            label = if (isShuffled) "Shuffle: on" else "Shuffle: off",
            detail = "Change queue order",
            onClick = onShuffle,
        )
        SheetAction(
            label = "Subtitle controls",
            detail = if (hasSubtitleTrack) {
                "Subtitle track is loaded; detailed controls are not available in this release."
            } else {
                "No subtitle track is available for this video."
            },
            enabled = false,
            onClick = {},
        )
        SheetAction(
            label = "Sleep timer",
            detail = "Pause playback after a selected interval",
            onClick = onSleepTimer,
        )
        SheetAction(
            label = "Take screenshot",
            detail = "Save the current video frame",
            onClick = onScreenshot,
        )
        SheetAction(
            label = if (isPiP) "Picture in picture: active" else "Picture in picture",
            detail = if (canUsePip) {
                "Continue watching in a floating window"
            } else {
                "Picture in picture is not supported on this Android version."
            },
            enabled = canUsePip,
            onClick = onPip,
        )
        SheetAction(
            label = if (isBackground) "Background play: on" else "Background play",
            detail = "Continue audio when leaving the player",
            onClick = onBackground,
        )
        SheetBottomSpace()
    }
}

/** File-management actions with a distinct, trailing destructive action. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileActionsSheet(
    isFavourite: Boolean,
    onShare: () -> Unit,
    onFavourite: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onExtractAudio: (() -> Unit)?,
    onTrimVideo: (() -> Unit)?,
    onCompressVideo: (() -> Unit)?,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        SheetTitle("File actions")
        SheetAction("Share", "Send this video to another app", onShare)
        SheetAction(
            label = if (isFavourite) "Remove from favourites" else "Add to favourites",
            detail = "Keep this video in your personal favourites collection",
            onClick = onFavourite,
        )
        SheetAction("Add to playlist", "Choose or create a playlist for this video", onAddToPlaylist)

        if (onExtractAudio != null || onTrimVideo != null || onCompressVideo != null) {
            SheetDivider()
            SheetSectionLabel("Media tools")
            onTrimVideo?.let {
                SheetAction("Trim", "Create a shorter version of this video", it)
            }
            onCompressVideo?.let {
                SheetAction("Compress", "Create a smaller copy of this video", it)
            }
            onExtractAudio?.let {
                SheetAction("Extract audio", "Create an audio file from this video", it)
            }
        }

        SheetDivider()
        SheetAction(
            label = "Delete from device",
            detail = "Permanently remove this video after confirmation",
            destructive = true,
            onClick = onDelete,
        )
        SheetBottomSpace()
    }
}

@Composable
private fun SheetTitle(text: String) {
    Text(
        text = text,
        color = PlayerColors.current.textPrimary,
        fontSize = 20.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = WatermelonSpacing.lg, vertical = WatermelonSpacing.sm),
    )
}

@Composable
private fun SheetSectionLabel(text: String) {
    Text(
        text = text,
        color = PlayerColors.current.textSecondary,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(horizontal = WatermelonSpacing.lg, vertical = WatermelonSpacing.xs),
    )
}

@Composable
private fun SheetDivider() {
    HorizontalDivider(
        color = PlayerColors.current.textPrimary.copy(alpha = 0.12f),
        modifier = Modifier.padding(vertical = WatermelonSpacing.xs),
    )
}

@Composable
private fun SheetAction(
    label: String,
    detail: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    destructive: Boolean = false,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    ) {
        ListItem(
            headlineContent = {
                Text(
                    text = label,
                    color = when {
                        destructive -> PlayerColors.current.accent
                        enabled -> PlayerColors.current.textPrimary
                        else -> PlayerColors.current.iconInactive
                    },
                )
            },
            supportingContent = {
                Text(
                    text = detail,
                    color = if (enabled) {
                        PlayerColors.current.textSecondary
                    } else {
                        PlayerColors.current.iconInactive
                    },
                )
            },
        )
    }
}

@Composable
private fun SheetBottomSpace() {
    Column(modifier = Modifier.padding(bottom = WatermelonSpacing.lg)) {}
}

private fun formatSpeed(speed: Float): String =
    if (speed == speed.toLong().toFloat()) "${speed.toLong()}×" else "${speed}×"
