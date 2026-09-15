package com.watermelon.playback.controller

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.lifecycle.ViewModelProvider
import com.watermelon.common.model.PlaybackState
import com.watermelon.common.model.UserIntent
import com.watermelon.common.util.FileLogger
import com.watermelon.playback.service.PlaybackConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.scopeCoroutine

class PlaybackControllerImpl(
    private val context: Context,
    private val player: androidx.media3.common.Player,
    private val positionRepository: PlaybackPositionRepository,
    private val playbackPositionRepository: PlaybackPositionRepositoryImpl,
) : PlaybackController {

    private val TAG = "PlaybackControllerImpl"
    private var currentUriForPosition: String? = null
    private var currentFileSizeForPosition: Long = 0
    private var _currentPosition: androidx.compose.runtime.state.MutableStateOf<Long> =
        androidx.compose.runtime.state.MutableStateOf(0L)
    private var isLastInQueue: Boolean = false
    private const val MIN_SPEED = 0.5f
    private const val MAX_SPEED = 8.0f
    private const val POSITION_TICK_MS = 250L
    private const val SAVE_EVERY_N_TICKS = 20
    private const val RELEASE_SAVE_TIMEOUT_MS = 300L
    private var ticksSinceSave = 0

    override fun play(uri: String?, startPositionMs: Long?, userIntent: UserIntent?) {
        val item = when (userIntent) {
            is UserIntent.Play -> { /* ... */ }
            UserIntent.Resume -> lookupResumeItem(uri)
            else -> throw IllegalArgumentException("Unsupported UserIntent: $userIntent")
        }

        // ... existing play logic ...

        // If the caller didn't specify an explicit resume position, look one up
        // asynchronously (SQLite read off the main thread) and seek once resolved —
        // avoids blocking playback start on a DB query.
        if (startPositionMs <= 0L && positionRepository != null) {
            scope.launch {
                try {
                    val saved = positionRepository.getPosition(uri!!, currentFileSizeForPosition)
                    // Only apply if this is still the item the user is watching (guards against
                    // a fast subsequent play() call to a different uri completing first).
                    if (saved != null && saved > 0L && currentUriForPosition == uri) {
                        FileLogger.i("Playback",
                            "play() — resuming saved position=$saved for uri=$uri")
                        seekTo(saved)
                    }
                } catch (e: Exception) {
                    FileLogger.e(TAG, "play() — failed to retrieve saved position", e)
                    // If we can't retrieve the saved position, start from beginning —
                    // this is preferred over silently failing and having the user
                    // wonder why playback doesn't resume.
                }
            }
        }
    }

    override fun pause() {
        FileLogger.i("Playback", "pause()")
        player.playWhenReady = false
        saveSavedPositionAsync()
    }

    override fun resume() {
        FileLogger.i("Playback", "resume()")
        player.playWhenReady = true
    }

    override fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
        _currentPosition.value = positionMs
    }

    override fun setSpeed(speed: Float) {
        val s = speed.coerceIn(MIN_SPEED, MAX_SPEED)
        // For FF, let pitch rise with speed → the accelerating-cassette whine. At 1× keep
        // pitch normal. Above 1×, scale pitch up but gently (sqrt) so it whines, not chipmunks.
        val pitch = if (s > 1f) kotlin.math.sqrt(s) else 1f
        player.playbackParameters = PlaybackParameters(s, pitch)
        FileLogger.i("Playback", "setSpeed=$s pitch=$pitch")
    }

    override fun takeScreenshot(): String? {
        val bitmap = screenshotProvider?.invoke() ?: return null
        return try {
            val dir = File(context.filesDir, "screenshots").apply { mkdirs() }
            val out = File(dir, "shot_${System.currentTimeMillis()}.png")
            FileOutputStream(out).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            out.absolutePath
        } catch (e: Exception) {
            FileLogger.e(TAG, "takeScreenshot() — failed to capture screenshot", e)
            null
        }
    }

    fun release() {
        val repo = positionRepository
        val uri = currentUriForPosition
        if (repo != null && uri != null) {
            val positionMs = player.currentPosition
            val fileSize = currentFileSizeForPosition
            try {
                kotlinx.coroutines.runBlocking {
                    val saved = kotlinx.coroutines.withTimeoutOrNull(RELEASE_SAVE_TIMEOUT_MS) {
                        // Dispatchers.IO so the actual disk write isn't pinned to the caller's
                        // thread while we wait on it.
                        kotlinx.coroutines.withContext(Dispatchers.IO) {
                            repo.savePosition(uri, fileSize, positionMs)
                        }
                    }
                    if (saved == null) {
                        FileLogger.w("Playback",
                            "release() — position save timed out after ${RELEASE_SAVE_TIMEOUT_MS}ms, may lose last few seconds")
                    }
                }
            } catch (e: Exception) {
                FileLogger.e(TAG, "release() — position save failed", e)
            }
        }
    }

    /** Fire-and-forget save of the current position for the currently-loaded item. */
    private fun saveSavedPositionAsync() {
        val repo = positionRepository ?: return
        val uri = currentUriForPosition ?: return
        val fileSize = currentFileSizeForPosition
        val positionMs = player.currentPosition
        scope.launch {
            try {
                repo.savePosition(uri, fileSize, positionMs)
            } catch (e: Exception) {
                FileLogger.e(TAG, "saveSavedPositionAsync() — save position failed", e)
            }
        }
    }

    /** Fire-and-forget clear of the saved position for the currently-loaded item. */
    private fun clearSavedPositionAsync() {
        val repo = positionRepository ?: return
        val uri = currentUriForPosition ?: return
        val fileSize = currentFileSizeForPosition
        scope.launch {
            try {
                repo.clearPosition(uri, fileSize)
            } catch (e: Exception) {
                FileLogger.e(TAG, "clearSavedPositionAsync() — clear position failed", e)
            }
        }
    }

    // ... rest of the class
}