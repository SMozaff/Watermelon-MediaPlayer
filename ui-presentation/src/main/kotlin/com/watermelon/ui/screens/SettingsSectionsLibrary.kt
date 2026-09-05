package com.watermelon.ui.screens

import androidx.compose.runtime.Composable

@Composable
internal fun AppearanceSection(
    state: SettingsState,
    onStateChange: (SettingsState) -> Unit
) {
    SettingsGroup(title = "Appearance", summary = "Theme and reading direction") {
        ToggleRow(
            label = "Pure dark theme",
            checked = state.pureDark
        ) { onStateChange(state.copy(pureDark = it)) }

        ToggleRow(
            label = "Force RTL overrides",
            checked = state.forcedRtl
        ) { onStateChange(state.copy(forcedRtl = it)) }
    }
}

@Composable
internal fun BrowsingSection(
    state: SettingsState,
    onStateChange: (SettingsState) -> Unit
) {
    SettingsGroup(title = "Library & browsing", summary = "How your media library is displayed") {
        ToggleRow(
            label = "Grid layout by default",
            checked = state.gridDefault
        ) { onStateChange(state.copy(gridDefault = it)) }

        ToggleRow(
            label = "Show thumbnails",
            checked = state.showThumbnails
        ) { onStateChange(state.copy(showThumbnails = it)) }

        ToggleRow(
            label = "Show durations",
            checked = state.showDurations
        ) { onStateChange(state.copy(showDurations = it)) }

        ToggleRow(
            label = "Show file size",
            checked = state.showFileSize
        ) { onStateChange(state.copy(showFileSize = it)) }
    }
}

@Composable
internal fun ContinueWatchingSection(
    state: SettingsState,
    onStateChange: (SettingsState) -> Unit
) {
    SettingsGroup(title = "Continue watching", summary = "Resume and playlist behaviour") {
        ToggleRow(
            label = "Continue Watching playlist",
            checked = state.continueWatchingEnabled
        ) { onStateChange(state.copy(continueWatchingEnabled = it)) }
    }
}

@Composable
internal fun AutoSyncSection(
    state: SettingsState,
    onStateChange: (SettingsState) -> Unit
) {
    SettingsGroup(
        title = "Subtitle Auto Sync",
        summary = "Automatic subtitle timing correction"
    ) {
        ToggleRow(
            label = "Auto Sync action",
            checked = state.autoSyncEnabled
        ) { onStateChange(state.copy(autoSyncEnabled = it)) }
    }
}
