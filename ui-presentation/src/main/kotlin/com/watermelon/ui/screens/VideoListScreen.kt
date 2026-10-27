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
import androidx.compose.material3.MaterialTheme
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
import com.watermelon.ui.components.ItemSizeSlider
import com.watermelon.ui.components.TetrisVideoLayout
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import com.watermelon.common.model.MediaItem
import com.watermelon.common.model.Playlist
import com.watermelon.ui.R
import com.watermelon.ui.WatermelonIcons
import com.watermelon.ui.components.IconType
import com.watermelon.ui.components.LabeledIconButton
import com.watermelon.ui.components.MultiSelectionDock
import com.watermelon.ui.components.StatusBadge
import com.watermelon.ui.components.VideoListItem
import com.watermelon.ui.components.VelocityGuardImage
import com.watermelon.ui.components.WatermelonHeader
import com.watermelon.ui.components.WatermelonGlassButton
import com.watermelon.ui.components.WatermelonGlassCard
import com.watermelon.ui.components.WatermelonLoadingAnimation
import com.watermelon.ui.components.VideoItemSize
import com.watermelon.ui.theme.WatermelonColors
import com.watermelon.ui.theme.WatermelonGlass
import com.watermelon.ui.theme.WatermelonShapes
import com.watermelon.ui.theme.WatermelonSpacing
import com.watermelon.ui.theme.WatermelonTypography
import com.watermelon.ui.viewmodel.LibraryUiState
import com.watermelon.ui.viewmodel.DeleteError
import com.watermelon.ui.viewmodel.VideoListViewModel

// Plain enum: the display label is localized at the call site through
// VideoSort.label(), since string resources resolve only in composition.
private enum class VideoSort {
    NAME, DATE, DURATION, FILE_TYPE, SIZE, QUALITY, CUSTOM
}

@Composable
private fun VideoSort.label(): String = stringResource(
    when (this) {
        VideoSort.NAME -> R.string.sort_by_name
        VideoSort.DATE -> R.string.sort_by_date
        VideoSort.DURATION -> R.string.sort_by_duration
        VideoSort.FILE_TYPE -> R.string.sort_by_file_type
        VideoSort.SIZE -> R.string.sort_by_size
        VideoSort.QUALITY -> R.string.sort_by_quality
        VideoSort.CUSTOM -> R.string.sort_by_custom
    }
)

private enum class VideoLayout { LIST, GRID, TETRIS }

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
    tetrisViewEnabled: Boolean = false,
    showThumbnails: Boolean = true,
    showDurations: Boolean = true,
    showFileSize: Boolean = false,
    folderName: String = stringResource(R.string.nav_videos),
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
    val deleteError by viewModel.deleteError.collectAsStateWithLifecycle()

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
        mutableStateOf(VideoLayout.TETRIS)
    }
    val isGrid = currentLayout == VideoLayout.GRID
    LaunchedEffect(tetrisViewEnabled) {
        if (!tetrisViewEnabled && currentLayout == VideoLayout.TETRIS) currentLayout = VideoLayout.GRID
    }
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
                context.getString(R.string.video_toast_deleted),
                android.widget.Toast.LENGTH_SHORT,
            ).show()
        } else {
            viewModel.onDeleteCancelled()
            android.widget.Toast.makeText(
                context,
                context.getString(R.string.video_toast_deletion_cancelled),
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

    LaunchedEffect(deleteError) {
        deleteError?.let { error ->
            val messageRes = when (error) {
                DeleteError.NONE_SELECTED -> R.string.error_delete_none_selected
                DeleteError.INVALID_URIS -> R.string.error_delete_invalid_uris
                DeleteError.PREPARE_FAILED -> R.string.error_delete_prepare_failed
                DeleteError.DELETE_FAILED -> R.string.error_delete_failed
                DeleteError.PARTIAL -> R.string.error_delete_partial
            }
            android.widget.Toast.makeText(
                context,
                context.getString(messageRes),
                android.widget.Toast.LENGTH_LONG,
            ).show()
            viewModel.clearDeleteError()
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

    // Do not reset the browse position when this destination is recreated after
    // returning from the player. The Lazy*State is saveable, so its position should be
    // restored by Navigation Compose. Only an actual browse-option change should move
    // the user back to the beginning.
    var hasAppliedBrowseOptions by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(currentSort, ascending, currentLayout, currentItemSize) {
        if (hasAppliedBrowseOptions) {
            runCatching { listState.scrollToItem(0) }
            runCatching { gridState.scrollToItem(0) }
        } else {
            hasAppliedBrowseOptions = true
        }
    }

    val gridColumns = when (currentItemSize) {
        // One distinct column count per slider detent. This ladder used to read 4/3/2/2/1,
        // which made the fourth detent a no-op: tapping the slider from MEDIUM to LARGE
        // redrew the grid identically, so the control appeared broken.
        VideoItemSize.TINY -> GridCells.Fixed(5)
        VideoItemSize.SMALL -> GridCells.Fixed(4)
        VideoItemSize.MEDIUM -> GridCells.Fixed(3)
        VideoItemSize.LARGE -> GridCells.Fixed(2)
        VideoItemSize.XLARGE -> GridCells.Fixed(1)
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
                    context.startActivity(
                        Intent.createChooser(intent, context.getString(R.string.video_share_chooser_title))
                    )
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
                        Text(stringResource(R.string.action_select_all), color = MaterialTheme.colorScheme.onSurface)
                    }
                    TextButton(onClick = { viewModel.clearSelection() }) {
                        Text(stringResource(R.string.action_cancel), color = MaterialTheme.colorScheme.onSurface)
                    }
                } else {
                    LabeledIconButton(
                        icon = IconType.DrawableIcon(WatermelonIcons.Sort),
                        label = stringResource(R.string.toolbar_sort, currentSort.label()),
                        onClick = { sortMenuOpen = true },
                    )
                    LabeledIconButton(
                        icon = IconType.DrawableIcon(
                            if (isGrid) WatermelonIcons.ViewGrid else WatermelonIcons.ViewList
                        ),
                        label = stringResource(
                            R.string.toolbar_view,
                            stringResource(
                                when (currentLayout) {
                                    VideoLayout.GRID -> R.string.layout_grid
                                    VideoLayout.TETRIS -> R.string.layout_tetris
                                    else -> R.string.layout_list
                                }
                            )
                        ),
                        onClick = { viewOptionsOpen = true },
                    )
                    ItemSizeSlider(
                        value = currentItemSize.ordinal,
                        onValueChange = { currentItemSize = VideoItemSize.entries[it] },
                        leadingIcon = WatermelonIcons.VideoLibrary,
                        trailingIcon = WatermelonIcons.VideoLibrary,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            HorizontalDivider(
                thickness = WatermelonSpacing.hairline,
                color = WatermelonGlass.borderColor()
            )

            if (libraryState !is LibraryUiState.Content) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    when (val state = libraryState) {
                        LibraryUiState.Loading -> WatermelonGlassCard(
                            modifier = Modifier.padding(WatermelonSpacing.lg),
                            elevated = true
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(WatermelonSpacing.md),
                                modifier = Modifier.padding(WatermelonSpacing.xl)
                            ) {
                                WatermelonLoadingAnimation(modifier = Modifier.size(160.dp))
                                Text(
                                    stringResource(R.string.library_scanning_videos),
                                    style = WatermelonTypography.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        LibraryUiState.Empty -> WatermelonGlassCard(
                            modifier = Modifier.padding(WatermelonSpacing.lg),
                            elevated = true
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(WatermelonSpacing.md),
                                modifier = Modifier.padding(WatermelonSpacing.xl)
                            ) {
                                Text(
                                    stringResource(R.string.library_empty_videos_title),
                                    style = WatermelonTypography.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    stringResource(R.string.library_empty_videos_body),
                                    style = WatermelonTypography.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                WatermelonGlassButton(onClick = onRefresh, selected = true) {
                                    Text(stringResource(R.string.library_refresh), color = WatermelonColors.Accent)
                                }
                            }
                        }
                        is LibraryUiState.Error -> WatermelonGlassCard(
                            modifier = Modifier.padding(WatermelonSpacing.lg),
                            elevated = true
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(WatermelonSpacing.md),
                                modifier = Modifier.padding(WatermelonSpacing.xl)
                            ) {
                                Text(
                                    stringResource(R.string.library_error_title),
                                    style = WatermelonTypography.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    stringResource(R.string.library_error_body),
                                    style = WatermelonTypography.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                WatermelonGlassButton(onClick = onRefresh, selected = true) {
                                    Text(stringResource(R.string.library_try_again), color = WatermelonColors.Accent)
                                }
                            }
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

                    VideoLayout.TETRIS -> TetrisVideoLayout(
                        items = sorted,
                        itemSize = currentItemSize,
                        isScrollingFast = isScrolling,
                        isSelected = { item -> selection.contains(item.uri) },
                        selectionActive = selection.isActive,
                        showThumbnails = showThumbnails,
                        showDurations = showDurations,
                        onClick = { item ->
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
                        onLongClick = { item ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.onLongPress(item.uri)
                        },
                        onExtractAudio = onExtractAudio,
                        onTrimVideo = onTrimVideo,
                        onCompressVideo = onCompressVideo,
                        modifier = Modifier.fillMaxSize().padding(horizontal = WatermelonSpacing.sm),
                    )

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
            containerColor = WatermelonGlass.surfaceFor(WatermelonGlass.Role.Modal),
            tonalElevation = 0.dp,
            title = { Text(stringResource(R.string.sort_videos_title)) },
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
                                text = if (option == currentSort) "✓ ${option.label()}" else option.label(),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                    HorizontalDivider()
                    TextButton(
                        onClick = { ascending = !ascending },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = if (ascending) "✓ ${stringResource(R.string.sort_ascending)}"
                            else stringResource(R.string.sort_descending),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { sortMenuOpen = false }) { Text(stringResource(R.string.action_done)) }
            },
        )
    }

    if (viewOptionsOpen) {
        AlertDialog(
            onDismissRequest = { viewOptionsOpen = false },
            containerColor = WatermelonGlass.surfaceFor(WatermelonGlass.Role.Modal),
            tonalElevation = 0.dp,
            title = { Text(stringResource(R.string.view_options_title)) },
            text = {
                Column {
                    VideoLayout.values()
                        .filter { it != VideoLayout.TETRIS || tetrisViewEnabled }
                        .forEach { layout ->
                        TextButton(
                            onClick = { currentLayout = layout },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            val layoutLabel = stringResource(
                                when (layout) {
                                    VideoLayout.GRID -> R.string.layout_grid
                                    VideoLayout.TETRIS -> R.string.layout_tetris
                                    else -> R.string.layout_list
                                }
                            )
                            Text(
                                text = if (layout == currentLayout) "✓ $layoutLabel" else layoutLabel,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }

                }
            },
            confirmButton = {
                TextButton(onClick = { viewOptionsOpen = false }) { Text(stringResource(R.string.action_done)) }
            },
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor = WatermelonGlass.surfaceFor(WatermelonGlass.Role.Modal),
            tonalElevation = 0.dp,
            title = { Text(stringResource(R.string.video_delete_dialog_title, selection.count), color = MaterialTheme.colorScheme.onSurface) },
            text = { Text(stringResource(R.string.video_delete_dialog_body), color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = {
                TextButton(onClick = {
                    val sender = viewModel.buildDeleteRequest(context.contentResolver)
                    showDeleteDialog = false
                    if (sender != null) {
                        deleteLauncher.launch(
                            androidx.activity.result.IntentSenderRequest.Builder(sender).build()
                        )
                    }
                }) {
                    Text(stringResource(R.string.action_delete), color = WatermelonColors.Error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.action_cancel), color = MaterialTheme.colorScheme.onSurface)
                }
            }
        )
    }

    if (showPlaylistPicker) {
        var showCreateField by remember { mutableStateOf(false) }
        var newPlaylistName by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showPlaylistPicker = false },
            containerColor = WatermelonGlass.surfaceFor(WatermelonGlass.Role.Modal),
            tonalElevation = 0.dp,
            title = { Text(stringResource(R.string.video_add_to_playlist_title), color = MaterialTheme.colorScheme.onSurface) },
            text = {
                Column {
                    if (showCreateField) {
                        TextField(
                            value = newPlaylistName,
                            onValueChange = { newPlaylistName = it },
                            placeholder = { Text(stringResource(R.string.playlists_name_placeholder)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(modifier = Modifier.fillMaxWidth()) {
                            TextButton(onClick = {
                                showCreateField = false
                                newPlaylistName = ""
                            }) {
                                Text(stringResource(R.string.action_cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                Text(stringResource(R.string.action_create), color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    } else {
                        TextButton(
                            onClick = { showCreateField = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.video_create_new_playlist), color = MaterialTheme.colorScheme.onSurface)
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
                            Text(playlist.name, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPlaylistPicker = false }) {
                    Text(stringResource(R.string.action_cancel), color = MaterialTheme.colorScheme.onSurface)
                }
            }
        )
    }
}
