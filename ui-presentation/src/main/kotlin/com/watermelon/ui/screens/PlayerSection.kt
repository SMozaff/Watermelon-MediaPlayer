package com.watermelon.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.watermelon.ui.R

@Composable
internal fun PlayerSection(
    state: SettingsState,
    onStateChange: (SettingsState) -> Unit
) {
    val context = LocalContext.current
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
            value = stringResource(state.vhsIntensity.labelRes()),
            options = VhsIntensity.values().map { context.getString(it.labelRes()) }
        ) { selected ->
            val next = VhsIntensity.values().first { context.getString(it.labelRes()) == selected }
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

private fun VhsIntensity.labelRes(): Int = when (this) {
    VhsIntensity.OFF -> R.string.vhs_intensity_off
    VhsIntensity.LOW -> R.string.vhs_intensity_low
    VhsIntensity.MED -> R.string.vhs_intensity_med
    VhsIntensity.HIGH -> R.string.vhs_intensity_high
}
