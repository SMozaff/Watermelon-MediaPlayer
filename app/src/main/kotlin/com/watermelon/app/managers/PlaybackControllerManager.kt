package com.watermelon.app.managers

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import com.watermelon.common.controller.PlaybackController
import com.watermelon.common.repository.PlaybackPositionRepository
import com.watermelon.playback.controller.PlaybackControllerImpl
import com.watermelon.playback.service.PlaybackConnection
import com.watermelon.storage.repository.PlaybackPositionRepositoryImpl
import kotlinx.coroutines.CoroutineScope

/**
 * Manages the MediaController and PlaybackController lifecycle.
 * Handles connection, disconnection, and provides access to the controllers.
 */
@UnstableApi
class PlaybackControllerManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val database: com.watermelon.storage.db.WatermelonDatabase,
) : LifecycleObserver {

    private val playbackPositionRepository = PlaybackPositionRepositoryImpl(database)
    private val playbackConnection = PlaybackConnection(context)

    private var mediaController: MediaController? = null
    private var playbackController: PlaybackController? = null

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    fun onStart() {
        if (playbackController == null) {
            playbackConnection.connect { controller ->
                mediaController = controller
                playbackController = PlaybackControllerImpl(
                    context = context,
                    player = controller,
                    positionRepository = playbackPositionRepository
                )
            }
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    fun onStop() {
        if (playbackController != null) {
            (playbackController as? PlaybackControllerImpl)?.release()
            playbackConnection.release()
            mediaController = null
            playbackController = null
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    fun onDestroy() {
        playbackConnection.release()
    }

    fun getMediaController(): MediaController? = mediaController
    fun getPlaybackController(): PlaybackController? = playbackController

    fun isConnected(): Boolean = mediaController != null && playbackController != null
}