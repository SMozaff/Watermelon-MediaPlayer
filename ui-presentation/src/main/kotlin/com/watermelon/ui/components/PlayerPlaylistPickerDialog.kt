package com.watermelon.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.watermelon.common.model.Playlist
import com.watermelon.common.model.PlaylistType
import com.watermelon.ui.R
import com.watermelon.ui.theme.WatermelonGlass

/**
 * The player-side destination chooser for the explicit "Add to playlist" action. Favourites is
 * intentionally absent: it remains a separate action in the player control panel.
 */
@Composable
fun PlayerPlaylistPickerDialog(
    playlists: List<Playlist>,
    onSelect: (Playlist) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var isCreating by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    val userPlaylists = remember(playlists) { playlists.filter { it.type == PlaylistType.USER } }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = WatermelonGlass.surfaceFor(WatermelonGlass.Role.Modal),
        tonalElevation = 0.dp,
        title = { Text(stringResource(R.string.video_add_to_playlist_title)) },
        text = {
            Column {
                if (isCreating) {
                    TextField(
                        value = newPlaylistName,
                        onValueChange = { newPlaylistName = it },
                        placeholder = { Text(stringResource(R.string.playlists_name_placeholder)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = WatermelonGlass.controlSurface(),
                            unfocusedContainerColor = WatermelonGlass.controlSurface(),
                            focusedIndicatorColor = WatermelonGlass.highlightColor(),
                            unfocusedIndicatorColor = WatermelonGlass.borderColor(),
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                    Row(modifier = Modifier.fillMaxWidth()) {
                        TextButton(onClick = {
                            isCreating = false
                            newPlaylistName = ""
                        }) {
                            Text(stringResource(R.string.action_cancel))
                        }
                        TextButton(
                            onClick = {
                                val name = newPlaylistName.trim()
                                if (name.isNotEmpty()) onCreate(name)
                            }
                        ) {
                            Text(stringResource(R.string.playlists_create_and_add))
                        }
                    }
                } else {
                    TextButton(
                        onClick = { isCreating = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.playlists_create_new))
                    }
                }

                if (userPlaylists.isNotEmpty()) {
                    HorizontalDivider(
                        color = WatermelonGlass.borderColor(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    userPlaylists.forEach { playlist ->
                        TextButton(
                            onClick = { onSelect(playlist) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(playlist.name, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                } else if (!isCreating) {
                    Text(
                        text = stringResource(R.string.playlists_empty_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}
