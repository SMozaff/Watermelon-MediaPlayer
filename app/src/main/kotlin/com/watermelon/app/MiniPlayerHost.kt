package com.watermelon.app

import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import androidx.navigation.NavHostController
import com.watermelon.common.model.PlaybackState

/**
 * The persistent mini-player, extracted from MainActivity in Phase 2b.
 *
 * The mini-player is visible only when something is loaded AND we're not already
 * looking at the full player screen — showing both at once would be redundant and
 * would also mean two PlayerViews racing to attach to the same Player (Media3 only
 * keeps the most-recently-attached view live). Its visibility can't be driven by
 * "is the player route the current destination" either: the whole point of a
 * mini-player is surviving navigation away from the player route, so the session
 * URI lives on the Activity, not in the back stack.
 */
@OptIn(UnstableApi::class)
@Composable
internal fun MiniPlayerHost(
    activity: MainActivity,
    navController: NavHostController,
    visible: Boolean
) {
    val miniUri = activity.miniPlayerUri
    val controller = activity.mediaController
    val pbController = activity.playbackController
    if (miniUri == null || controller == null || pbController == null) return

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
                // Natural end with an empty queue closes the mini-player entirely,
                // matching the spec's dismissal conditions (Close / restore-tap /
                // natural end with empty queue).
                if (events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED) &&
                    player.playbackState == Player.STATE_ENDED &&
                    com.watermelon.ui.screens.PlaybackQueue.nextOf(miniUri) == null
                ) {
                    activity.miniPlayerUri = null
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
        visible = visible,
        title = miniTitle,
        isPlaying = playbackState == PlaybackState.PLAYING,
        isMuted = activity.isMuted,
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
                popUpTo(Routes.PLAYER) { inclusive = true }
            }
        },
        onPlayPause = {
            if (playbackState == PlaybackState.PLAYING) pbController.pause()
            else pbController.resume()
        },
        onNext = {
            com.watermelon.ui.screens.PlaybackQueue.nextOf(miniUri)?.let { next ->
                activity.miniPlayerUri = next
                pbController.play(next)
            }
        },
        onPrevious = {
            com.watermelon.ui.screens.PlaybackQueue.previousOf(miniUri)?.let { prev ->
                activity.miniPlayerUri = prev
                pbController.play(prev)
            }
        },
        onMuteToggle = {
            activity.isMuted = !activity.isMuted
            controller.volume = if (activity.isMuted) 0f else 1f
        },
        onClose = {
            pbController.pause()
            activity.miniPlayerUri = null
        }
    )
}
