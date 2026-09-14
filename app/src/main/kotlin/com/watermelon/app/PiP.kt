package com.watermelon.app

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.media3.common.PlaybackState
import androidx.media3.common.Player

private val pipActionReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val controller = playbackController ?: return
        when (intent.action) {
            "play_pause" -> {
                val state = controller.playbackState.value
                if (state == PlaybackState.PLAYING) controller.pause()
                else controller.resume()
            }
            "mute" -> {
                val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                val cur = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, if (cur == 0) max / 2 else 0, 0)
            }
            "prev" -> seekRelative(-30_000)
            "next" -> seekRelative(30_000)
            "rewind" -> seekRelative(-10_000)
            "forward" -> seekRelative(10_000)
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

private val isPiPActive: Boolean get() = playbackMode == PlaybackMode.PIP

private val requiredPermissions: Array<String> = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
        arrayOf(android.Manifest.permission.READ_MEDIA_VIDEO)
    else -> arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
}

private val permissionLauncher = registerForActivityResult(
    androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
) { results ->
    // permission handling
}

private enum class PiPTier { SMALL, MID, EXPANDED }

private fun tierForWidth(widthDp: Int): PiPTier = when {
    widthDp < 200 -> PiPTier.SMALL
    widthDp < 400 -> PiPTier.MID
    else -> PiPTier.EXPANDED
}

private fun buildPiPParams(tier: PiPTier): android.app.PictureInPictureParams {
    val videoWidth = mediaController?.videoSize?.width ?: 16
    val videoHeight = mediaController?.videoSize?.height ?: 9
    val rational = if (videoWidth > 0 && videoHeight > 0)
        android.util.Rational(videoWidth, videoHeight) else android.util.Rational(16, 9)
    return android.app.PictureInPictureParams.Builder()
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

private fun buildPiPActions(tier: PiPTier): List<android.app.RemoteAction> {
    val isPlaying = playbackController?.playbackState?.value == PlaybackState.PLAYING
    val ppIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
    val playPause = makePiPAction("play_pause", ppIcon, if (isPlaying) "Pause" else "Play")
    val prev = makePiPAction("prev", android.R.drawable.ic_media_previous, "Previous")
    val next = makePiPAction("next", android.R.drawable.ic_media_next, "Next")
    val rew = makePiPAction("rewind", android.R.drawable.ic_media_rew, "Rewind 10s")
    val fwd = makePiPAction("forward", android.R.drawable.ic_media_ff, "Forward 10s")
    return when (tier) {
        PiPTier.SMALL -> listOf(playPause)
        PiPTier.MID -> listOf(prev, playPause, next)
        PiPTier.EXPANDED -> listOf(rew, prev, playPause, next, fwd)
    }
}

private fun makePiPAction(action: String, iconRes: Int, title: String): android.app.RemoteAction {
    val intent = PendingIntent.getBroadcast(
        /* context */ applicationContext, action.hashCode(),
        Intent(action).setPackage(applicationContext.packageName),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    return android.app.RemoteAction(
        android.graphics.drawable.Icon.createWithResource(applicationContext, iconRes), title, title, intent
    )
}