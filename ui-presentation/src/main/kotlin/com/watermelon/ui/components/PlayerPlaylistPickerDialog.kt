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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.watermelon.common.model.Playlist
import com.watermelon.common.model.PlaylistType

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
        title = { Text("Add to playlist") },
        text = {
            Column {
                if (isCreating) {
                    TextField(
                        value = newPlaylistName,
                        onValueChange = { newPlaylistName = it },
                        placeholder = { Text("Playlist name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(modifier = Modifier.fillMaxWidth()) {
                        TextButton(onClick = {
                            isCreating = false
                            newPlaylistName = ""
                        }) {
                            Text("Cancel")
                        }
                        TextButton(
                            onClick = {
                                val name = newPlaylistName.trim()
                                if (name.isNotEmpty()) onCreate(name)
                            }
                        ) {
                            Text("Create and add")
                        }
                    }
                } else {
                    TextButton(
                        onClick = { isCreating = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Create new playlist")
                    }
                }

                if (userPlaylists.isNotEmpty()) {
                    HorizontalDivider(modifier = Modifier.fillMaxWidth())
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
                        text = "Create your first playlist to organise the current video.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
