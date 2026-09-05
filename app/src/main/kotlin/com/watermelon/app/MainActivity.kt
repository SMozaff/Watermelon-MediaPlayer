package com.watermelon.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.ui.PlayerView
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.watermelon.app.managers.IndexingManager
import com.watermelon.app.managers.LoggingManager
import com.watermelon.app.managers.MediaJobController
import com.watermelon.app.managers.MiniPlayerController
import com.watermelon.app.managers.PermissionManager
import com.watermelon.app.managers.PiPManager
import com.watermelon.app.managers.PlaybackControllerManager
import com.watermelon.app.managers.PlayerDeleteManager
import com.watermelon.app.managers.Repositories
import com.watermelon.app.managers.SubtitleSyncManager
import com.watermelon.app.navigation.WatermelonNavHost
import com.watermelon.common.controller.PlaybackController
import com.watermelon.common.model.PlaybackState
import com.watermelon.common.model.PlaybackMode
import com.watermelon.common.model.UserIntent
import com.watermelon.common.repository.FolderRepository
import com.watermelon.common.repository.MediaRepository
import com.watermelon.common.repository.PlaylistRepository
import com.watermelon.playback.controller.PlaybackControllerImpl
import com.watermelon.playback.service.PlaybackConnection
import com.watermelon.storage.db.WatermelonDatabase
import com.watermelon.storage.prefs.FolderVisibilityStoreImpl
import com.watermelon.storage.indexer.MediaStoreIndexer
import com.watermelon.storage.indexer.Phase1Sweep
import com.watermelon.storage.indexer.Phase2Extractor
import com.watermelon.storage.repository.FolderRepositoryImpl
import com.watermelon.storage.repository.MediaRepositoryImpl
import com.watermelon.storage.repository.PlaylistRepositoryImpl
import com.watermelon.subtitle.repository.SubtitleRepositoryImpl
import com.watermelon.ui.components.WatermelonBottomNavigation
import com.watermelon.ui.components.BottomNavItem
import com.watermelon.ui.components.activeMediaJobs
import com.watermelon.ui.screens.DesignSystemScreen
import com.watermelon.ui.screens.FolderBrowserScreen
import com.watermelon.ui.screens.FolderVisibilityScreen
import com.watermelon.ui.screens.PhonePlayerScreen
import com.watermelon.ui.screens.PlaylistsScreen
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
class MainActivity : ComponentActivity() {

    // Core managers
    private val prefs: SharedPreferences by lazy { getSharedPreferences("watermelon_prefs", Context.MODE_PRIVATE) }
    private val permissionManager by lazy { PermissionManager(this) }
    private val database by lazy { WatermelonDatabase(applicationContext) }
    private val repositories by lazy { Repositories(applicationContext, database, lifecycleScope) }
    private val indexingManager by lazy { IndexingManager(applicationContext, database, lifecycleScope) }
    private val playbackManager by lazy { PlaybackControllerManager(applicationContext, lifecycleScope, database) }
    private val miniPlayerController by lazy { MiniPlayerController(lifecycleScope) }
    private val pipManager by lazy {
        PiPManager(
            context = applicationContext,
            audioManager = getSystemService(AUDIO_SERVICE) as android.media.AudioManager,
            playbackControllerProvider = { playbackManager.getPlaybackController() },
            mediaControllerProvider = { playbackManager.getMediaController() },
        )
    }
    private val subtitleSyncManager by lazy { SubtitleSyncManager(applicationContext, lifecycleScope) }
    private val mediaJobController by lazy {
        MediaJobController(
            activity = this,
            mediaJobManager = (application as WatermelonApplication).mediaJobManager,
            outputFileStore = (application as WatermelonApplication).outputFileStore,
            scope = lifecycleScope,
        )
    }
    private val playerDeleteManager by lazy { PlayerDeleteManager(this, contentResolver, lifecycleScope) }
    private val loggingManager by lazy { LoggingManager() }

    // UI state
    private var pureDarkTheme by mutableStateOf(false)
    private var forcedRtl by mutableStateOf(false)

    // Activity result launchers (must be registered in onCreate)
    private lateinit var permissionLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>
    private lateinit var playerDeleteLauncher: androidx.activity.result.ActivityResultLauncher<androidx.activity.result.IntentSenderRequest>

    override fun onCreate(savedInstanceState: Bundle?) {
        loggingManager.installFileLogger(this)
        loggingManager.installCrashLogger()
        com.watermelon.common.util.FileLogger.i("App", "onCreate — app starting")
        super.onCreate(savedInstanceState)

        // Initialize launchers
        permissionLauncher = permissionManager.permissionLauncher
        playerDeleteManager.getPlayerDeleteLauncher().also { playerDeleteLauncher = it }

        // Initialize original file deleter
        mediaJobController.getOriginalFileDeleter()

        // Restore saved volume
        val audioManager = getSystemService(AUDIO_SERVICE) as android.media.AudioManager
        val savedVolume = prefs.getInt("volume", -1)
        if (savedVolume >= 0) {
            audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, savedVolume, 0)
        }

        // Check permissions
        if (permissionManager.checkAndRequestPermissions()) {
            indexingManager.triggerInitialIndex { error ->
                com.watermelon.common.util.FileLogger.e("App", error)
            }
        }

        addObserver(playbackManager)
        addObserver(miniPlayerController)
        addObserver(pipManager)

        setContent {
            pureDarkTheme = prefs.getBoolean("pure_dark", true)
            forcedRtl = prefs.getBoolean("forced_rtl", false)

            WatermelonTheme(darkTheme = pureDarkTheme, forceRtl = forcedRtl) {
                val navController = rememberNavController()
                val currentDestination = navController.currentBackStackEntryAsState().value?.destination

                val onPlayerRoute = currentDestination?.route == "player/{uri}"
                val showMiniPlayer = miniPlayerController.miniPlayerUri != null && !onPlayerRoute && !pipManager.isPiPActive
                val mediaJobs = mediaJobController.mediaJobsViewModel.jobs.collectAsStateWithLifecycle()
                val activeMediaJobs = remember(mediaJobs) { mediaJobs.activeMediaJobs() }
                var showJobsSheet by remember { mutableStateOf(false) }
                var reviewOriginalJobId by remember { mutableStateOf<String?>(null) }
                var globalOriginalDeletePending by remember { mutableStateOf(false) }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        if (shouldShowBottomBar(currentDestination)) {
                            WatermelonBottomNavigation(
                                navController = navController,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                    }
                ) { innerPadding ->
                    Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                        // Mini player bar
                        val miniUri = miniPlayerController.miniPlayerUri
                        val controller = playbackManager.getMediaController()
                        val pbController = playbackManager.getPlaybackController()
                        if (miniUri != null && controller != null && pbController != null) {
                            val position by pbController.currentPositionMs.collectAsStateWithLifecycle()
                            val playbackState by pbController.playbackState.collectAsStateWithLifecycle()
                            var miniDurationMs by remember(miniUri) {
                                mutableStateOf(controller.duration.coerceAtLeast(0L))
                            }
                            DisposableEffect(controller, miniUri) {
                                val listener = object : Player.Listener {
                                    override fun onEvents(player: Player, events: Player.Events) {
                                        if (events.containsAny(
                                                Player.EVENT_TIMELINE_CHANGED,
                                                Player.EVENT_MEDIA_ITEM_TRANSITION,
                                                Player.EVENT_PLAYBACK_STATE_CHANGED
                                            )
                                        ) {
                                            miniDurationMs = player.duration.coerceAtLeast(0L)
                                        }
                                    }
                                }
                                controller.addListener(listener)
                                onDispose { controller.removeListener(listener) }
                            }
                            val miniTitle = miniPlayerController.getCurrentTitle() ?: ""
                            com.watermelon.ui.components.MiniPlayerBar(
                                visible = showMiniPlayer,
                                title = miniTitle,
                                isPlaying = playbackState == PlaybackState.PLAYING,
                                isMuted = miniPlayerController.isMuted,
                                progressFraction = if (miniDurationMs > 0)
                                    (position.toFloat() / miniDurationMs.toFloat()).coerceIn(0f, 1f) else 0f,
                                hasNext = miniPlayerController.hasNext(),
                                hasPrevious = miniPlayerController.hasPrevious(),
                                videoSurface = { mod ->
                                    AndroidView(
                                        modifier = mod,
                                        factory = { ctx ->
                                            val view = android.view.LayoutInflater.from(ctx)
                                                .inflate(R.layout.player_view_texture, null) as PlayerView
                                            view.player = controller
                                            view.useController = false
                                            view
                                        }
                                    )
                                },
                                onRestore = {
                                    navController.navigate("player/${Uri.encode(miniUri)}") {
                                        popUpTo("player/{uri}") { inclusive = true }
                                    }
                                },
                                onPlayPause = {
                                    if (playbackState == PlaybackState.PLAYING) pbController.pause()
                                    else pbController.resume()
                                },
                                onNext = { miniPlayerController.next() },
                                onPrevious = { miniPlayerController.previous() },
                                onMuteToggle = { miniPlayerController.toggleMute() },
                                onClose = { miniPlayerController.close() }
                            )
                        }

                        if (activeMediaJobs.isNotEmpty()) {
                            com.watermelon.ui.components.MediaJobsBar(
                                activeJobs = activeMediaJobs,
                                onOpenJobs = { showJobsSheet = true },
                            )
                        }

                        if (permissionManager.permissionsGranted) {
                            WatermelonNavHost(
                                navController = navController,
                                playbackManager = playbackManager,
                                miniPlayerController = miniPlayerController,
                                pipManager = pipManager,
                                subtitleSyncManager = subtitleSyncManager,
                                mediaJobController = mediaJobController,
                                repositories = repositories,
                                settingsStore = repositories.settingsStoreInstance,
                                prefs = prefs,
                                pureDarkTheme = pureDarkTheme,
                                forcedRtl = forcedRtl,
                                onPlayerUriChanged = { uri -> miniPlayerController.play(uri) },
                                onPipClick = { activity, uri -> miniPlayerController.play(uri); pipManager.enterPiPMode(activity) },
                                onDeleteClick = { uri, name -> playerDeleteManager.requestPlayerDelete(uri, name) },
                                onBrightnessChange = { brightness -> /* handled by player */ },
                                onFavouriteClick = { uri, fav -> repositories.playlistRepository.setFavourite(uri, fav) },
                                onSkipToTrack = { nextUri -> navController.navigate("player/${Uri.encode(nextUri)}") { popUpTo("player/{uri}") { inclusive = true } } },
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            PermissionPrompt(onRequest = { permissionLauncher.launch(permissionManager.requiredPermissions) })
                        }
                    }
                }

                if (showJobsSheet) {
                    com.watermelon.ui.components.MediaJobsSheet(
                        jobs = mediaJobs,
                        onCancel = { job -> mediaJobController.mediaJobsViewModel.cancel(job.id) },
                        onDismissJob = { job -> mediaJobController.mediaJobsViewModel.dismiss(job.id) },
                        onOpenResult = { job ->
                            val completed = job.state as? com.watermelon.mediatools.job.MediaJobState.Completed
                            val outputUri = completed?.outputUri
                            if (outputUri == null) return@MediaJobsSheet
                            runCatching {
                                val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(Uri.parse(outputUri), contentResolver.getType(Uri.parse(outputUri)))
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                startActivity(viewIntent)
                            }.onFailure {
                                android.widget.Toast.makeText(
                                    this@MainActivity,
                                    "Could not open this output on the device",
                                    android.widget.Toast.LENGTH_SHORT,
                                ).show()
                            }
                        },
                        onOpenSettings = {
                            showJobsSheet = false
                            navController.navigate(Routes.SETTINGS) { launchSingleTop = true }
                        },
                        onReviewOriginal = { job ->
                            showJobsSheet = false
                            reviewOriginalJobId = job.id
                        },
                        onDismiss = { showJobsSheet = false },
                    )
                }

                val reviewOriginalJob = mediaJobs.find { it.id == reviewOriginalJobId }
                val reviewCompleted = reviewOriginalJob?.state as? com.watermelon.mediatools.job.MediaJobState.Completed
                LaunchedEffect(reviewOriginalJobId, reviewCompleted?.awaitingOriginalFileDecision) {
                    if (reviewOriginalJobId != null &&
                        (reviewCompleted == null || !reviewCompleted.awaitingOriginalFileDecision)
                    ) {
                        reviewOriginalJobId = null
                        globalOriginalDeletePending = false
                    }
                }
                if (reviewOriginalJob != null && reviewCompleted?.awaitingOriginalFileDecision == true) {
                    com.watermelon.ui.components.KeepOrDeleteOriginalDialog(
                        originalFileName = com.watermelon.ui.components.jobSourceLabel(reviewOriginalJob),
                        outputFileName = Uri.decode(reviewCompleted.outputUri).substringAfterLast('/'),
                        isTrim = reviewOriginalJob.type == com.watermelon.mediatools.job.MediaJobType.TRIM,
                        isPendingSystemConsent = globalOriginalDeletePending,
                        actualTrimRangeMs = reviewCompleted.actualTrimRangeMs,
                        compressionSizeBytes = reviewOriginalJob.sourceSizeBytes?.let { originalSize ->
                            reviewCompleted.outputSizeBytes?.let { outputSize -> originalSize to outputSize }
                        },
                        onKeepOriginal = {
                            mediaJobController.mediaJobsViewModel.resolveOriginalFileDecision(
                                reviewOriginalJob.id,
                                deleteOriginal = false,
                                contentResolver = contentResolver,
                            )
                        },
                        onDeleteOriginal = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                globalOriginalDeletePending = true
                                mediaJobController.getOriginalFileDeleter().requestDelete(
                                    reviewOriginalJob.id,
                                    Uri.parse(reviewOriginalJob.inputUri),
                                    contentResolver,
                                )
                            } else {
                                mediaJobController.mediaJobsViewModel.resolveOriginalFileDecision(
                                    reviewOriginalJob.id,
                                    deleteOriginal = true,
                                    contentResolver = contentResolver,
                                )
                            }
                        },
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        pipManager.registerReceiver()
    }

    override fun onStop() {
        super.onStop()
        pipManager.unregisterReceiver()
        val audioManager = getSystemService(AUDIO_SERVICE) as android.media.AudioManager
        prefs.edit().putInt("volume", audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)).apply()
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        pipManager.onUserLeaveHint()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        pipManager.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
    }

    private fun shouldShowBottomBar(destination: NavDestination?): Boolean {
        if (com.watermelon.ui.screens.PlayerDeviceRouting.isTelevision(this)) return false
        return destination?.route != "player/{uri}" && !pipManager.isPiPActive
    }

    @Composable
    private fun PermissionPrompt(onRequest: () -> Unit) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            androidx.compose.material3.Button(onClick = onRequest) {
                androidx.compose.material3.Text("Grant storage permission")
            }
        }
    }

    companion object {
        const val VOLUME_KEY = "volume"
    }
}