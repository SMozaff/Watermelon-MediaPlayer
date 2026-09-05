package com.watermelon.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.ui.PlayerView
import androidx.navigation.NavDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.watermelon.app.managers.MediaJobController
import com.watermelon.app.managers.MiniPlayerController
import com.watermelon.app.managers.PiPManager
import com.watermelon.app.managers.SubtitleSyncManager
import com.watermelon.common.controller.PlaybackController
import com.watermelon.common.model.MediaItem
import com.watermelon.common.model.PlaybackState
import com.watermelon.common.model.UserIntent
import com.watermelon.common.repository.FolderRepository
import com.watermelon.common.repository.MediaRepository
import com.watermelon.common.repository.PlaylistRepository
import com.watermelon.playback.controller.PlaybackControllerImpl
import com.watermelon.playback.service.PlaybackConnection
import com.watermelon.storage.db.WatermelonDatabase
import com.watermelon.storage.prefs.FolderVisibilityStoreImpl
import com.watermelon.storage.repository.SubtitleSyncRepositoryImpl
import com.watermelon.subtitle.repository.SubtitleRepositoryImpl
import com.watermelon.ui.components.MiniPlayerBar
import com.watermelon.ui.components.KeepOrDeleteOriginalDialog
import com.watermelon.ui.components.WatermelonBottomNavigation
import com.watermelon.ui.components.activeMediaJobs
import com.watermelon.ui.screens.DesignSystemScreen
import com.watermelon.ui.screens.FolderBrowserScreen
import com.watermelon.ui.screens.FolderVisibilityScreen
import com.watermelon.ui.screens.PhonePlayerScreen
import com.watermelon.ui.screens.PlaylistsScreen
import com.watermelon.ui.screens.Routes
import com.watermelon.ui.screens.ScreenshotMode
import com.watermelon.ui.screens.SettingsScreen
import com.watermelon.ui.screens.VideoListScreen
import com.watermelon.ui.theme.WatermelonTheme
import com.watermelon.ui.viewmodel.FolderViewModel
import com.watermelon.ui.viewmodel.MediaJobsViewModel
import com.watermelon.ui.viewmodel.PlayerViewModel
import com.watermelon.ui.viewmodel.PlaylistViewModel
import com.watermelon.ui.viewmodel.VideoListViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@UnstableApi
@Composable
fun WatermelonNavHost(
    navController: NavHostController,
    playbackManager: PlaybackControllerManager,
    miniPlayerController: MiniPlayerController,
    pipManager: PiPManager,
    subtitleSyncManager: SubtitleSyncManager,
    mediaJobController: MediaJobController,
    repositories: Repositories,
    settingsStore: FolderVisibilityStoreImpl,
    prefs: android.content.SharedPreferences,
    pureDarkTheme: Boolean,
    forcedRtl: Boolean,
    onPlayerUriChanged: (String) -> Unit,
    onPipClick: (Activity, String) -> Unit,
    onDeleteClick: (String, String) -> Unit,
    onBrightnessChange: (Float) -> Unit,
    onFavouriteClick: (String, Boolean) -> Unit,
    onSkipToTrack: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var settingsState by remember {
        mutableStateOf(loadSettingsState(prefs, pureDarkTheme))
    }
    var showPremiumUpsell by remember { mutableStateOf(false) }
    var pendingExtractAudio by remember { mutableStateOf<Pair<String, String>?>(null) }
    var activeMp3JobId by rememberSaveable { mutableStateOf<String?>(null) }
    val mediaJobs = mediaJobController.mediaJobsViewModel.jobs.collectAsStateWithLifecycle()
    val activeMp3Job = mediaJobs.firstOrNull { it.id == activeMp3JobId }
    LaunchedEffect(activeMp3Job?.state) {
        if (activeMp3Job?.state is com.watermelon.mediatools.job.MediaJobState.Cancelled) {
            activeMp3JobId = null
        }
    }
    val savedBrightness = remember { prefs.getFloat("brightness", -1f) }
    val showJobsSheet = remember { mutableStateOf(false) }
    val reviewOriginalJobId = remember { mutableStateOf<String?>(null) }
    val globalOriginalDeletePending = remember { mutableStateOf(false) }

    NavHost(navController, startDestination = Routes.FOLDERS, modifier = modifier) {
        composable(Routes.FOLDERS) {
            val vm = remember { FolderViewModel(repositories.folderRepository, repositories.mediaRepository, repositories.playlistRepository, settingsStore) }
            val onFolderClick: (com.watermelon.common.model.FolderNode) -> Unit = { folder ->
                if (folder.isPlaylist) {
                    navController.navigate("videos/${Uri.encode(folder.playlistId!!)}?isPlaylist=true")
                } else {
                    navController.navigate("videos/${Uri.encode(folder.path)}?isPlaylist=false")
                }
            }
            val isTelevision = remember { com.watermelon.ui.screens.PlayerDeviceRouting.isTelevision(LocalContext.current) }
            if (isTelevision) {
                com.watermelon.ui.tv.TvFolderBrowserScreen(
                    viewModel = vm,
                    onFolderClick = onFolderClick,
                    onAllVideosClick = { navController.navigate(Routes.ALL_VIDEOS) },
                    onPlaylistsClick = { navController.navigate(Routes.PLAYLISTS) },
                    onSettingsClick = { navController.navigate(Routes.SETTINGS) }
                )
            } else {
                FolderBrowserScreen(
                    viewModel = vm,
                    onFolderClick = onFolderClick,
                    onSettingsClick = { navController.navigate(Routes.SETTINGS) },
                    layout = if (settingsState.gridDefault) com.watermelon.ui.screens.FolderLayout.GRID else com.watermelon.ui.screens.FolderLayout.LIST,
                    showDurations = settingsState.showDurations,
                    showFileSize = settingsState.showFileSize
                )
            }
        }
        composable(Routes.ALL_VIDEOS) {
            val vm = remember { VideoListViewModel(repositories.mediaRepository, "", true, repositories.folderRepository, settingsStore) }
            val isTelevision = remember { com.watermelon.ui.screens.PlayerDeviceRouting.isTelevision(LocalContext.current) }
            if (isTelevision) {
                com.watermelon.ui.tv.TvVideoListScreen(
                    viewModel = vm,
                    title = "All Videos",
                    onVideoClick = { item -> navController.navigate("player/${Uri.encode(item.uri)}") },
                    showThumbnails = settingsState.showThumbnails,
                    showDurations = settingsState.showDurations,
                    showFileSize = settingsState.showFileSize
                )
            } else {
                val playlists = repositories.playlistRepository.observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
                VideoListScreen(
                    viewModel = vm,
                    onVideoClick = { item -> navController.navigate("player/${Uri.encode(item.uri)}") },
                    onRefresh = vm::refresh,
                    availablePlaylists = playlists,
                    defaultGrid = settingsState.gridDefault,
                    showThumbnails = settingsState.showThumbnails,
                    showDurations = settingsState.showDurations,
                    showFileSize = settingsState.showFileSize,
                    folderName = "Videos",
                    onBack = { navController.popBackStack() },
                    onExtractAudio = { item -> pendingExtractAudio = item.uri to item.displayName },
                    onTrimVideo = { item -> navController.navigate("trim/${Uri.encode(item.uri)}/${Uri.encode(item.displayName)}/${item.durationMs}") },
                    onCompressVideo = { item -> navController.navigate("compress/${Uri.encode(item.uri)}/${Uri.encode(item.displayName)}") }
                )
            }
        }
        composable(Routes.PLAYLISTS) {
            val vm = remember { PlaylistViewModel(repositories.playlistRepository) }
            val isTelevision = remember { com.watermelon.ui.screens.PlayerDeviceRouting.isTelevision(LocalContext.current) }
            if (isTelevision) {
                com.watermelon.ui.tv.TvPlaylistsScreen(viewModel = vm, onPlaylistClick = { playlist -> navController.navigate("videos/${Uri.encode(playlist.id)}?isPlaylist=true") }, onSettingsClick = { navController.navigate(Routes.SETTINGS) }, continueWatchingEnabled = settingsState.continueWatchingEnabled)
            } else {
                PlaylistsScreen(viewModel = vm, onPlaylistClick = { playlist -> navController.navigate("videos/${Uri.encode(playlist.id)}?isPlaylist=true") }, continueWatchingEnabled = settingsState.continueWatchingEnabled)
            }
        }
        composable(Routes.FAVORITES) {
            LaunchedEffect(Unit) {
                navController.navigate("videos/${Uri.encode(com.watermelon.common.model.SystemPlaylist.ID_FAVOURITES)}?isPlaylist=true") { popUpTo(Routes.FAVORITES) { inclusive = true } }
            }
        }
        composable(route = "videos/{folderPath}?isPlaylist={isPlaylist}", arguments = listOf(navArgument("folderPath") { type = NavType.StringType }, navArgument("isPlaylist") { type = NavType.BoolType; defaultValue = false })) { backStackEntry ->
            val folderPath = Uri.decode(backStackEntry.arguments?.getString("folderPath").orEmpty())
            val isPlaylist = backStackEntry.arguments?.getBoolean("isPlaylist") ?: false
            val vm = remember(folderPath) { VideoListViewModel(repositories.mediaRepository, folderPath, repositories.playlistRepository, isPlaylist) }
            val screenTitle = if (isPlaylist) "Playlist" else folderPath.substringAfterLast("/")
            val isTelevision = remember { com.watermelon.ui.screens.PlayerDeviceRouting.isTelevision(LocalContext.current) }
            if (isTelevision) {
                com.watermelon.ui.tv.TvVideoListScreen(viewModel = vm, title = screenTitle, onVideoClick = { item -> navController.navigate("player/${Uri.encode(item.uri)}") }, showThumbnails = settingsState.showThumbnails, showDurations = settingsState.showDurations, showFileSize = settingsState.showFileSize)
            } else {
                val playlists = repositories.playlistRepository.observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
                VideoListScreen(viewModel = vm, onVideoClick = { item -> navController.navigate("player/${Uri.encode(item.uri)}") }, onRefresh = vm::refresh, availablePlaylists = playlists, defaultGrid = settingsState.gridDefault, showThumbnails = settingsState.showThumbnails, showDurations = settingsState.showDurations, showFileSize = settingsState.showFileSize, folderName = screenTitle, onBack = { navController.popBackStack() }, onExtractAudio = { item -> pendingExtractAudio = item.uri to item.displayName }, onTrimVideo = { item -> navController.navigate("trim/${Uri.encode(item.uri)}/${Uri.encode(item.displayName)}/${item.durationMs}") }, onCompressVideo = { item -> navController.navigate("compress/${Uri.encode(item.uri)}/${Uri.encode(item.displayName)}") })
            }
        }
        composable(route = "player/{uri}", arguments = listOf(navArgument("uri") { type = NavType.StringType })) { backStackEntry ->
            val mediaUri = Uri.decode(backStackEntry.arguments?.getString("uri").orEmpty())
            val controller = playbackManager.getMediaController()
            val pbController = playbackManager.getPlaybackController()
            if (controller == null || pbController == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { androidx.compose.material3.Text("Connecting…", style = MaterialTheme.typography.bodyLarge) }
            } else {
                val vm = remember(pbController) { PlayerViewModel(pbController) }
                LaunchedEffect(mediaUri) {
                    vm.onIntent(UserIntent.Play(mediaUri))
                    onPlayerUriChanged(mediaUri)
                }

                var isFavourite by remember(mediaUri) { mutableStateOf(false) }
                var playerMedia by remember(mediaUri) { mutableStateOf<MediaItem?>(null) }
                LaunchedEffect(mediaUri) {
                    isFavourite = runCatching { repositories.playlistRepository.isFavourite(mediaUri) }.getOrDefault(false)
                    playerMedia = runCatching { repositories.mediaRepository.getByUri(mediaUri) }.getOrNull()
                }

                val vhsController = com.watermelon.ui.player.rememberVhsEffectController(
                    shaderProvider = { intensity, timeSec, w, h ->
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            com.watermelon.app.VhsShader.build(com.watermelon.app.VhsCapability.detectTier(LocalContext.current as Activity), intensity, timeSec, w, h)
                        } else null
                    },
                    reverseSound = { active, speed ->
                        if (active) { com.watermelon.app.VhsReverseSound().start(speed); com.watermelon.app.VhsReverseSound().setSpeed(speed) }
                        else com.watermelon.app.VhsReverseSound().stop()
                    }
                )
                val mappedIntensity = when (settingsState.vhsIntensity) {
                    com.watermelon.ui.screens.VhsIntensity.OFF -> 0f
                    com.watermelon.ui.screens.VhsIntensity.LOW -> 0.35f
                    com.watermelon.ui.screens.VhsIntensity.MED -> 0.6f
                    com.watermelon.ui.screens.VhsIntensity.HIGH -> 1f
                }
                val isTelevision = remember { com.watermelon.ui.screens.PlayerDeviceRouting.isTelevision(LocalContext.current) }
                val subtitleTrackState = run {
                    var track by remember(mediaUri) { mutableStateOf<com.watermelon.common.model.ParsedSubtitle?>(null) }
                    LaunchedEffect(mediaUri) {
                        subtitleSyncManager.onNewMediaUri()
                        val discovered = discoverSubtitle(mediaUri, repositories.subtitleRepository)
                        track = discovered
                        if (discovered != null) {
                            val mediaItem = runCatching { repositories.mediaRepository.getByUri(mediaUri) }.getOrNull()
                            if (mediaItem != null) {
                                val fingerprint = subtitleSyncManager.subtitleFingerprintProvider.fingerprint(discovered)
                                val profile = runCatching { repositories.subtitleSyncRepository.get(mediaUri, mediaItem.fileSize, fingerprint) }.getOrNull()
                                subtitleSyncManager.subtitleOffsetMs = profile?.effectiveOffsetMs() ?: 0L
                            }
                        }
                    }
                    track
                }
                var durationMs by remember(mediaUri) { mutableStateOf(controller.duration.coerceAtLeast(0L)) }
                DisposableEffect(controller, mediaUri) {
                    val listener = object : Player.Listener {
                        override fun onEvents(player: Player, events: Player.Events) {
                            if (events.containsAny(Player.EVENT_TIMELINE_CHANGED, Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_PLAYBACK_STATE_CHANGED)) {
                                durationMs = player.duration.coerceAtLeast(0L)
                            }
                        }
                    }
                    controller.addListener(listener)
                    durationMs = controller.duration.coerceAtLeast(0L)
                    onDispose { controller.removeListener(listener) }
                }

                if (isTelevision) {
                    com.watermelon.ui.tv.TvPlayerScreen(
                        viewModel = vm,
                        durationMs = durationMs,
                        hasPreviousTrack = remember(mediaUri) { com.watermelon.ui.screens.PlaybackQueue.previousOf(mediaUri) != null },
                        hasNextTrack = remember(mediaUri) { com.watermelon.ui.screens.PlaybackQueue.nextOf(mediaUri) != null },
                        onSkipPrevious = { val prev = com.watermelon.ui.screens.PlaybackQueue.previousOf(mediaUri); if (prev != null) navController.navigate("player/${Uri.encode(prev)}") { popUpTo("player/{uri}") { inclusive = true } } else vm.onIntent(UserIntent.Seek(0L)) },
                        onSkipNext = { com.watermelon.ui.screens.PlaybackQueue.nextOf(mediaUri)?.let { next -> navController.navigate("player/${Uri.encode(next)}") { popUpTo("player/{uri}") { inclusive = true } } } },
                        onExit = { navController.popBackStack() },
                        subtitleTrack = subtitleTrackState,
                        subtitleStyle = settingsState.subtitleStyle,
                        subtitleOffsetMs = subtitleSyncManager.subtitleOffsetMs,
                        autoSyncEnabled = settingsState.autoSyncEnabled,
                        autoSyncStatus = subtitleSyncManager.autoSyncStatus,
                        onSubtitleNudge = { deltaMs -> subtitleTrackState?.let { track -> subtitleSyncManager.applySubtitleManualNudge(mediaUri, playerMedia, track, deltaMs) } },
                        onAutoSync = { subtitleTrackState?.let { track -> subtitleSyncManager.triggerSubtitleAutoSync(mediaUri, playerMedia, track, durationMs) } },
                        surface = { modifier -> AndroidView(modifier = modifier, factory = { ctx -> val view = android.view.LayoutInflater.from(ctx).inflate(R.layout.player_view_texture, null) as PlayerView; view.player = controller; view.useController = false; view }) }
                    )
                    return@composable
                }
                PhonePlayerScreen(
                    viewModel = vm,
                    vhs = vhsController,
                    vhsEnabled = settingsState.vhsEnabled,
                    vhsIntensity = mappedIntensity,
                    tunerSeekBarEnabled = settingsState.tunerSeekBarEnabled,
                    tunerSeekStepSeconds = settingsState.tunerSeekStepSeconds,
                    onTunerSeekBarEnabledChange = { enabled -> settingsState = settingsState.copy(tunerSeekBarEnabled = enabled); saveSettingsState(prefs, settingsState) },
                    durationMs = durationMs,
                    surface = { modifier -> AndroidView(modifier = modifier, factory = { ctx -> val view = android.view.LayoutInflater.from(ctx).inflate(R.layout.player_view_texture, null) as PlayerView; view.player = controller; view.useController = false; view }) },
                    onBack = { navController.popBackStack() },
                    uri = mediaUri,
                    mediaTitle = playerMedia?.displayName ?: "",
                    mediaContext = playerMedia?.parentFolder ?: "",
                    subtitleTrack = subtitleTrackState,
                    subtitleStyle = settingsState.subtitleStyle,
                    subtitleOffsetMs = subtitleSyncManager.subtitleOffsetMs,
                    autoSyncEnabled = settingsState.autoSyncEnabled,
                    autoSyncStatus = subtitleSyncManager.autoSyncStatus,
                    onSubtitleNudge = { deltaMs -> subtitleTrackState?.let { track -> subtitleSyncManager.applySubtitleManualNudge(mediaUri, playerMedia, track, deltaMs) } },
                    onAutoSync = { subtitleTrackState?.let { track -> subtitleSyncManager.triggerSubtitleAutoSync(mediaUri, playerMedia, track, durationMs) } },
                    screenshotMode = settingsState.screenshotMode,
                    initialBrightness = savedBrightness,
                    onPipClick = { miniPlayerController.play(mediaUri); onPipClick(LocalContext.current as Activity, mediaUri) },
                    onBackgroundClick = { enabled -> /* background play */ },
                    onBrightnessChange = onBrightnessChange,
                    onSkipToTrack = onSkipToTrack,
                    onShare = { /* share */ },
                    isFavourite = isFavourite,
                    onFavourite = { isFavourite = it; onFavouriteClick(mediaUri, it) },
                    onAddToPlaylist = { navController.navigate(Routes.PLAYLISTS) },
                    onDelete = { onDeleteClick(mediaUri, playerMedia?.displayName ?: "") },
                    onExtractAudio = { navController.navigate("extract/${Uri.encode(mediaUri)}") },
                    onTrimVideo = { navController.navigate("trim/${Uri.encode(mediaUri)}/${Uri.encode(playerMedia?.displayName ?: "")}/${durationMs}") },
                    onCompressVideo = { navController.navigate("compress/${Uri.encode(mediaUri)}/${Uri.encode(playerMedia?.displayName ?: "")}") },
                    onLockChanged = { /* lock changed */ },
                    isInPipMode = pipManager.isPiPActive,
                    modifier = Modifier
                )
            }
        }
        // Other routes abbreviated - trim, compress, extract, settings, folder visibility, design system
        composable(Routes.SETTINGS) {
            SettingsScreen(
                state = settingsState,
                onStateChange = { newState ->
                    settingsState = newState
                    saveSettingsState(prefs, newState)
                },
                onFolderVisibilityClick = { navController.navigate(Routes.FOLDER_VISIBILITY) },
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.FOLDER_VISIBILITY) {
            FolderVisibilityScreen(
                viewModel = remember { com.watermelon.ui.viewmodel.FolderVisibilityViewModel(settingsStore) },
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.DESIGN_SYSTEM) {
            DesignSystemScreen()
        }
        composable(route = "trim/{uri}/{displayName}/{durationMs}", arguments = listOf(
            navArgument("uri") { type = NavType.StringType },
            navArgument("displayName") { type = NavType.StringType },
            navArgument("durationMs") { type = NavType.LongType }
        )) { backStackEntry ->
            val uri = Uri.decode(backStackEntry.arguments?.getString("uri").orEmpty())
            val displayName = Uri.decode(backStackEntry.arguments?.getString("displayName").orEmpty())
            val durationMs = backStackEntry.arguments?.getLong("durationMs") ?: 0L
            com.watermelon.ui.screens.TrimScreen(
                uri = uri,
                displayName = displayName,
                durationMs = durationMs,
                mediaJobController = mediaJobController,
                onBack = { navController.popBackStack() }
            )
        }
        composable(route = "compress/{uri}/{displayName}", arguments = listOf(
            navArgument("uri") { type = NavType.StringType },
            navArgument("displayName") { type = NavType.StringType }
        )) { backStackEntry ->
            val uri = Uri.decode(backStackEntry.arguments?.getString("uri").orEmpty())
            val displayName = Uri.decode(backStackEntry.arguments?.getString("displayName").orEmpty())
            com.watermelon.ui.screens.CompressScreen(
                uri = uri,
                displayName = displayName,
                mediaJobController = mediaJobController,
                onBack = { navController.popBackStack() }
            )
        }
        composable(route = "extract/{uri}", arguments = listOf(navArgument("uri") { type = NavType.StringType })) { backStackEntry ->
            val uri = Uri.decode(backStackEntry.arguments?.getString("uri").orEmpty())
            com.watermelon.ui.screens.ExtractAudioScreen(
                uri = uri,
                mediaJobController = mediaJobController,
                onBack = { navController.popBackStack() }
            )
        }
    }
}

@UnstableApi
private fun discoverSubtitle(mediaUri: String, subtitleRepository: SubtitleRepositoryImpl): com.watermelon.common.model.ParsedSubtitle? {
    return subtitleRepository.findLocalSubtitle(mediaUri)
        ?: runCatching { subtitleRepository.downloadBestSubtitle(mediaUri) }.getOrNull()
}