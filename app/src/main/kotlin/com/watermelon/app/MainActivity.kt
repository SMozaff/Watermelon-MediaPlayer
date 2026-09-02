package com.watermelon.app

import android.Manifest
import android.app.PendingIntent
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
import androidx.annotation.RequiresApi
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
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
    //
    // OutputFileStore reads the same watermelon_prefs keys written by SettingsPersistence,
    // so exports use the destination currently shown in the Settings screen.
    private val mediaJobManager by lazy {
        (application as com.watermelon.app.WatermelonApplication).mediaJobManager
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
    // Backs TrimScreen's redesigned UX (filmstrip + haptic keyframe snapping) -- see
    // TrimViewModel's class doc.
    private val keyframeIndexer by lazy {
        com.watermelon.mediatools.engine.KeyframeIndexer(applicationContext)
    }
    private val filmstripExtractor by lazy {
        com.watermelon.mediatools.engine.FilmstripExtractor(applicationContext)
    }
    private val mediaJobsViewModel by lazy {
        com.watermelon.ui.viewmodel.MediaJobsViewModel(mediaJobManager)
    }
    // Constructed in onCreate, not lazily -- registerForActivityResult must be called
    // unconditionally during Activity initialization (Android's own constraint, not a
    // choice made here), so a `by lazy` field wouldn't reliably register in time.
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
    private val subtitleRepository by lazy {
        com.watermelon.subtitle.repository.SubtitleRepositoryImpl(applicationContext)
    }
    private val phase1Sweep by lazy { Phase1Sweep(contentResolver) }
    private val indexer by lazy {
        MediaStoreIndexer(
            phase1Sweep = phase1Sweep,
            phase2Extractor = Phase2Extractor(applicationContext, database),
            mediaUriProvider = { phase1Sweep.lastSweepUris() }
        )
    }
    private val mediaRepository: MediaRepository by lazy { MediaRepositoryImpl(database, indexer) }
    private val folderRepository: FolderRepository by lazy { FolderRepositoryImpl(indexer) }
    private val playlistRepository: PlaylistRepository by lazy {
        PlaylistRepositoryImpl(database, mediaRepository, settingsStore)
    }
    private val playbackPositionRepository by lazy {
        com.watermelon.storage.repository.PlaybackPositionRepositoryImpl(database)
    }
    private val subtitleFingerprintProvider by lazy {
        com.watermelon.subtitle.sync.SubtitleFingerprintProvider()
    }
    private val subtitleSyncRepository by lazy {
        com.watermelon.storage.repository.SubtitleSyncRepositoryImpl(database)
    }
    private val subtitleSyncCoordinator by lazy {
        com.watermelon.subtitle.sync.SubtitleSyncCoordinator(
            repository = subtitleSyncRepository,
            probeSelector = com.watermelon.subtitle.sync.SubtitleProbeSelectorImpl(),
            subtitleActivityBuilder = com.watermelon.subtitle.sync.SubtitleActivityBuilderImpl(),
            speechProbeSource = com.watermelon.mediatools.subtitle.sync.SparseSpeechProbeSource(applicationContext),
            correlator = com.watermelon.subtitle.sync.ActivityCorrelatorImpl(),
            consensus = com.watermelon.subtitle.sync.OffsetConsensus(),
        )
    }

    // Auto Sync UI state, owned here (not inside the player composable) so a result computed
    // for one video can never be mistaken for another: [subtitleSyncSession] is bumped every
    // time the player's mediaUri changes, and triggerSubtitleAutoSync captures the session id
    // at launch time and discards its result if the session has since moved on -- e.g. the
    // user backed out to the library and opened a different video while the probe/correlation
    // coroutine was still running.
    private var subtitleSyncSession = 0L
    private var subtitleOffsetMs by mutableStateOf(0L)
    private var autoSyncStatus by mutableStateOf(com.watermelon.common.subtitle.sync.SyncStatus.IDLE)

    /**
     * Runs Auto Sync for the currently open [subtitle] against [mediaUri]/[mediaItem], applying
     * the result only if playback hasn't moved on to a different video in the meantime.
     */
    private fun triggerSubtitleAutoSync(
        mediaUri: String,
        mediaItem: com.watermelon.common.model.MediaItem?,
        subtitle: com.watermelon.common.model.ParsedSubtitle,
        durationMs: Long,
    ) {
        val sessionAtStart = subtitleSyncSession
        autoSyncStatus = com.watermelon.common.subtitle.sync.SyncStatus.ANALYZING
        lifecycleScope.launch {
            val fingerprint = subtitleFingerprintProvider.fingerprint(subtitle)
            val result = runCatching {
                subtitleSyncCoordinator.synchronize(
                    com.watermelon.common.subtitle.sync.SubtitleSyncRequest(
                        mediaId = mediaUri,
                        mediaUri = mediaUri,
                        mediaFileSize = mediaItem?.fileSize ?: 0L,
                        mediaDurationMs = durationMs,
                        subtitleFingerprint = fingerprint,
                        subtitleLanguage = null,
                        subtitle = subtitle,
                        playbackSessionId = sessionAtStart,
                    )
                )
            }.getOrElse { com.watermelon.common.subtitle.sync.SubtitleSyncResult.Failed(it.message ?: "error") }

            // Stale: the user has since opened a different video. Discard silently.
            if (subtitleSyncSession != sessionAtStart) return@launch

            when (result) {
                is com.watermelon.common.subtitle.sync.SubtitleSyncResult.Synchronized -> {
                    autoSyncStatus = com.watermelon.common.subtitle.sync.SyncStatus.SYNCHRONIZED
                    subtitleOffsetMs = when (val model = result.model) {
                        is com.watermelon.common.subtitle.sync.SubtitleSyncModel.Offset -> model.offsetMs
                        is com.watermelon.common.subtitle.sync.SubtitleSyncModel.Affine -> model.offsetMs
                        else -> subtitleOffsetMs
                    }
                }
                is com.watermelon.common.subtitle.sync.SubtitleSyncResult.ComplexDriftDetected ->
                    autoSyncStatus = com.watermelon.common.subtitle.sync.SyncStatus.COMPLEX_DRIFT
                is com.watermelon.common.subtitle.sync.SubtitleSyncResult.LowConfidence ->
                    autoSyncStatus = com.watermelon.common.subtitle.sync.SyncStatus.LOW_CONFIDENCE
                com.watermelon.common.subtitle.sync.SubtitleSyncResult.Unsupported ->
                    autoSyncStatus = com.watermelon.common.subtitle.sync.SyncStatus.UNSUPPORTED
                com.watermelon.common.subtitle.sync.SubtitleSyncResult.ResourceDenied ->
                    autoSyncStatus = com.watermelon.common.subtitle.sync.SyncStatus.RESOURCE_DENIED
                com.watermelon.common.subtitle.sync.SubtitleSyncResult.Cancelled ->
                    autoSyncStatus = com.watermelon.common.subtitle.sync.SyncStatus.IDLE
                is com.watermelon.common.subtitle.sync.SubtitleSyncResult.Failed ->
                    autoSyncStatus = com.watermelon.common.subtitle.sync.SyncStatus.FAILED
            }
        }
    }

    /** Persists a manual subtitle offset nudge and reflects it immediately in the UI. */
    private fun applySubtitleManualNudge(
        mediaUri: String,
        mediaItem: com.watermelon.common.model.MediaItem?,
        subtitle: com.watermelon.common.model.ParsedSubtitle,
        deltaMs: Long,
    ) {
        val newOffsetMs = subtitleOffsetMs + deltaMs
        subtitleOffsetMs = newOffsetMs
        autoSyncStatus = com.watermelon.common.subtitle.sync.SyncStatus.IDLE
        val fileSize = mediaItem?.fileSize ?: return
        lifecycleScope.launch {
            val fingerprint = subtitleFingerprintProvider.fingerprint(subtitle)
            runCatching {
                subtitleSyncRepository.setManualOffset(mediaUri, fileSize, fingerprint, newOffsetMs)
            }
        }
    }

    private val playbackConnection by lazy { PlaybackConnection(applicationContext) }
    private var mediaController by mutableStateOf<MediaController?>(null)
    private var playbackController: PlaybackController? = null

    // Mini-player: which URI is "in session" (loaded into the shared MediaController),
    // independent of which screen is currently shown. Set the moment the player route issues
    // UserIntent.Play; cleared on mini-player Close or natural end with an empty queue. This is
    // deliberately separate from NavHost back-stack state — the whole point of a mini-player is
    // that it survives navigating away from the player route, so its visibility can't be driven
    // by "is the player route the current destination".
    private var miniPlayerUri by mutableStateOf<String?>(null)
    private var isMuted by mutableStateOf(false)

    private val audioManager by lazy { getSystemService(AUDIO_SERVICE) as AudioManager }
    private var permissionsGranted by mutableStateOf(false)
    private var playbackMode by mutableStateOf(PlaybackMode.NORMAL)
    private val isPiPActive: Boolean get() = playbackMode == PlaybackMode.PIP

    private val requiredPermissions: Array<String> = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
            arrayOf(Manifest.permission.READ_MEDIA_VIDEO)
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        permissionsGranted = results.values.all { it }
        if (permissionsGranted) triggerInitialIndex()
    }

    private val pipActionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val controller = playbackController ?: return
            when (intent.action) {
                PiPReceiver.ACTION_PLAY_PAUSE -> {
                    val state = controller.playbackState.value
                    if (state == PlaybackState.PLAYING) controller.pause()
                    else controller.resume()
                }
                PiPReceiver.ACTION_MUTE -> {
                    val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    val cur = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, if (cur == 0) max / 2 else 0, 0)
                }
                PiPReceiver.ACTION_PREV -> seekRelative(controller, -30_000)
                PiPReceiver.ACTION_NEXT -> seekRelative(controller, +30_000)
                PiPReceiver.ACTION_REWIND -> seekRelative(controller, -10_000)
                PiPReceiver.ACTION_FORWARD -> seekRelative(controller, +10_000)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isPiPActive) {
                val tier = tierForWidth(resources.configuration.screenWidthDp)
                setPictureInPictureParams(buildPiPParams(tier))
            }
        }
    }

    private fun seekRelative(controller: PlaybackController, deltaMs: Long) {
        val pos = controller.currentPositionMs.value
        controller.seekTo((pos + deltaMs).coerceAtLeast(0))
    }

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

        permissionsGranted = requiredPermissions.all { perm ->
            ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED
        }
        if (permissionsGranted) triggerInitialIndex()
        else permissionLauncher.launch(requiredPermissions)

        setContent {
            var pureDarkTheme by remember {
                mutableStateOf(prefs.getBoolean("pure_dark", true))
            }
            var forcedRtl by remember {
                mutableStateOf(prefs.getBoolean("forced_rtl", false))
            }

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
                            PermissionPrompt(onRequest = { permissionLauncher.launch(requiredPermissions) })
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
    private fun shouldShowBottomBar(destination: NavDestination?): Boolean {
        if (com.watermelon.ui.screens.PlayerDeviceRouting.isTelevision(this)) return false
        return destination?.route != "player/{uri}" && !isPiPActive
    }

    override fun onStart() {
        super.onStart()
        if (playbackController == null) {
            playbackConnection.connect { controller ->
                mediaController = controller
                playbackController = PlaybackControllerImpl(
                    context = applicationContext,
                    player = controller,
                    positionRepository = playbackPositionRepository
                )
                com.watermelon.common.util.FileLogger.i("App", "playbackController ready from MediaController")
            }
        } else {
            com.watermelon.common.util.FileLogger.i("App", "onStart — controller already live, reusing")
        }
        val filter = IntentFilter().apply {
            addAction(PiPReceiver.ACTION_PLAY_PAUSE)
            addAction(PiPReceiver.ACTION_MUTE)
            addAction(PiPReceiver.ACTION_PREV)
            addAction(PiPReceiver.ACTION_NEXT)
            addAction(PiPReceiver.ACTION_REWIND)
            addAction(PiPReceiver.ACTION_FORWARD)
        }
        ContextCompat.registerReceiver(
            this, pipActionReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(pipActionReceiver)
        prefs.edit()
            .putInt("volume", audioManager.getStreamVolume(AudioManager.STREAM_MUSIC))
            .apply()
        if (playbackMode == PlaybackMode.NORMAL) {
            (playbackController as? PlaybackControllerImpl)?.release()
            playbackConnection.release()
            mediaController = null
            playbackController = null
        }
    }

    override fun onDestroy() {
        vhsReverseSound.stop()
        playbackConnection.release()
        super.onDestroy()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        com.watermelon.common.util.FileLogger.i("PiP",
            "onUserLeaveHint — isPiPActive=$isPiPActive mode=$playbackMode")
        val controller = playbackController
        val isPlaying = controller?.playbackState?.value == PlaybackState.PLAYING
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            playbackMode == PlaybackMode.NORMAL && isPlaying
        ) {
            playbackMode = PlaybackMode.PIP
            enterPiPMode()
        } else if (isPiPActive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            enterPiPMode()
        }
    }

    private fun installFileLogger() {
        val docsDir = android.os.Environment.getExternalStoragePublicDirectory(
            android.os.Environment.DIRECTORY_DOCUMENTS
        )
        val primary = java.io.File(docsDir, "watermelon.log")
        val fallback = java.io.File(getExternalFilesDir(null), "watermelon.log")
        val logFile = if (runCatching {
                docsDir.mkdirs()
                java.io.FileWriter(primary, true).use { it.append("") }
                true
            }.getOrDefault(false)) primary else fallback
        runCatching { if (logFile.exists()) logFile.delete() }
        val lock = Any()
        com.watermelon.common.util.FileLogger.install { line ->
            synchronized(lock) {
                runCatching {
                    java.io.FileWriter(logFile, true).use { it.append(line).append('\n') }
                }
            }
        }
        com.watermelon.common.util.FileLogger.i("Log", "log file at: ${logFile.absolutePath}")
    }

    private fun installCrashLogger() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val dir = android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_DOCUMENTS
                )
                val file = java.io.File(dir, "watermelon_crash_${System.currentTimeMillis()}.txt")
                file.writeText(
                    "Thread: ${thread.name}\n\n" +
                    android.util.Log.getStackTraceString(throwable)
                )
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun triggerInitialIndex() {
        lifecycleScope.launch {
            runCatching { mediaRepository.refreshIndex() }
                .onFailure { error ->
                    com.watermelon.common.util.FileLogger.e(
                        "App",
                        "initial library index failed: ${error.message ?: error::class.java.simpleName}"
                    )
                }
        }
    }

    private fun requestPlayerDelete(target: PlayerDeleteTarget) {
        val mediaUri = runCatching {
            val id = android.content.ContentUris.parseId(Uri.parse(target.uri))
            android.content.ContentUris.withAppendedId(
                android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                id
            )
        }.getOrElse { error ->
            playerDeleteOutcome = PlayerDeleteOutcome.Failed(
                target,
                "This video is no longer available in the media library."
            )
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                val request = android.provider.MediaStore.createDeleteRequest(
                    contentResolver,
                    listOf(mediaUri)
                )
                playerDeleteLauncher.launch(
                    androidx.activity.result.IntentSenderRequest.Builder(request.intentSender).build()
                )
            }.onFailure { error ->
                pendingPlayerDelete = null
                playerDeleteOutcome = PlayerDeleteOutcome.Failed(
                    target,
                    error.message ?: "Android could not start the delete request."
                )
            }
            return
        }

        lifecycleScope.launch {
            try {
                val rowsDeleted = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    contentResolver.delete(mediaUri, null, null)
                }
                pendingPlayerDelete = null
                playerDeleteOutcome = if (rowsDeleted > 0) {
                    PlayerDeleteOutcome.Deleted(target)
                } else {
                    PlayerDeleteOutcome.Failed(target, "Watermelon could not delete this video.")
                }
            } catch (error: SecurityException) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                    error is android.app.RecoverableSecurityException
                ) {
                    runCatching {
                        playerDeleteLauncher.launch(
                            androidx.activity.result.IntentSenderRequest.Builder(
                                error.userAction.actionIntent.intentSender
                            ).build()
                        )
                    }.onFailure { launchError ->
                        pendingPlayerDelete = null
                        playerDeleteOutcome = PlayerDeleteOutcome.Failed(
                            target,
                            launchError.message ?: "Android could not start the delete request."
                        )
                    }
                    return@launch
                }
                pendingPlayerDelete = null
                playerDeleteOutcome = PlayerDeleteOutcome.Failed(
                    target,
                    error.message ?: "Watermelon does not have permission to delete this video."
                )
            } catch (error: Throwable) {
                pendingPlayerDelete = null
                playerDeleteOutcome = PlayerDeleteOutcome.Failed(
                    target,
                    error.message ?: "Watermelon could not delete this video."
                )
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun enterPiPMode() {
        com.watermelon.common.util.FileLogger.i("PiP", "enterPiPMode called — entering now")
        val ok = enterPictureInPictureMode(buildPiPParams(PiPTier.MID))
        com.watermelon.common.util.FileLogger.i("PiP", "enterPictureInPictureMode returned $ok")
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun makePiPAction(action: String, iconRes: Int, title: String): android.app.RemoteAction {
        val intent = PendingIntent.getBroadcast(
            this, action.hashCode(),
            Intent(action).setPackage(packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return android.app.RemoteAction(
            Icon.createWithResource(this, iconRes), title, title, intent
        )
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun buildPiPActions(tier: PiPTier): List<android.app.RemoteAction> {
        val isPlaying = playbackController?.playbackState?.value == PlaybackState.PLAYING
        val ppIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val playPause = makePiPAction(PiPReceiver.ACTION_PLAY_PAUSE, ppIcon, if (isPlaying) "Pause" else "Play")
        val prev = makePiPAction(PiPReceiver.ACTION_PREV, android.R.drawable.ic_media_previous, "Previous")
        val next = makePiPAction(PiPReceiver.ACTION_NEXT, android.R.drawable.ic_media_next, "Next")
        val rew = makePiPAction(PiPReceiver.ACTION_REWIND, android.R.drawable.ic_media_rew, "Rewind 10s")
        val fwd = makePiPAction(PiPReceiver.ACTION_FORWARD, android.R.drawable.ic_media_ff, "Forward 10s")
        return when (tier) {
            PiPTier.SMALL -> listOf(playPause)
            PiPTier.MID -> listOf(prev, playPause, next)
            PiPTier.EXPANDED -> listOf(rew, prev, playPause, next, fwd)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun buildPiPParams(tier: PiPTier): PictureInPictureParams {
        val videoWidth = mediaController?.videoSize?.width ?: 16
        val videoHeight = mediaController?.videoSize?.height ?: 9
        val rational = if (videoWidth > 0 && videoHeight > 0)
            Rational(videoWidth, videoHeight) else Rational(16, 9)
        return PictureInPictureParams.Builder()
            .setAspectRatio(rational)
            .setActions(buildPiPActions(tier))
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setAutoEnterEnabled(false)
                    setSeamlessResizeEnabled(true)
                }
            }
            .build()
    }

    private fun tierForWidth(widthDp: Int): PiPTier = when {
        widthDp < 200 -> PiPTier.SMALL
        widthDp < 400 -> PiPTier.MID
        else -> PiPTier.EXPANDED
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: android.content.res.Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (isInPictureInPictureMode && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val tier = tierForWidth(newConfig.screenWidthDp)
            com.watermelon.common.util.FileLogger.i("PiP",
                "size change: width=${newConfig.screenWidthDp}dp -> tier=$tier")
            setPictureInPictureParams(buildPiPParams(tier))
        } else {
            com.watermelon.common.util.FileLogger.i("PiP",
                "exited PiP — resetting playbackMode to NORMAL")
            playbackMode = PlaybackMode.NORMAL
        }
    }

    @Composable
    private fun WatermelonNavHost(
        navController: NavHostController,
        pureDarkTheme: Boolean,
        onPureDarkThemeChange: (Boolean) -> Unit,
        onForcedRtlChange: (Boolean) -> Unit,
        onPlayerUriChanged: (String) -> Unit,
        modifier: Modifier = Modifier
    ) {
        var settingsState by remember {
            mutableStateOf(loadSettingsState(prefs, pureDarkTheme))
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
        val mediaJobs by mediaJobsViewModel.jobs.collectAsStateWithLifecycle()
        val activeMp3Job = mediaJobs.firstOrNull { it.id == activeMp3JobId }
        LaunchedEffect(activeMp3Job?.state) {
            if (activeMp3Job?.state is com.watermelon.mediatools.job.MediaJobState.Cancelled) {
                activeMp3JobId = null
            }
        }

        val savedBrightness = remember { prefs.getFloat("brightness", -1f) }

        NavHost(
            navController = navController,
            startDestination = Routes.FOLDERS,
            modifier = modifier
        ) {
            composable(Routes.FOLDERS) {
                val vm = remember {
                    FolderViewModel(folderRepository, mediaRepository, playlistRepository, settingsStore)
                }
                val onFolderClick: (com.watermelon.common.model.FolderNode) -> Unit = { folder ->
                    if (folder.isPlaylist) {
                        navController.navigate("videos/${Uri.encode(folder.playlistId!!)}?isPlaylist=true")
                    } else {
                        navController.navigate("videos/${Uri.encode(folder.path)}?isPlaylist=false")
                    }
                }
                val isTelevision = remember {
                    com.watermelon.ui.screens.PlayerDeviceRouting.isTelevision(this@MainActivity)
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
                    FolderBrowserScreen(
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
                val vm = remember {
                    VideoListViewModel(
                        mediaRepository = mediaRepository,
                        folderPath = "",
                        isAllVideos = true,
                        folderRepository = folderRepository,
                        folderVisibilityStore = settingsStore
                    )
                }
                val isTelevision = remember {
                    com.watermelon.ui.screens.PlayerDeviceRouting.isTelevision(this@MainActivity)
                }
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
                    val playlists by playlistRepository.observeAll()
                        .collectAsStateWithLifecycle(initialValue = emptyList())
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
                val vm = remember { PlaylistViewModel(playlistRepository) }
                val isTelevision = remember {
                    com.watermelon.ui.screens.PlayerDeviceRouting.isTelevision(this@MainActivity)
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
                    PlaylistsScreen(
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
                val vm = remember(folderPath) {
                    VideoListViewModel(mediaRepository, folderPath, playlistRepository, isPlaylist)
                }
                val screenTitle = if (isPlaylist) "Playlist" else folderPath.substringAfterLast("/")
                val isTelevision = remember {
                    com.watermelon.ui.screens.PlayerDeviceRouting.isTelevision(this@MainActivity)
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
                    val playlists by playlistRepository.observeAll()
                        .collectAsStateWithLifecycle(initialValue = emptyList())
                    VideoListScreen(
                        viewModel = vm,
                        onVideoClick = { item -> navController.navigate("player/${Uri.encode(item.uri)}") },
                        onRefresh = vm::refresh,
                        availablePlaylists = playlists,
                        defaultGrid = settingsState.gridDefault,
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
                route = "player/{uri}",
                arguments = listOf(navArgument("uri") { type = NavType.StringType })
            ) { backStackEntry ->
                val mediaUri = Uri.decode(backStackEntry.arguments?.getString("uri").orEmpty())
                val controller = mediaController
                val pbController = playbackController
                if (controller == null || pbController == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Connecting…", style = MaterialTheme.typography.bodyLarge)
                    }
                } else {
                    val vm = remember(pbController) { PlayerViewModel(pbController) }
                    LaunchedEffect(mediaUri) {
                        vm.onIntent(UserIntent.Play(mediaUri))
                        onPlayerUriChanged(mediaUri)
                    }

                    var isFavourite by remember(mediaUri) { mutableStateOf(false) }
                    var playerMedia by remember(mediaUri) {
                        mutableStateOf<com.watermelon.common.model.MediaItem?>(null)
                    }
                    LaunchedEffect(mediaUri) {
                        isFavourite = runCatching { playlistRepository.isFavourite(mediaUri) }.getOrDefault(false)
                        playerMedia = runCatching { mediaRepository.getByUri(mediaUri) }.getOrNull()
                    }

                    val vhsController = com.watermelon.ui.player.rememberVhsEffectController(
                        shaderProvider = { intensity, timeSec, w, h ->
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                VhsShader.build(
                                    tier = VhsCapability.detectTier(this@MainActivity),
                                    intensity = intensity, time = timeSec, width = w, height = h
                                )
                            } else null
                        },
                        reverseSound = { active, speed ->
                            if (active) { vhsReverseSound.start(speed); vhsReverseSound.setSpeed(speed) }
                            else vhsReverseSound.stop()
                        }
                    )
                    val mappedIntensity = when (settingsState.vhsIntensity) {
                        com.watermelon.ui.screens.VhsIntensity.OFF -> 0f
                        com.watermelon.ui.screens.VhsIntensity.LOW -> 0.35f
                        com.watermelon.ui.screens.VhsIntensity.MED -> 0.6f
                        com.watermelon.ui.screens.VhsIntensity.HIGH -> 1f
                    }
                    val isTelevision = remember {
                        com.watermelon.ui.screens.PlayerDeviceRouting.isTelevision(this@MainActivity)
                    }
                    val subtitleTrackState = run {
                        var track by remember(mediaUri) {
                            mutableStateOf<com.watermelon.common.model.ParsedSubtitle?>(null)
                        }
                        LaunchedEffect(mediaUri) {
                            subtitleSyncSession += 1
                            subtitleOffsetMs = 0L
                            autoSyncStatus = com.watermelon.common.subtitle.sync.SyncStatus.IDLE
                            val discovered = discoverSubtitle(mediaUri)
                            track = discovered
                            if (discovered != null) {
                                val mediaItem = runCatching { mediaRepository.getByUri(mediaUri) }.getOrNull()
                                if (mediaItem != null) {
                                    val fingerprint = subtitleFingerprintProvider.fingerprint(discovered)
                                    val profile = runCatching {
                                        subtitleSyncRepository.get(mediaUri, mediaItem.fileSize, fingerprint)
                                    }.getOrNull()
                                    subtitleOffsetMs = profile?.effectiveOffsetMs() ?: 0L
                                }
                            }
                        }
                        track
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
                                        popUpTo("player/{uri}") { inclusive = true }
                                    }
                                } else {
                                    vm.onIntent(UserIntent.Seek(0L))
                                }
                            },
                            onSkipNext = {
                                com.watermelon.ui.screens.PlaybackQueue.nextOf(mediaUri)?.let { next ->
                                    navController.navigate("player/${Uri.encode(next)}") {
                                        popUpTo("player/{uri}") { inclusive = true }
                                    }
                                }
                            },
                            onExit = { navController.popBackStack() },
                            subtitleTrack = subtitleTrackState,
                            subtitleStyle = settingsState.subtitleStyle,
                            subtitleOffsetMs = subtitleOffsetMs,
                            autoSyncEnabled = settingsState.autoSyncEnabled,
                            autoSyncStatus = autoSyncStatus,
                            onSubtitleNudge = { deltaMs ->
                                subtitleTrackState?.let { track ->
                                    applySubtitleManualNudge(mediaUri, playerMedia, track, deltaMs)
                                }
                            },
                            onAutoSync = {
                                subtitleTrackState?.let { track ->
                                    triggerSubtitleAutoSync(mediaUri, playerMedia, track, durationMs)
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
                    PhonePlayerScreen(
                        viewModel = vm,
                        vhs = vhsController,
                        vhsEnabled = settingsState.vhsEnabled,
                        vhsIntensity = mappedIntensity,
                        tunerSeekBarEnabled = settingsState.tunerSeekBarEnabled,
                        tunerSeekStepSeconds = settingsState.tunerSeekStepSeconds,
                        onTunerSeekBarEnabledChange = { enabled ->
                            settingsState = settingsState.copy(tunerSeekBarEnabled = enabled)
                            saveSettingsState(prefs, settingsState)
                        },
                        isInPipMode = isPiPActive,
                        onBack = { navController.popBackStack() },
                        durationMs = durationMs,
                        subtitleTrack = subtitleTrackState,
                        subtitleOffsetMs = subtitleOffsetMs,
                        autoSyncEnabled = settingsState.autoSyncEnabled,
                        autoSyncStatus = autoSyncStatus,
                        onSubtitleNudge = { deltaMs ->
                            subtitleTrackState?.let { track ->
                                applySubtitleManualNudge(mediaUri, playerMedia, track, deltaMs)
                            }
                        },
                        onAutoSync = {
                            subtitleTrackState?.let { track ->
                                triggerSubtitleAutoSync(mediaUri, playerMedia, track, durationMs)
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
                                com.watermelon.common.util.FileLogger.i("PiP", "onPipClick tapped")
                                playbackMode = PlaybackMode.PIP
                                enterPiPMode()
                            }
                        } else {
                            null
                        },
                        onBackgroundClick = { enabled ->
                            playbackMode = if (enabled) PlaybackMode.BACKGROUND else PlaybackMode.NORMAL
                        },
                        onBrightnessChange = { brightness ->
                            prefs.edit().putFloat("brightness", brightness).apply()
                        },
                        onSkipToTrack = { newUri ->
                            lifecycleScope.launch {
                                runCatching { mediaRepository.markAsPlayed(newUri) }
                            }
                            navController.navigate("player/${Uri.encode(newUri)}") {
                                popUpTo("player/{uri}") { inclusive = true }
                            }
                        },
                        onLockChanged = { locked ->
                            runCatching {
                                if (locked) startLockTask() else stopLockTask()
                            }.onFailure {
                                com.watermelon.common.util.FileLogger.i("Lock", "lock task not available: ${it.message}")
                            }
                        },
                        onShare = {
                            val sendIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "video/*"
                                putExtra(android.content.Intent.EXTRA_STREAM, Uri.parse(mediaUri))
                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            startActivity(android.content.Intent.createChooser(sendIntent, "Share video"))
                        },
                        isFavourite = isFavourite,
                        onFavourite = { wantFavourite ->
                            isFavourite = wantFavourite
                            lifecycleScope.launch {
                                val ok = runCatching {
                                    if (wantFavourite) playlistRepository.addToFavourites(mediaUri)
                                    else playlistRepository.removeFromFavourites(mediaUri)
                                }.isSuccess
                                if (!ok) isFavourite = !wantFavourite
                            }
                        },
                        onAddToPlaylist = {
                            playerPlaylistUri = mediaUri
                            showPlayerPlaylistPicker = true
                        },
                        onDelete = {
                            lifecycleScope.launch {
                                val displayName = runCatching {
                                    mediaRepository.getByUri(mediaUri)?.displayName
                                }.getOrNull() ?: mediaUri.substringAfterLast('/')
                                pendingPlayerDelete = PlayerDeleteTarget(mediaUri, displayName)
                                playerDeleteOutcome = null
                                showPlayerDeleteDialog = true
                            }
                        },
                        onExtractAudio = {
                            // Premium gating temporarily disabled -- see VideoListScreen's
                            // identical note above.
                            lifecycleScope.launch {
                                val displayName = runCatching { mediaRepository.getByUri(mediaUri)?.displayName }
                                    .getOrNull() ?: mediaUri.substringAfterLast('/')
                                pendingExtractAudio = mediaUri to displayName
                            }
                        },
                        onTrimVideo = {
                            lifecycleScope.launch {
                                val displayName = runCatching { mediaRepository.getByUri(mediaUri)?.displayName }
                                    .getOrNull() ?: mediaUri.substringAfterLast('/')
                                navController.navigate(
                                    "trim/${Uri.encode(mediaUri)}/${Uri.encode(displayName)}/$durationMs"
                                )
                            }
                        },
                        onCompressVideo = {
                            lifecycleScope.launch {
                                val displayName = runCatching { mediaRepository.getByUri(mediaUri)?.displayName }
                                    .getOrNull() ?: mediaUri.substringAfterLast('/')
                                navController.navigate(
                                    "compress/${Uri.encode(mediaUri)}/${Uri.encode(displayName)}"
                                )
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
                val controller = mediaController
                val pbController = playbackController
                if (controller == null || pbController == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Connecting…", style = MaterialTheme.typography.bodyLarge)
                    }
                } else {
                    val playerVm = remember(pbController) { PlayerViewModel(pbController) }
                    LaunchedEffect(mediaUri) { playerVm.onIntent(UserIntent.Play(mediaUri)) }
                    val trimVm = remember { com.watermelon.ui.viewmodel.TrimViewModel(mediaJobManager, videoTrimmer, keyframeIndexer, filmstripExtractor) }

                    com.watermelon.ui.screens.TrimScreen(
                        playerViewModel = playerVm,
                        trimViewModel = trimVm,
                        mediaJobsViewModel = mediaJobsViewModel,
                        contentResolver = contentResolver,
                        originalFileDeleter = originalFileDeleter,
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
                val compressVm = remember { com.watermelon.ui.viewmodel.CompressViewModel(mediaJobManager, videoCompressor) }

                com.watermelon.ui.screens.CompressScreen(
                    compressViewModel = compressVm,
                    mediaJobsViewModel = mediaJobsViewModel,
                    contentResolver = contentResolver,
                    originalFileDeleter = originalFileDeleter,
                    inputUri = Uri.parse(mediaUri),
                    originalDisplayName = displayName,
                    isPremiumUnlocked = settingsState.isPremiumUnlocked,
                    onRequestUpsell = { showPremiumUpsell = true },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Routes.SETTINGS) {
                val isTelevision = remember {
                    com.watermelon.ui.screens.PlayerDeviceRouting.isTelevision(this@MainActivity)
                }
                val onSettingsStateChange: (com.watermelon.ui.screens.SettingsState) -> Unit = { newState ->
                    settingsState = newState
                    saveSettingsState(prefs, settingsState)
                    if (newState.pureDark != pureDarkTheme) {
                        onPureDarkThemeChange(newState.pureDark)
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
                    SettingsScreen(
                        state = settingsState,
                        onStateChange = onSettingsStateChange,
                        onFolderVisibilityClick = { navController.navigate(Routes.FOLDER_VISIBILITY) },
                        onBack = { navController.popBackStack() }
                    )
                }
            }
            composable(Routes.FOLDER_VISIBILITY) {
                val vm = remember {
                    FolderViewModel(folderRepository, mediaRepository, playlistRepository, settingsStore)
                }
                val folders by vm.allFoldersForSettings.collectAsStateWithLifecycle()
                val folderRows = folders
                    .filter { !it.first.isPlaylist }
                    .map { (node, visible) -> Triple(node.path, node.displayName, visible) }
                val isTelevision = remember {
                    com.watermelon.ui.screens.PlayerDeviceRouting.isTelevision(this@MainActivity)
                }
                if (isTelevision) {
                    com.watermelon.ui.tv.TvFolderVisibilityScreen(
                        folders = folderRows,
                        onToggle = { path, visible -> vm.setFolderHidden(path, !visible) },
                        onBack = { navController.popBackStack() }
                    )
                } else {
                    FolderVisibilityScreen(
                        folders = folderRows,
                        onToggle = { path, visible -> vm.setFolderHidden(path, !visible) },
                        onBack = { navController.popBackStack() }
                    )
                }
            }
            // NEW: Design System route
            composable(Routes.DESIGN_SYSTEM) {
                DesignSystemScreen(onBack = { navController.popBackStack() })
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
                    mediaJobManager.extractAudio(audioExtractor, uri, displayName, preset.kbps)
                    pendingExtractAudio = null
                },
                onDismiss = { pendingExtractAudio = null }
            )
        }

        val playlistUri = playerPlaylistUri
        if (showPlayerPlaylistPicker && playlistUri != null) {
            val playlists by playlistRepository.observeAll()
                .collectAsStateWithLifecycle(initialValue = emptyList())
            com.watermelon.ui.components.PlayerPlaylistPickerDialog(
                playlists = playlists,
                onSelect = { playlist ->
                    showPlayerPlaylistPicker = false
                    playerPlaylistUri = null
                    lifecycleScope.launch {
                        runCatching {
                            val alreadyPresent = playlistRepository.observeVideos(playlist.id)
                                .first()
                                .any { it.uri == playlistUri }
                            if (!alreadyPresent) {
                                playlistRepository.addToPlaylist(playlist.id, playlistUri)
                            }
                            alreadyPresent
                        }.onSuccess { alreadyPresent ->
                            android.widget.Toast.makeText(
                                this@MainActivity,
                                if (alreadyPresent) {
                                    "Already in ${playlist.name}"
                                } else {
                                    "Added to ${playlist.name}"
                                },
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }.onFailure {
                                android.widget.Toast.makeText(
                                    this@MainActivity,
                                    "Could not add video to ${playlist.name}",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                    }
                },
                onCreate = { name ->
                    showPlayerPlaylistPicker = false
                    playerPlaylistUri = null
                    lifecycleScope.launch {
                        runCatching {
                            val id = playlistRepository.createPlaylist(name)
                            playlistRepository.addToPlaylist(id, playlistUri)
                        }.onSuccess {
                            android.widget.Toast.makeText(
                                this@MainActivity,
                                "Created ${name} and added the video",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }.onFailure {
                            android.widget.Toast.makeText(
                                this@MainActivity,
                                "Could not create ${name}",
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

        val deleteTarget = pendingPlayerDelete
        if (showPlayerDeleteDialog && deleteTarget != null) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = {
                    showPlayerDeleteDialog = false
                    pendingPlayerDelete = null
                },
                title = { androidx.compose.material3.Text("Delete from device?") },
                text = {
                    androidx.compose.material3.Text(
                        "${deleteTarget.displayName} will be permanently removed from your device and library."
                    )
                },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = {
                        showPlayerDeleteDialog = false
                        requestPlayerDelete(deleteTarget)
                    }) {
                        androidx.compose.material3.Text("Delete")
                    }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = {
                        showPlayerDeleteDialog = false
                        pendingPlayerDelete = null
                    }) {
                        androidx.compose.material3.Text("Cancel")
                    }
                }
            )
        }

        playerDeleteOutcome?.let { outcome ->
            LaunchedEffect(outcome) {
                when (outcome) {
                    is PlayerDeleteOutcome.Deleted -> {
                        playbackController?.pause()
                        if (miniPlayerUri == outcome.target.uri) miniPlayerUri = null
                        runCatching { mediaRepository.refreshIndex() }
                        android.widget.Toast.makeText(
                            this@MainActivity,
                            "Deleted ${outcome.target.displayName}",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                        if (navController.currentDestination?.route == "player/{uri}") {
                            navController.popBackStack()
                        }
                    }
                    is PlayerDeleteOutcome.Cancelled -> {
                        android.widget.Toast.makeText(
                            this@MainActivity,
                            "Deletion cancelled",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                    is PlayerDeleteOutcome.Failed -> {
                        android.widget.Toast.makeText(
                            this@MainActivity,
                            outcome.reason,
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                }
                playerDeleteOutcome = null
            }
        }
    }

    private suspend fun discoverSubtitle(uri: String): com.watermelon.common.model.ParsedSubtitle? {
        val item = runCatching { mediaRepository.getByUri(uri) }.getOrNull() ?: return null
        return subtitleRepository.parsedFor(
            mediaItem = item,
            preferredLanguages = listOf("fa", "ar", "ur", "ku", "en")
        )
    }

    @Composable
    private fun PermissionPrompt(onRequest: () -> Unit) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(24.dp)
            ) {
                Text(
                    "Watermelon needs access to your videos to build the library.",
                    style = MaterialTheme.typography.bodyLarge
                )
                Button(onClick = onRequest) { Text("Grant access") }
            }
        }
    }

    private object Routes {
        const val FOLDERS = "folders"
        const val ALL_VIDEOS = "all_videos"
        const val SETTINGS = "settings"
        const val FOLDER_VISIBILITY = "folder_visibility"
        const val DESIGN_SYSTEM = "design_system"  // NEW
        const val PLAYLISTS = "playlists"
        const val FAVORITES = "favorites"
    }

    private enum class PiPTier { SMALL, MID, EXPANDED }
}
