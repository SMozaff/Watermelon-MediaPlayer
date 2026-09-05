package com.watermelon.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.watermelon.ui.R
import com.watermelon.ui.theme.WatermelonColors
import com.watermelon.ui.theme.WatermelonSpacing
import com.watermelon.ui.theme.WatermelonTypography

@Composable
internal fun LibraryAccessSection(
    onFolderVisibilityClick: () -> Unit
) {
    SettingsGroup(title = "Library access", summary = "Choose which indexed folders appear in Watermelon") {
        NavRow(
            label = "Folder visibility",
            value = "Manage",
            onClick = onFolderVisibilityClick
        )
    }
}

@Composable
internal fun PrivacySection() {
    SettingsGroup(title = "Privacy", summary = "What network access is used for") {
        Text(
            text = stringResource(R.string.settings_privacy_internet_usage),
            style = WatermelonTypography.typography.bodySmall,
            color = WatermelonColors.DarkOnSurfaceVariant,
            modifier = Modifier.padding(vertical = WatermelonSpacing.sm)
        )
    }
}
