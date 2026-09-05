package com.watermelon.app.managers

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Icon
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import com.watermelon.common.controller.PlaybackController
import com.watermelon.common.model.PlaybackMode
import com.watermelon.common.model.PlaybackState
import com.watermelon.app.PiPReceiver
import com.watermelon.common.util.FileLogger

/**
 * Handles Picture-in-Picture mode lifecycle and actions.
 */
class PiPManager(
    private val context: Context,
    private val audioManager: AudioManager,
    private val playbackControllerProvider: () -> PlaybackController?,
    private val mediaControllerProvider: () -> MediaController?,
) : LifecycleObserver {

    private var playbackMode: PlaybackMode = PlaybackMode.NORMAL
    var isPiPActive: Boolean get() = playbackMode == PlaybackMode.PIP

    private val pipActionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val controller = playbackControllerProvider() ?: return
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
                val tier = tierForWidth(context.resources.configuration.screenWidthDp)
                (context as? android.app.Activity)?.setPictureInPictureParams(buildPiPParams(tier))
            }
        }
    }

    private fun seekRelative(controller: PlaybackController, deltaMs: Long) {
        val pos = controller.currentPositionMs.value
        controller.seekTo((pos + deltaMs).coerceAtLeast(0))
    }

    fun registerReceiver() {
        val filter = IntentFilter().apply {
            addAction(PiPReceiver.ACTION_PLAY_PAUSE)
            addAction(PiPReceiver.ACTION_MUTE)
            addAction(PiPReceiver.ACTION_PREV)
            addAction(PiPReceiver.ACTION_NEXT)
            addAction(PiPReceiver.ACTION_REWIND)
            addAction(PiPReceiver.ACTION_FORWARD)
        }
        ContextCompat.registerReceiver(
            context, pipActionReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    fun unregisterReceiver() {
        context.unregisterReceiver(pipActionReceiver)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun enterPiPMode(activity: android.app.Activity) {
        FileLogger.i("PiP", "enterPiPMode called — entering now")
        val ok = activity.enterPictureInPictureMode(buildPiPParams(PiPTier.MID))
        FileLogger.i("PiP", "enterPictureInPictureMode returned $ok")
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun makePiPAction(action: String, iconRes: Int, title: String): android.app.RemoteAction {
        val intent = PendingIntent.getBroadcast(
            context, action.hashCode(),
            Intent(action).setPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return android.app.RemoteAction(
            Icon.createWithResource(context, iconRes), title, title, intent
        )
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun buildPiPActions(tier: PiPTier): List<android.app.RemoteAction> {
        val isPlaying = playbackControllerProvider()?.playbackState?.value == PlaybackState.PLAYING
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
        val videoWidth = mediaControllerProvider()?.videoSize?.width ?: 16
        val videoHeight = mediaControllerProvider()?.videoSize?.height ?: 9
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

    fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        if (isInPictureInPictureMode && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val tier = tierForWidth(newConfig.screenWidthDp)
            FileLogger.i("PiP", "size change: width=${newConfig.screenWidthDp}dp -> tier=$tier")
            (context as? android.app.Activity)?.setPictureInPictureParams(buildPiPParams(tier))
        } else {
            FileLogger.i("PiP", "exited PiP — resetting playbackMode to NORMAL")
            playbackMode = PlaybackMode.NORMAL
        }
    }

    fun onUserLeaveHint() {
        val controller = playbackControllerProvider()
        val isPlaying = controller?.playbackState?.value == PlaybackState.PLAYING
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            playbackMode == PlaybackMode.NORMAL && isPlaying
        ) {
            playbackMode = PlaybackMode.PIP
            (context as? android.app.Activity)?.let { enterPiPMode(it) }
        } else if (isPiPActive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            (context as? android.app.Activity)?.let { enterPiPMode(it) }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private enum class PiPTier { SMALL, MID, EXPANDED }
}