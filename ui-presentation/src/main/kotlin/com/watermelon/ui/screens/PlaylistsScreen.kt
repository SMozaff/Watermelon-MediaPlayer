package com.watermelon.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.watermelon.common.model.Playlist
import com.watermelon.common.model.PlaylistType
import com.watermelon.ui.R
import com.watermelon.ui.WatermelonIcons
import com.watermelon.ui.components.WatermelonHeader
import com.watermelon.ui.components.WatermelonGlassButton
import com.watermelon.ui.components.WatermelonGlassCard
import com.watermelon.ui.components.WatermelonGlassIconButton
import com.watermelon.ui.components.WatermelonGlyph
import com.watermelon.ui.theme.WatermelonColors
import com.watermelon.ui.theme.WatermelonGlass
import com.watermelon.ui.theme.WatermelonSpacing
import com.watermelon.ui.theme.WatermelonTypography
import com.watermelon.ui.viewmodel.PlaylistViewModel

/**
 * Lists all playlists (system: Continue Watching, Recently Added, Favourites,
 * plus user-created ones) and provides full management for user playlists:
 * create, rename, delete. Tapping a row opens its videos via the existing
 * `videos/{folderPath}?isPlaylist=true` route, where items can be added/removed
 * (see VideoListScreen's MultiSelectionDock "Add to playlist" / "Remove from playlist").
 *
 * System playlists (Recently Added, Favourites, Continue Watching) have no rename/delete
 * menu — they're computed or have their own dedicated add/remove mechanism, matching
 * Playlist's own doc comment ("System playlists have fixed ids and cannot be renamed or
 * deleted").
 *
 * [continueWatchingEnabled] hides the Continue Watching row entirely when the user has
 * turned it off in Settings — resume-position tracking itself keeps running in the
 * background regardless (that's handled at the PlaybackController level, not here); this
 * screen only controls whether the entry point is visible.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlaylistsScreen(
    viewModel: PlaylistViewModel,
    onPlaylistClick: (Playlist) -> Unit,
    continueWatchingEnabled: Boolean = true
) {
    val allPlaylists by viewModel.playlists.collectAsStateWithLifecycle()
    val playlists = remember(allPlaylists, continueWatchingEnabled) {
        if (continueWatchingEnabled) {
            allPlaylists
        } else {
            allPlaylists.filter { it.type != PlaylistType.CONTINUE_WATCHING }
        }
    }

    var showCreateDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<Playlist?>(null) }
    var deleteTarget by remember { mutableStateOf<Playlist?>(null) }
    var menuTarget by remember { mutableStateOf<Playlist?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        WatermelonHeader(title = "Playlists")

        WatermelonGlassButton(
            onClick = { showCreateDialog = true },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = WatermelonSpacing.md, vertical = WatermelonSpacing.sm)
        ) {
            WatermelonGlyph(
                icon = WatermelonIcons.PlaylistAdd,
                contentDescription = null,
                tint = WatermelonColors.Accent,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.size(WatermelonSpacing.sm))
            Text(
                "New playlist",
                style = WatermelonTypography.typography.bodyLarge,
                color = WatermelonColors.Accent
            )
        }

        if (playlists.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                WatermelonGlassCard(
                    modifier = Modifier.padding(WatermelonSpacing.lg),
                    elevated = true
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(WatermelonSpacing.xl),
                    ) {
                    Text(
                        "Create playlists for the videos you want to watch again.",
                        style = WatermelonTypography.typography.bodyLarge,
                        color = WatermelonColors.DarkOnSurface,
                    )
                    Text(
                        "Keep favourites, trips, shows, or any group together without moving the original files.",
                        style = WatermelonTypography.typography.bodyMedium,
                        color = WatermelonColors.DarkOnSurfaceVariant,
                        modifier = Modifier.padding(top = WatermelonSpacing.sm),
                    )
                        WatermelonGlassButton(
                            onClick = { showCreateDialog = true },
                            modifier = Modifier.padding(top = WatermelonSpacing.lg)
                        ) {
                            Text("Create playlist", color = WatermelonColors.Accent)
                        }
                    }
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(playlists, key = { it.id }) { playlist ->
                    val isUserPlaylist = playlist.type == PlaylistType.USER

                    WatermelonGlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = WatermelonSpacing.md, vertical = WatermelonSpacing.xs),
                        elevated = isUserPlaylist
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = { onPlaylistClick(playlist) },
                                    onLongClick = { if (isUserPlaylist) menuTarget = playlist }
                                )
                                .padding(horizontal = WatermelonSpacing.md, vertical = WatermelonSpacing.sm)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                WatermelonGlyph(
                                    icon = when (playlist.type) {
                                        PlaylistType.FAVOURITES -> WatermelonIcons.Star
                                        PlaylistType.CONTINUE_WATCHING -> WatermelonIcons.VideoLibrary
                                        PlaylistType.RECENTLY_ADDED -> WatermelonIcons.VideoLibrary
                                        PlaylistType.USER -> WatermelonIcons.Playlist
                                    },
                                    contentDescription = null,
                                    tint = WatermelonColors.Accent,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(Modifier.size(WatermelonSpacing.md))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = playlist.name,
                                        style = WatermelonTypography.typography.bodyLarge,
                                        color = WatermelonColors.DarkOnSurface
                                    )
                                    Text(
                                        text = "${playlist.itemCount} video${if (playlist.itemCount == 1) "" else "s"}",
                                        style = WatermelonTypography.typography.bodyMedium,
                                        color = WatermelonColors.DarkOnSurfaceVariant
                                    )
                                }
                                if (isUserPlaylist) {
                                    WatermelonGlassIconButton(
                                        onClick = { menuTarget = playlist },
                                        icon = WatermelonIcons.MoreVert,
                                        contentDescription = "Playlist options"
                                    )
                                }
                            }
                        }
                        DropdownMenu(
                            expanded = menuTarget?.id == playlist.id,
                            onDismissRequest = { menuTarget = null },
                            containerColor = WatermelonGlass.popupSurface(),
                            border = BorderStroke(1.dp, WatermelonGlass.borderColor())
                        ) {
                            DropdownMenuItem(
                                text = { Text("Rename") },
                                onClick = {
                                    renameTarget = playlist
                                    menuTarget = null
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete", color = WatermelonColors.Error) },
                                onClick = {
                                    deleteTarget = playlist
                                    menuTarget = null
                                }
                            )
                        }
                    }

                }
            }
        }
    }

    if (showCreateDialog) {
        var name by remember { mutableStateOf("") }
        val nameFocusRequester = remember { FocusRequester() }
        LaunchedEffect(Unit) { nameFocusRequester.requestFocus() }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            containerColor = WatermelonGlass.modalSurface(),
            tonalElevation = 0.dp,
            title = { Text("New playlist", color = WatermelonColors.DarkOnSurface) },
            text = {
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text("Playlist name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(nameFocusRequester)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmed = name.trim()
                        if (trimmed.isNotEmpty()) {
                            viewModel.createPlaylist(trimmed)
                            showCreateDialog = false
                        }
                    }
                ) { Text("Create", color = WatermelonColors.Accent) }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel", color = WatermelonColors.DarkOnSurface)
                }
            }
        )
    }

    renameTarget?.let { target ->
        var name by remember(target.id) { mutableStateOf(target.name) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            containerColor = WatermelonGlass.modalSurface(),
            tonalElevation = 0.dp,
            title = { Text("Rename playlist", color = WatermelonColors.DarkOnSurface) },
            text = {
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmed = name.trim()
                        if (trimmed.isNotEmpty()) {
                            viewModel.renamePlaylist(target.id, trimmed)
                            renameTarget = null
                        }
                    }
                ) { Text("Save", color = WatermelonColors.Accent) }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text("Cancel", color = WatermelonColors.DarkOnSurface)
                }
            }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor = com.watermelon.ui.theme.WatermelonGlass.surfaceElevated,
            tonalElevation = 0.dp,
            title = { Text("Delete \"${target.name}\"?", color = WatermelonColors.DarkOnSurface) },
            text = {
                Text(
                    "This removes the playlist. The video files themselves are not deleted.",
                    color = WatermelonColors.DarkOnSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deletePlaylist(target.id)
                        deleteTarget = null
                    }
                ) { Text("Delete", color = WatermelonColors.Error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("Cancel", color = WatermelonColors.DarkOnSurface)
                }
            }
        )
    }
}
