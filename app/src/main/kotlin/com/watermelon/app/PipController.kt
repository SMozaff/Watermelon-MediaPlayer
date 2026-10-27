package com.watermelon.app

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Icon
import android.media.AudioManager
import android.os.Build
import android.util.Rational
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.watermelon.common.model.PlaybackState
import com.watermelon.common.util.FileLogger

/**
 * PiP plumbing, extracted from MainActivity in Phase 2b. Everything here operates on
 * the Activity through internal extensions so the state (playbackController,
 * mediaController, playbackMode) stays owned by the Activity instead of being plumbed
 * through yet another object graph.
 *
 * [PiPTier] mirrors the three window widths the PiP window can assume; each tier gets a
 * progressively richer set of remote actions (see buildPiPActions).
 */
internal enum class PiPTier { SMALL, MID, EXPANDED }

internal fun tierForWidth(widthDp: Int): PiPTier = when {
    widthDp < 200 -> PiPTier.SMALL
    widthDp < 400 -> PiPTier.MID
    else -> PiPTier.EXPANDED
}

/**
 * BroadcastReceiver for the PiP window's remote actions. Registered by
 * [registerPipReceiver] in onStart and unregistered in onStop; forwards button presses
 * to the shared PlaybackController, then refreshes the PiP action set so the
 * play/pause icon matches the new state.
 */
internal fun MainActivity.pipActionReceiver(): BroadcastReceiver =
    object : BroadcastReceiver() {
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
                    audioManager.setStreamVolume(
                        AudioManager.STREAM_MUSIC, if (cur == 0) max / 2 else 0, 0
                    )
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

/** Registers [receiver] for every PiP remote action. Call from onStart. */
internal fun MainActivity.registerPipReceiver(receiver: BroadcastReceiver) {
    val filter = IntentFilter().apply {
        addAction(PiPReceiver.ACTION_PLAY_PAUSE)
        addAction(PiPReceiver.ACTION_MUTE)
        addAction(PiPReceiver.ACTION_PREV)
        addAction(PiPReceiver.ACTION_NEXT)
        addAction(PiPReceiver.ACTION_REWIND)
        addAction(PiPReceiver.ACTION_FORWARD)
    }
    ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
}

@RequiresApi(Build.VERSION_CODES.O)
internal fun MainActivity.enterPipMode() {
    FileLogger.i("PiP", "enterPiPMode called — entering now")
    val ok = enterPictureInPictureMode(buildPiPParams(PiPTier.MID))
    FileLogger.i("PiP", "enterPictureInPictureMode returned $ok")
}

@RequiresApi(Build.VERSION_CODES.O)
private fun MainActivity.makePiPAction(action: String, iconRes: Int, title: String): android.app.RemoteAction {
    val intent = PendingIntent.getBroadcast(
        this, action.hashCode(),
        Intent(action).setPackage(packageName),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    return android.app.RemoteAction(
        Icon.createWithResource(this, iconRes), title, title, intent
    )
}

/** PiP remote actions per tier: the smallest window keeps only play/pause so the
 *  buttons stay tappable at that size. */
@RequiresApi(Build.VERSION_CODES.O)
internal fun MainActivity.buildPiPActions(tier: PiPTier): List<android.app.RemoteAction> {
    val isPlaying = playbackController?.playbackState?.value == PlaybackState.PLAYING
    val ppIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
    val playPause = makePiPAction(
        PiPReceiver.ACTION_PLAY_PAUSE, ppIcon,
        if (isPlaying) getString(R.string.pip_action_pause) else getString(R.string.pip_action_play)
    )
    val prev = makePiPAction(PiPReceiver.ACTION_PREV, android.R.drawable.ic_media_previous, getString(R.string.pip_action_previous))
    val next = makePiPAction(PiPReceiver.ACTION_NEXT, android.R.drawable.ic_media_next, getString(R.string.pip_action_next))
    val rew = makePiPAction(PiPReceiver.ACTION_REWIND, android.R.drawable.ic_media_rew, getString(R.string.pip_action_rewind))
    val fwd = makePiPAction(PiPReceiver.ACTION_FORWARD, android.R.drawable.ic_media_ff, getString(R.string.pip_action_forward))
    return when (tier) {
        PiPTier.SMALL -> listOf(playPause)
        PiPTier.MID -> listOf(prev, playPause, next)
        PiPTier.EXPANDED -> listOf(rew, prev, playPause, next, fwd)
    }
}

/** Builds the PiP window parameters: aspect ratio from the current video (16:9
 *  fallback until Media3 reports a real size) plus the tier's action set. */
@RequiresApi(Build.VERSION_CODES.O)
internal fun MainActivity.buildPiPParams(tier: PiPTier): PictureInPictureParams {
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
