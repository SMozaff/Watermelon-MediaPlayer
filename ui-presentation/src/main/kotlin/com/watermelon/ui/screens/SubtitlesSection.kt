package com.watermelon.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.watermelon.common.model.SubtitleDirection
import com.watermelon.common.model.SubtitlePosition
import com.watermelon.common.model.SubtitleStyle
import com.watermelon.ui.R

@Composable
internal fun SubtitlesSection(
    state: SettingsState,
    onStateChange: (SettingsState) -> Unit
) {
    SettingsGroup(
        title = stringResource(R.string.settings_subtitles_title),
        summary = stringResource(R.string.settings_subtitles_summary)
    ) {
        val st = state.subtitleStyle
        fun up(new: SubtitleStyle) {
            onStateChange(state.copy(subtitleStyle = new))
        }

        ToggleRow(
            label = stringResource(R.string.settings_subtitles_enable),
            checked = st.enabled
        ) { up(st.copy(enabled = it)) }

        StepperRow(
            label = stringResource(R.string.settings_subtitle_text_size),
            value = "${st.sizeSp}sp",
            onMinus = { up(st.copy(sizeSp = (st.sizeSp - 2).coerceAtLeast(12))) },
            onPlus = { up(st.copy(sizeSp = (st.sizeSp + 2).coerceAtMost(48))) }
        )

        DropdownNavRow(
            label = stringResource(R.string.settings_subtitle_text_color),
            value = subtitleColorName(st.textColorArgb),
            options = SUBTITLE_COLORS.map { it.second }
        ) { selected ->
            val argb = SUBTITLE_COLORS.first { it.second == selected }.first
            up(st.copy(textColorArgb = argb))
        }

        DropdownNavRow(
            label = stringResource(R.string.settings_subtitle_position),
            value = st.position.name.lowercase().replaceFirstChar { it.uppercase() },
            options = SubtitlePosition.values()
                .map { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } }
        ) { selected ->
            val next = SubtitlePosition.values().first {
                it.name.lowercase().replaceFirstChar { c -> c.uppercase() } == selected
            }
            up(st.copy(position = next))
        }

        ToggleRow(label = stringResource(R.string.settings_subtitle_bold), checked = st.bold) { up(st.copy(bold = it)) }
        ToggleRow(label = stringResource(R.string.settings_subtitle_italic), checked = st.italic) { up(st.copy(italic = it)) }
        ToggleRow(label = stringResource(R.string.settings_subtitle_underline), checked = st.underline) { up(st.copy(underline = it)) }

        DropdownNavRow(
            label = stringResource(R.string.settings_subtitle_direction),
            value = st.direction.label(),
            options = SubtitleDirection.values().map { it.label() }
        ) { selected ->
            val next = SubtitleDirection.values().first { it.label() == selected }
            up(st.copy(direction = next))
        }

        DropdownNavRow(
            label = stringResource(R.string.settings_subtitle_second_direction),
            value = st.secondaryDirection.label(),
            options = SubtitleDirection.values().map { it.label() }
        ) { selected ->
            val next = SubtitleDirection.values().first { it.label() == selected }
            up(st.copy(secondaryDirection = next))
        }
    }
}

private val SUBTITLE_COLORS = listOf(
    0xFFFFFFFFL to "White",
    0xFFFFEB3BL to "Yellow",
    0xFF00E5FFL to "Cyan",
    0xFF69F0AEL to "Green",
    0xFFFF8A80L to "Coral",
    0xFF000000L to "Black"
)

private fun subtitleColorName(argb: Long): String =
    SUBTITLE_COLORS.firstOrNull { it.first == argb }?.second ?: "Custom"

private fun SubtitleDirection.label(): String = when (this) {
    SubtitleDirection.AUTO -> "Auto"
    SubtitleDirection.FORCE_RTL -> "Force RTL"
    SubtitleDirection.FORCE_LTR -> "Force LTR"
}
