package com.watermelon.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.watermelon.ui.theme.WatermelonSpacing
import com.watermelon.ui.theme.WatermelonTypography

@Composable
internal fun MediaToolsSection(
    state: SettingsState,
    onStateChange: (SettingsState) -> Unit
) {
    SettingsGroup(title = "Media tools", summary = "Where exported audio and video are saved") {
        // No premium toggle here on purpose. `isPremiumUnlocked` is plumbed all the way from
        // this screen through MainActivity into Trim/Compress, but nothing ever gates on it:
        // no purchase flow exists, `PremiumUpsellDialog` is never shown, and the Trim/Compress
        // screens accept `onRequestUpsell` without ever invoking it. The toggle was therefore
        // a control that changed a persisted flag with no observable effect, labelled with
        // engineering wording ("placeholder -- no purchase flow yet"). Sprint story WM-406
        // requires placeholder/premium wording be kept out of consumer-facing paths, so the
        // toggle is hidden rather than reworded. The state field is retained so a real gating
        // decision can be made without a schema change.
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = WatermelonSpacing.sm)
            )
        }
    }
}
