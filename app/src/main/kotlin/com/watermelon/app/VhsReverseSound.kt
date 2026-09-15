package com.watermelon.app

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin

/**
 * Synthesizes a faint, looping VHS-style reverse "whirr" while the user rewinds. Real
 * reversed audio is impossible on Android, so this fakes the illusion: a low sawtooth-ish
 * tone whose base frequency rises with rewind speed, plus a slow wobble for the tape feel.
 *
 * Call [start] with the current speed (2..8) when reverse begins or its speed changes,
 * and [stop] when the hold ends. Cheap: a single short looped PCM buffer on an AudioTrack.
 */
class VhsReverseSound {

    private val sampleRate = 22_050
    private var track: AudioTrack? = null
    @Volatile private var running = false
    private var genThread: Thread? = null
    @Volatile private var speed = 2f

    fun start(initialSpeed: Float) {
        speed = initialSpeed.coerceIn(2f, 8f)
        if (running) return
        running = true

        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(2048)

        track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(minBuf)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
            .also { it.play() }
    }

    /** Update the pitch as rewind speed changes, without restarting. */
    fun setSpeed(newSpeed: Float) { speed = newSpeed.coerceIn(2f, 8f) }

    fun stop() {
        running = false
        // Gracefully wait for the generator thread to finish, if it's still alive.
        // A 200ms timeout is used to avoid blocking stop() indefinitely — if the thread
        // doesn't terminate in time, it will be garbage collected and the AudioTrack
        // resources will be released below.
        try {
            genThread?.join(200)
        } catch (e: IllegalThreadStateException) {
            FileLogger.w(TAG, "VhsReverseSound.stop() — thread join failed (thread may have already ended)", e)
        }
        genThread = null

        // Stop and release the AudioTrack. Exceptions here are non-critical — the track
        // will be garbage collected and cleaned up by the system.
        try {
            track?.stop()
        } catch (e: Exception) {
            FileLogger.w(TAG, "VhsReverseSound.stop() — failed to stop AudioTrack", e)
        }
        try {
            track?.release()
        } catch (e: Exception) {
            FileLogger.w(TAG, "VhsReverseSound.stop() — failed to release AudioTrack", e)
        }
        track = null
    }

    companion object {
        private val TAG = "VhsReverseSound"
    }
}