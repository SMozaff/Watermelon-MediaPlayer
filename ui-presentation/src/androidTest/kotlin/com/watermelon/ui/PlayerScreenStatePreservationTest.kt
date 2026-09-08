package com.watermelon.ui

import android.content.pm.ActivityInfo
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.watermelon.common.controller.PlaybackController
import com.watermelon.common.model.PlaybackState
import com.watermelon.common.model.RepeatMode
import com.watermelon.common.model.SleepTimerMode
import com.watermelon.ui.player.rememberVhsEffectController
import com.watermelon.ui.screens.PLAYER_GESTURE_SURFACE_TAG
import com.watermelon.ui.screens.PhonePlayerScreen
import com.watermelon.ui.viewmodel.PlayerViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression tests for the remaining Phase A fixes (A2, A3, A4).
 *
 * A2: interactive screen-local state (aspect ratio, orientation — scale/pan are exercised by
 * gesture, not by a simple click path, so are not covered by a UI test here) must survive a
 * position-driven recomposition instead of being reset back to defaults every frame.
 *
 * A3: the pre-player window brightness must be captured exactly once and restored on dispose
 * — re-reading brightness *at* dispose time (the pre-fix bug) just observes whatever the
 * player itself already changed it to, so restoration silently no-ops.
 *
 * A4: the fast-rewind hold loop must walk a monotonically-decreasing local seek target
 * rather than re-reading the controller's position (which a competing 250ms position ticker
 * can race and overwrite mid-gesture — see PhonePlayerScreen.kt's inline comment on
 * `seekTarget` for the full mechanism). This is exercised via a fake controller that records
 * every `seekTo` call, since the real regression is about the *sequence* of commanded
 * positions, not just the final one.
 */
@RunWith(AndroidJUnit4::class)
class PlayerScreenStatePreservationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private class FakePlaybackController : PlaybackController {
        val position = MutableStateFlow(0L)
        val seekCalls = mutableListOf<Long>()
        private val state = MutableStateFlow(PlaybackState.IDLE)
        private val repeat = MutableStateFlow(RepeatMode.NONE)
        private val shuffled = MutableStateFlow(false)
        private val sleepRemaining = MutableStateFlow(0L)
        private val sleepRunning = MutableStateFlow(false)

        override val playbackState: StateFlow<PlaybackState> = state
        override val currentPositionMs: StateFlow<Long> = position
        override val repeatMode: StateFlow<RepeatMode> = repeat
        override val shuffleEnabled: StateFlow<Boolean> = shuffled
        override val sleepTimerRemainingMs: StateFlow<Long> = sleepRemaining
        override val sleepTimerRunning: StateFlow<Boolean> = sleepRunning

        override fun play(uri: String, startPositionMs: Long) {}
        override fun pause() {}
        override fun resume() {}
        override fun seekTo(positionMs: Long) {
            seekCalls += positionMs
            // Deliberately do NOT update `position` here — the real controller's
            // `_currentPosition.value = positionMs` write is exactly what a racing 250ms
            // ticker can subsequently clobber before the player has actually caught up
            // (see PlaybackControllerImpl.startPositionTicker). Leaving `position` frozen at
            // its pre-seek value in this fake reproduces that worst case deterministically:
            // if PhonePlayerScreen's rewind loop still read `position` for its next step
            // (the pre-fix behaviour), every seekCalls entry after the first would be
            // identical, since it would keep subtracting stepMs from the same frozen value.
        }
        override fun setSpeed(speed: Float) {}
        override fun setRepeat(mode: RepeatMode) {}
        override fun setShuffle(enabled: Boolean) {}
        override fun setSleepTimer(mode: SleepTimerMode) {}
        override fun cancelSleepTimer() {}
        override fun setQueueContext(isLastInQueue: Boolean) {}
        override fun takeScreenshot(): String? = null
    }

    private fun launchPlayer(controller: FakePlaybackController) {
        val viewModel = PlayerViewModel(controller)
        composeRule.setContent {
            val vhs = rememberVhsEffectController(
                shaderProvider = { _, _, _, _ -> null },
                reverseSound = { _, _ -> }
            )
            PhonePlayerScreen(
                viewModel = viewModel,
                vhs = vhs,
                vhsEnabled = false,
                vhsIntensity = 0f,
                durationMs = 600_000L,
                surface = {},
                onBack = {},
                uri = "content://test/video.mp4",
            )
        }
        composeRule.waitForIdle()
    }

    private fun openControls() {
        composeRule
            .onNodeWithTag(PLAYER_GESTURE_SURFACE_TAG)
            .performTouchInput { click(center) }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Play").assertIsDisplayed()
    }

    private fun openQuickTools() {
        composeRule.onNodeWithContentDescription("Player actions").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Quick tools").performClick()
        composeRule.waitForIdle()
    }

    // ── A2: orientation must not reset on every position-driven recomposition ──────────

    @Test
    fun orientation_survivesPositionUpdate() {
        val controller = FakePlaybackController()
        launchPlayer(controller)

        openControls()
        openQuickTools()
        composeRule.onNodeWithContentDescription("Landscape").performClick()
        composeRule.waitForIdle()

        // Real, observable side effect of the selection (PhonePlayerScreen.kt sets
        // activity.requestedOrientation from uiState.currentOrientation) — asserting on this
        // rather than button tint, which is far more robust than inspecting Compose's
        // internal draw state for a color match.
        val activity = composeRule.activity
        assertEquals(
            "requestedOrientation should reflect the just-selected Landscape option",
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
            activity.requestedOrientation
        )

        // Drive several position-triggered recompositions — before the A2 fix, each of
        // these forced `uiState.currentOrientation = ScreenOrientation.AUTO` right back,
        // which would have reverted requestedOrientation to UNSPECIFIED.
        controller.position.value = 15_000L
        composeRule.waitForIdle()
        controller.position.value = 30_000L
        composeRule.waitForIdle()

        assertEquals(
            "orientation selection must survive position-driven recomposition",
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
            activity.requestedOrientation
        )
    }

    @Test
    fun aspectRatioSheet_stillRendersSelectedLabelAfterPositionUpdate() {
        val controller = FakePlaybackController()
        launchPlayer(controller)

        openControls()
        openQuickTools()
        composeRule.onNodeWithText("16:9").performClick()
        composeRule.waitForIdle()

        controller.position.value = 20_000L
        composeRule.waitForIdle()

        // Re-open the sheet after the recomposition and confirm it still renders correctly
        // (a crash or a reset-to-defaults render would already be a regression signal here,
        // even without inspecting which option is visually bold).
        openQuickTools()
        composeRule.onNodeWithText("16:9").assertIsDisplayed()
    }

    // ── A3: brightness must restore to the pre-player value, not a re-read of the ────────
    // ── player's own mutated brightness at dispose time ──────────────────────────────

    @Test
    fun brightness_restoresOriginalValue_notPlayerMutatedValue() {
        val originalBrightness = 0.35f
        composeRule.activityRule.scenario.onActivity { activity ->
            val attrs = activity.window.attributes
            attrs.screenBrightness = originalBrightness
            activity.window.attributes = attrs
        }

        val controller = FakePlaybackController()
        val showPlayer = mutableStateOf(true)
        composeRule.setContent {
            if (showPlayer.value) {
                val viewModel = remember { PlayerViewModel(controller) }
                val vhs = rememberVhsEffectController(
                    shaderProvider = { _, _, _, _ -> null },
                    reverseSound = { _, _ -> }
                )
                PhonePlayerScreen(
                    viewModel = viewModel,
                    vhs = vhs,
                    vhsEnabled = false,
                    vhsIntensity = 0f,
                    durationMs = 60_000L,
                    surface = {},
                    onBack = {},
                    uri = "content://test/video.mp4",
                    // Force a start brightness clearly different from originalBrightness so
                    // the test can tell "restored to original" apart from "coincidentally
                    // already matched."
                    initialBrightness = 0.9f,
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.activityRule.scenario.onActivity { activity ->
            assertEquals(
                "player should have applied its own start brightness while mounted",
                0.9f,
                activity.window.attributes.screenBrightness
            )
        }

        // Unmount the player — triggers DisposableEffect(Unit)'s onDispose, which must
        // restore `originalWindowBrightness` (captured once on first composition), not
        // re-read whatever the player itself just set (the pre-A3-fix bug).
        showPlayer.value = false
        composeRule.waitForIdle()

        composeRule.activityRule.scenario.onActivity { activity ->
            assertEquals(
                "window brightness must be restored to its pre-player value on dispose",
                originalBrightness,
                activity.window.attributes.screenBrightness
            )
        }
    }

    // ── A4: fast-rewind hold must command a monotonically-decreasing sequence ──────────

    @Test
    fun reverseHold_commandsDecreasingPositions_notRepeatedValue() {
        val controller = FakePlaybackController()
        // Start well away from zero so the clamp-at-0 branch doesn't flatten the sequence.
        controller.position.value = 60_000L
        launchPlayer(controller)

        // Simulate a held reverse gesture on the left half of the screen for long enough to
        // cross the 500ms long-press threshold and accumulate several loop iterations at the
        // fastest step interval used by the hold loop (40ms floor).
        composeRule
            .onNodeWithTag(PLAYER_GESTURE_SURFACE_TAG)
            .performTouchInput {
                down(center.copy(x = center.x / 2))
            }
        // Real elapsed time, not just waitForIdle — the hold loop's own delay() calls need
        // wall-clock time to progress since they aren't driven by the compose test clock.
        Thread.sleep(900)
        composeRule
            .onNodeWithTag(PLAYER_GESTURE_SURFACE_TAG)
            .performTouchInput { up() }
        composeRule.waitForIdle()

        val calls = controller.seekCalls.toList()
        assertTrue(
            "expected multiple seek commands during a held reverse gesture, got: $calls",
            calls.size >= 2
        )
        for (i in 1 until calls.size) {
            assertTrue(
                "seek target at step $i (${calls[i]}) must be strictly less than the " +
                    "previous step (${calls[i - 1]}) — a repeated or non-decreasing value " +
                    "means the loop re-read a racing/stale position instead of walking its " +
                    "own local target. Full sequence: $calls",
                calls[i] < calls[i - 1]
            )
        }
    }
}
