package com.watermelon.app

import android.Manifest
import android.app.PictureInPictureParams
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.ui.PlayerView
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
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
import com.watermelon.ui.viewmodel.PlayerViewModel
import com.watermelon.ui.viewmodel.PlaylistViewModel
import com.watermelon.ui.viewmodel.VideoListViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

import kotlin.runCatching

@UnstableApi
class MainActivity : ComponentActivity() {

    private val prefs: SharedPreferences by lazy {
        getSharedPreferences("watermelon_prefs", Context.MODE_PRIVATE)
    }

    private val database by lazy { WatermelonDatabase(applicationContext) }
    private val settingsStore by lazy { FolderVisibilityStoreImpl(applicationContext) }

    // media-tools: reuse the singleton MediaJobManager/OutputFileStore owned by
    // WatermelonApplication (see that class's doc — no DI framework in this app, so this
    // Activity just reads the Application-scoped instances rather than constructing new ones).
    private val mediaJobManager by lazy {
        (application as com.wamwatermelon.app.WatermelonApplication).mediaJobManager
    }
    private val outputFileStore by lazy {
        (application as com.watermelon.app.WatermelonApplication).outputFileStore
    }
    private val audioExtractor by lazy {
        com.watermelon.mediatools.engine.AudioExtractor(applicationContext)
    }
    private val videoTrimmer by lazy {
        com.watermelon.mediatools.engine.VideoTrimmer(applicationContext, outputFileStore)
    }
    private val videoCompressor by lazy {
        com.watermelon.mediatools.engine.VideoCompressor(applicationContext, outputFileStore)
    }
    private val keyframeIndexer by lazy {
        com.watermelon.mediatools.engine.KeyframeIndexer(applicationContext)
    }
    private val filmstripExtractor by lazy {
        com.watermelon.mediatools.engine.FilmstripExtractor(applicationContext)
    }
    private val mediaJobsViewModel by lazy {
        com.watermelon.ui.viewmodel.MediaJobsViewModel(mediaJobManager)
    }

    private lateinit var originalFileDeleter: com.watermelon.mediatools.output.OriginalFileDeleter
    private lateinit var playerDeleteLauncher: androidx.activity.result.ActivityResultLauncher<
        androidx.activity.result.IntentSenderRequest
    >

    private data class PlayerDeleteTarget(val uri: String, val displayName: String)

    private sealed interface PlayerDeleteOutcome {
        data class Deleted(val target: PlayerDeleteTarget) : PlayerDeleteOutcome
        data class Cancelled(val target: PlayerDeleteTarget) : PlayerDeleteOutcome
        data class Failed(val target: PlayerDeleteTarget, val reason: String) : PlayerDeleteOutcome
    }

    private var pendingPlayerDelete by mutableStateOf<PlayerDeleteTarget?>(null)
    private var showPlayerDeleteDialog by mutableStateOf(false)
    private var showPlayerPlaylistPicker by mutableStateOf(false)
    private var playerPlaylistUri by mutableStateOf<String?>(null)
    private var playerDeleteOutcome by mutableStateOf<PlayerDeleteOutcome?>(null)

    private val vhsReverseSound by lazy { VhsReverseSound() }

    // Submodule: subtitle handling
    private val subtitleRepository by lazy {
        com.watermelon.app.subtitle.SubtitleModuleKt.subtitleRepository
    }
    private val subtitleSyncSession by mutableStateOf(0L)
    private var subtitleOffsetMs by mutableStateOf(0L)
    private var autoSyncStatus by mutableStateOf(com.watermelon.common.subtitle.sync.SyncStatus.IDLE)

    // Player module
    private val playbackConnection by lazy { com.watermelon.app.PlayerModuleKt.playbackConnection }
    private var mediaController by mutableStateOf<MediaController?>(null)
    private var playbackController: PlaybackController? = null

    // PiP module
    private val pipActionReceiver = com.watermelon.app.PiPModuleKt.pipActionReceiver

    // Bottom nav state
    private var pureDarkTheme by remember {
        mutableStateOf(prefs.getBoolean("pure_dark", true))
    }
    private var forcedRtl by remember {
        mutableStateOf(prefs.getBoolean("forced_rtl", false))
    }
    private var savedBrightness by remember { prefs.getFloat("brightness", -1f) }

    override fun onCreate(savedInstanceState: Bundle?) {
        installFileLogger()
        installCrashLogger()
        com.watermelon.common.util.FileLogger.i("App", "onCreate — app starting")
        super.onCreate(savedInstanceState)

        originalFileDeleter = com.watermelon.mediatools.output.OriginalFileDeleter(this) { jobId, deleted ->
            mediaJobManager.resolveOriginalFileDecision(jobId, deleteOriginal = deleted, contentResolver)
        }
        playerDeleteLauncher = registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult()
        ) { result ->
            val target = pendingPlayerDelete
            pendingPlayerDelete = null
            if (target == null) {
                com.watermelon.common.util.FileLogger.e("Delete", "player delete result arrived with no pending target")
                return@registerForActivityResult
            }
            playerDeleteOutcome = if (result.resultCode == RESULT_OK) {
                PlayerDeleteOutcome.Deleted(target)
            } else {
                PlayerDeleteOutcome.Cancelled(target)
            }
        }

        val savedVolume = prefs.getInt("volume", -1)
        if (savedVolume >= 0) {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, savedVolume, 0)
        }

        // Register PiP broadcast receiver
        val pipFilter = IntentFilter().apply {
            addAction(com.watermelon.app.PiPModuleKt.PiPReceiver_ACTION_PLAY_PAUSE)
            addAction(com.watermelon.app.PiPModuleKt.PiPReceiver_ACTION_MUTE)
            addAction(com.watermelon.app.PiPModuleKt.PiPReceiver_ACTION_PREV)
            addAction(com.watermelon.app.PiPModuleKt.PiPReceiver_ACTION_NEXT)
            addAction(com.watermelon.app.PiPModuleKt.PiPReceiver_ACTION_REWIND)
            addAction(com.watermelon.app.PiPModuleKt.PiPReceiver_ACTION_FORWARD)
        }
        ContextCompat.registerReceiver(
            this, pipActionReceiver, pipFilter, ContextCompat.RECEIVER_NOT_EXPORTED
        )

        permissionsGranted = requiredPermissions.all { perm ->
            ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED
        }
        if (permissionsGranted) triggerInitialIndex()
        else launchPermissionRequest()

        setContent {
            WatermelonTheme(darkTheme = pureDarkTheme, forceRtl = forcedRtl) {
                val navController = rememberNavController()

                // Track current destination for bottom navigation
                val currentDestination = navController.currentBackStackEntryAsState().value?.destination

                // Mini-player is visible only when something is loaded AND we're not already
                // looking at the full player screen — showing both at once would be redundant
                // and would also mean two PlayerViews racing to attach to the same Player
                // (Media3 only keeps the most-recently-attached view live).
                val onPlayerRoute = currentDestination?.route == "player/{uri}"
                val showMiniPlayer = miniPlayerUri != null && !onPlayerRoute && !isPiPActive
                val mediaJobs by mediaJobsViewModel.jobs.collectAsStateWithLifecycle()
                val activeMediaJobs = remember(mediaJobs) {
                    mediaJobs.activeMediaJobs()
                }
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
                        val miniUri = miniPlayerUri
                        val controller = mediaController
                        val pbController = playbackController
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
                                        // Natural end with an empty queue closes the mini-player
                                        // entirely, matching the spec's dismissal conditions
                                        // (Close / restore-tap / natural end with empty queue).
                                        if (events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED) &&
                                            player.playbackState == Player.STATE_ENDED &&
                                            com.watermelon.ui.screens.PlaybackQueue.nextOf(miniUri) == null
                                        ) {
                                            miniPlayerUri = null
                                        }
                                    }
                                }
                                controller.addListener(listener)
                                onDispose { controller.removeListener(listener) }
                            }
                            val miniTitle = remember(miniUri) {
                                Uri.decode(miniUri).substringAfterLast('/')
                            }
                            com.watermelon.ui.components.MiniPlayerBar(
                                visible = showMiniPlayer,
                                title = miniTitle,
                                isPlaying = playbackState == PlaybackState.PLAYING,
                                isMuted = isMuted,
                                progressFraction = if (miniDurationMs > 0)
                                    (position.toFloat() / miniDurationMs.toFloat()).coerceIn(0f, 1f) else 0f,
                                hasNext = com.watermelon.ui.screens.PlaybackQueue.nextOf(miniUri) != null,
                                hasPrevious = com.watermelon.ui.screens.PlaybackQueue.previousOf(miniUri) != null,
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
                                onNext = {
                                    com.watermelon.ui.screens.PlaybackQueue.nextOf(miniUri)?.let { next ->
                                        miniPlayerUri = next
                                        pbController.play(next)
                                    }
                                },
                                onPrevious = {
                                    com.watermelon.ui.screens.PlaybackQueue.previousOf(miniUri)?.let { prev ->
                                        miniPlayerUri = prev
                                        pbController.play(prev)
                                    }
                                },
                                onMuteToggle = {
                                    isMuted = !isMuted
                                    controller.volume = if (isMuted) 0f else 1f
                                },
                                onClose = {
                                    pbController.pause()
                                    miniPlayerUri = null
                                }
                            )
                        }
                        if (activeMediaJobs.isNotEmpty()) {
                            com.watermelon.ui.components.MediaJobsBar(
                                activeJobs = activeMediaJobs,
                                onOpenJobs = { showJobsSheet = true },
                            )
                        }
                        if (permissionsGranted) {
                            WatermelonNavHost(
                                navController = navController,
                                pureDarkTheme = pureDarkTheme,
                                onPureDarkThemeChange = { enabled ->
                                    pureDarkTheme = enabled
                                    prefs.edit().putBoolean("pure_dark", enabled).apply()
                                },
                                onForcedRtlChange = { enabled -> forcedRtl = enabled },
                                onPlayerUriChanged = { uri -> miniPlayerUri = uri },
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            PermissionPrompt(onRequest = { launchPermissionRequest() })
                        }
                    }
                }
                if (showJobsSheet) {
                    com.watermelon.ui.components.MediaJobsSheet(
                        jobs = mediaJobs,
                        onCancel = { job -> mediaJobsViewModel.cancel(job.id) },
                        onDismissJob = { job -> mediaJobsViewModel.dismiss(job.id) },
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
                            mediaJobsViewModel.resolveOriginalFileDecision(
                                reviewOriginalJob.id,
                                deleteOriginal = false,
                                contentResolver = contentResolver,
                            )
                        },
                        onDeleteOriginal = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                globalOriginalDeletePending = true
                                originalFileDeleter.requestDelete(
                                    reviewOriginalJob.id,
                                    Uri.parse(reviewOriginalJob.inputUri),
                                    contentResolver,
                                )
                            } else {
                                mediaJobsViewModel.resolveOriginalFileDecision(
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

    /**
     * Determine if bottom navigation bar should be shown.
     * Hide for full-screen player and PiP mode, and hide unconditionally on TV — TV has no
     * touch-oriented bottom nav at all; its root/home surface is TvFolderBrowserScreen's own
     * pinned Settings/All Videos/Playlists rows (D-pad rows, not a bottom bar), reached at the
     * same Routes.FOLDERS start destination every device uses.
     */
    private fun shouldShowBottomBar(destination: androidx.navigation.NavDestination?): Boolean {
        if (com.watermelon.ui.screens.PlayerDeviceRouting.isTelevision(this)) return false
        return destination?.route != "player/{uri}" && !isPiPActive
    }
}