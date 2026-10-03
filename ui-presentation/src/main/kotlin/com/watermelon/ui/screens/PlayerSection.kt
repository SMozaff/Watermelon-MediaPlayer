package com.watermelon.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.watermelon.ui.R

@Composable
internal fun PlayerSection(
    state: SettingsState,
    onStateChange: (SettingsState) -> Unit
) {
    SettingsGroup(
        title = stringResource(R.string.settings_player_title),
        summary = stringResource(R.string.settings_player_summary)
    ) {
        ToggleRow(
            label = stringResource(R.string.settings_burst_screenshot),
            checked = state.screenshotMode == ScreenshotMode.BURST
        ) {
            onStateChange(
                state.copy(screenshotMode = if (it) ScreenshotMode.BURST else ScreenshotMode.SINGLE)
            )
        }

        ToggleRow(
            label = stringResource(R.string.settings_vhs_effect),
            checked = state.vhsEnabled
        ) { onStateChange(state.copy(vhsEnabled = it)) }

        DropdownNavRow(
            label = stringResource(R.string.settings_vhs_intensity),
            value = state.vhsIntensity.name.lowercase().replaceFirstChar { it.uppercase() },
            options = VhsIntensity.values().map { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } }
        ) { selected ->
            val next = VhsIntensity.values().first {
                it.name.lowercase().replaceFirstChar { c -> c.uppercase() } == selected
            }
            onStateChange(state.copy(vhsIntensity = next))
        }

        ToggleRow(
            label = stringResource(R.string.settings_tuner_seekbar),
            checked = state.tunerSeekBarEnabled
        ) { onStateChange(state.copy(tunerSeekBarEnabled = it)) }

        if (state.tunerSeekBarEnabled) {
            StepperRow(
                label = stringResource(R.string.settings_tuner_seek_step),
                value = "${state.tunerSeekStepSeconds}s",
                onMinus = {
                    onStateChange(
                        state.copy(tunerSeekStepSeconds = (state.tunerSeekStepSeconds - 1).coerceAtLeast(1))
                    )
                },
                onPlus = {
                    onStateChange(
                        state.copy(tunerSeekStepSeconds = (state.tunerSeekStepSeconds + 1).coerceAtMost(20))
                    )
                }
            )
        }
    }
}
