package com.watermelon.ui.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.watermelon.common.model.FolderNode
import com.watermelon.ui.theme.WatermelonSpacing
import com.watermelon.ui.viewmodel.FolderViewModel

/**
 * Android TV library home. Top-level destinations are always visible and D-pad focus moves
 * through one vertical sequence: All Videos, Playlists, Settings, and then indexed folders.
 * This avoids a touch-oriented bottom bar or hidden navigation drawer at a ten-foot distance.
 */
@Composable
fun TvFolderBrowserScreen(
    viewModel: FolderViewModel,
    onFolderClick: (FolderNode) -> Unit,
    onAllVideosClick: () -> Unit,
    onPlaylistsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val folders by viewModel.folderTree.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(WatermelonSpacing.sm)
    ) {
        item {
            TvScreenHeader(
                title = "Watermelon",
                supportingText = "Browse your library with the D-pad. Press SELECT to open an item."
            )
        }
        item {
            TvHomeDestination(
                label = "All videos",
                detail = "Browse every video in your library",
                onClick = onAllVideosClick
            )
        }
        item {
            TvHomeDestination(
                label = "Playlists",
                detail = "Recently added, favourites, and your playlists",
                onClick = onPlaylistsClick
            )
        }
        item {
            TvHomeDestination(
                label = "Settings",
                detail = "Library, playback, subtitles, and exports",
                onClick = onSettingsClick
            )
        }
        item {
            Text(
                text = "Folders",
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

        if (folders.isEmpty()) {
            item {
                Text(
                    text = "No folders are indexed yet. Grant media access or add videos, then return here.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = WatermelonSpacing.xl + WatermelonSpacing.md)
                )
            }
        } else {
            items(folders, key = { it.path }) { folder ->
                TvFolderRow(folder = folder, onClick = { onFolderClick(folder) })
            }
        }
    }
}

@Composable
private fun TvHomeDestination(
    label: String,
    detail: String,
    onClick: () -> Unit
) {
    TvFocusableSurface(
        onClick = onClick,
        modifier = Modifier.padding(horizontal = WatermelonSpacing.xl + WatermelonSpacing.md)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(WatermelonSpacing.md)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = WatermelonSpacing.xs)
            )
        }
    }
}

@Composable
private fun TvFolderRow(
    folder: FolderNode,
    onClick: () -> Unit
) {
    TvFocusableSurface(
        onClick = onClick,
        modifier = Modifier.padding(horizontal = WatermelonSpacing.xl + WatermelonSpacing.md)
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
                    text = folder.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${folder.itemCount} video${if (folder.itemCount == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = WatermelonSpacing.xs)
                )
            }
            Text(
                text = "Open",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}
