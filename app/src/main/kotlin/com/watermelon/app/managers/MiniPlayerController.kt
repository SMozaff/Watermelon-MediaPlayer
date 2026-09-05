package com.watermelon.app.managers

import android.net.Uri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import com.watermelon.common.controller.PlaybackController
import com.watermelon.common.model.PlaybackState
import com.watermelon.ui.screens.PlaybackQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Manages the mini-player state and lifecycle.
 * The mini-player persists across navigation, independent of the player route.
 */
class MiniPlayerController(
    private val scope: CoroutineScope,
) : LifecycleObserver {

    private var mediaController: MediaController? = null
    private var playbackController: PlaybackController? = null

    var miniPlayerUri: String? = null
        private set

    var isMuted: Boolean = false
        private set

    fun setControllers(mediaController: MediaController, playbackController: PlaybackController) {
        this.mediaController = mediaController
        this.playbackController = playbackController

        if (miniPlayerUri != null) {
            setupMiniPlayerListener()
        }
    }

    private fun setupMiniPlayerListener() {
        mediaController?.let { controller ->
            scope.launch {
                val listener = object : Player.Listener {
                    override fun onEvents(player: Player, events: Player.Events) {
                        if (events.containsAny(
                                Player.EVENT_TIMELINE_CHANGED,
                                Player.EVENT_MEDIA_ITEM_TRANSITION,
                                Player.EVENT_PLAYBACK_STATE_CHANGED
                            )
                        ) {
                            // Duration updates handled by UI
                        }
                        // Natural end with an empty queue closes the mini-player
                        if (events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED) &&
                            player.playbackState == Player.STATE_ENDED &&
                            PlaybackQueue.nextOf(miniPlayerUri!!) == null
                        ) {
                            miniPlayerUri = null
                        }
                    }
                }
                controller.addListener(listener)
            }
        }
    }

    fun play(uri: String) {
        miniPlayerUri = uri
        playbackController?.play(uri)
    }

    fun pause() {
        playbackController?.pause()
    }

    fun resume() {
        playbackController?.resume()
    }

    fun next() {
        PlaybackQueue.nextOf(miniPlayerUri!!)?.let { next ->
            miniPlayerUri = next
            playbackController?.play(next)
        }
    }

    fun previous() {
        PlaybackQueue.previousOf(miniPlayerUri!!)?.let { prev ->
            miniPlayerUri = prev
            playbackController?.play(prev)
        }
    }

    fun toggleMute() {
        isMuted = !isMuted
        mediaController?.volume = if (isMuted) 0f else 1f
    }

    fun close() {
        playbackController?.pause()
        miniPlayerUri = null
    }

    fun getCurrentTitle(): String? {
        return miniPlayerUri?.let { Uri.decode(it).substringAfterLast('/') }
    }

    fun hasNext(): Boolean = PlaybackQueue.nextOf(miniPlayerUri!!) != null
    fun hasPrevious(): Boolean = PlaybackQueue.previousOf(miniPlayerUri!!) != null

    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    fun onDestroy() {
        mediaController = null
        playbackController = null
        miniPlayerUri = null
    }
}