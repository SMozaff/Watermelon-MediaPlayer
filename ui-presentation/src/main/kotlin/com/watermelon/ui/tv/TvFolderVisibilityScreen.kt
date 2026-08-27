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
                title = "Folder visibility",
                supportingText = "Choose which indexed folders appear in your library."
            )
        }
        item {
            Text(
                text = if (hiddenCount == 0) {
                    "All indexed folders are visible. Press SELECT to hide a folder."
                } else {
                    "$hiddenCount hidden. Press SELECT to show or hide a folder."
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
                    text = "No folders are indexed yet. Return to the library after granting media access.",
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
                    text = "Back to settings",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(WatermelonSpacing.md)
                )
            }
        }
    }
}
