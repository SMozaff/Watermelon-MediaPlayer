package com.watermelon.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.watermelon.common.model.PlaybackMode
import com.watermelon.common.model.UserIntent
import com.watermelon.common.util.FileLogger
import com.watermelon.ui.screens.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The application navigation graph, extracted from MainActivity in Phase 2b.
 *
 * Every screen route lives here; the Activity shell keeps only the Scaffold, bottom
 * bar, mini-player host, and permission prompt around it. Screens reach shared
 * infrastructure (repositories, media-tool engines, the playback controllers, the
 * subtitle-sync engine, and the delete coordinator) through [activity], which owns
 * the dependency graph — no second wiring layer, no parameter-plumbing wall.
 *
 * Route-scoped state that must survive nothing more than its screen (premium
 * upsell, MP3 bitrate dialog, playlist picker) is kept local to this composable;
 * state that belongs to the shell (mini-player session URI, playback mode) stays
 * on the Activity.
 */
@OptIn(UnstableApi::class)
@Composable
internal fun AppNavGraph(
    activity: MainActivity,
    navController: NavHostController,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onForcedRtlChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val deps = activity.deps
    val subtitleSync = deps.subtitleSyncEngine
    val playerDelete = activity.playerDelete

    var settingsState by remember {
        mutableStateOf(loadSettingsState(activity.prefs))
    }
    // Premium gating is temporarily disabled everywhere (product decision: full-featured
    // for now, limitations reintroduced later) -- nothing currently sets this to true,
    // so PremiumUpsellDialog never renders. Left wired rather than deleted so
    // re-enabling gating later doesn't require re-plumbing this state + the dialog.
    var showPremiumUpsell by remember { mutableStateOf(false) }
    // Extract Audio now asks for a bitrate first (product request) -- this holds the
    // (uri, displayName) of whichever video was tapped while Mp3BitrateDialog is open.
    var pendingExtractAudio by remember { mutableStateOf<Pair<String, String>?>(null) }
    // Kept by id rather than by a snapshot of MediaJob so the sheet follows every
    // progress/state update emitted by the application-scoped MediaJobManager.
    var activeMp3JobId by rememberSaveable { mutableStateOf<String?>(null) }
    // Playlist picker requested from the player screen: which video to file away.
    var showPlayerPlaylistPicker by remember { mutableStateOf(false) }
    var playerPlaylistUri by remember { mutableStateOf<String?>(null) }

    val mediaJobs by deps.mediaJobsViewModel.jobs.collectAsStateWithLifecycle()
    val activeMp3Job = mediaJobs.firstOrNull { it.id == activeMp3JobId }
    LaunchedEffect(activeMp3Job?.state) {
        if (activeMp3Job?.state is com.watermelon.mediatools.job.MediaJobState.Cancelled) {
            activeMp3JobId = null
        }
    }

    val savedBrightness = remember { activity.prefs.getFloat("brightness", -1f) }

    NavHost(
        navController = navController,
        startDestination = Routes.FOLDERS,
        modifier = modifier
    ) {
        composable(Routes.FOLDERS) {
            // viewModel() (scoped to this NavBackStackEntry) rather than remember{}: the
            // ViewModel survives configuration changes, is restored across process death,
            // and is properly cleared — viewModelScope cancelled, collect jobs stopped —
            // the moment this entry is popped. remember-constructed ViewModels were never
            // cleared, so their viewModelScope coroutines ran until process death.
            val vm = viewModel {
                com.watermelon.ui.viewmodel.FolderViewModel(
                    deps.folderRepository, deps.mediaRepository, deps.playlistRepository, deps.settingsStore
                )
            }
            val onFolderClick: (com.watermelon.common.model.FolderNode) -> Unit = { folder ->
                if (folder.isPlaylist) {
                    navController.navigate("videos/${Uri.encode(folder.playlistId!!)}?isPlaylist=true")
                } else {
                    navController.navigate("videos/${Uri.encode(folder.path)}?isPlaylist=false")
                }
            }
            val isTelevision = remember {
                com.watermelon.ui.screens.PlayerDeviceRouting.isTelevision(activity)
            }
            if (isTelevision) {
                // D-pad-navigable row list with visible focus rings — no grid/sort/filter
                // chrome, which are touch affordances that don't map to a 10-foot D-pad UI.
                // Also the TV app's root nav surface (see shouldShowBottomBar) — All Videos
                // and Playlists are pinned rows here rather than reached via a bottom bar.
                com.watermelon.ui.tv.TvFolderBrowserScreen(
                    viewModel = vm,
                    onFolderClick = onFolderClick,
                    onAllVideosClick = { navController.navigate(Routes.ALL_VIDEOS) },
                    onPlaylistsClick = { navController.navigate(Routes.PLAYLISTS) },
                    onSettingsClick = { navController.navigate(Routes.SETTINGS) }
                )
            } else {
                com.watermelon.ui.screens.FolderBrowserScreen(
                    viewModel = vm,
                    onFolderClick = onFolderClick,
                    onSettingsClick = { navController.navigate(Routes.SETTINGS) },
                    layout = if (settingsState.gridDefault) {
                        com.watermelon.ui.screens.FolderLayout.GRID
                    } else {
                        com.watermelon.ui.screens.FolderLayout.LIST
                    },
                    showDurations = settingsState.showDurations,
                    showFileSize = settingsState.showFileSize
                )
            }
        }
        composable(Routes.ALL_VIDEOS) {
            val vm = viewModel {
                com.watermelon.ui.viewmodel.VideoListViewModel(
                    mediaRepository = deps.mediaRepository,
                    folderPath = "",
                    isAllVideos = true,
                    folderRepository = deps.folderRepository,
                    folderVisibilityStore = deps.settingsStore
                )
            }
            val isTelevision = remember {
                com.watermelon.ui.screens.PlayerDeviceRouting.isTelevision(activity)
            }
            if (isTelevision) {
                com.watermelon.ui.tv.TvVideoListScreen(
                    viewModel = vm,
                    title = stringResource(com.watermelon.ui.R.string.all_videos_title),
                    onVideoClick = { item -> navController.navigate("player/${Uri.encode(item.uri)}") },
                    showThumbnails = settingsState.showThumbnails,
                    showDurations = settingsState.showDurations,
                    showFileSize = settingsState.showFileSize
                )
            } else {
                val playlists by deps.playlistRepository.observeAll()
                    .collectAsStateWithLifecycle(initialValue = emptyList())
                com.watermelon.ui.screens.VideoListScreen(
                    viewModel = vm,
                    onVideoClick = { item -> navController.navigate("player/${Uri.encode(item.uri)}") },
                    onRefresh = vm::refresh,
                    availablePlaylists = playlists,
                    defaultGrid = settingsState.gridDefault,
                    tetrisViewEnabled = settingsState.tetrisViewEnabled,
                    showThumbnails = settingsState.showThumbnails,
                    showDurations = settingsState.showDurations,
                    showFileSize = settingsState.showFileSize,
                    folderName = stringResource(com.watermelon.ui.R.string.nav_videos),
                    onBack = { navController.popBackStack() },
                    onExtractAudio = { item ->
                        // Premium gating temporarily disabled -- everything unlocked
                        // for now, gating comes back deliberately later. Bitrate is
                        // now chosen via Mp3BitrateDialog before the job actually starts
                        // (see pendingExtractAudio below).
                        pendingExtractAudio = item.uri to item.displayName
                    },
                    onTrimVideo = { item ->
                        navController.navigate(
                            "trim/${Uri.encode(item.uri)}/${Uri.encode(item.displayName)}/${item.durationMs}"
                        )
                    },
                    onCompressVideo = { item ->
                        navController.navigate(
                            "compress/${Uri.encode(item.uri)}/${Uri.encode(item.displayName)}"
                        )
                    }
                )
            }
        }
        composable(Routes.PLAYLISTS) {
            val vm = viewModel { com.watermelon.ui.viewmodel.PlaylistViewModel(deps.playlistRepository) }
            val isTelevision = remember {
                com.watermelon.ui.screens.PlayerDeviceRouting.isTelevision(activity)
            }
            if (isTelevision) {
                com.watermelon.ui.tv.TvPlaylistsScreen(
                    viewModel = vm,
                    onPlaylistClick = { playlist ->
                        navController.navigate("videos/${Uri.encode(playlist.id)}?isPlaylist=true")
                    },
                    onSettingsClick = { navController.navigate(Routes.SETTINGS) },
                    continueWatchingEnabled = settingsState.continueWatchingEnabled
                )
            } else {
                com.watermelon.ui.screens.PlaylistsScreen(
                    viewModel = vm,
                    onPlaylistClick = { playlist ->
                        navController.navigate("videos/${Uri.encode(playlist.id)}?isPlaylist=true")
                    },
                    continueWatchingEnabled = settingsState.continueWatchingEnabled
                )
            }
        }
        composable(Routes.FAVORITES) {
            LaunchedEffect(Unit) {
                navController.navigate(
                    "videos/${Uri.encode(com.watermelon.common.model.SystemPlaylist.ID_FAVOURITES)}?isPlaylist=true"
                ) {
                    popUpTo(Routes.FAVORITES) { inclusive = true }
                }
            }
        }
        composable(
            route = "videos/{folderPath}?isPlaylist={isPlaylist}",
            arguments = listOf(
                navArgument("folderPath") { type = NavType.StringType },
                navArgument("isPlaylist") { type = NavType.BoolType; defaultValue = false }
            )
        ) { backStackEntry ->
            val folderPath = Uri.decode(backStackEntry.arguments?.getString("folderPath").orEmpty())
            val isPlaylist = backStackEntry.arguments?.getBoolean("isPlaylist") ?: false
            // Keyed by folderPath: each folder/playlist entry gets its own store anyway,
            // but the key keeps instances distinct even if a future navigation change ever
            // shares one store across argument variants.
            val vm = viewModel(key = folderPath) {
                com.watermelon.ui.viewmodel.VideoListViewModel(
                    deps.mediaRepository, folderPath, deps.playlistRepository, isPlaylist
                )
            }
            val screenTitle = if (isPlaylist) {
                stringResource(com.watermelon.ui.R.string.playlist_title)
            } else {
                folderPath.substringAfterLast("/")
            }
            val isTelevision = remember {
                com.watermelon.ui.screens.PlayerDeviceRouting.isTelevision(activity)
            }
            if (isTelevision) {
                com.watermelon.ui.tv.TvVideoListScreen(
                    viewModel = vm,
                    title = screenTitle,
                    onVideoClick = { item -> navController.navigate("player/${Uri.encode(item.uri)}") },
                    showThumbnails = settingsState.showThumbnails,
                    showDurations = settingsState.showDurations,
                    showFileSize = settingsState.showFileSize
                )
            } else {
                val playlists by deps.playlistRepository.observeAll()
                    .collectAsStateWithLifecycle(initialValue = emptyList())
                com.watermelon.ui.screens.VideoListScreen(
                    viewModel = vm,
                    onVideoClick = { item -> navController.navigate("player/${Uri.encode(item.uri)}") },
                    onRefresh = vm::refresh,
                    availablePlaylists = playlists,
                    defaultGrid = settingsState.gridDefault,
                    tetrisViewEnabled = settingsState.tetrisViewEnabled,
                    showThumbnails = settingsState.showThumbnails,
                    showDurations = settingsState.showDurations,
                    showFileSize = settingsState.showFileSize,
                    folderName = screenTitle,
                    onBack = { navController.popBackStack() },
                    onExtractAudio = { item ->
                        // Premium gating temporarily disabled -- everything unlocked
                        // for now, gating comes back deliberately later. Bitrate is
                        // now chosen via Mp3BitrateDialog before the job actually starts
                        // (see pendingExtractAudio below).
                        pendingExtractAudio = item.uri to item.displayName
                    },
                    onTrimVideo = { item ->
                        navController.navigate(
                            "trim/${Uri.encode(item.uri)}/${Uri.encode(item.displayName)}/${item.durationMs}"
                        )
                    },
                    onCompressVideo = { item ->
                        navController.navigate(
                            "compress/${Uri.encode(item.uri)}/${Uri.encode(item.displayName)}"
                        )
                    }
                )
            }
        }
        composable(
            route = Routes.PLAYER,
            arguments = listOf(navArgument("uri") { type = NavType.StringType })
        ) { backStackEntry ->
            val mediaUri = Uri.decode(backStackEntry.arguments?.getString("uri").orEmpty())
            val controller = activity.mediaController
            val pbController = activity.playbackController
            if (controller == null || pbController == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.connecting), style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                // PlayerViewModel stays keyed to the live MediaController instance rather
                // than back-stack-scoped: the controller is released and recreated by the
                // Activity when playback stops, and a retained ViewModel would keep a stale
                // controller alive across that boundary. The same session-scoping rationale
                // keeps Trim/Compress ViewModels remember-constructed — each belongs to a
                // single job on a single file, not to the destination.
                val vm = remember(pbController) {
                    com.watermelon.ui.viewmodel.PlayerViewModel(pbController)
                }
                LaunchedEffect(mediaUri) {
                    vm.onIntent(UserIntent.Play(mediaUri))
                    activity.miniPlayerUri = mediaUri
                }

                var isFavourite by remember(mediaUri) { mutableStateOf(false) }
                var playerMedia by remember(mediaUri) {
                    mutableStateOf<com.watermelon.common.model.MediaItem?>(null)
                }
                LaunchedEffect(mediaUri) {
                    isFavourite = runCatching { deps.playlistRepository.isFavourite(mediaUri) }.getOrDefault(false)
                    playerMedia = runCatching { deps.mediaRepository.getByUri(mediaUri) }.getOrNull()
                }

                val vhsController = com.watermelon.ui.player.rememberVhsEffectController(
                    shaderProvider = { intensity, timeSec, w, h ->
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            VhsShader.build(
                                tier = VhsCapability.detectTier(activity),
                                intensity = intensity, time = timeSec, width = w, height = h
                            )
                        } else null
                    },
                    reverseSound = { active, speed ->
                        val sound = deps.vhsReverseSound
                        if (active) { sound.start(speed); sound.setSpeed(speed) }
                        else sound.stop()
                    }
                )
                val mappedIntensity = when (settingsState.vhsIntensity) {
                    com.watermelon.ui.screens.VhsIntensity.OFF -> 0f
                    com.watermelon.ui.screens.VhsIntensity.LOW -> 0.35f
                    com.watermelon.ui.screens.VhsIntensity.MED -> 0.6f
                    com.watermelon.ui.screens.VhsIntensity.HIGH -> 1f
                }
                val isTelevision = remember {
                    com.watermelon.ui.screens.PlayerDeviceRouting.isTelevision(activity)
                }
                var subtitleTrackState by remember(mediaUri) {
                    mutableStateOf<com.watermelon.common.model.ParsedSubtitle?>(null)
                }
                LaunchedEffect(mediaUri) {
                    subtitleSync.beginSession()
                    val discovered = activity.discoverSubtitle(mediaUri)
                    subtitleTrackState = discovered
                    if (discovered != null) {
                        val mediaItem = runCatching { deps.mediaRepository.getByUri(mediaUri) }.getOrNull()
                        if (mediaItem != null) {
                            subtitleSync.restoreOffset(
                                subtitleSync.storedOffsetFor(mediaUri, mediaItem.fileSize, discovered) ?: 0L
                            )
                        }
                    }
                }
                // controller.duration is a plain Media3 Player getter, not something
                // Compose observes — reading it directly in the composable body means it
                // only updates when something else happens to trigger recomposition, with
                // no guarantee that happens exactly when Media3 resolves the real duration.
                // In practice this mostly "worked" because position ticks frequently and
                // incidentally forces recomposition, EXCEPT right after opening a video:
                // duration is C.TIME_UNSET (0 here) until playback reaches STATE_READY, so
                // any seek attempted in that window — on either seek bar — silently
                // coerced to position 0. A real Player.Listener fixes this by updating
                // state exactly when Media3 says duration actually changed.
                var durationMs by remember(mediaUri) { mutableStateOf(controller.duration.coerceAtLeast(0L)) }
                DisposableEffect(controller, mediaUri) {
                    val listener = object : Player.Listener {
                        override fun onEvents(player: Player, events: Player.Events) {
                            if (events.containsAny(
                                    Player.EVENT_TIMELINE_CHANGED,
                                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                                    Player.EVENT_PLAYBACK_STATE_CHANGED
                                )
                            ) {
                                durationMs = player.duration.coerceAtLeast(0L)
                            }
                        }
                    }
                    controller.addListener(listener)
                    durationMs = controller.duration.coerceAtLeast(0L)
                    onDispose { controller.removeListener(listener) }
                }

                if (isTelevision) {
                    // TV is a separate composition (Manifest §8) — D-pad-first, no touch
                    // gestures, no VHS shader/PiP/rotation. Shares only the playback core
                    // and video surface with the phone screen.
                    com.watermelon.ui.tv.TvPlayerScreen(
                        viewModel = vm,
                        durationMs = durationMs,
                        hasPreviousTrack = remember(mediaUri) {
                            com.watermelon.ui.screens.PlaybackQueue.previousOf(mediaUri) != null
                        },
                        hasNextTrack = remember(mediaUri) {
                            com.watermelon.ui.screens.PlaybackQueue.nextOf(mediaUri) != null
                        },
                        onSkipPrevious = {
                            val prev = com.watermelon.ui.screens.PlaybackQueue.previousOf(mediaUri)
                            if (prev != null) {
                                navController.navigate("player/${Uri.encode(prev)}") {
                                    popUpTo(Routes.PLAYER) { inclusive = true }
                                }
                            } else {
                                vm.onIntent(UserIntent.Seek(0L))
                            }
                        },
                        onSkipNext = {
                            com.watermelon.ui.screens.PlaybackQueue.nextOf(mediaUri)?.let { next ->
                                navController.navigate("player/${Uri.encode(next)}") {
                                    popUpTo(Routes.PLAYER) { inclusive = true }
                                }
                            }
                        },
                        onExit = { navController.popBackStack() },
                        subtitleTrack = subtitleTrackState,
                        subtitleStyle = settingsState.subtitleStyle,
                        subtitleOffsetMs = subtitleSync.offsetMs,
                        autoSyncEnabled = settingsState.autoSyncEnabled,
                        autoSyncStatus = subtitleSync.status,
                        onSubtitleNudge = { deltaMs ->
                            subtitleTrackState?.let { track ->
                                subtitleSync.nudge(mediaUri, playerMedia, track, deltaMs)
                            }
                        },
                        onAutoSync = {
                            subtitleTrackState?.let { track ->
                                subtitleSync.trigger(mediaUri, playerMedia, track, durationMs)
                            }
                        },
                        surface = { modifier ->
                            AndroidView(
                                modifier = modifier,
                                factory = { ctx ->
                                    val view = android.view.LayoutInflater.from(ctx)
                                        .inflate(R.layout.player_view_texture, null) as PlayerView
                                    view.player = controller
                                    view.useController = false
                                    view
                                }
                            )
                        }
                    )
                    return@composable
                }
                com.watermelon.ui.screens.PhonePlayerScreen(
                    viewModel = vm,
                    vhs = vhsController,
                    vhsEnabled = settingsState.vhsEnabled,
                    vhsIntensity = mappedIntensity,
                    tunerSeekBarEnabled = settingsState.tunerSeekBarEnabled,
                    tunerSeekStepSeconds = settingsState.tunerSeekStepSeconds,
                    onTunerSeekBarEnabledChange = { enabled ->
                        settingsState = settingsState.copy(tunerSeekBarEnabled = enabled)
                        saveSettingsState(activity.prefs, settingsState)
                    },
                    isInPipMode = activity.isPiPActive,
                    onBack = { navController.popBackStack() },
                    durationMs = durationMs,
                    subtitleTrack = subtitleTrackState,
                    subtitleStyle = settingsState.subtitleStyle,
                    subtitleOffsetMs = subtitleSync.offsetMs,
                    autoSyncEnabled = settingsState.autoSyncEnabled,
                    autoSyncStatus = subtitleSync.status,
                    onSubtitleNudge = { deltaMs ->
                        subtitleTrackState?.let { track ->
                            subtitleSync.nudge(mediaUri, playerMedia, track, deltaMs)
                        }
                    },
                    onAutoSync = {
                        subtitleTrackState?.let { track ->
                            subtitleSync.trigger(mediaUri, playerMedia, track, durationMs)
                        }
                    },
                    uri = mediaUri,
                    mediaTitle = playerMedia?.displayName ?: mediaUri.substringAfterLast('/'),
                    mediaContext = playerMedia?.parentFolder
                        ?.substringAfterLast('/')
                        .orEmpty(),
                    screenshotMode = settingsState.screenshotMode,
                    initialBrightness = savedBrightness,
                    onPipClick = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        {
                            FileLogger.i("PiP", "onPipClick tapped")
                            activity.playbackMode = PlaybackMode.PIP
                            activity.enterPipMode()
                        }
                    } else {
                        null
                    },
                    onBackgroundClick = { enabled ->
                        activity.playbackMode = if (enabled) PlaybackMode.BACKGROUND else PlaybackMode.NORMAL
                    },
                    onBrightnessChange = { brightness ->
                        activity.prefs.edit().putFloat("brightness", brightness).apply()
                    },
                    onSkipToTrack = { newUri ->
                        activity.lifecycleScope.launch {
                            runCatching { deps.mediaRepository.markAsPlayed(newUri) }
                        }
                        navController.navigate("player/${Uri.encode(newUri)}") {
                            popUpTo(Routes.PLAYER) { inclusive = true }
                        }
                    },
                    onLockChanged = { locked ->
                        runCatching {
                            if (locked) activity.startLockTask() else activity.stopLockTask()
                        }.onFailure {
                            FileLogger.i("Lock", "lock task not available: ${it.message}")
                        }
                    },
                    onShare = {
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "video/*"
                            putExtra(Intent.EXTRA_STREAM, Uri.parse(mediaUri))
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        activity.startActivity(
                            Intent.createChooser(sendIntent, activity.getString(R.string.share_chooser_title))
                        )
                    },
                    isFavourite = isFavourite,
                    onFavourite = { wantFavourite ->
                        isFavourite = wantFavourite
                        activity.lifecycleScope.launch {
                            val ok = runCatching {
                                if (wantFavourite) deps.playlistRepository.addToFavourites(mediaUri)
                                else deps.playlistRepository.removeFromFavourites(mediaUri)
                            }.isSuccess
                            if (!ok) isFavourite = !wantFavourite
                        }
                    },
                    onAddToPlaylist = {
                        playerPlaylistUri = mediaUri
                        showPlayerPlaylistPicker = true
                    },
                    onDelete = {
                        activity.lifecycleScope.launch {
                            val displayName = runCatching {
                                deps.mediaRepository.getByUri(mediaUri)?.displayName
                            }.getOrNull() ?: mediaUri.substringAfterLast('/')
                            playerDelete.pendingTarget = PlayerDeleteTarget(mediaUri, displayName)
                            playerDelete.outcome = null
                            playerDelete.showDialog = true
                        }
                    },
                    onExtractAudio = {
                        // Premium gating temporarily disabled -- see VideoListScreen's
                        // identical note above.
                        activity.lifecycleScope.launch {
                            val displayName = runCatching { deps.mediaRepository.getByUri(mediaUri)?.displayName }
                                .getOrNull() ?: mediaUri.substringAfterLast('/')
                            pendingExtractAudio = mediaUri to displayName
                        }
                    },
                    onTrimVideo = {
                        activity.lifecycleScope.launch {
                            val displayName = runCatching { deps.mediaRepository.getByUri(mediaUri)?.displayName }
                                .getOrNull() ?: mediaUri.substringAfterLast('/')
                            navController.navigate(
                                "trim/${Uri.encode(mediaUri)}/${Uri.encode(displayName)}/$durationMs"
                            )
                        }
                    },
                    onCompressVideo = {
                        activity.lifecycleScope.launch {
                            val displayName = runCatching { deps.mediaRepository.getByUri(mediaUri)?.displayName }
                                .getOrNull() ?: mediaUri.substringAfterLast('/')
                            navController.navigate(
                                "compress/${Uri.encode(mediaUri)}/${Uri.encode(displayName)}"
                            )
                        }
                    },
                    subtitleRepository = deps.subtitleRepository,
                    onSubtitleLoaded = { parsedSubtitle ->
                        subtitleTrackState = parsedSubtitle
                        subtitleSync.beginSession()
                    },
                    mediaItem = playerMedia,
                    surface = { modifier ->
                        AndroidView(
                            modifier = modifier,
                            factory = { ctx ->
                                val view = android.view.LayoutInflater.from(ctx)
                                    .inflate(R.layout.player_view_texture, null) as PlayerView
                                view.player = controller
                                view.useController = false
                                view
                            }
                        )
                    }
                )
            }
        }
        composable(
            route = "trim/{uri}/{displayName}/{durationMs}",
            arguments = listOf(
                navArgument("uri") { type = NavType.StringType },
                navArgument("displayName") { type = NavType.StringType },
                navArgument("durationMs") { type = NavType.LongType },
            )
        ) { backStackEntry ->
            val mediaUri = Uri.decode(backStackEntry.arguments?.getString("uri").orEmpty())
            val displayName = Uri.decode(backStackEntry.arguments?.getString("displayName").orEmpty())
            val durationMs = backStackEntry.arguments?.getLong("durationMs") ?: 0L
            val controller = activity.mediaController
            val pbController = activity.playbackController
            if (controller == null || pbController == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.connecting), style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                val playerVm = remember(pbController) {
                    com.watermelon.ui.viewmodel.PlayerViewModel(pbController)
                }
                LaunchedEffect(mediaUri) { playerVm.onIntent(UserIntent.Play(mediaUri)) }
                val trimVm = remember {
                    com.watermelon.ui.viewmodel.TrimViewModel(
                        deps.mediaJobManager, deps.videoTrimmer, deps.keyframeIndexer, deps.filmstripExtractor
                    )
                }

                com.watermelon.ui.screens.TrimScreen(
                    playerViewModel = playerVm,
                    trimViewModel = trimVm,
                    mediaJobsViewModel = deps.mediaJobsViewModel,
                    contentResolver = activity.contentResolver,
                    originalFileDeleter = activity.originalFileDeleter,
                    inputUri = Uri.parse(mediaUri),
                    originalDisplayName = displayName,
                    durationMs = durationMs,
                    isPremiumUnlocked = settingsState.isPremiumUnlocked,
                    onRequestUpsell = { showPremiumUpsell = true },
                    onBack = { navController.popBackStack() },
                    surface = { modifier ->
                        AndroidView(
                            modifier = modifier,
                            factory = { ctx ->
                                val view = android.view.LayoutInflater.from(ctx)
                                    .inflate(R.layout.player_view_texture, null) as PlayerView
                                view.player = controller
                                view.useController = false
                                view
                            }
                        )
                    }
                )
            }
        }
        composable(
            route = "compress/{uri}/{displayName}",
            arguments = listOf(
                navArgument("uri") { type = NavType.StringType },
                navArgument("displayName") { type = NavType.StringType },
            )
        ) { backStackEntry ->
            val mediaUri = Uri.decode(backStackEntry.arguments?.getString("uri").orEmpty())
            val displayName = Uri.decode(backStackEntry.arguments?.getString("displayName").orEmpty())
            val compressVm = remember {
                com.watermelon.ui.viewmodel.CompressViewModel(deps.mediaJobManager, deps.videoCompressor)
            }

            com.watermelon.ui.screens.CompressScreen(
                compressViewModel = compressVm,
                mediaJobsViewModel = deps.mediaJobsViewModel,
                contentResolver = activity.contentResolver,
                originalFileDeleter = activity.originalFileDeleter,
                inputUri = Uri.parse(mediaUri),
                originalDisplayName = displayName,
                isPremiumUnlocked = settingsState.isPremiumUnlocked,
                onRequestUpsell = { showPremiumUpsell = true },
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.SETTINGS) {
            val isTelevision = remember {
                com.watermelon.ui.screens.PlayerDeviceRouting.isTelevision(activity)
            }
            val onSettingsStateChange: (com.watermelon.ui.screens.SettingsState) -> Unit = { newState ->
                settingsState = newState
                saveSettingsState(activity.prefs, settingsState)
                if (newState.themeMode != themeMode) {
                    onThemeModeChange(newState.themeMode)
                }
                onForcedRtlChange(newState.forcedRtl)
            }
            if (isTelevision) {
                com.watermelon.ui.tv.TvSettingsScreen(
                    state = settingsState,
                    onStateChange = onSettingsStateChange,
                    onFolderVisibilityClick = { navController.navigate(Routes.FOLDER_VISIBILITY) },
                    onBack = { navController.popBackStack() }
                )
            } else {
                com.watermelon.ui.screens.SettingsScreen(
                    state = settingsState,
                    onStateChange = onSettingsStateChange,
                    onFolderVisibilityClick = { navController.navigate(Routes.FOLDER_VISIBILITY) },
                    onBack = { navController.popBackStack() }
                )
            }
        }
        composable(Routes.FOLDER_VISIBILITY) {
            val vm = viewModel {
                com.watermelon.ui.viewmodel.FolderViewModel(
                    deps.folderRepository, deps.mediaRepository, deps.playlistRepository, deps.settingsStore
                )
            }
            val folders by vm.allFoldersForSettings.collectAsStateWithLifecycle()
            val folderRows = folders
                .filter { !it.first.isPlaylist }
                .map { (node, visible) -> Triple(node.path, node.displayName, visible) }
            val isTelevision = remember {
                com.watermelon.ui.screens.PlayerDeviceRouting.isTelevision(activity)
            }
            if (isTelevision) {
                com.watermelon.ui.tv.TvFolderVisibilityScreen(
                    folders = folderRows,
                    onToggle = { path, visible -> vm.setFolderHidden(path, !visible) },
                    onBack = { navController.popBackStack() }
                )
            } else {
                com.watermelon.ui.screens.FolderVisibilityScreen(
                    folders = folderRows,
                    onToggle = { path, visible -> vm.setFolderHidden(path, !visible) },
                    onBack = { navController.popBackStack() }
                )
            }
        }
        // NEW: Design System route
        composable(Routes.DESIGN_SYSTEM) {
            com.watermelon.ui.screens.DesignSystemScreen(onBack = { navController.popBackStack() })
        }
    }

    if (showPremiumUpsell) {
        com.watermelon.ui.components.PremiumUpsellDialog(
            onDismiss = { showPremiumUpsell = false }
        )
    }

    pendingExtractAudio?.let { (uri, displayName) ->
        com.watermelon.ui.components.Mp3BitrateDialog(
            onSelect = { preset ->
                deps.mediaJobManager.extractAudio(deps.audioExtractor, uri, displayName, preset.kbps)
                pendingExtractAudio = null
            },
            onDismiss = { pendingExtractAudio = null }
        )
    }

    val playlistUri = playerPlaylistUri
    if (showPlayerPlaylistPicker && playlistUri != null) {
        val playlists by deps.playlistRepository.observeAll()
            .collectAsStateWithLifecycle(initialValue = emptyList())
        com.watermelon.ui.components.PlayerPlaylistPickerDialog(
            playlists = playlists,
            onSelect = { playlist ->
                showPlayerPlaylistPicker = false
                playerPlaylistUri = null
                activity.lifecycleScope.launch {
                    runCatching {
                        val alreadyPresent = deps.playlistRepository.observeVideos(playlist.id)
                            .first()
                            .any { it.uri == playlistUri }
                        if (!alreadyPresent) {
                            deps.playlistRepository.addToPlaylist(playlist.id, playlistUri)
                        }
                        alreadyPresent
                    }.onSuccess { alreadyPresent ->
                        android.widget.Toast.makeText(
                            activity,
                            if (alreadyPresent) {
                                activity.getString(R.string.toast_already_in_playlist, playlist.name)
                            } else {
                                activity.getString(R.string.toast_added_to_playlist, playlist.name)
                            },
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }.onFailure {
                            android.widget.Toast.makeText(
                                activity,
                                activity.getString(R.string.toast_add_to_playlist_failed, playlist.name),
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                }
            },
            onCreate = { name ->
                showPlayerPlaylistPicker = false
                playerPlaylistUri = null
                activity.lifecycleScope.launch {
                    runCatching {
                        val id = deps.playlistRepository.createPlaylist(name)
                        deps.playlistRepository.addToPlaylist(id, playlistUri)
                    }.onSuccess {
                        android.widget.Toast.makeText(
                            activity,
                            activity.getString(R.string.toast_playlist_created, name),
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }.onFailure {
                        android.widget.Toast.makeText(
                            activity,
                            activity.getString(R.string.toast_playlist_create_failed, name),
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            },
            onDismiss = {
                showPlayerPlaylistPicker = false
                playerPlaylistUri = null
            }
        )
    }

    val deleteTarget = playerDelete.pendingTarget
    if (playerDelete.showDialog && deleteTarget != null) {
        AlertDialog(
            containerColor = com.watermelon.ui.theme.WatermelonGlass.surfaceFor(
                com.watermelon.ui.theme.WatermelonGlass.Role.Modal
            ),
            tonalElevation = 0.dp,
            onDismissRequest = {
                playerDelete.showDialog = false
                playerDelete.pendingTarget = null
            },
            title = { Text(stringResource(R.string.player_delete_title)) },
            text = {
                Text(
                    stringResource(R.string.player_delete_body, deleteTarget.displayName)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    playerDelete.showDialog = false
                    playerDelete.request(deleteTarget)
                }) {
                    Text(stringResource(com.watermelon.ui.R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    playerDelete.showDialog = false
                    playerDelete.pendingTarget = null
                }) {
                    Text(stringResource(com.watermelon.ui.R.string.action_cancel))
                }
            }
        )
    }

    playerDelete.outcome?.let { outcome ->
        LaunchedEffect(outcome) {
            when (outcome) {
                is PlayerDeleteOutcome.Deleted -> {
                    activity.playbackController?.pause()
                    if (activity.miniPlayerUri == outcome.target.uri) activity.miniPlayerUri = null
                    runCatching { deps.mediaRepository.refreshIndex() }
                    android.widget.Toast.makeText(
                        activity,
                        activity.getString(R.string.toast_deleted, outcome.target.displayName),
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                    if (navController.currentDestination?.route == Routes.PLAYER) {
                        navController.popBackStack()
                    }
                }
                is PlayerDeleteOutcome.Cancelled -> {
                    android.widget.Toast.makeText(
                        activity,
                        activity.getString(R.string.toast_deletion_cancelled),
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
                is PlayerDeleteOutcome.Failed -> {
                    android.widget.Toast.makeText(
                        activity,
                        activity.getString(R.string.toast_delete_failed, outcome.reason),
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            }
            playerDelete.outcome = null
        }
    }
}
