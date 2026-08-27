package com.watermelon.ui.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.watermelon.common.model.Playlist
import com.watermelon.common.model.PlaylistType
import com.watermelon.ui.theme.WatermelonSpacing
import com.watermelon.ui.viewmodel.PlaylistViewModel

/**
 * Android TV playlists. Playlist opening, create, rename, and delete are separate focus stops;
 * a remote user never has to discover a phone-style long press or a nested touch target. Text
 * entry intentionally hands focus to the platform IME in a dedicated dialog.
 */
@Composable
fun TvPlaylistsScreen(
    viewModel: PlaylistViewModel,
    onPlaylistClick: (Playlist) -> Unit,
    onSettingsClick: () -> Unit,
    continueWatchingEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val allPlaylists by viewModel.playlists.collectAsStateWithLifecycle()
    val playlists = remember(allPlaylists, continueWatchingEnabled) {
        if (continueWatchingEnabled) allPlaylists
        else allPlaylists.filter { it.type != PlaylistType.CONTINUE_WATCHING }
    }

    var createName by remember { mutableStateOf<String?>(null) }
    var renameTarget by remember { mutableStateOf<Playlist?>(null) }
    var deleteTarget by remember { mutableStateOf<Playlist?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(WatermelonSpacing.sm)
    ) {
        item {
            TvScreenHeader(
                title = "Playlists",
                supportingText = "Press SELECT to open a playlist or choose a visible action."
            )
        }
        item {
            TvPlaylistAction(
                label = "New playlist",
                detail = "Create a playlist with the TV keyboard",
                actionLabel = "Create",
                onClick = { createName = "" }
            )
        }
        item {
            TvPlaylistAction(
                label = "Settings",
                detail = "Library, playback, subtitles, and exports",
                actionLabel = "Open",
                onClick = onSettingsClick
            )
        }
        item {
            Text(
                text = "Your playlists",
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
        if (playlists.isEmpty()) {
            item {
                Text(
                    text = "No playlists yet. Select New playlist to create one.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = WatermelonSpacing.xl + WatermelonSpacing.md)
                )
            }
        } else {
            items(playlists, key = { it.id }) { playlist ->
                TvPlaylistRow(
                    playlist = playlist,
                    onOpen = { onPlaylistClick(playlist) },
                    onRename = if (playlist.type == PlaylistType.USER) {
                        { renameTarget = playlist }
                    } else {
                        null
                    },
                    onDelete = if (playlist.type == PlaylistType.USER) {
                        { deleteTarget = playlist }
                    } else {
                        null
                    }
                )
            }
        }
    }

    createName?.let {
        TvPlaylistNameDialog(
            title = "New playlist",
            initialName = it,
            confirmLabel = "Create",
            onConfirm = { name ->
                viewModel.createPlaylist(name)
                createName = null
            },
            onDismiss = { createName = null }
        )
    }
    renameTarget?.let { target ->
        TvPlaylistNameDialog(
            title = "Rename playlist",
            initialName = target.name,
            confirmLabel = "Save",
            onConfirm = { name ->
                viewModel.renamePlaylist(target.id, name)
                renameTarget = null
            },
            onDismiss = { renameTarget = null }
        )
    }
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete \"${target.name}\"?") },
            text = { Text("This removes the playlist. The video files themselves are not deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deletePlaylist(target.id)
                    deleteTarget = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun TvPlaylistAction(
    label: String,
    detail: String,
    actionLabel: String,
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
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = WatermelonSpacing.xs)
                )
            }
            Text(
                text = actionLabel,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

@Composable
private fun TvPlaylistRow(
    playlist: Playlist,
    onOpen: () -> Unit,
    onRename: (() -> Unit)?,
    onDelete: (() -> Unit)?
) {
    Column(
        modifier = Modifier.padding(horizontal = WatermelonSpacing.xl + WatermelonSpacing.md),
        verticalArrangement = Arrangement.spacedBy(WatermelonSpacing.xs)
    ) {
        TvFocusableSurface(onClick = onOpen) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(WatermelonSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(WatermelonSpacing.md)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = playlist.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${playlist.itemCount} video${if (playlist.itemCount == 1) "" else "s"}",
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
        if (onRename != null && onDelete != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(WatermelonSpacing.sm)
            ) {
                TvPlaylistSecondaryAction(
                    label = "Rename",
                    onClick = onRename,
                    modifier = Modifier.weight(1f)
                )
                TvPlaylistSecondaryAction(
                    label = "Delete",
                    onClick = onDelete,
                    isDestructive = true,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun TvPlaylistSecondaryAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDestructive: Boolean = false
) {
    TvFocusableSurface(onClick = onClick, modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(WatermelonSpacing.sm)
        )
    }
}

@Composable
private fun TvPlaylistNameDialog(
    title: String,
    initialName: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    val textFieldFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { textFieldFocus.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(
                    text = "Use the TV keyboard to enter a name, then select $confirmLabel.",
                    style = MaterialTheme.typography.bodyMedium
                )
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = WatermelonSpacing.md)
                        .focusRequester(textFieldFocus)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim()) },
                enabled = name.trim().isNotEmpty()
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
