package com.watermelon.ui.screens

import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import com.watermelon.common.model.MediaItem
import com.watermelon.common.model.Playlist
import com.watermelon.ui.R
import com.watermelon.ui.WatermelonIcons
import com.watermelon.ui.components.LabeledIconButton
import com.watermelon.ui.components.MultiSelectionDock
import com.watermelon.ui.components.StatusBadge
import com.watermelon.ui.components.VideoListItem
import com.watermelon.ui.components.VelocityGuardImage
import com.watermelon.ui.components.WatermelonHeader
import com.watermelon.ui.components.WatermelonLoadingAnimation
import com.watermelon.ui.components.VideoItemSize
import com.watermelon.ui.theme.WatermelonColors
import com.watermelon.ui.theme.WatermelonShapes
import com.watermelon.ui.theme.WatermelonSpacing
import com.watermelon.ui.theme.WatermelonTypography
import com.watermelon.ui.viewmodel.LibraryUiState
import com.watermelon.ui.viewmodel.VideoListViewModel

private enum class VideoSort(val label: String) {
    NAME("Name"), DATE("Date"), DURATION("Duration"),
    FILE_TYPE("File Type"), SIZE("Size"), QUALITY("Quality"), CUSTOM("Custom")
}

private enum class VideoLayout { LIST, GRID }

private val LayoutSaver = androidx.compose.runtime.saveable.Saver<VideoLayout, String>(
    save = { it.name },
    restore = { VideoLayout.valueOf(it) }
)

private val SortSaver = androidx.compose.runtime.saveable.Saver<VideoSort, String>(
    save = { it.name },
    restore = { VideoSort.valueOf(it) }
)

private val ItemSizeSaver = androidx.compose.runtime.saveable.Saver<VideoItemSize, String>(
    save = { it.name },
    restore = { VideoItemSize.valueOf(it) }
)

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun VideoListScreen(
    viewModel: VideoListViewModel,
    onVideoClick: (MediaItem) -> Unit,
    onRefresh: () -> Unit = { viewModel.refresh() },
    availablePlaylists: List<Playlist> = emptyList(),
    defaultGrid: Boolean = false,
    showThumbnails: Boolean = true,
    showDurations: Boolean = true,
    showFileSize: Boolean = false,
    folderName: String = "Videos",
    onBack: () -> Unit = {},
    // Media tools entry points (extract audio / trim / compress). Null = feature not wired
    // by this caller yet; the context menu simply won't show that item. Kept optional so
    // existing callers of VideoListScreen don't all need updating at once.
    onExtractAudio: ((MediaItem) -> Unit)? = null,
    onTrimVideo: ((MediaItem) -> Unit)? = null,
    onCompressVideo: ((MediaItem) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
    val videos by viewModel.videos.collectAsStateWithLifecycle()
    val libraryState by viewModel.libraryState.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()

    // Recently Added is a computed chronological feed. Users may select another sort later,
    // but its first render must preserve the repository's newest-first order.
    var currentSort by rememberSaveable(stateSaver = SortSaver) {
        mutableStateOf(if (viewModel.isRecentlyAddedPlaylist) VideoSort.DATE else VideoSort.NAME)
    }
    var ascending by rememberSaveable { mutableStateOf(true) }
    var currentItemSize by rememberSaveable(stateSaver = ItemSizeSaver) {
        mutableStateOf(VideoItemSize.SMALL)
    }
    var currentLayout by rememberSaveable(defaultGrid, stateSaver = LayoutSaver) {
        mutableStateOf(if (defaultGrid) VideoLayout.GRID else VideoLayout.LIST)
    }
    val isGrid = currentLayout == VideoLayout.GRID
    var sortMenuOpen by remember { mutableStateOf(false) }
    var viewOptionsOpen by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showPlaylistPicker by remember { mutableStateOf(false) }
    // (contextMenuItem/showContextMenu removed -- the context menu now lives inside
    // VideoListItem itself, correctly anchored to its own 3-dot button. See VideoListItem's
    // file-level fix note for why the old root-level approach never actually worked.)

    val deleteLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            viewModel.onDeleteConfirmed()
            android.widget.Toast.makeText(
                context,
                "Deleted selected videos",
                android.widget.Toast.LENGTH_SHORT,
            ).show()
        } else {
            android.widget.Toast.makeText(
                context,
                "Deletion cancelled",
                android.widget.Toast.LENGTH_SHORT,
            ).show()
        }
    }
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            onRefresh()
            kotlinx.coroutines.delay(2000)
            isRefreshing = false
        }
    }

    val sorted = remember(videos, currentSort, ascending) {
        if (currentSort == VideoSort.CUSTOM) {
            videos
        } else {
            val cmp: Comparator<MediaItem> = when (currentSort) {
                VideoSort.NAME -> compareBy { it.displayName.lowercase() }
                VideoSort.DATE -> compareByDescending {
                    if (it.dateAdded > 0L) it.dateAdded else it.firstSeenAt
                }
                VideoSort.DURATION -> compareByDescending { it.durationMs }
                VideoSort.FILE_TYPE -> compareBy { it.fileExtension.lowercase() }
                VideoSort.SIZE -> compareByDescending { it.fileSize }
                VideoSort.QUALITY -> compareByDescending { it.pixelCount }
                VideoSort.CUSTOM -> compareBy { 0 }
            }
            videos.sortedWith(if (ascending) cmp else Comparator { a, b -> cmp.compare(b, a) })
        }
    }

    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()
    val isScrolling by remember {
        derivedStateOf { listState.isScrollInProgress || gridState.isScrollInProgress }
    }

    LaunchedEffect(currentSort, ascending, currentLayout, currentItemSize) {
        runCatching { listState.scrollToItem(0) }
        runCatching { gridState.scrollToItem(0) }
    }

    val gridColumns = when (currentItemSize) {
        VideoItemSize.SMALL -> GridCells.Fixed(3)
        VideoItemSize.LARGE -> GridCells.Fixed(2)
    }

    Column(modifier = modifier.fillMaxSize()) {
        WatermelonHeader(
            title = folderName,
            showBackButton = true,
            onBackClick = onBack,
            modifier = Modifier.fillMaxWidth()
        )

        if (selection.isActive) {
            MultiSelectionDock(
                selectedCount = selection.count,
                onDeselectAll = { viewModel.clearSelection() },
                onDelete = { showDeleteDialog = true },
                onAddToPlaylist = { showPlaylistPicker = true },
                onShare = {
                    val intent = viewModel.buildShareIntent()
                    context.startActivity(Intent.createChooser(intent, "Share videos"))
                },
                visible = selection.isActive,
                onRemoveFromPlaylist = if (viewModel.isPlaylist && viewModel.isRemovable) {
                    { viewModel.removeSelectedFromPlaylist() }
                } else null,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = WatermelonSpacing.sm, vertical = WatermelonSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(WatermelonSpacing.sm),
            ) {
                if (selection.isActive) {
                    TextButton(onClick = { viewModel.selectAll() }) {
                        Text("Select all", color = WatermelonColors.DarkOnSurface)
                    }
                    TextButton(onClick = { viewModel.clearSelection() }) {
                        Text("Cancel", color = WatermelonColors.DarkOnSurface)
                    }
                } else {
                    LabeledIconButton(
                        icon = WatermelonIcons.Sort,
                        label = "Sort: ${currentSort.label}",
                        onClick = { sortMenuOpen = true },
                    )
                    LabeledIconButton(
                        icon = if (isGrid) WatermelonIcons.ViewGrid else WatermelonIcons.ViewList,
                        label = "View: ${if (isGrid) "Grid" else "List"}",
                        onClick = { viewOptionsOpen = true },
                    )
                }
            }

            HorizontalDivider(
                thickness = WatermelonSpacing.hairline,
                color = WatermelonColors.DarkOutline
            )

            if (libraryState !is LibraryUiState.Content) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    when (val state = libraryState) {
                        LibraryUiState.Loading -> Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(WatermelonSpacing.md)
                        ) {
                            WatermelonLoadingAnimation(modifier = Modifier.size(160.dp))
                            Text("Scanning your videos", style = WatermelonTypography.typography.bodyLarge, color = WatermelonColors.DarkOnSurface)
                        }
                        LibraryUiState.Empty -> Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(WatermelonSpacing.md),
                            modifier = Modifier.padding(WatermelonSpacing.lg)
                        ) {
                            Text("No videos found", style = WatermelonTypography.typography.titleMedium, color = WatermelonColors.DarkOnSurface)
                            Text("Refresh the library or check which folders Watermelon can see.", style = WatermelonTypography.typography.bodyMedium, color = WatermelonColors.DarkOnSurfaceVariant)
                            Button(onClick = onRefresh) { Text("Refresh library") }
                        }
                        is LibraryUiState.Error -> Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(WatermelonSpacing.md),
                            modifier = Modifier.padding(WatermelonSpacing.lg)
                        ) {
                            Text("Library unavailable", style = WatermelonTypography.typography.titleMedium, color = WatermelonColors.DarkOnSurface)
                            Text(state.message, style = WatermelonTypography.typography.bodyMedium, color = WatermelonColors.DarkOnSurfaceVariant)
                            Button(onClick = onRefresh) { Text("Try again") }
                        }
                        LibraryUiState.Content -> Unit
                    }
                }
                return@Column
            }

            PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = { isRefreshing = true }) {
                when (currentLayout) {
                    VideoLayout.LIST -> LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().padding(horizontal = WatermelonSpacing.sm),
                        verticalArrangement = Arrangement.spacedBy(WatermelonSpacing.xs / 2)
                    ) {
                        items(sorted, key = { it.uri }) { item ->
                            val isSelected = selection.contains(item.uri)

                            VideoListItem(
                                item = item,
                                itemSize = currentItemSize,
                                isGrid = false,
                                isScrollingFast = isScrolling,
                                isSelected = isSelected,
                                selectionActive = selection.isActive,
                                showThumbnails = showThumbnails,
                                showDurations = showDurations,
                                showFileSize = showFileSize,
                                onClick = {
                                    if (selection.isActive) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        viewModel.onToggleSelect(item.uri)
                                    } else {
                                        viewModel.markPlayed(item.uri)
                                        coroutineScope.launch {
                                            val queueUris = viewModel.resolvePlaybackQueueUris(item.uri, sorted)
                                            PlaybackQueue.set(queueUris)
                                            onVideoClick(item)
                                        }
                                    }
                                },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.onLongPress(item.uri)
                                },
                                onExtractAudio = onExtractAudio,
                                onTrimVideo = onTrimVideo,
                                onCompressVideo = onCompressVideo
                            )
                        }
                    }

                    VideoLayout.GRID -> LazyVerticalGrid(
                        state = gridState,
                        columns = gridColumns,
                        modifier = Modifier.fillMaxSize().padding(WatermelonSpacing.sm),
                        horizontalArrangement = Arrangement.spacedBy(WatermelonSpacing.sm),
                        verticalArrangement = Arrangement.spacedBy(WatermelonSpacing.sm)
                    ) {
                        gridItems(sorted, key = { it.uri }) { item ->
                            val isSelected = selection.contains(item.uri)

                            VideoListItem(
                                item = item,
                                itemSize = currentItemSize,
                                isGrid = true,
                                isScrollingFast = isScrolling,
                                isSelected = isSelected,
                                selectionActive = selection.isActive,
                                showThumbnails = showThumbnails,
                                showDurations = showDurations,
                                showFileSize = showFileSize,
                                onClick = {
                                    if (selection.isActive) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        viewModel.onToggleSelect(item.uri)
                                    } else {
                                        viewModel.markPlayed(item.uri)
                                        coroutineScope.launch {
                                            val queueUris = viewModel.resolvePlaybackQueueUris(item.uri, sorted)
                                            PlaybackQueue.set(queueUris)
                                            onVideoClick(item)
                                        }
                                    }
                                },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.onLongPress(item.uri)
                                },
                                onExtractAudio = onExtractAudio,
                                onTrimVideo = onTrimVideo,
                                onCompressVideo = onCompressVideo
                            )
                        }
                    }
                }
            }
        }
    }

    if (sortMenuOpen) {
        AlertDialog(
            onDismissRequest = { sortMenuOpen = false },
            title = { Text("Sort videos") },
            text = {
                Column {
                    VideoSort.values().forEach { option ->
                        TextButton(
                            onClick = {
                                currentSort = option
                                sortMenuOpen = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = if (option == currentSort) "✓ ${option.label}" else option.label,
                                color = WatermelonColors.DarkOnSurface,
                            )
                        }
                    }
                    HorizontalDivider()
                    TextButton(
                        onClick = { ascending = !ascending },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = if (ascending) "✓ Ascending" else "Descending",
                            color = WatermelonColors.DarkOnSurface,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { sortMenuOpen = false }) { Text("Done") }
            },
        )
    }

    if (viewOptionsOpen) {
        AlertDialog(
            onDismissRequest = { viewOptionsOpen = false },
            title = { Text("View options") },
            text = {
                Column {
                    VideoLayout.values().forEach { layout ->
                        TextButton(
                            onClick = { currentLayout = layout },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = if (layout == currentLayout) {
                                    "✓ ${layout.name.lowercase().replaceFirstChar { it.uppercase() }}"
                                } else {
                                    layout.name.lowercase().replaceFirstChar { it.uppercase() }
                                },
                                color = WatermelonColors.DarkOnSurface,
                            )
                        }
                    }
                    HorizontalDivider()
                    VideoItemSize.values().forEach { size ->
                        TextButton(
                            onClick = { currentItemSize = size },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = if (size == currentItemSize) "✓ ${size.label}" else size.label,
                                color = WatermelonColors.DarkOnSurface,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewOptionsOpen = false }) { Text("Done") }
            },
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete ${selection.count} video(s)?", color = WatermelonColors.DarkOnSurface) },
            text = { Text("This will permanently delete the selected files from your device.", color = WatermelonColors.DarkOnSurfaceVariant) },
            confirmButton = {
                TextButton(onClick = {
                    val sender = viewModel.buildDeleteRequest(context.contentResolver)
                    if (sender != null) {
                        deleteLauncher.launch(
                            androidx.activity.result.IntentSenderRequest.Builder(sender).build()
                        )
                    }
                    showDeleteDialog = false
                }) {
                    Text("Delete", color = WatermelonColors.Error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel", color = WatermelonColors.DarkOnSurface)
                }
            }
        )
    }

    if (showPlaylistPicker) {
        var showCreateField by remember { mutableStateOf(false) }
        var newPlaylistName by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showPlaylistPicker = false },
            title = { Text("Add to playlist", color = WatermelonColors.DarkOnSurface) },
            text = {
                Column {
                    if (showCreateField) {
                        TextField(
                            value = newPlaylistName,
                            onValueChange = { newPlaylistName = it },
                            placeholder = { Text("Playlist name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(modifier = Modifier.fillMaxWidth()) {
                            TextButton(onClick = {
                                showCreateField = false
                                newPlaylistName = ""
                            }) {
                                Text("Cancel", color = WatermelonColors.DarkOnSurfaceVariant)
                            }
                            TextButton(
                                onClick = {
                                    val name = newPlaylistName.trim()
                                    if (name.isNotEmpty()) {
                                        viewModel.createPlaylistAndAddSelected(name)
                                        showPlaylistPicker = false
                                        showCreateField = false
                                        newPlaylistName = ""
                                    }
                                }
                            ) {
                                Text("Create", color = WatermelonColors.DarkOnSurface)
                            }
                        }
                    } else {
                        TextButton(
                            onClick = { showCreateField = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("+ Create new playlist", color = WatermelonColors.DarkOnSurface)
                        }
                    }

                    val userPlaylists = availablePlaylists.filter {
                        it.type == com.watermelon.common.model.PlaylistType.USER
                    }
                    if (userPlaylists.isNotEmpty()) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    }
                    userPlaylists.forEach { playlist ->
                        TextButton(
                            onClick = {
                                viewModel.addSelectedToPlaylist(playlist.id)
                                showPlaylistPicker = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(playlist.name, color = WatermelonColors.DarkOnSurface)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPlaylistPicker = false }) {
                    Text("Cancel", color = WatermelonColors.DarkOnSurface)
                }
            }
        )
    }
}
