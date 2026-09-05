package com.watermelon.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset

/**
 * Aggregates all mutable UI state for the phone player screen.
 * Hoisted to the main composable and passed down to layer composables.
 */
class PlayerScreenState {
    // Player feature state
    var playbackSpeed by mutableStateOf(1f)
    var currentRatio by mutableStateOf(VideoRatio.FILL)
    var scale by mutableStateOf(1f)
    var panOffset by mutableStateOf(Offset.Zero)
    var currentOrientation by mutableStateOf(ScreenOrientation.AUTO)

    // Sheet visibility
    var showControlPanel by mutableStateOf(false)
    var showQuickTools by mutableStateOf(false)
    var showFileActions by mutableStateOf(false)
    var showMediaInfo by mutableStateOf(false)
    var showTunerSeekTip by mutableStateOf(false)
    var showSleepTimerDialog by mutableStateOf(false)
    var isPiPEnabled by mutableStateOf(false)
    var isBackgroundEnabled by mutableStateOf(false)

    // Transient indicators
    var screenshotMessage by mutableStateOf<String?>(null)
    var currentVolume by mutableIntStateOf(0)
    var volumeFraction by mutableFloatStateOf(0f)
    var showVolumeIndicator by mutableStateOf(false)
    var currentBrightness by mutableFloatStateOf(0.5f)
    var showBrightnessIndicator by mutableStateOf(false)

    // Gesture transient state
    var isHolding by mutableStateOf(false)
    var isPointerDown by mutableStateOf(false)
    var isGestureMoving by mutableStateOf(false)
    var holdIsLeft by mutableStateOf(false)
    var holdSpeed by mutableFloatStateOf(2f)
    var seekFrac by mutableFloatStateOf(0f)
    var isScrubbingSeekBar by mutableStateOf(false)
    var tunerPreviewPosition by mutableLongStateOf(0L)
    var lastGestureTapNanos by mutableLongStateOf(0L)
    var lastInteraction by mutableLongStateOf(0L)

    val isPlayerSheetOpen: Boolean
        get() = showControlPanel || showQuickTools || showFileActions

    // Dynamic values set each frame
    var position: Long = 0L
    var isPlaying: Boolean = false
    var durationMs: Long = 0L
    var uri: String = ""
    var mediaTitle: String = ""
    var mediaContext: String = ""
    var subtitleTrack: com.watermelon.common.model.ParsedSubtitle? = null
    var subtitleStyle: com.watermelon.common.model.SubtitleStyle = com.watermelon.common.model.SubtitleStyle()
    var subtitleOffsetMs: Long = 0L
    var autoSyncEnabled: Boolean = false
    var autoSyncStatus: com.watermelon.common.subtitle.sync.SyncStatus =
        com.watermelon.common.subtitle.sync.SyncStatus.IDLE
    var repeatMode: com.watermelon.common.model.RepeatMode = com.watermelon.common.model.RepeatMode.NONE
    var isShuffled: Boolean = false
    var sleepTimerRunning: Boolean = false
    var sleepTimerRemainingMs: Long = 0L
    var tunerSeekBarEnabled: Boolean = true
    var tunerSeekStepSeconds: Int = 5
    var onTunerSeekBarEnabledChange: ((Boolean) -> Unit)? = null
    var onBack: (() -> Unit)? = null
    var onPipClick: (() -> Unit)? = null
    var onBackgroundClick: ((Boolean) -> Unit)? = null
    var onBrightnessChange: ((Float) -> Unit)? = null
    var onSkipToTrack: ((String) -> Unit)? = null
    var onShare: (() -> Unit)? = null
    var isFavourite: Boolean = false
    var onFavourite: ((Boolean) -> Unit)? = null
    var onAddToPlaylist: (() -> Unit)? = null
    var onDelete: (() -> Unit)? = null
    var onExtractAudio: (() -> Unit)? = null
    var onTrimVideo: (() -> Unit)? = null
    var onCompressVideo: (() -> Unit)? = null
    var onLockChanged: ((Boolean) -> Unit)? = null
    var viewModel: com.watermelon.ui.viewmodel.PlayerViewModel? = null
    var haptic: androidx.compose.ui.hapticfeedback.HapticFeedback? = null
    var audioManager: android.media.AudioManager? = null
    var maxVolume: Int = 0
    var screenshotMode: ScreenshotMode = ScreenshotMode.SINGLE
    var initialBrightness: Float = -1f
    var tvm: Any? = null
}