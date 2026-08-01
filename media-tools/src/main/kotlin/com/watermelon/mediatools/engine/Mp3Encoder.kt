package com.watermelon.mediatools.engine

import net.sourceforge.lame.mp3.Lame
import net.sourceforge.lame.mp3.MPEGMode
import net.sourceforge.lame.mp3.VbrMode
import java.io.OutputStream

/**
 * Wraps java-lame's low-level [Lame]/`LameGlobalFlags` API directly -- a pure-Java, LGPL
 * port of LAME (no JNI/NDK, unlike the earlier libmp3lame-via-JNI plan, which was abandoned
 * because this sandbox has no network access to fetch and vendor libmp3lame's C source; see
 * media-tools/build.gradle.kts for the java-lame dependency's own provenance).
 *
 * IMPORTANT, corrected after a real CI build failure: this class originally wrapped
 * java-lame's higher-level `net.sourceforge.lame.lowlevel.LameEncoder` convenience class.
 * That failed to compile on Android with "Unresolved reference" / "Cannot access class
 * javax.sound.sampled.AudioFormat" -- LameEncoder's public constructor requires
 * javax.sound.sampled.AudioFormat, which is part of desktop Java SE's java.desktop module
 * and does not exist on Android at all (confirmed via web search: this throws
 * ClassNotFoundException on Android even when present at compile time via a third-party
 * stub -- and no complete/working stub was found either, see project history).
 *
 * The fix: bypass LameEncoder entirely and drive `Lame`/`LameGlobalFlags` directly, the same
 * way java-lame's own `LameDecoder.java` does internally (confirmed by directly fetching
 * that file's real source this session) -- `new Lame()`, configure via `lame.getFlags()`,
 * `lame.initParams()`, `lame.encodeBuffer(...)`, `lame.encodeFlush(...)`, `lame.close()`.
 * None of this touches javax.sound.sampled. The float-array PCM conversion in
 * [interleavedPcm16ToFloatChannels] replicates what LameEncoder's own (now-unused)
 * doEncodeBuffer did internally -- confirmed by directly fetching LameEncoder.java's real
 * source and reading that method, not guessed.
 *
 * Also fixed the same session: the MPEGMode import was wrong
 * (net.sourceforge.lame.mpeg.MPEGMode, which doesn't exist) -- the real package, confirmed
 * from MPEGMode.java's own source, is net.sourceforge.lame.mp3.MPEGMode.
 *
 * NOT run/compiled in this sandbox. Every method/field referenced here was confirmed by
 * directly fetching the real java-lame source this session (LameEncoder.java, Encoder.java,
 * MPEGMode.java, LameDecoder.java) -- not inferred or guessed -- but "confirmed by reading
 * source" is still not the same as "verified by compiling."
 *
 * Usage: feed interleaved 16-bit PCM bytes via [encodeChunk], call [flush] once at
 * end-of-stream, then [close]. One instance = one encode session (not reusable).
 */
class Mp3Encoder(
    sampleRateHz: Int,
    private val numChannels: Int,
    /** Target bitrate in kbps. */
    bitrateKbps: Int = 128,
) {
    private val lame = Lame()

    init {
        val flags = lame.flags
        flags.setInNumChannels(numChannels)
        flags.setInSampleRate(sampleRateHz)
        flags.setMode(if (numChannels >= 2) MPEGMode.STEREO else MPEGMode.MONO)
        flags.setVBR(VbrMode.vbr_off)
        flags.setBitRate(bitrateKbps)
        flags.setQuality(Lame.QUALITY_HIGHEST)
        flags.setWriteId3tagAutomatic(false)
        val rc = lame.initParams()
        check(rc >= 0) { "Lame.initParams() failed with code $rc -- parameters not supported by LAME" }
    }

    // Generous fixed size, matching LAME's own "worst case ~1.25x input + 7200 bytes"
    // encoding-overhead guidance (same reasoning used in the earlier JNI version -- this is
    // about LAME's actual algorithm, not specific to which Java wrapper calls it).
    private val outBuffer = ByteArray(32 * 1024)

    /** Encodes one chunk of interleaved 16-bit PCM bytes and writes resulting MP3 bytes to [sink]. */
    fun encodeChunk(pcm: ByteArray, offset: Int, length: Int, sink: OutputStream) {
        val (left, right) = interleavedPcm16ToFloatChannels(pcm, offset, length, numChannels)
        val written = lame.encodeBuffer(left, right, left.size, outBuffer)
        if (written > 0) sink.write(outBuffer, 0, written)
        else if (written < 0) error("Lame.encodeBuffer error code=$written")
    }

    /** Call once after the last [encodeChunk], before [close]. */
    fun flush(sink: OutputStream) {
        val written = lame.encodeFlush(outBuffer)
        if (written > 0) sink.write(outBuffer, 0, written)
    }

    fun close() {
        lame.close()
    }

    companion object {
        /**
         * Converts interleaved 16-bit little-endian PCM bytes into the per-channel float
         * arrays Lame.encodeBuffer expects. Replicates the logic in java-lame's own
         * LameEncoder.doEncodeBuffer (confirmed by fetching that file's real source this
         * session) for the 16-bit-sample case specifically -- that method handles 1/2/3/4
         * byte sample sizes generically; this only needs the 16-bit path since
         * AudioExtractor's decoder output is assumed 16-bit PCM (see AudioExtractor's own
         * doc for that assumption's caveats).
         *
         * Mono input duplicates the single channel into both left/right, matching
         * LameEncoder's own mono handling.
         */
        internal fun interleavedPcm16ToFloatChannels(
            pcm: ByteArray, offset: Int, length: Int, numChannels: Int,
        ): Pair<FloatArray, FloatArray> {
            val bytesPerSample = 2
            val totalSamples = length / bytesPerSample
            val samplesPerChannel = totalSamples / numChannels
            val left = FloatArray(samplesPerChannel)
            val right = FloatArray(samplesPerChannel)

            var pos = offset
            for (i in 0 until samplesPerChannel) {
                // Little-endian 16-bit signed sample -> float. Uses the standard
                // 16-bit-PCM-to-float conversion (raw sample value, sign-extended via
                // toShort()), matching the numeric range Lame's encodeBuffer(float[],
                // float[], ...) expects (confirmed against LameEncoder.doEncodeBuffer's own
                // real source, which does the equivalent bit-shifting internally).
                val lo = pcm[pos].toInt() and 0xFF
                val hi = pcm[pos + 1].toInt()
                val sampleLeft = ((hi shl 8) or lo).toShort().toFloat()
                left[i] = sampleLeft
                pos += bytesPerSample

                if (numChannels >= 2) {
                    val lo2 = pcm[pos].toInt() and 0xFF
                    val hi2 = pcm[pos + 1].toInt()
                    right[i] = ((hi2 shl 8) or lo2).toShort().toFloat()
                    pos += bytesPerSample
                } else {
                    right[i] = sampleLeft
                }
            }
            return left to right
        }
    }
}
