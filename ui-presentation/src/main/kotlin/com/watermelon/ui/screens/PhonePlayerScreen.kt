package com.watermelon.ui.screens

import android.app.Activity
import android.content.pm.ActivityInfo
import android.media.AudioManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.watermelon.common.model.PlaybackState
import com.watermelon.common.model.SleepTimerMode
import com.watermelon.common.model.UserIntent
import com.watermelon.ui.WatermelonIcons
import com.watermelon.ui.components.LevelIndicator
import com.watermelon.ui.components.SleepTimerDialog
import com.watermelon.ui.components.SubtitleOverlay
import com.watermelon.ui.components.WatermelonSeekBar
import com.watermelon.ui.components.WatermelonTunerSeekBar
import com.watermelon.ui.components.WatermelonGlyph
import com.watermelon.ui.player.VhsEffectController
import com.watermelon.ui.theme.PlayerColors
import com.watermelon.ui.theme.WatermelonSpacing
import com.watermelon.ui.utils.ScreenshotManager
import com.watermelon.ui.utils.ScreenshotResult
import com.watermelon.ui.viewmodel.PlayerViewModel
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Phone video player — X-Player-style, layered architecture.
 *
 * LAYER ORDER (bottom → top), each layer's touch handling is explicit:
 *   1. Video surface
 *   2. Gesture surface        — active ONLY when ui.gesturesEnabled (no sheet, not locked)
 *   3. Tap/scrim + controls   — controls are tap-toggled; a light gradient sits only behind
 *                               the top/bottom bars (NOT a full-screen pause dim)
 *   4. Transient indicators   — brightness/volume level, hold speed
 *   5. Panels / dialogs       — control panel, sleep timer (suspend auto-hide while open)
 *
 * VHS is fully external: this screen only calls vhs.configure / onSurfaceSize / setRewind /
 * effectOrNull, and — on API 23–32 devices where AGSL isn't available — draws a lightweight
 * Compose scanline overlay driven by vhs.usesLegacyOverlay / scanlinePhase / overlayAlpha, so
 * the effect isn't AGSL/API-33-exclusive. When VHS is disabled in settings the controller is a
 * complete no-op either way.
 *
 * FF/FR hold gesture is core and stays here (hold → 2×, drag ramps 3/4/8×, left = reverse).
 */
@Composable
fun PhonePlayerScreen(
    viewModel: PlayerViewModel,
    vhs: VhsEffectController,
    vhsEnabled: Boolean,
    vhsIntensity: Float,
    tunerSeekBarEnabled: Boolean = true,
    tunerSeekStepSeconds: Int = 5,
    onTunerSeekBarEnabledChange: ((Boolean) -> Unit)? = null,
    durationMs: Long,
    surface: @Composable (Modifier) -> Unit,
    onBack: () -> Unit,
    uri: String = "",
    mediaTitle: String = "",
    mediaContext: String = "",
    subtitleTrack: com.watermelon.common.model.ParsedSubtitle? = null,
    subtitleStyle: com.watermelon.common.model.SubtitleStyle = com.watermelon.common.model.SubtitleStyle(),
    subtitleOffsetMs: Long = 0L,
    autoSyncEnabled: Boolean = false,
    autoSyncStatus: com.watermelon.common.subtitle.sync.SyncStatus =
        com.watermelon.common.subtitle.sync.SyncStatus.IDLE,
    onSubtitleNudge: (Long) -> Unit = {},
    onAutoSync: () -> Unit = {},
    screenshotMode: ScreenshotMode = ScreenshotMode.SINGLE,
    initialBrightness: Float = -1f,
    onPipClick: (() -> Unit)? = null,
    onBackgroundClick: ((Boolean) -> Unit)? = null,
    onBrightnessChange: ((Float) -> Unit)? = null,
    onSkipToTrack: ((String) -> Unit)? = null,
    onShare: (() -> Unit)? = null,
    isFavourite: Boolean = false,
    onFavourite: ((Boolean) -> Unit)? = null,
    onAddToPlaylist: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onExtractAudio: (() -> Unit)? = null,
    onTrimVideo: (() -> Unit)? = null,
    onCompressVideo: (() -> Unit)? = null,
    onLockChanged: ((Boolean) -> Unit)? = null,
    isInPipMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val activity = context as? Activity
    val audioManager = remember { context.getSystemService(AudioManager::class.java) }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }
    val scope = rememberCoroutineScope()

    val position by viewModel.currentPositionMs.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val repeatMode by viewModel.repeatMode.collectAsStateWithLifecycle()
    val isShuffled by viewModel.shuffleEnabled.collectAsStateWithLifecycle()
    val sleepTimerRunning by viewModel.sleepTimerRunning.collectAsStateWithLifecycle()
    val sleepTimerRemainingMs by viewModel.sleepTimerRemainingMs.collectAsStateWithLifecycle()

    val uiState = remember { PlayerScreenState() }
    uiState.currentRatio = VideoRatio.FILL
    uiState.scale = 1f
    uiState.panOffset = Offset.Zero
    uiState.currentOrientation = ScreenOrientation.AUTO
    uiState.position = position
    uiState.isPlaying = playbackState == PlaybackState.PLAYING
    uiState.durationMs = durationMs
    uiState.uri = uri
    uiState.mediaTitle = mediaTitle
    uiState.mediaContext = mediaContext
    uiState.subtitleTrack = subtitleTrack
    uiState.subtitleStyle = subtitleStyle
    uiState.subtitleOffsetMs = subtitleOffsetMs
    uiState.autoSyncEnabled = autoSyncEnabled
    uiState.autoSyncStatus = autoSyncStatus
    uiState.repeatMode = repeatMode
    uiState.isShuffled = isShuffled
    uiState.sleepTimerRunning = sleepTimerRunning
    uiState.sleepTimerRemainingMs = sleepTimerRemainingMs
    uiState.tunerSeekBarEnabled = tunerSeekBarEnabled
    uiState.tunerSeekStepSeconds = tunerSeekStepSeconds
    uiState.onTunerSeekBarEnabledChange = onTunerSeekBarEnabledChange
    uiState.onBack = onBack
    uiState.onPipClick = onPipClick
    uiState.onBackgroundClick = onBackgroundClick
    uiState.onBrightnessChange = onBrightnessChange
    uiState.onSkipToTrack = onSkipToTrack
    uiState.onShare = onShare
    uiState.isFavourite = isFavourite
    uiState.onFavourite = onFavourite
    uiState.onAddToPlaylist = onAddToPlaylist
    uiState.onDelete = onDelete
    uiState.onExtractAudio = onExtractAudio
    uiState.onTrimVideo = onTrimVideo
    uiState.onCompressVideo = onCompressVideo
    uiState.onLockChanged = onLockChanged
    uiState.viewModel = viewModel
    uiState.haptic = haptic
    uiState.audioManager = audioManager
    uiState.maxVolume = maxVolume
    uiState.screenshotMode = screenshotMode
    uiState.initialBrightness = initialBrightness

    val ui = PlayerUiState()

    // Keep local PiP flag in sync with the real system PiP state
    LaunchedEffect(isInPipMode) {
        uiState.isPiPEnabled = isInPipMode
        if (!isInPipMode) {
            ui.showControls()
        }
    }
    var isBackgroundEnabled by remember { mutableStateOf(false) }

    // Layer composables
    VideoSurfaceLayer(
        state = uiState,
        vhs = vhs,
        vhsEnabled = vhsEnabled,
        vhsIntensity = vhsIntensity,
        durationMs = durationMs,
        surface = surface,
        subtitleTrack = subtitleTrack,
        subtitleStyle = subtitleStyle,
        subtitleOffsetMs = subtitleOffsetMs,
        position = position,
        ui = ui,
    )

    GestureLayer(
        state = uiState,
        ui = ui,
        viewModel = viewModel,
        durationMs = durationMs,
        position = position,
        isPlaying = uiState.isPlaying,
        audioManager = audioManager,
        maxVolume = maxVolume,
        haptic = haptic,
        activity = activity,
        vhs = vhs,
        onBrightnessChange = onBrightnessChange,
    )

    ControlsLayer(
        state = uiState,
        ui = ui,
        viewModel = viewModel,
        position = position,
        durationMs = durationMs,
        isPlaying = uiState.isPlaying,
        playbackState = playbackState,
        repeatMode = repeatMode,
        isShuffled = isShuffled,
        sleepTimerRunning = sleepTimerRunning,
        sleepTimerRemainingMs = sleepTimerRemainingMs,
        uri = uri,
        mediaTitle = mediaTitle,
        mediaContext = mediaContext,
        subtitleTrack = subtitleTrack,
        subtitleStyle = subtitleStyle,
        subtitleOffsetMs = subtitleOffsetMs,
        autoSyncEnabled = autoSyncEnabled,
        autoSyncStatus = autoSyncStatus,
        onSubtitleNudge = onSubtitleNudge,
        onAutoSync = onAutoSync,
        screenshotMode = screenshotMode,
        onPipClick = onPipClick,
        onBackgroundClick = onBackgroundClick,
        onShare = onShare,
        isFavourite = isFavourite,
        onFavourite = onFavourite,
        onAddToPlaylist = onAddToPlaylist,
        onDelete = onDelete,
        onExtractAudio = onExtractAudio,
        onTrimVideo = onTrimVideo,
        onCompressVideo = onCompressVideo,
        onLockChanged = onLockChanged,
        onTunerSeekBarEnabledChange = onTunerSeekBarEnabledChange,
        tunerSeekBarEnabled = tunerSeekBarEnabled,
        tunerSeekStepSeconds = tunerSeekStepSeconds,
        haptic = haptic,
        scope = scope,
        context = context,
        audioManager = audioManager,
        maxVolume = maxVolume,
        onBack = onBack,
    )

    TransientIndicatorsLayer(
        state = uiState,
        ui = ui,
        vhs = vhs,
    )

    ProgressBarLayer(
        tunerSeekBarEnabled = tunerSeekBarEnabled,
        durationMs = durationMs,
        position = position,
    )

    PlayerDialogs(
        state = uiState,
        context = context,
        tunerSeekBarEnabled = tunerSeekBarEnabled,
        tunerSeekStepSeconds = tunerSeekStepSeconds,
        mediaTitle = mediaTitle,
        mediaContext = mediaContext,
        showSleepTimerDialog = uiState.showSleepTimerDialog,
        sleepTimerRunning = sleepTimerRunning,
        sleepTimerRemainingMs = sleepTimerRemainingMs,
        viewModel = viewModel,
        onSleepTimerDismiss = { uiState.showSleepTimerDialog = false },
        onSleepTimerSet = { mode, minutes ->
            val sleepMode = when (mode) {
                "current_video" -> SleepTimerMode.EndOfVideo
                "folder" -> SleepTimerMode.EndOfFolder
                "custom" -> SleepTimerMode.Custom(minutes ?: 15)
                else -> SleepTimerMode.Custom(15)
            }
            viewModel.setSleepTimer(sleepMode)
            uiState.showSleepTimerDialog = false
        },
    )

    // Auto-advance on natural end-of-video
    LaunchedEffect(playbackState) {
        if (playbackState == PlaybackState.ENDED) {
            PlaybackQueue.nextOf(uri)?.let { onSkipToTrack?.invoke(it) }
        }
    }

    // Pushes "is this the last item in the queue" to the controller
    LaunchedEffect(uri) {
        viewModel.setQueueContext(PlaybackQueue.nextOf(uri) == null)
    }

    // Auto-hide timer
    LaunchedEffect(
        uiState.lastInteraction, ui.controlsVisible, uiState.isPlaying, uiState.isPlayerSheetOpen, ui.isLocked,
        uiState.isScrubbingSeekBar, uiState.isHolding
    ) {
        if (uiState.isScrubbingSeekBar || uiState.isHolding) return@LaunchedEffect
        if (ui.autoHideEligible(uiState.isPlaying)) {
            kotlinx.coroutines.delay(5_000)
            if (!uiState.isScrubbingSeekBar && !uiState.isHolding) ui.hideControls()
        }
    }
    LaunchedEffect(uiState.showVolumeIndicator) { if (uiState.showVolumeIndicator) { kotlinx.coroutines.delay(1_500); uiState.showVolumeIndicator = false } }
    LaunchedEffect(uiState.showBrightnessIndicator) { if (uiState.showBrightnessIndicator) { kotlinx.coroutines.delay(1_500); uiState.showBrightnessIndicator = false } }
    LaunchedEffect(uiState.screenshotMessage) { if (uiState.screenshotMessage != null) { kotlinx.coroutines.delay(2_500); uiState.screenshotMessage = null } }
    LaunchedEffect(tunerSeekBarEnabled) {
        val playerPreferences = context.getSharedPreferences("player_ui", android.content.Context.MODE_PRIVATE)
        if (tunerSeekBarEnabled && !playerPreferences.getBoolean("tuner_seek_tip_seen", false)) {
            uiState.showTunerSeekTip = true
        }
    }

    // Restore brightness on launch (window-scoped, reverts on exit).
    LaunchedEffect(Unit) {
        val priorWindowBrightness = activity?.window?.attributes?.screenBrightness ?: -1f
        val startBrightness = initialBrightness.takeIf { it in 0f..1f }
            ?: priorWindowBrightness.takeIf { it in 0f..1f }
            ?: 0.5f
        if (startBrightness in 0f..1f) activity?.window?.let { win ->
            val a = win.attributes; a.screenBrightness = startBrightness; win.attributes = a
        }
    }
    LaunchedEffect(uiState.currentOrientation) {
        activity?.requestedOrientation = when (uiState.currentOrientation) {
            ScreenOrientation.AUTO -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            ScreenOrientation.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            ScreenOrientation.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }

    // FF/FR hold gesture (CORE — independent of VHS). Notifies vhs.setRewind for the effect.
    LaunchedEffect(uiState.isPointerDown, uiState.isGestureMoving) {
        if (uiState.isPointerDown && !uiState.isGestureMoving) {
            kotlinx.coroutines.delay(500L)
            if (uiState.isPointerDown && !uiState.isGestureMoving) {
                uiState.isHolding = true
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                while (uiState.isPointerDown) {
                    if (uiState.holdIsLeft) {
                        vhs.setRewind(active = true, forward = false, speed = uiState.holdSpeed)
                        val stepMs = (uiState.holdSpeed * 1_000L).toLong()
                        viewModel.onIntent(UserIntent.Seek((position - stepMs).coerceAtLeast(0L)))
                        kotlinx.coroutines.delay((220L / uiState.holdSpeed).toLong().coerceAtLeast(40L))
                    } else {
                        vhs.setRewind(active = true, forward = true, speed = uiState.holdSpeed)
                        viewModel.onIntent(UserIntent.SetSpeed(uiState.holdSpeed))
                        kotlinx.coroutines.delay(80L)
                    }
                }
            }
        } else if (uiState.isHolding) {
            uiState.isHolding = false
            uiState.holdSpeed = 2f
            vhs.setRewind(active = false, forward = false, speed = 0f)
            viewModel.onIntent(UserIntent.SetSpeed(1f))
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            // Don't pause if the user chose background play or PiP — that's the whole point.
            if (!isBackgroundEnabled && !uiState.isPiPEnabled) viewModel.onIntent(UserIntent.Pause)
            viewModel.onIntent(UserIntent.SetSpeed(1f))
            // Revert the window brightness to whatever it was before the player opened.
            val priorWindowBrightness = activity?.window?.attributes?.screenBrightness ?: -1f
            activity?.window?.let { win ->
                val a = win.attributes
                a.screenBrightness = priorWindowBrightness
                win.attributes = a
            }
        }
    }
    BackHandler(enabled = true) {
        when {
            ui.isLocked -> { /* locked: Back does nothing — must use the slide-unlock */ }
            ui.sheetOpen || uiState.isPlayerSheetOpen -> {
                uiState.showControlPanel = false
                uiState.showQuickTools = false
                uiState.showFileActions = false
                ui.closeSheet()
            }
            else -> onBack()
        }
    }
}