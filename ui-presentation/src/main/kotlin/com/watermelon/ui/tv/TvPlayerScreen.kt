package com.watermelon.ui.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.watermelon.common.model.ParsedSubtitle
import com.watermelon.common.model.PlaybackState
import com.watermelon.common.model.SubtitleStyle
import com.watermelon.common.model.UserIntent
import com.watermelon.ui.R
import com.watermelon.ui.components.SubtitleOverlay
import com.watermelon.ui.theme.PlayerColors
import com.watermelon.ui.theme.WatermelonSpacing
import com.watermelon.ui.viewmodel.PlayerViewModel

/**
 * TV player composition (Manifest §8) — a separate D-pad-first composition from
 * [com.watermelon.ui.screens.PhonePlayerScreen], sharing only the playback core
 * ([PlayerViewModel]) and video [surface], per this module's design: phone and TV are two
 * compositions sharing one playback core, not a single screen with conditionals.
 *
 * Deliberately excluded, matching the agreed TV scope: pinch-zoom, brightness/volume drag,
 * VHS shader, PiP, rotation lock. None of these map to a D-pad + OK input model, and the VHS
 * shader in particular relies on touch-driven hold gestures that don't exist on a remote.
 *
 * Requirements satisfied:
 *  - D-pad-first: every primary action is a visible focus stop operated by SELECT.
 *  - Works with a partially-broken remote: the initial focus lands on Play/Pause, so SELECT is
 *    the single guaranteed control needed to operate the player.
 *  - Minimal action set: transport, fixed seek, and subtitle timing adjustment when subtitles
 *    are available.
 *  - Large, focusable targets with a visible focus ring and scale cue (no reliance on color alone).
 */
@Composable
fun TvPlayerScreen(
    viewModel: PlayerViewModel,
    surface: @Composable (Modifier) -> Unit,
    durationMs: Long,
    hasPreviousTrack: Boolean,
    hasNextTrack: Boolean,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onExit: () -> Unit,
    subtitleTrack: ParsedSubtitle? = null,
    subtitleStyle: SubtitleStyle = SubtitleStyle(),
    subtitleOffsetMs: Long = 0L,
    autoSyncEnabled: Boolean = false,
    autoSyncStatus: com.watermelon.common.subtitle.sync.SyncStatus =
        com.watermelon.common.subtitle.sync.SyncStatus.IDLE,
    onSubtitleNudge: (Long) -> Unit = {},
    onAutoSync: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val positionMs by viewModel.currentPositionMs.collectAsStateWithLifecycle()
    val isPlaying = playbackState == PlaybackState.PLAYING

    // Keep Android's Back behaviour explicit without consuming D-pad direction keys. Directional
    // focus movement remains under Compose's default traversal and every player action has a
    // visible control below.
    BackHandler(onBack = onExit)

    // Auto-advance on natural end-of-video — same reasoning as PhonePlayerScreen's
    // identical effect: reuses the same onSkipNext the manual Next button calls, so it
    // inherits the correct Continue Watching scoping (queue is seeded per-screen, not
    // re-derived here), and naturally never fires against an active sleep timer (see
    // PhonePlayerScreen's LaunchedEffect doc for why the StateFlow conflation makes that
    // safe without an explicit check here).
    LaunchedEffect(playbackState) {
        if (playbackState == PlaybackState.ENDED && hasNextTrack) {
            onSkipNext()
        }
    }

    // Pushes "is this the last item in the queue" to the controller on every item change, so
    // an active EndOfFolder sleep timer can tell "auto-advance" from "stop here" -- see
    // PlaybackController.setQueueContext's doc and PhonePlayerScreen's identical effect.
    LaunchedEffect(hasNextTrack) {
        viewModel.setQueueContext(!hasNextTrack)
    }

    // Manual/automatic offset is owned by the caller (MainActivity) and persisted per
    // media+subtitle via SubtitleSyncRepository — see onSubtitleNudge/onAutoSync below.
    val effectiveSubtitle = remember(subtitleTrack, subtitleOffsetMs) {
        subtitleTrack?.copy(offsetMs = subtitleOffsetMs)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PlayerColors.current.background)
    ) {
        surface(Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .fillMaxSize()
                // Overscan-safe padding for 10-foot readability (Manifest §8).
                .padding(
                    horizontal = dimensionResource(R.dimen.tv_overscan_horizontal),
                    vertical = dimensionResource(R.dimen.tv_overscan_vertical)
                )
        ) {
            // Status text at top — visible confirmation of playback/buffering/error state,
            // since a TV viewer sitting across the room can't rely on subtle chrome cues.
            when (playbackState) {
                PlaybackState.BUFFERING -> Text("Buffering…", color = PlayerColors.current.textPrimary)
                PlaybackState.LOADING -> Text("Loading…", color = PlayerColors.current.textPrimary)
                PlaybackState.ERROR -> Text("Playback error", color = PlayerColors.current.textPrimary)
                else -> {}
            }

            val activeCue = remember(effectiveSubtitle, positionMs) { effectiveSubtitle?.cueAt(positionMs) }
            SubtitleOverlay(
                text = activeCue?.displayText,
                isRtl = activeCue?.baseRtl ?: false,
                style = subtitleStyle,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.CenterHorizontally)
            )

            Spacer(Modifier.weight(1f))

            // Progress is read-only; the visible Rewind and Forward controls below provide
            // fixed 10-second D-pad actions without presenting a touch-style draggable seekbar.
            val progress = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
                color = PlayerColors.current.accent,
                trackColor = PlayerColors.current.sheetBackground
            )

            Text(
                text = "${formatTvTime(positionMs)} / ${formatTvTime(durationMs)}",
                color = PlayerColors.current.textPrimary,
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(top = WatermelonSpacing.xs)
            )
            Text(
                text = buildTvRemoteHint(subtitleTrack != null),
                color = PlayerColors.current.textPrimary.copy(alpha = 0.78f),
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = WatermelonSpacing.xs, bottom = WatermelonSpacing.sm)
            )

            TvPlayerControls(
                isPlaying = isPlaying,
                hasPreviousTrack = hasPreviousTrack,
                hasNextTrack = hasNextTrack,
                hasSubtitles = subtitleTrack != null,
                onIntent = viewModel::onIntent,
                onSkipPrevious = onSkipPrevious,
                onSkipNext = onSkipNext,
                onSubtitleNudge = onSubtitleNudge,
                onSeek = { direction ->
                    val target = (positionMs + direction * SEEK_STEP_MS).coerceIn(0L, durationMs)
                    viewModel.onIntent(UserIntent.Seek(target))
                },
                showAutoSync = autoSyncEnabled && subtitleTrack != null,
                autoSyncStatusLabel = autoSyncStatusLabel(autoSyncStatus),
                onAutoSync = onAutoSync
            )
        }
    }
}

/** Fixed seek amount for the visible TV controls, matching the phone player's base 10-second
 * swipe-to-seek granularity so the two platforms feel consistent. */
private const val SEEK_STEP_MS = 10_000L

private fun buildTvRemoteHint(hasSubtitles: Boolean): String =
    if (hasSubtitles) {
        "SELECT a control  ·  Subtitle timing controls are below  ·  BACK library"
    } else {
        "SELECT a control  ·  BACK library"
    }

private fun formatTvTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

/** Human-readable label for the current Auto Sync attempt, or null when there's nothing to show. */
private fun autoSyncStatusLabel(status: com.watermelon.common.subtitle.sync.SyncStatus): String? =
    when (status) {
        com.watermelon.common.subtitle.sync.SyncStatus.IDLE -> null
        com.watermelon.common.subtitle.sync.SyncStatus.CHECKING_CACHE,
        com.watermelon.common.subtitle.sync.SyncStatus.ANALYZING -> "Auto Sync: analyzing…"
        com.watermelon.common.subtitle.sync.SyncStatus.SYNCHRONIZED -> "Auto Sync: synchronized"
        com.watermelon.common.subtitle.sync.SyncStatus.LOW_CONFIDENCE ->
            "Auto Sync: low confidence, no change applied"
        com.watermelon.common.subtitle.sync.SyncStatus.COMPLEX_DRIFT ->
            "Auto Sync: drift detected, use manual nudge"
        com.watermelon.common.subtitle.sync.SyncStatus.UNSUPPORTED -> "Auto Sync: unsupported for this file"
        com.watermelon.common.subtitle.sync.SyncStatus.RESOURCE_DENIED -> "Auto Sync: unavailable right now"
        com.watermelon.common.subtitle.sync.SyncStatus.FAILED -> "Auto Sync: failed"
    }
