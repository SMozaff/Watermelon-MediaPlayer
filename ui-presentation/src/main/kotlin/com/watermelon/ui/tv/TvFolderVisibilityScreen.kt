package com.watermelon.ui.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.watermelon.ui.R
import androidx.compose.ui.res.stringResource
import com.watermelon.ui.theme.WatermelonSpacing

/**
 * Remote-first folder visibility manager. Selecting a row includes or excludes that folder from
 * the media library, with wording that directly reflects the effect of the persisted visibility
 * flag rather than exposing a touch switch with an ambiguous state at a ten-foot distance.
 */
@Composable
fun TvFolderVisibilityScreen(
    folders: List<Triple<String, String, Boolean>>,
    onToggle: (path: String, visible: Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sortedFolders = folders.sortedBy { it.second.lowercase() }
    val hiddenCount = folders.count { !it.third }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(WatermelonSpacing.sm)
    ) {
        item {
            TvScreenHeader(
                title = stringResource(R.string.settings_folder_visibility),
                supportingText = stringResource(R.string.tv_folder_visibility_hint)
            )
        }
        item {
            Text(
                text = if (hiddenCount == 0) {
                    stringResource(R.string.tv_folder_visibility_all_visible)
                } else {
                    stringResource(R.string.tv_folder_visibility_hidden_count, hiddenCount)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(
                    start = WatermelonSpacing.xl + WatermelonSpacing.md,
                    end = WatermelonSpacing.xl + WatermelonSpacing.md,
                    bottom = WatermelonSpacing.sm
                )
            )
        }

        if (sortedFolders.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.tv_folder_visibility_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = WatermelonSpacing.xl + WatermelonSpacing.md)
                )
            }
        } else {
            items(sortedFolders, key = { it.first }) { (path, displayName, isVisible) ->
                TvToggleSetting(
                    label = displayName,
                    enabled = isVisible,
                    supportingText = path,
                    onClick = { onToggle(path, !isVisible) }
                )
            }
        }

        item {
            TvFocusableSurface(
                onClick = onBack,
                modifier = Modifier.padding(
                    start = WatermelonSpacing.xl + WatermelonSpacing.md,
                    end = WatermelonSpacing.xl + WatermelonSpacing.md,
                    top = WatermelonSpacing.md,
                    bottom = WatermelonSpacing.xl
                )
            ) {
                Text(
                    text = stringResource(R.string.tv_back_to_settings),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(WatermelonSpacing.md)
                )
            }
        }
    }
}
