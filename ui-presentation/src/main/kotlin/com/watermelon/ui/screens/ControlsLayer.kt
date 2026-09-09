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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import com.watermelon.common.PLAYER_SUBTITLE_LANGUAGE_PRIORITY
import com.watermelon.ui.WatermelonIcons
import com.watermelon.ui.components.LevelIndicator
import com.watermelon.ui.components.SleepTimerDialog
import com.watermelon.ui.components.WatermelonGlyph
import com.watermelon.ui.components.WatermelonSeekBar
import com.watermelon.ui.components.WatermelonTunerSeekBar
import com.watermelon.ui.screens.PlayerControlPanel.FileActionsSheet
import com.watermelon.ui.screens.PlayerControlPanel.PlayerActionsSheet
import com.watermelon.ui.screens.PlayerControlPanel.QuickToolsSheet
import com.watermelon.ui.screens.OnlineSubtitlesSheet
import com.watermelon.ui.screens.OnlineSubtitlesUiState
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
    subtitleRepository: com.watermelon.common.repository.SubtitleRepository? = null,
    onSubtitleLoaded: ((com.watermelon.common.model.ParsedSubtitle) -> Unit)? = null,
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback,
    scope: kotlinx.coroutines.CoroutineScope,
    context: android.content.Context,
    audioManager: AudioManager,
    maxVolume: Int,
    onBack: () -> Unit,
    mediaItem: com.watermelon.common.model.MediaItem? = null,
) {
    val actualState = state
    val actualMediaItem = mediaItem
    val actualSubtitleRepository = subtitleRepository
    val actualScope = scope
    val actualOnSubtitleLoaded = onSubtitleLoaded
    
    if (!ui.controlsVisible) return

    // Full-screen overlay scope: reproduces the original root Box so .align()
    // calls below resolve against a BoxScope, as when this was inline.
    Box(Modifier.fillMaxSize()) {
    Box(
        Modifier.fillMaxSize()
            .pointerInput(actualState.isPlayerSheetOpen) {
                detectTapGestures(
                    onTap = {
                        if (actualState.isPlayerSheetOpen) {
                            actualState.showControlPanel = false
                            actualState.showQuickTools = false
                            actualState.showFileActions = false
                            actualState.showOnlineSubtitlesSheet = false
                        } else {
                            actualState.lastInteraction = System.nanoTime(); ui.hideControls()
                        }
                    },
                    onDoubleTap = {
                        viewModel.onIntent(if (isPlaying) UserIntent.Pause else UserIntent.Resume)
                        actualState.lastInteraction = System.nanoTime()
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
            if (actualState.isPlayerSheetOpen) {
                actualState.showControlPanel = false
                actualState.showQuickTools = false
                actualState.showFileActions = false
                actualState.showOnlineSubtitlesSheet = false
            } else {
                onBack()
            }
        }) {
            WatermelonGlyph(WatermelonIcons.ArrowBack, "Back", tint = PlayerColors.current.iconDefault)
        }
        TextButton(
            onClick = { actualState.showMediaInfo = true },
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
            actualState.showControlPanel = !actualState.showControlPanel
            actualState.showQuickTools = false
            actualState.showFileActions = false
            actualState.showOnlineSubtitlesSheet = false
        }) {
            WatermelonGlyph(
                WatermelonIcons.MoreVert,
                "Player actions",
                tint = if (actualState.showControlPanel) PlayerColors.current.iconActive else PlayerColors.current.iconDefault
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
                else PlaybackQueue.previousOf(uri)?.let { actualState.onSkipToTrack?.invoke(it) }
                    ?: viewModel.onIntent(UserIntent.Seek(0L))
                actualState.lastInteraction = System.nanoTime(); ui.showControls()
            },
            onPlayPause = {
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                viewModel.onIntent(if (isPlaying) UserIntent.Pause else UserIntent.Resume)
                actualState.lastInteraction = System.nanoTime(); ui.showControls()
            },
            onNext = {
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                PlaybackQueue.nextOf(uri)?.let { actualState.onSkipToTrack?.invoke(it) }
                actualState.lastInteraction = System.nanoTime(); ui.showControls()
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
                    actualState.lastInteraction = System.nanoTime()
                    actualState.isScrubbingSeekBar = scrubbing
                    ui.showControls()
                },
                onPreviewPositionChanged = { actualState.tunerPreviewPosition = it },
                onDetent = {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                },
                modifier = Modifier
            )
            Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 6.dp)) {
                Text(formatTime(actualState.tunerPreviewPosition), color = PlayerColors.current.textPrimary)
                Spacer(Modifier.weight(1f))
                Text("-${formatTime((durationMs - actualState.tunerPreviewPosition).coerceAtLeast(0L))}", color = PlayerColors.current.textPrimary)
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
                    actualState.lastInteraction = System.nanoTime()
                    actualState.isScrubbingSeekBar = scrubbing
                    ui.showControls()
                },
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
            )
        }
    }

    if (actualState.showControlPanel) {
        PlayerActionsSheet(
            onQuickTools = {
                actualState.showControlPanel = false
                actualState.showQuickTools = true
            },
            onFileActions = {
                actualState.showControlPanel = false
                actualState.showFileActions = true
            },
            onDismiss = { actualState.showControlPanel = false },
        )
    }
    if (actualState.showQuickTools) {
        QuickToolsSheet(
            currentSpeed = actualState.playbackSpeed,
            isMuted = actualState.currentVolume == 0,
            currentRatio = actualState.currentRatio,
            currentOrientation = actualState.currentOrientation,
            tunerSeekBarEnabled = tunerSeekBarEnabled,
            tunerSeekStepSeconds = tunerSeekStepSeconds,
            repeatMode = repeatMode,
            isShuffled = isShuffled,
            isPiP = actualState.isPiPEnabled,
            canUsePip = onPipClick != null,
            isBackground = actualState.isBackgroundEnabled,
            hasSubtitleTrack = subtitleTrack != null,
            subtitleOffsetMs = subtitleOffsetMs,
            autoSyncEnabled = autoSyncEnabled,
            autoSyncStatus = autoSyncStatus,
            // Only expose online search when the sheet can actually render (it
            // requires both mediaItem and subtitleRepository) — otherwise the
            // tap would set showOnlineSubtitlesSheet with no visible sheet and
            // leave isPlayerSheetOpen stuck true.
            onFindOnlineSubtitles =
                if (actualMediaItem != null && actualSubtitleRepository != null) {
                    {
                        actualState.showQuickTools = false
                        actualState.showOnlineSubtitlesSheet = true
                    }
                } else {
                    null
                },
            onSubtitleNudge = onSubtitleNudge,
            onAutoSync = onAutoSync,
            onSpeedChange = { speed ->
                actualState.playbackSpeed = speed
                viewModel.onIntent(UserIntent.SetSpeed(speed))
            },
            onMuteToggle = {
                val muted = actualState.currentVolume == 0
                val volume = if (muted) (maxVolume / 2).coerceAtLeast(1) else 0
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
                actualState.currentVolume = volume
                actualState.volumeFraction = volume.toFloat() / maxVolume
            },
            onRatioChange = { actualState.currentRatio = it },
            onOrientationChange = { actualState.currentOrientation = it },
            onTunerSeekBarEnabledChange = { enabled ->
                onTunerSeekBarEnabledChange?.invoke(enabled)
                actualState.showQuickTools = false
            },
            onRepeat = { viewModel.cycleRepeat() },
            onShuffle = { viewModel.toggleShuffle() },
            onScreenshot = {
                actualScope.launch {
                    val mode = when (screenshotMode) {
                        ScreenshotMode.BURST -> ScreenshotManager.Mode.BURST
                        ScreenshotMode.SINGLE -> ScreenshotManager.Mode.SINGLE
                    }
                    val result = ScreenshotManager.takeScreenshot(context, uri, position, durationMs, mode)
                    actualState.screenshotMessage = when (result) {
                        is ScreenshotResult.Success -> "Saved ${result.uris.size} screenshot(s)"
                        is ScreenshotResult.Error -> "Screenshot failed"
                    }
                }
            },
            onSleepTimer = {
                actualState.showQuickTools = false
                actualState.showSleepTimerDialog = true
            },
            onPip = {
                if (onPipClick != null) {
                    actualState.showQuickTools = false
                    actualState.isPiPEnabled = true
                    actualState.isBackgroundEnabled = false
                    ui.hideControls()
                    onPipClick.invoke()
                }
            },
            onBackground = {
                if (!actualState.isBackgroundEnabled) {
                    actualState.isBackgroundEnabled = true
                    actualState.isPiPEnabled = false
                    onBackgroundClick?.invoke(true)
                } else {
                    actualState.isBackgroundEnabled = false
                    onBackgroundClick?.invoke(false)
                }
            },
            onDismiss = { actualState.showQuickTools = false },
        )
    }
    if (actualState.showFileActions) {
        FileActionsSheet(
            isFavourite = isFavourite,
            onShare = {
                actualState.showFileActions = false
                onShare?.invoke()
            },
            onFavourite = { onFavourite?.invoke(!isFavourite) },
            onAddToPlaylist = {
                actualState.showFileActions = false
                onAddToPlaylist?.invoke()
            },
            onExtractAudio = onExtractAudio?.let { action ->
                { actualState.showFileActions = false; action() }
            },
            onTrimVideo = onTrimVideo?.let { action ->
                { actualState.showFileActions = false; action() }
            },
            onCompressVideo = onCompressVideo?.let { action ->
                { actualState.showFileActions = false; action() }
            },
            onDelete = {
                actualState.showFileActions = false
                onDelete?.invoke()
            },
            onDismiss = { actualState.showFileActions = false },
        )
    }
    if (actualState.showOnlineSubtitlesSheet && actualMediaItem != null && actualSubtitleRepository != null) {
        OnlineSubtitlesSheet(
            uiState = actualState.onlineSubtitlesUiState,
            onSearch = {
                actualScope.launch {
                    actualState.onlineSubtitlesUiState = OnlineSubtitlesUiState.Searching
                    try {
                        val result = actualSubtitleRepository.searchOnlineSubtitles(
                            mediaItem = actualMediaItem,
                            preferredLanguages = PLAYER_SUBTITLE_LANGUAGE_PRIORITY
                        )
                        actualState.onlineSubtitlesUiState = when (result) {
                            is com.watermelon.common.repository.OnlineSubtitleSearchResult.Success ->
                                OnlineSubtitlesUiState.Results(result.tracks)
                            com.watermelon.common.repository.OnlineSubtitleSearchResult.NoResults ->
                                OnlineSubtitlesUiState.Results(emptyList())
                            com.watermelon.common.repository.OnlineSubtitleSearchResult.Offline ->
                                OnlineSubtitlesUiState.Offline
                            com.watermelon.common.repository.OnlineSubtitleSearchResult.ProviderNotConfigured ->
                                OnlineSubtitlesUiState.ProviderNotConfigured
                            com.watermelon.common.repository.OnlineSubtitleSearchResult.AuthenticationRequired ->
                                OnlineSubtitlesUiState.AuthenticationRequired
                            com.watermelon.common.repository.OnlineSubtitleSearchResult.PermissionDenied ->
                                OnlineSubtitlesUiState.Error("Permission denied")
                            com.watermelon.common.repository.OnlineSubtitleSearchResult.QuotaExceeded ->
                                OnlineSubtitlesUiState.QuotaExceeded
                            is com.watermelon.common.repository.OnlineSubtitleSearchResult.Failure ->
                                OnlineSubtitlesUiState.Error(result.message)
                        }
                    } catch (e: Exception) {
                        actualState.onlineSubtitlesUiState = OnlineSubtitlesUiState.Error(e.message ?: "Unknown error")
                    }
                }
            },
            onDownload = { track ->
                actualScope.launch {
                    actualState.onlineSubtitlesUiState = OnlineSubtitlesUiState.Downloading(track)
                    try {
                        val downloaded = actualSubtitleRepository.downloadSubtitle(
                            mediaItem = actualMediaItem,
                            track = track
                        )
                        // Activate the downloaded subtitle immediately
                        actualOnSubtitleLoaded?.invoke(downloaded.subtitle)
                        actualState.showOnlineSubtitlesSheet = false
                        actualState.onlineSubtitlesUiState = OnlineSubtitlesUiState.Idle
                    } catch (e: Exception) {
                        actualState.onlineSubtitlesUiState = OnlineSubtitlesUiState.Error(e.message ?: "Download failed")
                    }
                }
            },
            onDismiss = {
                actualState.showOnlineSubtitlesSheet = false
                actualState.onlineSubtitlesUiState = OnlineSubtitlesUiState.Idle
            },
        )
    }
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
