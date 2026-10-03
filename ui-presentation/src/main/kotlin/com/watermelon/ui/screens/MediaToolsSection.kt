package com.watermelon.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.watermelon.ui.R
import com.watermelon.ui.theme.WatermelonSpacing
import com.watermelon.ui.theme.WatermelonTypography

@Composable
internal fun MediaToolsSection(
    state: SettingsState,
    onStateChange: (SettingsState) -> Unit
) {
    SettingsGroup(
        title = stringResource(R.string.settings_media_tools_title),
        summary = stringResource(R.string.settings_media_tools_summary)
    ) {
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
                label = stringResource(R.string.settings_mp3_folder),
                value = state.mp3OutputPath,
                onValueChange = { onStateChange(state.copy(mp3OutputPath = it)) },
            )
            TextFieldRow(
                label = stringResource(R.string.settings_compressed_folder),
                value = state.compressedOutputPath,
                onValueChange = { onStateChange(state.copy(compressedOutputPath = it)) },
            )
            TextFieldRow(
                label = stringResource(R.string.settings_trimmed_folder),
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
                text = stringResource(R.string.settings_custom_folders_note),
                style = WatermelonTypography.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = WatermelonSpacing.sm)
            )
        }
    }
}
