package com.watermelon.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
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
    val context = LocalContext.current
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
            options = SUBTITLE_COLORS.map { context.getString(it.second) }
        ) { selected ->
            val argb = SUBTITLE_COLORS.first { context.getString(it.second) == selected }.first
            up(st.copy(textColorArgb = argb))
        }

        DropdownNavRow(
            label = stringResource(R.string.settings_subtitle_position),
            value = stringResource(st.position.labelRes()),
            options = SubtitlePosition.values().map { context.getString(it.labelRes()) }
        ) { selected ->
            val next = SubtitlePosition.values().first { context.getString(it.labelRes()) == selected }
            up(st.copy(position = next))
        }

        ToggleRow(label = stringResource(R.string.settings_subtitle_bold), checked = st.bold) { up(st.copy(bold = it)) }
        ToggleRow(label = stringResource(R.string.settings_subtitle_italic), checked = st.italic) { up(st.copy(italic = it)) }
        ToggleRow(label = stringResource(R.string.settings_subtitle_underline), checked = st.underline) { up(st.copy(underline = it)) }

        DropdownNavRow(
            label = stringResource(R.string.settings_subtitle_direction),
            value = stringResource(st.direction.labelRes()),
            options = SubtitleDirection.values().map { context.getString(it.labelRes()) }
        ) { selected ->
            val next = SubtitleDirection.values().first { context.getString(it.labelRes()) == selected }
            up(st.copy(direction = next))
        }

        DropdownNavRow(
            label = stringResource(R.string.settings_subtitle_second_direction),
            value = stringResource(st.secondaryDirection.labelRes()),
            options = SubtitleDirection.values().map { context.getString(it.labelRes()) }
        ) { selected ->
            val next = SubtitleDirection.values().first { context.getString(it.labelRes()) == selected }
            up(st.copy(secondaryDirection = next))
        }
    }
}

private val SUBTITLE_COLORS = listOf(
    0xFFFFFFFFL to R.string.subtitle_color_white,
    0xFFFFEB3BL to R.string.subtitle_color_yellow,
    0xFF00E5FFL to R.string.subtitle_color_cyan,
    0xFF69F0AEL to R.string.subtitle_color_green,
    0xFFFF8A80L to R.string.subtitle_color_coral,
    0xFF000000L to R.string.subtitle_color_black
)

@Composable
private fun subtitleColorName(argb: Long): String =
    SUBTITLE_COLORS.firstOrNull { it.first == argb }
        ?.let { stringResource(it.second) }
        ?: stringResource(R.string.subtitle_color_custom)

private fun SubtitlePosition.labelRes(): Int = when (this) {
    SubtitlePosition.BOTTOM -> R.string.subtitle_position_bottom
    SubtitlePosition.TOP -> R.string.subtitle_position_top
}

private fun SubtitleDirection.labelRes(): Int = when (this) {
    SubtitleDirection.AUTO -> R.string.subtitle_direction_auto
    SubtitleDirection.FORCE_RTL -> R.string.subtitle_direction_force_rtl
    SubtitleDirection.FORCE_LTR -> R.string.subtitle_direction_force_ltr
}
