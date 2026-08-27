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
                title = "Settings",
                supportingText = "Use the D-pad to choose a setting. Press SELECT to change it."
            )
        }

        item { TvSettingsSection("Appearance") }
        item {
            TvToggleSetting(
                label = "Pure dark theme",
                enabled = state.pureDark,
                onClick = { onStateChange(state.copy(pureDark = !state.pureDark)) }
            )
        }
        item {
            TvToggleSetting(
                label = "Force RTL overrides",
                enabled = state.forcedRtl,
                onClick = { onStateChange(state.copy(forcedRtl = !state.forcedRtl)) }
            )
        }

        item { TvSettingsSection("Library and browsing") }
        item {
            TvToggleSetting(
                label = "Grid layout by default",
                enabled = state.gridDefault,
                supportingText = "Used on touch devices; TV always uses a D-pad list.",
                onClick = { onStateChange(state.copy(gridDefault = !state.gridDefault)) }
            )
        }
        item {
            TvToggleSetting(
                label = "Show video previews",
                enabled = state.showThumbnails,
                supportingText = "Controls previews in the TV catalogue.",
                onClick = { onStateChange(state.copy(showThumbnails = !state.showThumbnails)) }
            )
        }
        item {
            TvToggleSetting(
                label = "Show durations",
                enabled = state.showDurations,
                onClick = { onStateChange(state.copy(showDurations = !state.showDurations)) }
            )
        }
        item {
            TvToggleSetting(
                label = "Show file size",
                enabled = state.showFileSize,
                onClick = { onStateChange(state.copy(showFileSize = !state.showFileSize)) }
            )
        }
        item {
            TvActionSetting(
                label = "Folder visibility",
                value = "Manage",
                supportingText = "Choose which indexed folders appear in the library.",
                onClick = onFolderVisibilityClick
            )
        }

        item { TvSettingsSection("Continue watching") }
        item {
            TvToggleSetting(
                label = "Continue Watching playlist",
                enabled = state.continueWatchingEnabled,
                supportingText = "Resume positions remain stored when this is hidden.",
                onClick = {
                    onStateChange(state.copy(continueWatchingEnabled = !state.continueWatchingEnabled))
                }
            )
        }

        item { TvSettingsSection("Media tools") }
        item {
            TvToggleSetting(
                label = "Premium unlocked",
                enabled = state.isPremiumUnlocked,
                supportingText = "Placeholder setting; no purchase flow is implemented.",
                onClick = { onStateChange(state.copy(isPremiumUnlocked = !state.isPremiumUnlocked)) }
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            item {
                TvActionSetting(
                    label = "MP3 audio folder",
                    value = state.mp3OutputPath,
                    supportingText = "Press SELECT to edit using the on-screen keyboard.",
                    onClick = { editingPath = OutputPathField.MP3 }
                )
            }
            item {
                TvActionSetting(
                    label = "Compressed video folder",
                    value = state.compressedOutputPath,
                    supportingText = "Press SELECT to edit using the on-screen keyboard.",
                    onClick = { editingPath = OutputPathField.COMPRESSED }
                )
            }
            item {
                TvActionSetting(
                    label = "Trimmed video folder",
                    value = state.trimmedOutputPath,
                    supportingText = "Press SELECT to edit using the on-screen keyboard.",
                    onClick = { editingPath = OutputPathField.TRIMMED }
                )
            }
        } else {
            item {
                TvStaticNote("Custom output folders require Android 10 or later on this device.")
            }
        }

        item { TvSettingsSection("Player") }
        item {
            TvChoiceSetting(
                label = "Screenshot mode",
                value = if (state.screenshotMode == ScreenshotMode.BURST) "Burst (9 frames)" else "Single",
                options = listOf(ScreenshotMode.SINGLE, ScreenshotMode.BURST),
                current = state.screenshotMode,
                labelFor = { if (it == ScreenshotMode.BURST) "Burst (9 frames)" else "Single" },
                onSelect = { onStateChange(state.copy(screenshotMode = it)) }
            )
        }
        item {
            TvToggleSetting(
                label = "VHS effect",
                enabled = state.vhsEnabled,
                supportingText = "Applies to the touch player; TV playback stays clean and direct.",
                onClick = { onStateChange(state.copy(vhsEnabled = !state.vhsEnabled)) }
            )
        }
        item {
            TvChoiceSetting(
                label = "VHS intensity",
                value = state.vhsIntensity.tvLabel(),
                options = VhsIntensity.values().toList(),
                current = state.vhsIntensity,
                labelFor = { it.tvLabel() },
                onSelect = { onStateChange(state.copy(vhsIntensity = it)) }
            )
        }
        item {
            TvToggleSetting(
                label = "Tuner-style seek bar",
                enabled = state.tunerSeekBarEnabled,
                supportingText = "Applies to the touch player; TV uses dedicated seek controls.",
                onClick = { onStateChange(state.copy(tunerSeekBarEnabled = !state.tunerSeekBarEnabled)) }
            )
        }
        if (state.tunerSeekBarEnabled) {
            item {
                TvChoiceSetting(
                    label = "Tuner seek step",
                    value = "${state.tunerSeekStepSeconds} seconds",
                    options = (1..20).toList(),
                    current = state.tunerSeekStepSeconds,
                    labelFor = { "$it seconds" },
                    onSelect = { onStateChange(state.copy(tunerSeekStepSeconds = it)) }
                )
            }
        }

        item { TvSettingsSection("Subtitles") }
        val subtitleStyle = state.subtitleStyle
        item {
            TvToggleSetting(
                label = "Enable subtitles",
                enabled = subtitleStyle.enabled,
                onClick = {
                    onStateChange(state.copy(subtitleStyle = subtitleStyle.copy(enabled = !subtitleStyle.enabled)))
                }
            )
        }
        item {
            TvChoiceSetting(
                label = "Text size",
                value = "${subtitleStyle.sizeSp}sp",
                options = (12..48 step 2).toList(),
                current = subtitleStyle.sizeSp,
                labelFor = { "${it}sp" },
                onSelect = { updateSubtitleStyle(state, onStateChange, subtitleStyle.copy(sizeSp = it)) }
            )
        }
        item {
            TvChoiceSetting(
                label = "Text color",
                value = subtitleStyle.textColorName(),
                options = TV_SUBTITLE_COLORS,
                current = subtitleStyle.textColorArgb,
                labelFor = { color -> TV_SUBTITLE_COLORS.first { it == color }.let { colorName(color) } },
                onSelect = { updateSubtitleStyle(state, onStateChange, subtitleStyle.copy(textColorArgb = it)) }
            )
        }
        item {
            TvChoiceSetting(
                label = "Position",
                value = subtitleStyle.position.tvLabel(),
                options = SubtitlePosition.values().toList(),
                current = subtitleStyle.position,
                labelFor = { it.tvLabel() },
                onSelect = { updateSubtitleStyle(state, onStateChange, subtitleStyle.copy(position = it)) }
            )
        }
        item {
            TvToggleSetting(
                label = "Bold subtitles",
                enabled = subtitleStyle.bold,
                onClick = { updateSubtitleStyle(state, onStateChange, subtitleStyle.copy(bold = !subtitleStyle.bold)) }
            )
        }
        item {
            TvToggleSetting(
                label = "Italic subtitles",
                enabled = subtitleStyle.italic,
                onClick = { updateSubtitleStyle(state, onStateChange, subtitleStyle.copy(italic = !subtitleStyle.italic)) }
            )
        }
        item {
            TvToggleSetting(
                label = "Underline subtitles",
                enabled = subtitleStyle.underline,
                onClick = {
                    updateSubtitleStyle(state, onStateChange, subtitleStyle.copy(underline = !subtitleStyle.underline))
                }
            )
        }
        item {
            TvChoiceSetting(
                label = "Primary direction",
                value = subtitleStyle.direction.tvLabel(),
                options = SubtitleDirection.values().toList(),
                current = subtitleStyle.direction,
                labelFor = { it.tvLabel() },
                onSelect = { updateSubtitleStyle(state, onStateChange, subtitleStyle.copy(direction = it)) }
            )
        }
        item {
            TvChoiceSetting(
                label = "Second subtitle direction",
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
                label = "Back to library",
                value = "BACK also returns",
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
            title = field.label,
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
        value = if (enabled) "On" else "Off",
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
    labelFor: (T) -> String,
    onSelect: (T) -> Unit
) {
    TvActionSetting(
        label = label,
        value = value,
        supportingText = "Press SELECT to change.",
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
                    text = "Enter a MediaStore-relative folder. The TV keyboard opens when the field is selected.",
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
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
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

private fun VhsIntensity.tvLabel(): String = name.lowercase().replaceFirstChar { it.uppercase() }

private fun SubtitlePosition.tvLabel(): String = name.lowercase().replaceFirstChar { it.uppercase() }

private fun SubtitleDirection.tvLabel(): String = name.lowercase().replaceFirstChar { it.uppercase() }

private fun SubtitleStyle.textColorName(): String = colorName(textColorArgb)

private fun colorName(color: Long): String = when (color) {
    0xFFFFFFFFL -> "White"
    0xFFFFFF00L -> "Yellow"
    0xFF00FFFFL -> "Cyan"
    0xFF00FF00L -> "Green"
    else -> "White"
}

private val TV_SUBTITLE_COLORS = listOf(0xFFFFFFFFL, 0xFFFFFF00L, 0xFF00FFFFL, 0xFF00FF00L)

private enum class OutputPathField(val label: String) {
    MP3("MP3 audio folder"),
    COMPRESSED("Compressed video folder"),
    TRIMMED("Trimmed video folder")
}
