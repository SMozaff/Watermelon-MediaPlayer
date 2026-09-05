package com.watermelon.ui.screens

import androidx.compose.runtime.Composable

@Composable
internal fun PlayerSection(
    state: SettingsState,
    onStateChange: (SettingsState) -> Unit
) {
    SettingsGroup(title = "Player", summary = "Playback controls and retro effects") {
        ToggleRow(
            label = "Burst screenshot (9 frames)",
            checked = state.screenshotMode == ScreenshotMode.BURST
        ) {
            onStateChange(
                state.copy(screenshotMode = if (it) ScreenshotMode.BURST else ScreenshotMode.SINGLE)
            )
        }

        ToggleRow(
            label = "VHS effect",
            checked = state.vhsEnabled
        ) { onStateChange(state.copy(vhsEnabled = it)) }

        DropdownNavRow(
            label = "VHS intensity",
            value = state.vhsIntensity.name.lowercase().replaceFirstChar { it.uppercase() },
            options = VhsIntensity.values().map { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } }
        ) { selected ->
            val next = VhsIntensity.values().first {
                it.name.lowercase().replaceFirstChar { c -> c.uppercase() } == selected
            }
            onStateChange(state.copy(vhsIntensity = next))
        }

        ToggleRow(
            label = "Tuner-style seek bar",
            checked = state.tunerSeekBarEnabled
        ) { onStateChange(state.copy(tunerSeekBarEnabled = it)) }

        if (state.tunerSeekBarEnabled) {
            StepperRow(
                label = "Tuner seek step",
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
