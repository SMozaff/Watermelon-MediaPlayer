package com.watermelon.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.watermelon.ui.components.WatermelonHeader
import com.watermelon.ui.theme.WatermelonSpacing

data class SettingsState(
    val pureDark: Boolean = true,
    val forcedRtl: Boolean = false,
    val gridDefault: Boolean = false,
    val showThumbnails: Boolean = true,
    val showDurations: Boolean = true,
    val showFileSize: Boolean = false,
    val vhsEnabled: Boolean = true,
    val vhsIntensity: VhsIntensity = VhsIntensity.MED,
    val tunerSeekBarEnabled: Boolean = true,
    /** Seconds moved per tick on the VHS tuner seek bar (1-20). Each tick crossing the
     *  center pointer moves playback by exactly this many seconds — a fixed, predictable
     *  step regardless of video length, rather than a free-form pixel-drag mapping (which
     *  made long videos practically unreachable: a fixed px-per-ms ratio meant reaching
     *  the end of a 2-hour video required dragging tens of thousands of pixels in one
     *  continuous gesture). */
    val tunerSeekStepSeconds: Int = 5,
    val memorySafety: Boolean = false,
    val fullFolderAccess: Boolean = false,
    val screenshotMode: ScreenshotMode = ScreenshotMode.SINGLE,
    val folderVisibility: Map<String, Boolean> = emptyMap(),
    val subtitleStyle: com.watermelon.common.model.SubtitleStyle = com.watermelon.common.model.SubtitleStyle(),
    /** When false, Continue Watching is hidden from the Playlists screen — but resume
     *  positions keep being recorded in the background regardless, so nothing is lost if
     *  the user re-enables it later. */
    val continueWatchingEnabled: Boolean = true,
    /** media-tools output paths (MediaStore RELATIVE_PATH strings). See OutputFileStore's
     *  doc for the API<29 limitation: custom subfolders are silently ignored on those OS
     *  versions, so the settings UI should surface that rather than pretend it works. */
    val mp3OutputPath: String = "Music/Watermelon",
    val compressedOutputPath: String = "Movies/Watermelon/compressed",
    val trimmedOutputPath: String = "Movies/Watermelon/trimmed",
    /** Phase 5 gating flag -- gating itself is currently disabled at every call site
     *  (product decision: full-featured for now), so this defaults to true to match
     *  actual behavior. Toggle still persists/works if you want to test the gated UI
     *  path later without re-wiring the checks. */
    val isPremiumUnlocked: Boolean = true,
    /** When false, the explicit "Auto Sync" subtitle-timing action is hidden on both the
     *  phone Quick Tools sheet and the TV player controls. Manual ±100 ms nudging and any
     *  previously-saved automatic or manual offset still apply either way. */
    val autoSyncEnabled: Boolean = true,
)

enum class VhsIntensity { OFF, LOW, MED, HIGH }
enum class ScreenshotMode { SINGLE, BURST }

@Composable
fun SettingsScreen(
    state: SettingsState,
    onStateChange: (SettingsState) -> Unit,
    onFolderVisibilityClick: () -> Unit = {},
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        WatermelonHeader(
            title = "Settings",
            showBackButton = true,
            onBackClick = onBack,
            modifier = Modifier.fillMaxWidth()
        )

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(
                horizontal = WatermelonSpacing.md,
                vertical = WatermelonSpacing.md
            ),
            verticalArrangement = Arrangement.spacedBy(WatermelonSpacing.md)
        ) {
            item { SettingsIntro() }
            item { AppearanceSection(state, onStateChange) }
            item { BrowsingSection(state, onStateChange) }
            item { ContinueWatchingSection(state, onStateChange) }
            item { AutoSyncSection(state, onStateChange) }
            item { MediaToolsSection(state, onStateChange) }
            item { PlayerSection(state, onStateChange) }
            item { SubtitlesSection(state, onStateChange) }
            item { LibraryAccessSection(onFolderVisibilityClick) }
            item { PrivacySection() }
        }
    }
}
