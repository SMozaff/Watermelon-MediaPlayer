package com.watermelon.ui.screens

import android.app.Activity
import android.media.AudioManager
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.align
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.watermelon.common.model.PlaybackState
import com.watermelon.common.model.RepeatMode
import com.watermelon.common.model.SleepTimerMode
import com.watermelon.common.model.UserIntent
import com.watermelon.ui.WatermelonIcons
import com.watermelon.ui.components.LevelIndicator
import com.watermelon.ui.components.SleepTimerDialog
import com.watermelon.ui.components.WatermelonGlyph
import com.watermelon.ui.components.WatermelonSeekBar
import com.watermelon.ui.components.WatermelonTunerSeekBar
import com.watermelon.ui.screens.PlayerControlPanel.FileActionsSheet
import com.watermelon.ui.screens.PlayerControlPanel.PlayerActionsSheet
import com.watermelon.ui.screens.PlayerControlPanel.QuickToolsSheet
import com.watermelon.ui.theme.PlayerColors
import com.watermelon.ui.theme.WatermelonSpacing
import com.watermelon.ui.utils.ScreenshotManager
import com.watermelon.ui.utils.ScreenshotResult
import kotlinx.coroutines.launch
import kotlin.math.roundToLong

@Composable
fun ControlsLayer(
    state: PlayerScreenState,
    ui: PlayerUiState,
    viewModel: com.watermelon.ui.viewmodel.PlayerViewModel,
    position: Long,
    durationMs: Long,
    isPlaying: Boolean,
    playbackState: PlaybackState,
    repeatMode: RepeatMode,
    isShuffled: Boolean,
    sleepTimerRunning: Boolean,
    sleepTimerRemainingMs: Long,
    uri: String,
    mediaTitle: String,
    mediaContext: String,
    subtitleTrack: com.watermelon.common.model.ParsedSubtitle?,
    subtitleStyle: com.watermelon.common.model.SubtitleStyle,
    subtitleOffsetMs: Long,
    autoSyncEnabled: Boolean,
    autoSyncStatus: com.watermelon.common.subtitle.sync.SyncStatus,
    onSubtitleNudge: (Long) -> Unit,
    onAutoSync: () -> Unit,
    screenshotMode: ScreenshotMode,
    onPipClick: (() -> Unit)?,
    onBackgroundClick: ((Boolean) -> Unit)?,
    onShare: (() -> Unit)?,
    isFavourite: Boolean,
    onFavourite: ((Boolean) -> Unit)?,
    onAddToPlaylist: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    onExtractAudio: (() -> Unit)?,
    onTrimVideo: (() -> Unit)?,
    onCompressVideo: (() -> Unit)?,
    onLockChanged: ((Boolean) -> Unit)?,
    onTunerSeekBarEnabledChange: ((Boolean) -> Unit)?,
    tunerSeekBarEnabled: Boolean,
    tunerSeekStepSeconds: Int,
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback,
    scope: kotlinx.coroutines.CoroutineScope,
    context: android.content.Context,
    audioManager: AudioManager,
    maxVolume: Int,
    onBack: () -> Unit,
) {
    if (!ui.controlsVisible) return

    Box(
        Modifier.fillMaxSize()
            .pointerInput(state.isPlayerSheetOpen) {
                detectTapGestures(
                    onTap = {
                        if (state.isPlayerSheetOpen) {
                            state.showControlPanel = false
                            state.showQuickTools = false
                            state.showFileActions = false
                        } else {
                            state.lastInteraction = System.nanoTime(); ui.hideControls()
                        }
                    },
                    onDoubleTap = {
                        viewModel.onIntent(if (isPlaying) UserIntent.Pause else UserIntent.Resume)
                        state.lastInteraction = System.nanoTime()
                    }
                )
            }
    )

    Box(
        Modifier.fillMaxWidth().height(96.dp).align(Alignment.TopCenter)
            .background(Brush.verticalGradient(listOf(PlayerColors.current.controlBarScrim.copy(alpha = 0.6f), Color.Transparent)))
    )
    Box(
        Modifier.fillMaxWidth().height(140.dp).align(Alignment.BottomCenter)
            .background(Brush.verticalGradient(listOf(Color.Transparent, PlayerColors.current.controlBarScrim.copy(alpha = 0.7f))))
    )

    Row(
        modifier = Modifier.fillMaxWidth().align(Alignment.TopStart).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = {
            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
            if (state.isPlayerSheetOpen) {
                state.showControlPanel = false
                state.showQuickTools = false
                state.showFileActions = false
            } else {
                onBack()
            }
        }) {
            WatermelonGlyph(WatermelonIcons.ArrowBack, "Back", tint = PlayerColors.current.iconDefault)
        }
        TextButton(
            onClick = { state.showMediaInfo = true },
            modifier = Modifier.weight(1f),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = mediaTitle.ifBlank { "Now playing" },
                    color = PlayerColors.current.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (mediaContext.isNotBlank()) {
                    Text(
                        text = mediaContext,
                        color = PlayerColors.current.textSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        IconButton(onClick = {
            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
            ui.lock(); onLockChanged?.invoke(true)
        }) {
            WatermelonGlyph(WatermelonIcons.Lock, "Lock", tint = PlayerColors.current.iconDefault)
        }
        IconButton(onClick = {
            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
            state.showControlPanel = !state.showControlPanel
            state.showQuickTools = false
            state.showFileActions = false
        }) {
            WatermelonGlyph(
                WatermelonIcons.MoreVert,
                "Player actions",
                tint = if (state.showControlPanel) PlayerColors.current.iconActive else PlayerColors.current.iconDefault
            )
        }
    }

    val hasNextTrack = remember(uri) { PlaybackQueue.nextOf(uri) != null }

    Column(
        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        PlayerTransportControls(
            isPlaying = isPlaying,
            hasNextTrack = hasNextTrack,
            onPrevious = {
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                if (position > 3_000L) viewModel.onIntent(UserIntent.Seek(0L))
                else PlaybackQueue.previousOf(uri)?.let { state.onSkipToTrack?.invoke(it) }
                    ?: viewModel.onIntent(UserIntent.Seek(0L))
                state.lastInteraction = System.nanoTime(); ui.showControls()
            },
            onPlayPause = {
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                viewModel.onIntent(if (isPlaying) UserIntent.Pause else UserIntent.Resume)
                state.lastInteraction = System.nanoTime(); ui.showControls()
            },
            onNext = {
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                PlaybackQueue.nextOf(uri)?.let { state.onSkipToTrack?.invoke(it) }
                state.lastInteraction = System.nanoTime(); ui.showControls()
            },
            modifier = Modifier.padding(bottom = WatermelonSpacing.md)
        )
        if (tunerSeekBarEnabled) {
            WatermelonTunerSeekBar(
                positionMs = position,
                durationMs = durationMs,
                onSeek = { viewModel.onIntent(UserIntent.Seek(it)) },
                secondsPerTick = tunerSeekStepSeconds,
                onScrubChange = { scrubbing ->
                    state.lastInteraction = System.nanoTime()
                    state.isScrubbingSeekBar = scrubbing
                    ui.showControls()
                },
                onPreviewPositionChanged = { state.tunerPreviewPosition = it },
                onDetent = {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                },
                modifier = Modifier
            )
            Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 6.dp)) {
                Text(formatTime(state.tunerPreviewPosition), color = PlayerColors.current.textPrimary)
                Spacer(Modifier.weight(1f))
                Text("-${formatTime((durationMs - state.tunerPreviewPosition).coerceAtLeast(0L))}", color = PlayerColors.current.textPrimary)
            }
        } else {
            Row(Modifier.fillMaxWidth()) {
                Text(formatTime(position), color = PlayerColors.current.textPrimary)
                Spacer(Modifier.weight(1f))
                Text(formatTime(durationMs), color = PlayerColors.current.textPrimary)
            }
            WatermelonSeekBar(
                positionMs = position,
                durationMs = durationMs,
                onSeek = { viewModel.onIntent(UserIntent.Seek(it)) },
                onScrubChange = { scrubbing ->
                    state.lastInteraction = System.nanoTime()
                    state.isScrubbingSeekBar = scrubbing
                    ui.showControls()
                },
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
            )
        }
    }

    if (state.showControlPanel) {
        PlayerActionsSheet(
            onQuickTools = {
                state.showControlPanel = false
                state.showQuickTools = true
            },
            onFileActions = {
                state.showControlPanel = false
                state.showFileActions = true
            },
            onDismiss = { state.showControlPanel = false },
        )
    }
    if (state.showQuickTools) {
        QuickToolsSheet(
            currentSpeed = state.playbackSpeed,
            isMuted = state.currentVolume == 0,
            currentRatio = state.currentRatio,
            currentOrientation = state.currentOrientation,
            tunerSeekBarEnabled = tunerSeekBarEnabled,
            tunerSeekStepSeconds = tunerSeekStepSeconds,
            repeatMode = repeatMode,
            isShuffled = isShuffled,
            isPiP = state.isPiPEnabled,
            canUsePip = onPipClick != null,
            isBackground = state.isBackgroundEnabled,
            hasSubtitleTrack = subtitleTrack != null,
            subtitleOffsetMs = subtitleOffsetMs,
            autoSyncEnabled = autoSyncEnabled,
            autoSyncStatus = autoSyncStatus,
            onSubtitleNudge = onSubtitleNudge,
            onAutoSync = onAutoSync,
            onSpeedChange = { speed ->
                state.playbackSpeed = speed
                viewModel.onIntent(UserIntent.SetSpeed(speed))
            },
            onMuteToggle = {
                val muted = state.currentVolume == 0
                val volume = if (muted) (maxVolume / 2).coerceAtLeast(1) else 0
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
                state.currentVolume = volume
                state.volumeFraction = volume.toFloat() / maxVolume
            },
            onRatioChange = { state.currentRatio = it },
            onOrientationChange = { state.currentOrientation = it },
            onTunerSeekBarEnabledChange = { enabled ->
                onTunerSeekBarEnabledChange?.invoke(enabled)
                state.showQuickTools = false
            },
            onRepeat = { viewModel.cycleRepeat() },
            onShuffle = { viewModel.toggleShuffle() },
            onScreenshot = {
                scope.launch {
                    val mode = when (screenshotMode) {
                        ScreenshotMode.BURST -> ScreenshotManager.Mode.BURST
                        ScreenshotMode.SINGLE -> ScreenshotManager.Mode.SINGLE
                    }
                    val result = ScreenshotManager.takeScreenshot(context, uri, position, durationMs, mode)
                    state.screenshotMessage = when (result) {
                        is ScreenshotResult.Success -> "Saved ${result.uris.size} screenshot(s)"
                        is ScreenshotResult.Error -> "Screenshot failed"
                    }
                }
            },
            onSleepTimer = {
                state.showQuickTools = false
                state.showSleepTimerDialog = true
            },
            onPip = {
                if (onPipClick != null) {
                    state.showQuickTools = false
                    state.isPiPEnabled = true
                    state.isBackgroundEnabled = false
                    ui.hideControls()
                    onPipClick.invoke()
                }
            },
            onBackground = {
                if (!state.isBackgroundEnabled) {
                    state.isBackgroundEnabled = true
                    state.isPiPEnabled = false
                    onBackgroundClick?.invoke(true)
                } else {
                    state.isBackgroundEnabled = false
                    onBackgroundClick?.invoke(false)
                }
            },
            onDismiss = { state.showQuickTools = false },
        )
    }
    if (state.showFileActions) {
        FileActionsSheet(
            isFavourite = isFavourite,
            onShare = {
                state.showFileActions = false
                onShare?.invoke()
            },
            onFavourite = { onFavourite?.invoke(!isFavourite) },
            onAddToPlaylist = {
                state.showFileActions = false
                onAddToPlaylist?.invoke()
            },
            onExtractAudio = onExtractAudio?.let { action ->
                { state.showFileActions = false; action() }
            },
            onTrimVideo = onTrimVideo?.let { action ->
                { state.showFileActions = false; action() }
            },
            onCompressVideo = onCompressVideo?.let { action ->
                { state.showFileActions = false; action() }
            },
            onDelete = {
                state.showFileActions = false
                onDelete?.invoke()
            },
            onDismiss = { state.showFileActions = false },
        )
    }
}

private fun formatTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0); return "%d:%02d".format(s / 60, s % 60)
}

@Composable
private fun PlayerTransportControls(
    isPlaying: Boolean,
    hasNextTrack: Boolean,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        IconButton(onClick = onPrevious) {
            WatermelonGlyph(
                WatermelonIcons.SkipPrevious,
                "Previous track",
                tint = PlayerColors.current.iconDefault,
                modifier = Modifier.width(30.dp).height(30.dp)
            )
        }
        IconButton(
            onClick = onPlayPause,
            modifier = Modifier
                .width(64.dp).height(64.dp)
                .background(PlayerColors.current.accent, androidx.compose.foundation.shape.CircleShape)
        ) {
            WatermelonGlyph(
                if (isPlaying) WatermelonIcons.Pause else WatermelonIcons.Play,
                if (isPlaying) "Pause" else "Play",
                tint = Color.White,
                modifier = Modifier.width(32.dp).height(32.dp)
            )
        }
        if (hasNextTrack) {
            IconButton(onClick = onNext) {
                WatermelonGlyph(
                    WatermelonIcons.SkipNext,
                    "Next track",
                    tint = PlayerColors.current.iconDefault,
                    modifier = Modifier.width(30.dp).height(30.dp)
                )
            }
        } else {
            Spacer(Modifier.width(48.dp))
        }
    }
}