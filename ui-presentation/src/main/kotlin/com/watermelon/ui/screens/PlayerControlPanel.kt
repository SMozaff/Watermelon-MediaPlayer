package com.watermelon.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.watermelon.ui.theme.WatermelonGlass
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.watermelon.common.model.RepeatMode
import com.watermelon.ui.R
import com.watermelon.ui.WatermelonIcons
import com.watermelon.ui.components.WatermelonGlyph
import com.watermelon.ui.theme.PlayerColors
import com.watermelon.ui.theme.WatermelonSpacing

object PlayerControlPanel {

private val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

/** The first stage of the player menu: choose an intent before seeing detailed controls. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerActionsSheet(
    onQuickTools: () -> Unit,
    onFileActions: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = WatermelonGlass.modalSurface(),
        scrimColor = WatermelonGlass.scrim,
        tonalElevation = 0.dp,
    ) {
        SheetTitle(stringResource(R.string.player_actions_title))
        SheetAction(
            label = stringResource(R.string.player_quick_tools),
            detail = stringResource(R.string.player_quick_tools_detail),
            onClick = onQuickTools,
        )
        SheetAction(
            label = stringResource(R.string.player_file_actions),
            detail = stringResource(R.string.player_file_actions_detail),
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
    subtitleOffsetMs: Long = 0L,
    autoSyncEnabled: Boolean = false,
    autoSyncStatus: com.watermelon.common.subtitle.sync.SyncStatus =
        com.watermelon.common.subtitle.sync.SyncStatus.IDLE,
    onFindOnlineSubtitles: (() -> Unit)? = null,
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
    onSubtitleNudge: (Long) -> Unit = {},
    onAutoSync: () -> Unit = {},
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = WatermelonGlass.modalSurface(),
        scrimColor = WatermelonGlass.scrim,
        tonalElevation = 0.dp,
    ) {
        SheetTitle(stringResource(R.string.player_quick_tools))

        SheetSectionLabel(stringResource(R.string.player_section_speed))
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
        SheetSectionLabel(stringResource(R.string.player_section_aspect))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = WatermelonSpacing.md),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            VideoRatio.values().forEach { ratio ->
                TextButton(onClick = { onRatioChange(ratio) }) {
                    Text(
                        stringResource(ratio.labelRes),
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
        SheetSectionLabel(stringResource(R.string.player_section_orientation))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = WatermelonSpacing.md),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            ScreenOrientation.values().forEach { orientation ->
                IconButton(onClick = { onOrientationChange(orientation) }) {
                    androidx.compose.material3.Icon(
                        painter = painterResource(orientation.iconRes),
                        contentDescription = stringResource(orientation.labelRes),
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
        SheetSectionLabel(stringResource(R.string.player_section_seeking))
        SheetAction(
            label = if (tunerSeekBarEnabled) stringResource(R.string.player_use_standard_seekbar) else stringResource(R.string.player_use_tuner_seek),
            detail = if (tunerSeekBarEnabled) {
                stringResource(R.string.player_use_standard_seekbar_detail)
            } else {
                stringResource(R.string.player_use_tuner_seek_detail, tunerSeekStepSeconds)
            },
            onClick = { onTunerSeekBarEnabledChange(!tunerSeekBarEnabled) },
        )

        SheetDivider()
        SheetAction(
            label = if (isMuted) stringResource(R.string.player_unmute) else stringResource(R.string.player_mute),
            detail = stringResource(R.string.player_mute_detail),
            onClick = onMuteToggle,
        )
        SheetAction(
            label = stringResource(
                R.string.player_repeat_label,
                stringResource(
                    when (repeatMode) {
                        RepeatMode.NONE -> R.string.repeat_mode_none
                        RepeatMode.ONE -> R.string.repeat_mode_one
                        RepeatMode.ALL -> R.string.repeat_mode_all
                    }
                )
            ),
            detail = stringResource(R.string.player_repeat_detail),
            onClick = onRepeat,
        )
        SheetAction(
            label = if (isShuffled) stringResource(R.string.player_shuffle_on) else stringResource(R.string.player_shuffle_off),
            detail = stringResource(R.string.player_shuffle_detail),
            onClick = onShuffle,
        )
        if (hasSubtitleTrack) {
            SheetDivider()
            SheetSectionLabel(stringResource(R.string.player_section_subtitle_timing))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = WatermelonSpacing.md),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                TextButton(onClick = { onSubtitleNudge(-100L) }) {
                    Text(stringResource(R.string.player_subtitles_minus_100ms), color = PlayerColors.current.textPrimary)
                }
                Text(
                    text = stringResource(R.string.player_subtitles_offset_ms, subtitleOffsetMs),
                    color = PlayerColors.current.textPrimary,
                    fontWeight = FontWeight.Bold,
                )
                TextButton(onClick = { onSubtitleNudge(+100L) }) {
                    Text(stringResource(R.string.player_subtitles_plus_100ms), color = PlayerColors.current.textPrimary)
                }
            }
            if (autoSyncEnabled) {
                SheetAction(
                    label = stringResource(R.string.subtitles_auto_sync),
                    detail = subtitleAutoSyncDetail(autoSyncStatus),
                    onClick = onAutoSync,
                )
            }
            if (onFindOnlineSubtitles != null) {
                SheetAction(
                    label = stringResource(R.string.subtitles_find_another),
                    detail = stringResource(R.string.subtitles_search_opensubtitles),
                    onClick = onFindOnlineSubtitles,
                )
            }
        } else {
            // No subtitle loaded: offer online search as explicit user action
            SheetDivider()
            SheetSectionLabel(stringResource(R.string.player_section_subtitles))
            if (onFindOnlineSubtitles != null) {
                SheetAction(
                    label = stringResource(R.string.subtitles_find_online),
                    detail = stringResource(R.string.subtitles_search_opensubtitles),
                    onClick = onFindOnlineSubtitles,
                )
            } else {
                SheetAction(
                    label = stringResource(R.string.subtitles_controls_label),
                    detail = stringResource(R.string.subtitles_no_track),
                    enabled = false,
                    onClick = {},
                )
            }
        }
        SheetAction(
            label = stringResource(R.string.sleep_timer_title),
            detail = stringResource(R.string.sleep_timer_detail),
            onClick = onSleepTimer,
        )
        SheetAction(
            label = stringResource(R.string.player_screenshot),
            detail = stringResource(R.string.player_screenshot_detail),
            onClick = onScreenshot,
        )
        SheetAction(
            label = if (isPiP) stringResource(R.string.player_pip_active) else stringResource(R.string.player_pip),
            detail = if (canUsePip) {
                stringResource(R.string.player_pip_detail)
            } else {
                stringResource(R.string.player_pip_unavailable)
            },
            enabled = canUsePip,
            onClick = onPip,
        )
        SheetAction(
            label = if (isBackground) stringResource(R.string.player_background_on) else stringResource(R.string.player_background),
            detail = stringResource(R.string.player_background_detail),
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
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = WatermelonGlass.modalSurface(),
        scrimColor = WatermelonGlass.scrim,
        tonalElevation = 0.dp,
    ) {
        SheetTitle(stringResource(R.string.player_file_actions))
        SheetAction(stringResource(R.string.action_share), stringResource(R.string.player_share_detail), onShare)
        SheetAction(
            label = if (isFavourite) stringResource(R.string.player_remove_from_favourites) else stringResource(R.string.player_add_to_favourites),
            detail = stringResource(R.string.player_favourite_detail),
            onClick = onFavourite,
        )
        SheetAction(stringResource(R.string.video_add_to_playlist_title), stringResource(R.string.player_add_to_playlist_detail), onAddToPlaylist)

        if (onExtractAudio != null || onTrimVideo != null || onCompressVideo != null) {
            SheetDivider()
            SheetSectionLabel(stringResource(R.string.player_section_media_tools))
            onTrimVideo?.let {
                SheetAction(stringResource(R.string.action_trim), stringResource(R.string.player_trim_detail), it)
            }
            onCompressVideo?.let {
                SheetAction(stringResource(R.string.action_compress), stringResource(R.string.player_compress_detail), it)
            }
            onExtractAudio?.let {
                SheetAction(stringResource(R.string.action_extract_audio), stringResource(R.string.player_extract_audio_detail), it)
            }
        }

        SheetDivider()
        SheetAction(
            label = stringResource(R.string.action_delete_from_device),
            detail = stringResource(R.string.player_delete_detail),
            destructive = true,
            onClick = onDelete,
        )
        SheetBottomSpace()
    }
}

private fun formatSpeed(speed: Float): String =
    if (speed == speed.toLong().toFloat()) "${speed.toLong()}×" else "${speed}×"

@Composable
private fun subtitleAutoSyncDetail(status: com.watermelon.common.subtitle.sync.SyncStatus): String =
    stringResource(
        when (status) {
            com.watermelon.common.subtitle.sync.SyncStatus.IDLE -> R.string.subtitles_sync_idle
            com.watermelon.common.subtitle.sync.SyncStatus.CHECKING_CACHE,
            com.watermelon.common.subtitle.sync.SyncStatus.ANALYZING -> R.string.subtitles_sync_analyzing
            com.watermelon.common.subtitle.sync.SyncStatus.SYNCHRONIZED -> R.string.subtitles_sync_synchronized
            com.watermelon.common.subtitle.sync.SyncStatus.LOW_CONFIDENCE ->
                R.string.subtitles_sync_low_confidence
            com.watermelon.common.subtitle.sync.SyncStatus.COMPLEX_DRIFT ->
                R.string.subtitles_sync_complex_drift
            com.watermelon.common.subtitle.sync.SyncStatus.UNSUPPORTED -> R.string.subtitles_sync_unsupported
            com.watermelon.common.subtitle.sync.SyncStatus.RESOURCE_DENIED -> R.string.subtitles_sync_unavailable
            com.watermelon.common.subtitle.sync.SyncStatus.FAILED -> R.string.subtitles_sync_failed
        }
    )


}