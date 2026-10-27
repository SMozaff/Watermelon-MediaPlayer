package com.watermelon.app

import androidx.annotation.OptIn
import android.content.Context
import android.content.SharedPreferences
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.navigation.NavDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.watermelon.common.controller.PlaybackController
import com.watermelon.common.model.PlaybackMode
import com.watermelon.common.model.ParsedSubtitle
import com.watermelon.common.util.FileLogger
import com.watermelon.mediatools.output.OriginalFileDeleter
import com.watermelon.playback.controller.PlaybackControllerImpl
import com.watermelon.playback.service.PlaybackConnection
import com.watermelon.ui.components.WatermelonBottomNavigation
import com.watermelon.ui.screens.ThemeMode
import com.watermelon.ui.theme.WatermelonTheme
import kotlinx.coroutines.launch

/**
 * The app shell. Phase 2b reduced this class to what an Activity actually is: lifecycle
 * management, the dependency graph ([deps], [playerDelete], [originalFileDeleter],
 * [permissionGate]), the playback connection, and the Scaffold chrome around
 * [AppNavGraph], [MediaJobsSection], [MiniPlayerHost], and [PermissionPrompt].
 *
 * Navigation, route-scoped state, PiP plumbing, permission handling, loggers, and the
 * subtitle-sync state machine each live in their own file (same package, internal
 * visibility) instead of as private members here.
 */
@OptIn(UnstableApi::class)
class MainActivity : ComponentActivity() {

    internal val prefs: SharedPreferences by lazy {
        getSharedPreferences("watermelon_prefs", Context.MODE_PRIVATE)
    }

    /** Composition root for every Activity-scoped dependency; see [AppDependencies]. */
    internal val deps by lazy {
        AppDependencies(applicationContext, application as WatermelonApplication, lifecycleScope)
    }

    // Constructed as a plain field, not lazily -- the permission launcher inside it must be
    // registered unconditionally during Activity initialization (Android's constraint).
    private val permissionGate = PermissionGate(this) { triggerInitialIndex() }

    // Same initialization-timing constraint for the delete flow's launcher: assigned right
    // after super.onCreate, matching the registerForActivityResult placement this code has
    // always had.
    internal lateinit var playerDelete: PlayerDeleteCoordinator
    internal lateinit var originalFileDeleter: OriginalFileDeleter

    internal val playbackConnection by lazy { PlaybackConnection(applicationContext) }
    internal var mediaController by mutableStateOf<MediaController?>(null)
    internal var playbackController: PlaybackController? = null

    // Mini-player: which URI is "in session" (loaded into the shared MediaController),
    // independent of which screen is currently shown. Set the moment the player route issues
    // UserIntent.Play; cleared on mini-player Close or natural end with an empty queue. This is
    // deliberately separate from NavHost back-stack state — the whole point of a mini-player is
    // that it survives navigating away from the player route, so its visibility can't be driven
    // by "is the player route the current destination".
    internal var miniPlayerUri by mutableStateOf<String?>(null)
    internal var isMuted by mutableStateOf(false)

    internal val audioManager by lazy { getSystemService(AUDIO_SERVICE) as AudioManager }
    internal var playbackMode by mutableStateOf(PlaybackMode.NORMAL)
    internal val isPiPActive: Boolean get() = playbackMode == PlaybackMode.PIP

    private val pipReceiver by lazy { pipActionReceiver() }

    override fun onCreate(savedInstanceState: Bundle?) {
        installFileLogger()
        installCrashLogger()
        FileLogger.i("App", "onCreate — app starting")
        super.onCreate(savedInstanceState)

        originalFileDeleter = OriginalFileDeleter(this) { jobId, deleted ->
            deps.mediaJobManager.resolveOriginalFileDecision(jobId, deleteOriginal = deleted, contentResolver)
        }
        playerDelete = PlayerDeleteCoordinator(this)

        val savedVolume = prefs.getInt("volume", -1)
        if (savedVolume >= 0) {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, savedVolume, 0)
        }

        permissionGate.checkOrRequest()

        setContent {
            var themeMode by remember { mutableStateOf(readThemeMode(prefs)) }
            var forcedRtl by remember { mutableStateOf(prefs.getBoolean("forced_rtl", false)) }

            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
            }

            WatermelonTheme(darkTheme = darkTheme, forceRtl = forcedRtl) {
                val navController = rememberNavController()

                // Track current destination for bottom navigation
                val currentDestination = navController.currentBackStackEntryAsState().value?.destination

                // Mini-player is visible only when something is loaded AND we're not already
                // looking at the full player screen — showing both at once would be redundant
                // and would also mean two PlayerViews racing to attach to the same Player
                // (Media3 only keeps the most-recently-attached view live).
                val onPlayerRoute = currentDestination?.route == Routes.PLAYER
                val showMiniPlayer = miniPlayerUri != null && !onPlayerRoute && !isPiPActive

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
                        MediaJobsSection(this@MainActivity, navController)
                        if (permissionGate.granted) {
                            AppNavGraph(
                                activity = this@MainActivity,
                                navController = navController,
                                themeMode = themeMode,
                                onThemeModeChange = { mode ->
                                    themeMode = mode
                                    prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
                                },
                                onForcedRtlChange = { enabled -> forcedRtl = enabled },
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            PermissionPrompt(
                                permanentlyDenied = permissionGate.permanentlyDenied,
                                onRequest = { permissionGate.launch() },
                                onOpenSettings = { openPermissionSettings() }
                            )
                        }
                    }
                    // Sibling of the Column (both children of Scaffold's content slot), so
                    // the mini-player overlays the screens instead of taking layout space.
                    MiniPlayerHost(
                        activity = this@MainActivity,
                        navController = navController,
                        visible = showMiniPlayer
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
        return destination?.route != Routes.PLAYER && !isPiPActive
    }

    override fun onStart() {
        super.onStart()
        // Re-check on every start: the user may have granted access from system settings
        // after a permanent denial, a path that fires no launcher callback.
        permissionGate.recheckAfterStart()
        if (playbackController == null) {
            playbackConnection.connect { controller ->
                mediaController = controller
                playbackController = PlaybackControllerImpl(
                    context = applicationContext,
                    player = controller,
                    positionRepository = deps.playbackPositionRepository
                )
                FileLogger.i("App", "playbackController ready from MediaController")
            }
        } else {
            FileLogger.i("App", "onStart — controller already live, reusing")
        }
        registerPipReceiver(pipReceiver)
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(pipReceiver)
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
        deps.vhsReverseSound.stop()
        playbackConnection.release()
        super.onDestroy()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        FileLogger.i("PiP", "onUserLeaveHint — isPiPActive=$isPiPActive mode=$playbackMode")
        val controller = playbackController
        val isPlaying = controller?.playbackState?.value == com.watermelon.common.model.PlaybackState.PLAYING
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            playbackMode == PlaybackMode.NORMAL && isPlaying
        ) {
            playbackMode = PlaybackMode.PIP
            enterPipMode()
        } else if (isPiPActive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            enterPipMode()
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: android.content.res.Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (isInPictureInPictureMode && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val tier = tierForWidth(newConfig.screenWidthDp)
            FileLogger.i("PiP", "size change: width=${newConfig.screenWidthDp}dp -> tier=$tier")
            setPictureInPictureParams(buildPiPParams(tier))
        } else {
            FileLogger.i("PiP", "exited PiP — resetting playbackMode to NORMAL")
            playbackMode = PlaybackMode.NORMAL
        }
    }

    internal fun seekRelative(controller: PlaybackController, deltaMs: Long) {
        val pos = controller.currentPositionMs.value
        controller.seekTo((pos + deltaMs).coerceAtLeast(0))
    }

    /** First library index of the session; failures are logged, never surfaced as a crash. */
    internal fun triggerInitialIndex() {
        lifecycleScope.launch {
            runCatching { deps.mediaRepository.refreshIndex() }
                .onFailure { error ->
                    FileLogger.e(
                        "App",
                        "initial library index failed: ${error.message ?: error::class.java.simpleName}"
                    )
                }
        }
    }

    /** Best-effort discovery of a sidecar/embedded subtitle track for [uri]. */
    internal suspend fun discoverSubtitle(uri: String): ParsedSubtitle? {
        val item = runCatching { deps.mediaRepository.getByUri(uri) }.getOrNull() ?: return null
        return deps.subtitleRepository.parsedFor(
            mediaItem = item,
            preferredLanguages = com.watermelon.common.PLAYER_SUBTITLE_LANGUAGE_PRIORITY
        )
    }
}
