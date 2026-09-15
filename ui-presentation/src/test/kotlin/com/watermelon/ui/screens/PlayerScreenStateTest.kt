/**
 * Created in 2026 as part of test coverage improvement initiative.
 * UI presentation previously had zero unit tests for player state preservation.
 * 
 * These tests verify that PlayerScreenState maintains correct invariants
 * and that composition decisions based on state are reliable.
 */
package com.watermelon.ui.screens

import com.watermelon.common.model.ParsedSubtitle
import com.watermelon.common.model.SubtitleStyle
import com.watermelon.common.subtitle.sync.SyncStatus
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import kotlin.test.assertTrue

/**
 * Tests for [PlayerScreenState] invariants.
 * 
 * Previously [ui-presentation] had zero unit test files. This provides baseline
 * coverage for the player screen state management that coordinates playback,
 * subtitles, PiP, and file operations.
 */
class PlayerScreenStateTest {

    private lateinit var state: com.watermelon.ui.screens.PlayerScreenState

    @Before
    fun setUp() {
        state = com.watermelon.ui.screens.PlayerScreenState()
    }

    @Test
    fun `initial state has all defaults`() = assertTrue {
        state.playbackSpeed == 1f &&
            state.currentRatio == com.watermelon.ui.screens.VideoRatio.FILL &&
            state.scale == 1f &&
            state.panOffset == com.watermelon.ui.geometry.Offset.Zero &&
            state.showControlPanel == false &&
            state.showQuickTools == false &&
            state.showFileActions == false &&
            state.showMediaInfo == false &&
            state.showTunerSeekTip == false &&
            state.showSleepTimerDialog == false &&
            state.showOnlineSubtitlesSheet == false &&
            state.onlineSubtitlesUiState == com.watermelon.ui.screens.OnlineSubtitlesUiState.Idle &&
            state.isPiPEnabled == false &&
            state.isBackgroundEnabled == false
    }

    @Test
    fun `state can update playbackSpeed`() = runTest {
        state.playbackSpeed = 1.5f
        assertTrue(state.playbackSpeed == 1.5f)
    }

    @Test
    fun `state can update orientation`() = runTest {
        state.currentOrientation = com.watermelon.ui.screens.ScreenOrientation.LANDSCAPE
        assertTrue(state.currentOrientation == com.watermelon.ui.screens.ScreenOrientation.LANDSCAPE)
    }

    @Test
    fun `isPlayerSheetOpen property logic`() = runTest {
        // Initially: no sheets open
        assertTrue(state.isPlayerSheetOpen == false)

        // Turn on control panel
        state.showControlPanel = true
        assertTrue(state.isPlayerSheetOpen == true)

        // Turn off control panel, turn on file actions
        state.showControlPanel = false
        state.showFileActions = true
        assertTrue(state.isPlayerSheetOpen == true)
    }

    @Test
    fun `state can track subtitle information`() = runTest {
        // Given: a parsed subtitle
        val testSubtitle = ParsedSubtitle(
            language = "en",
            languageName = "English",
            filePath = "/test/sub.srt",
            fontSize = 20f,
            fontColor = 0xFFFFFFFF,
            backdrop = 0.8f
        )

        // When: subtitle is set
        state.subtitleTrack = testSubtitle
        state.subtitleStyle = com.watermelon.ui.screens.SubtitleStyle()

        // Then: subtitle information is tracked
        assertTrue(state.subtitleTrack == testSubtitle)
    }
}