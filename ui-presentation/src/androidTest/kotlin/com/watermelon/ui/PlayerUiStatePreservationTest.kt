package com.watermelon.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.watermelon.common.controller.PlaybackController
import com.watermelon.common.model.PlaybackState
import com.watermelon.common.model.RepeatMode
import com.watermelon.common.model.SleepTimerMode
import com.watermelon.ui.player.rememberVhsEffectController
import com.watermelon.ui.screens.PhonePlayerScreen
import com.watermelon.ui.viewmodel.PlayerViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Commit 01 regression tests: PlayerUiState must survive position-driven
 * recompositions. Each test opens UI chrome, advances playback position
 * (forcing recomposition through the position StateFlow), and asserts the
 * chrome is still there. Before the fix, every position tick recreated
 * PlayerUiState and dropped controls/lock/sheets.
 */
@RunWith(AndroidJUnit4::class)
class PlayerUiStatePreservationTest {

    @get:Rule
    val composeRule = createComposeRule()

    private class FakePlaybackController : PlaybackController {
        val position = MutableStateFlow(0L)
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
        override fun seekTo(positionMs: Long) {}
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
                durationMs = 60_000L,
                surface = {},
                onBack = {},
                uri = "content://test/video.mp4",
            )
        }
        composeRule.waitForIdle()
    }

    private fun openControls() {
        composeRule.onRoot().performTouchInput { click(center) }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Play").assertIsDisplayed()
    }

    @Test
    fun controls_survivePositionUpdate() {
        val controller = FakePlaybackController()
        launchPlayer(controller)

        openControls()

        controller.position.value = 30_000L
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Play").assertIsDisplayed()
    }

    @Test
    fun lock_survivesPositionUpdate() {
        val controller = FakePlaybackController()
        launchPlayer(controller)

        openControls()
        composeRule.onNodeWithContentDescription("Lock").performClick()
        composeRule.waitForIdle()
        composeRule
            .onNodeWithText("Slide both locks up together to unlock")
            .assertIsDisplayed()

        controller.position.value = 30_000L
        composeRule.waitForIdle()

        composeRule
            .onNodeWithText("Slide both locks up together to unlock")
            .assertIsDisplayed()
    }

    @Test
    fun quickToolsSheet_survivesPositionUpdate() {
        val controller = FakePlaybackController()
        launchPlayer(controller)

        openControls()
        composeRule.onNodeWithContentDescription("Player actions").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Quick tools").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Quick tools").assertIsDisplayed()

        controller.position.value = 30_000L
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Quick tools").assertIsDisplayed()
    }
}
