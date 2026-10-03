package com.watermelon.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.watermelon.ui.R

@Composable
internal fun AppearanceSection(
    state: SettingsState,
    onStateChange: (SettingsState) -> Unit
) {
    SettingsGroup(
        title = stringResource(R.string.settings_appearance_title),
        summary = stringResource(R.string.settings_appearance_summary)
    ) {
        ToggleRow(
            label = stringResource(R.string.settings_pure_dark),
            checked = state.pureDark
        ) { onStateChange(state.copy(pureDark = it)) }

        ToggleRow(
            label = stringResource(R.string.settings_force_rtl),
            checked = state.forcedRtl
        ) { onStateChange(state.copy(forcedRtl = it)) }
    }
}

@Composable
internal fun BrowsingSection(
    state: SettingsState,
    onStateChange: (SettingsState) -> Unit
) {
    SettingsGroup(
        title = stringResource(R.string.settings_library_title),
        summary = stringResource(R.string.settings_library_summary)
    ) {
        ToggleRow(
            label = stringResource(R.string.settings_grid_default),
            checked = state.gridDefault
        ) { onStateChange(state.copy(gridDefault = it)) }

        ToggleRow(
            label = stringResource(R.string.settings_tetris_view),
            checked = state.tetrisViewEnabled
        ) { onStateChange(state.copy(tetrisViewEnabled = it)) }

        ToggleRow(
            label = stringResource(R.string.settings_show_thumbnails),
            checked = state.showThumbnails
        ) { onStateChange(state.copy(showThumbnails = it)) }

        ToggleRow(
            label = stringResource(R.string.settings_show_durations),
            checked = state.showDurations
        ) { onStateChange(state.copy(showDurations = it)) }

        ToggleRow(
            label = stringResource(R.string.settings_show_file_size),
            checked = state.showFileSize
        ) { onStateChange(state.copy(showFileSize = it)) }
    }
}

@Composable
internal fun ContinueWatchingSection(
    state: SettingsState,
    onStateChange: (SettingsState) -> Unit
) {
    SettingsGroup(
        title = stringResource(R.string.settings_continue_watching_title),
        summary = stringResource(R.string.settings_continue_watching_summary)
    ) {
        ToggleRow(
            label = stringResource(R.string.settings_continue_watching_playlist),
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
        title = stringResource(R.string.settings_auto_sync_title),
        summary = stringResource(R.string.settings_auto_sync_summary)
    ) {
        ToggleRow(
            label = stringResource(R.string.settings_auto_sync_action),
            checked = state.autoSyncEnabled
        ) { onStateChange(state.copy(autoSyncEnabled = it)) }
    }
}
