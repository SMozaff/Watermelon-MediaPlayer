package com.watermelon.ui.tv

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import com.watermelon.ui.R
import com.watermelon.common.model.SubtitleDirection
import com.watermelon.common.model.SubtitlePosition
import com.watermelon.common.model.SubtitleStyle
import com.watermelon.ui.screens.ScreenshotMode
import com.watermelon.ui.screens.SettingsState
import com.watermelon.ui.screens.VhsIntensity
import com.watermelon.ui.theme.WatermelonSpacing

/**
 * D-pad-native configuration surface for Android TV. It exposes the same persisted settings as
 * the phone screen, but every state change is a single, explicit SELECT action on a focusable
 * row. Choice values cycle predictably; text entry uses the platform IME in a dedicated dialog
 * and is therefore kept separate from the list's directional navigation.
 */
@Composable
fun TvSettingsScreen(
    state: SettingsState,
    onStateChange: (SettingsState) -> Unit,
    onFolderVisibilityClick: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var editingPath by remember { mutableStateOf<OutputPathField?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(WatermelonSpacing.sm)
    ) {
        item {
            TvScreenHeader(
                title = stringResource(R.string.settings_title),
                supportingText = stringResource(R.string.tv_settings_hint)
            )
        }

        item { TvSettingsSection(stringResource(R.string.settings_appearance_title)) }
        item {
            TvToggleSetting(
                label = stringResource(R.string.settings_pure_dark),
                enabled = state.pureDark,
                onClick = { onStateChange(state.copy(pureDark = !state.pureDark)) }
            )
        }
        item {
            TvToggleSetting(
                label = stringResource(R.string.settings_force_rtl),
                enabled = state.forcedRtl,
                onClick = { onStateChange(state.copy(forcedRtl = !state.forcedRtl)) }
            )
        }

        item { TvSettingsSection(stringResource(R.string.settings_library_title)) }
        item {
            TvToggleSetting(
                label = stringResource(R.string.settings_grid_default),
                enabled = state.gridDefault,
                supportingText = stringResource(R.string.tv_settings_grid_note),
                onClick = { onStateChange(state.copy(gridDefault = !state.gridDefault)) }
            )
        }
        item {
            TvToggleSetting(
                label = stringResource(R.string.settings_show_thumbnails),
                enabled = state.showThumbnails,
                supportingText = stringResource(R.string.tv_settings_previews_note),
                onClick = { onStateChange(state.copy(showThumbnails = !state.showThumbnails)) }
            )
        }
        item {
            TvToggleSetting(
                label = stringResource(R.string.settings_show_durations),
                enabled = state.showDurations,
                onClick = { onStateChange(state.copy(showDurations = !state.showDurations)) }
            )
        }
        item {
            TvToggleSetting(
                label = stringResource(R.string.settings_show_file_size),
                enabled = state.showFileSize,
                onClick = { onStateChange(state.copy(showFileSize = !state.showFileSize)) }
            )
        }
        item {
            TvActionSetting(
                label = stringResource(R.string.settings_folder_visibility),
                value = stringResource(R.string.tv_settings_manage),
                supportingText = stringResource(R.string.tv_folder_visibility_hint),
                onClick = onFolderVisibilityClick
            )
        }

        item { TvSettingsSection(stringResource(R.string.settings_continue_watching_title)) }
        item {
            TvToggleSetting(
                label = stringResource(R.string.settings_continue_watching_playlist),
                enabled = state.continueWatchingEnabled,
                supportingText = stringResource(R.string.tv_settings_resume_note),
                onClick = {
                    onStateChange(state.copy(continueWatchingEnabled = !state.continueWatchingEnabled))
                }
            )
        }

        item { TvSettingsSection(stringResource(R.string.settings_media_tools_title)) }
        // See MediaToolsSection.kt for why no premium toggle is surfaced here.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            item {
                TvActionSetting(
                    label = stringResource(R.string.settings_mp3_folder),
                    value = state.mp3OutputPath,
                    supportingText = stringResource(R.string.tv_settings_keyboard_hint),
                    onClick = { editingPath = OutputPathField.MP3 }
                )
            }
            item {
                TvActionSetting(
                    label = stringResource(R.string.settings_compressed_folder),
                    value = state.compressedOutputPath,
                    supportingText = stringResource(R.string.tv_settings_keyboard_hint),
                    onClick = { editingPath = OutputPathField.COMPRESSED }
                )
            }
            item {
                TvActionSetting(
                    label = stringResource(R.string.settings_trimmed_folder),
                    value = state.trimmedOutputPath,
                    supportingText = stringResource(R.string.tv_settings_keyboard_hint),
                    onClick = { editingPath = OutputPathField.TRIMMED }
                )
            }
        } else {
            item {
                TvStaticNote(stringResource(R.string.tv_settings_custom_folders_note))
            }
        }

        item { TvSettingsSection(stringResource(R.string.settings_player_title)) }
        item {
            TvChoiceSetting(
                label = stringResource(R.string.tv_settings_screenshot_mode),
                value = if (state.screenshotMode == ScreenshotMode.BURST) stringResource(R.string.tv_screenshot_burst) else stringResource(R.string.tv_screenshot_single),
                options = listOf(ScreenshotMode.SINGLE, ScreenshotMode.BURST),
                current = state.screenshotMode,
                labelFor = { if (it == ScreenshotMode.BURST) stringResource(R.string.tv_screenshot_burst) else stringResource(R.string.tv_screenshot_single) },
                onSelect = { onStateChange(state.copy(screenshotMode = it)) }
            )
        }
        item {
            TvToggleSetting(
                label = stringResource(R.string.settings_vhs_effect),
                enabled = state.vhsEnabled,
                supportingText = stringResource(R.string.tv_settings_vhs_note),
                onClick = { onStateChange(state.copy(vhsEnabled = !state.vhsEnabled)) }
            )
        }
        item {
            TvChoiceSetting(
                label = stringResource(R.string.settings_vhs_intensity),
                value = state.vhsIntensity.tvLabel(),
                options = VhsIntensity.values().toList(),
                current = state.vhsIntensity,
                labelFor = { it.tvLabel() },
                onSelect = { onStateChange(state.copy(vhsIntensity = it)) }
            )
        }
        item {
            TvToggleSetting(
                label = stringResource(R.string.settings_tuner_seekbar),
                enabled = state.tunerSeekBarEnabled,
                supportingText = stringResource(R.string.tv_settings_tuner_note),
                onClick = { onStateChange(state.copy(tunerSeekBarEnabled = !state.tunerSeekBarEnabled)) }
            )
        }
        if (state.tunerSeekBarEnabled) {
            item {
                TvChoiceSetting(
                    label = stringResource(R.string.settings_tuner_seek_step),
                    value = "${state.tunerSeekStepSeconds} seconds",
                    options = (1..20).toList(),
                    current = state.tunerSeekStepSeconds,
                    labelFor = { "$it seconds" },
                    onSelect = { onStateChange(state.copy(tunerSeekStepSeconds = it)) }
                )
            }
        }

        item { TvSettingsSection(stringResource(R.string.settings_subtitles_title)) }
        val subtitleStyle = state.subtitleStyle
        item {
            TvToggleSetting(
                label = stringResource(R.string.settings_subtitles_enable),
                enabled = subtitleStyle.enabled,
                onClick = {
                    onStateChange(state.copy(subtitleStyle = subtitleStyle.copy(enabled = !subtitleStyle.enabled)))
                }
            )
        }
        item {
            TvChoiceSetting(
                label = stringResource(R.string.settings_subtitle_text_size),
                value = "${subtitleStyle.sizeSp}sp",
                options = (12..48 step 2).toList(),
                current = subtitleStyle.sizeSp,
                labelFor = { "${it}sp" },
                onSelect = { updateSubtitleStyle(state, onStateChange, subtitleStyle.copy(sizeSp = it)) }
            )
        }
        item {
            TvChoiceSetting(
                label = stringResource(R.string.settings_subtitle_text_color),
                value = subtitleStyle.textColorName(),
                options = TV_SUBTITLE_COLORS,
                current = subtitleStyle.textColorArgb,
                labelFor = { color -> TV_SUBTITLE_COLORS.first { it == color }.let { colorName(color) } },
                onSelect = { updateSubtitleStyle(state, onStateChange, subtitleStyle.copy(textColorArgb = it)) }
            )
        }
        item {
            TvChoiceSetting(
                label = stringResource(R.string.settings_subtitle_position),
                value = subtitleStyle.position.tvLabel(),
                options = SubtitlePosition.values().toList(),
                current = subtitleStyle.position,
                labelFor = { it.tvLabel() },
                onSelect = { updateSubtitleStyle(state, onStateChange, subtitleStyle.copy(position = it)) }
            )
        }
        item {
            TvToggleSetting(
                label = stringResource(R.string.settings_subtitle_bold),
                enabled = subtitleStyle.bold,
                onClick = { updateSubtitleStyle(state, onStateChange, subtitleStyle.copy(bold = !subtitleStyle.bold)) }
            )
        }
        item {
            TvToggleSetting(
                label = stringResource(R.string.settings_subtitle_italic),
                enabled = subtitleStyle.italic,
                onClick = { updateSubtitleStyle(state, onStateChange, subtitleStyle.copy(italic = !subtitleStyle.italic)) }
            )
        }
        item {
            TvToggleSetting(
                label = stringResource(R.string.settings_subtitle_underline),
                enabled = subtitleStyle.underline,
                onClick = {
                    updateSubtitleStyle(state, onStateChange, subtitleStyle.copy(underline = !subtitleStyle.underline))
                }
            )
        }
        item {
            TvChoiceSetting(
                label = stringResource(R.string.settings_subtitle_direction),
                value = subtitleStyle.direction.tvLabel(),
                options = SubtitleDirection.values().toList(),
                current = subtitleStyle.direction,
                labelFor = { it.tvLabel() },
                onSelect = { updateSubtitleStyle(state, onStateChange, subtitleStyle.copy(direction = it)) }
            )
        }
        item {
            TvChoiceSetting(
                label = stringResource(R.string.settings_subtitle_second_direction),
                value = subtitleStyle.secondaryDirection.tvLabel(),
                options = SubtitleDirection.values().toList(),
                current = subtitleStyle.secondaryDirection,
                labelFor = { it.tvLabel() },
                onSelect = {
                    updateSubtitleStyle(state, onStateChange, subtitleStyle.copy(secondaryDirection = it))
                }
            )
        }

        item {
            TvActionSetting(
                label = stringResource(R.string.tv_back_to_library),
                value = stringResource(R.string.tv_back_also_returns),
                onClick = onBack,
                modifier = Modifier.padding(top = WatermelonSpacing.md, bottom = WatermelonSpacing.xl)
            )
        }
    }

    editingPath?.let { field ->
        val currentValue = when (field) {
            OutputPathField.MP3 -> state.mp3OutputPath
            OutputPathField.COMPRESSED -> state.compressedOutputPath
            OutputPathField.TRIMMED -> state.trimmedOutputPath
        }
        TvOutputPathDialog(
            title = stringResource(field.labelRes),
            initialValue = currentValue,
            onConfirm = { updated ->
                onStateChange(
                    when (field) {
                        OutputPathField.MP3 -> state.copy(mp3OutputPath = updated)
                        OutputPathField.COMPRESSED -> state.copy(compressedOutputPath = updated)
                        OutputPathField.TRIMMED -> state.copy(trimmedOutputPath = updated)
                    }
                )
                editingPath = null
            },
            onDismiss = { editingPath = null }
        )
    }
}

@Composable
private fun TvSettingsSection(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.secondary,
        modifier = Modifier.padding(
            start = WatermelonSpacing.xl + WatermelonSpacing.md,
            end = WatermelonSpacing.xl + WatermelonSpacing.md,
            top = WatermelonSpacing.lg,
            bottom = WatermelonSpacing.xs
        )
    )
}

@Composable
internal fun TvToggleSetting(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    supportingText: String? = null
) {
    TvActionSetting(
        label = label,
        value = if (enabled) stringResource(R.string.action_on) else stringResource(R.string.action_off),
        supportingText = supportingText,
        onClick = onClick
    )
}

@Composable
private fun TvActionSetting(
    label: String,
    value: String,
    onClick: () -> Unit,
    supportingText: String? = null,
    modifier: Modifier = Modifier
) {
    TvFocusableSurface(
        onClick = onClick,
        modifier = modifier.padding(horizontal = WatermelonSpacing.xl + WatermelonSpacing.md)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(WatermelonSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(WatermelonSpacing.md)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (supportingText != null) {
                    Text(
                        text = supportingText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = WatermelonSpacing.xs)
                    )
                }
            }
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

@Composable
private fun <T> TvChoiceSetting(
    label: String,
    value: String,
    options: List<T>,
    current: T,
    labelFor: @Composable (T) -> String,
    onSelect: (T) -> Unit
) {
    TvActionSetting(
        label = label,
        value = value,
        supportingText = stringResource(R.string.tv_settings_select_to_change),
        onClick = { onSelect(nextOption(options, current)) }
    )
}

@Composable
private fun TvStaticNote(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(
            horizontal = WatermelonSpacing.xl + WatermelonSpacing.md,
            vertical = WatermelonSpacing.xs
        )
    )
}

@Composable
private fun TvOutputPathDialog(
    title: String,
    initialValue: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var value by remember(initialValue) { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.tv_settings_folder_input_hint),
                    style = MaterialTheme.typography.bodyMedium
                )
                TextField(
                    value = value,
                    onValueChange = { value = it },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = WatermelonSpacing.md)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(value.trim()) },
                enabled = value.trim().isNotEmpty()
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

private fun <T> nextOption(options: List<T>, current: T): T {
    require(options.isNotEmpty())
    val currentIndex = options.indexOf(current)
    return options[(currentIndex + 1).floorMod(options.size)]
}

private fun Int.floorMod(divisor: Int): Int = ((this % divisor) + divisor) % divisor

private fun updateSubtitleStyle(
    state: SettingsState,
    onStateChange: (SettingsState) -> Unit,
    style: SubtitleStyle
) {
    onStateChange(state.copy(subtitleStyle = style))
}

@Composable
private fun VhsIntensity.tvLabel(): String = when (this) {
    VhsIntensity.OFF -> stringResource(R.string.vhs_intensity_off)
    VhsIntensity.LOW -> stringResource(R.string.vhs_intensity_low)
    VhsIntensity.MED -> stringResource(R.string.vhs_intensity_med)
    VhsIntensity.HIGH -> stringResource(R.string.vhs_intensity_high)
}

@Composable
private fun SubtitlePosition.tvLabel(): String = when (this) {
    SubtitlePosition.BOTTOM -> stringResource(R.string.subtitle_position_bottom)
    SubtitlePosition.TOP -> stringResource(R.string.subtitle_position_top)
}

@Composable
private fun SubtitleDirection.tvLabel(): String = when (this) {
    SubtitleDirection.AUTO -> stringResource(R.string.subtitle_direction_auto)
    SubtitleDirection.FORCE_RTL -> stringResource(R.string.subtitle_direction_force_rtl)
    SubtitleDirection.FORCE_LTR -> stringResource(R.string.subtitle_direction_force_ltr)
}

@Composable
private fun SubtitleStyle.textColorName(): String = colorName(textColorArgb)

@Composable
private fun colorName(color: Long): String = when (color) {
    0xFFFFFFFFL -> stringResource(R.string.subtitle_color_white)
    0xFFFFFF00L -> stringResource(R.string.subtitle_color_yellow)
    0xFF00FFFFL -> stringResource(R.string.subtitle_color_cyan)
    0xFF00FF00L -> stringResource(R.string.subtitle_color_green)
    else -> stringResource(R.string.subtitle_color_white)
}

private val TV_SUBTITLE_COLORS = listOf(0xFFFFFFFFL, 0xFFFFFF00L, 0xFF00FFFFL, 0xFF00FF00L)

private enum class OutputPathField(val labelRes: Int) {
    MP3(R.string.settings_mp3_folder),
    COMPRESSED(R.string.settings_compressed_folder),
    TRIMMED(R.string.settings_trimmed_folder)
}
