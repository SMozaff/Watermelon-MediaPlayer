package com.watermelon.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.watermelon.ui.theme.WatermelonColors
import com.watermelon.ui.theme.WatermelonSpacing
import com.watermelon.ui.theme.WatermelonTypography

@Composable
internal fun MediaToolsSection(
    state: SettingsState,
    onStateChange: (SettingsState) -> Unit
) {
    SettingsGroup(title = "Media tools", summary = "Export destinations and premium tools") {
        ToggleRow(
            label = "Premium unlocked (placeholder -- no purchase flow yet)",
            checked = state.isPremiumUnlocked
        ) { onStateChange(state.copy(isPremiumUnlocked = it)) }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            TextFieldRow(
                label = "MP3 audio folder",
                value = state.mp3OutputPath,
                onValueChange = { onStateChange(state.copy(mp3OutputPath = it)) },
            )
            TextFieldRow(
                label = "Compressed video folder",
                value = state.compressedOutputPath,
                onValueChange = { onStateChange(state.copy(compressedOutputPath = it)) },
            )
            TextFieldRow(
                label = "Trimmed video folder",
                value = state.trimmedOutputPath,
                onValueChange = { onStateChange(state.copy(trimmedOutputPath = it)) },
            )
        } else {
            // Custom RELATIVE_PATH subfolders are silently ignored by
            // MediaStore.insert() below API 29 (see OutputFileStore's doc) --
            // showing editable fields that silently fail to apply would be
            // worse than not showing them, so this note replaces the fields
            // entirely on those OS versions rather than accepting input we
            // can't honor.
            Text(
                text = "Custom folders require Android 10 or later. Compressed and " +
                    "trimmed videos will save to the default Movies location on this device.",
                style = WatermelonTypography.typography.bodySmall,
                color = WatermelonColors.DarkOnSurfaceVariant,
                modifier = Modifier.padding(vertical = WatermelonSpacing.sm)
            )
        }
    }
}
