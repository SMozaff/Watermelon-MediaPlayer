package com.watermelon.ui.screens

import androidx.compose.runtime.Composable
import com.watermelon.common.model.SubtitleDirection
import com.watermelon.common.model.SubtitlePosition
import com.watermelon.common.model.SubtitleStyle

@Composable
internal fun SubtitlesSection(
    state: SettingsState,
    onStateChange: (SettingsState) -> Unit
) {
    SettingsGroup(title = "Subtitles", summary = "Text style and reading direction") {
        val st = state.subtitleStyle
        fun up(new: SubtitleStyle) {
            onStateChange(state.copy(subtitleStyle = new))
        }

        ToggleRow(
            label = "Enable subtitles",
            checked = st.enabled
        ) { up(st.copy(enabled = it)) }

        StepperRow(
            label = "Text size",
            value = "${st.sizeSp}sp",
            onMinus = { up(st.copy(sizeSp = (st.sizeSp - 2).coerceAtLeast(12))) },
            onPlus = { up(st.copy(sizeSp = (st.sizeSp + 2).coerceAtMost(48))) }
        )

        DropdownNavRow(
            label = "Text color",
            value = subtitleColorName(st.textColorArgb),
            options = SUBTITLE_COLORS.map { it.second }
        ) { selected ->
            val argb = SUBTITLE_COLORS.first { it.second == selected }.first
            up(st.copy(textColorArgb = argb))
        }

        DropdownNavRow(
            label = "Position",
            value = st.position.name.lowercase().replaceFirstChar { it.uppercase() },
            options = SubtitlePosition.values()
                .map { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } }
        ) { selected ->
            val next = SubtitlePosition.values().first {
                it.name.lowercase().replaceFirstChar { c -> c.uppercase() } == selected
            }
            up(st.copy(position = next))
        }

        ToggleRow(label = "Bold", checked = st.bold) { up(st.copy(bold = it)) }
        ToggleRow(label = "Italic", checked = st.italic) { up(st.copy(italic = it)) }
        ToggleRow(label = "Underline", checked = st.underline) { up(st.copy(underline = it)) }

        DropdownNavRow(
            label = "Direction",
            value = st.direction.label(),
            options = SubtitleDirection.values().map { it.label() }
        ) { selected ->
            val next = SubtitleDirection.values().first { it.label() == selected }
            up(st.copy(direction = next))
        }

        DropdownNavRow(
            label = "2nd sub direction",
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
