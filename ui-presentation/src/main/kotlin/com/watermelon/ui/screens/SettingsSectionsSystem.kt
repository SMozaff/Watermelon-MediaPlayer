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
internal fun LibraryAccessSection(
    onFolderVisibilityClick: () -> Unit
) {
    SettingsGroup(
        title = stringResource(R.string.settings_library_access),
        summary = stringResource(R.string.settings_library_access_summary)
    ) {
        NavRow(
            label = stringResource(R.string.settings_folder_visibility),
            value = "Manage",
            onClick = onFolderVisibilityClick
        )
    }
}

@Composable
internal fun PrivacySection() {
    SettingsGroup(
        title = stringResource(R.string.settings_privacy),
        summary = stringResource(R.string.settings_privacy_summary)
    ) {
        Text(
            text = stringResource(R.string.settings_privacy_internet_usage),
            style = WatermelonTypography.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = WatermelonSpacing.sm)
        )
    }
}
