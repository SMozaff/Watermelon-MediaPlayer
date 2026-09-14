package com.watermelon.app

import androidx.media3.common.Player
import androidx.media3.session.MediaController
import com.watermelon.common.controller.PlaybackController
import com.watermelon.common.model.PlaybackState
import com.watermelon.common.model.PlaybackMode
import com.watermelon.playback.controller.PlaybackControllerImpl
import com.watermelon.playback.service.PlaybackConnection
import com.watermelon.storage.db.WatermelonDatabase
import com.watermelon.storage.repository.PlaybackPositionRepositoryImpl

val playbackConnection: PlaybackConnection by lazy { PlaybackConnection(applicationContext) }
var mediaController: MediaController? by mutableStateOf(null)
var playbackController: PlaybackController? = null

fun initPlayback(controller: MediaController?) {
    mediaController = controller
    playbackController = PlaybackControllerImpl(
        context = applicationContext,
        player = controller,
        positionRepository = PlaybackPositionRepositoryImpl(WatermelonDatabase(applicationContext))
    )
}

fun seekRelative(deltaMs: Long) {
    playbackController?.currentPositionMs.value?.let { pos ->
        playbackController?.seekTo((pos + deltaMs).coerceAtLeast(0))
    }
}