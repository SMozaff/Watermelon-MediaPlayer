package com.watermelon.mediatools.engine

import net.sourceforge.lame.lowlevel.LameEncoder
import net.sourceforge.lame.mp3.Lame
import net.sourceforge.lame.mpeg.MPEGMode
import java.io.OutputStream
import javax.sound.sampled.AudioFormat

/**
 * Wraps java-lame's [LameEncoder] -- a pure-Java, LGPL port of LAME (no JNI/NDK, unlike the
 * earlier libmp3lame-via-JNI plan, which was abandoned because this sandbox has no network
 * access to fetch and vendor libmp3lame's C source; see media-tools/build.gradle.kts for
 * the dependency coordinates and their provenance).
 *
 * API shape (constructor, encodeBuffer, encodeFinish, close, getPCMBufferSize) confirmed via
 * web search against the library's own README/tests -- not run/compiled in this sandbox.
 *
 * Usage: feed interleaved 16-bit PCM bytes via [encodeChunk] using chunks of exactly
 * [pcmBufferSize] bytes (matching java-lame's own recommended pattern), call [flush] once
 * at end-of-stream, then [close].
 */
class Mp3Encoder(
    sampleRateHz: Int,
    numChannels: Int,
    /** Target bitrate in kbps (not bits/sec -- java-lame's constructor takes kbps directly). */
    bitrateKbps: Int = 128,
) {
    private val channelMode = if (numChannels >= 2) MPEGMode.STEREO else MPEGMode.MONO

    private val audioFormat = AudioFormat(
        sampleRateHz.toFloat(),
        /* sampleSizeInBits = */ 16,
        numChannels,
        /* signed = */ true,
        /* bigEndian = */ false,
    )

    private val encoder = LameEncoder(
        audioFormat,
        bitrateKbps,
        channelMode,
        Lame.QUALITY_HIGHEST,
        /* VBR = */ false,
    )

    /** Feed PCM in chunks of this size for best results, per java-lame's own recommendation. */
    val pcmBufferSize: Int get() = encoder.pcmBufferSize

    private val outBuffer = ByteArray(pcmBufferSizeSafe(encoder))

    /** Encodes one chunk of interleaved 16-bit PCM bytes and writes resulting MP3 bytes to [sink]. */
    fun encodeChunk(pcm: ByteArray, offset: Int, length: Int, sink: OutputStream) {
        val written = encoder.encodeBuffer(pcm, offset, length, outBuffer)
        if (written > 0) sink.write(outBuffer, 0, written)
    }

    /** Call once after the last [encodeChunk], before [close]. */
    fun flush(sink: OutputStream) {
        val written = encoder.encodeFinish(outBuffer)
        if (written > 0) sink.write(outBuffer, 0, written)
    }

    fun close() {
        encoder.close()
    }

    companion object {
        // Encoder's output buffer needs to be sized independently of the input PCM buffer;
        // java-lame's own examples reuse a buffer sized off getPCMBufferSize(), which is
        // input-side sizing, not a documented output-side guarantee. Using a generous fixed
        // size instead, matching the same "worst case ~1.25x + 7200 bytes" LAME guidance
        // used in the previous JNI version, since that guidance is about LAME's actual
        // encoding overhead, not specific to the JNI vs pure-Java wrapper.
        private fun pcmBufferSizeSafe(encoder: LameEncoder): Int =
            maxOf(encoder.pcmBufferSize, 32 * 1024)
    }
}
