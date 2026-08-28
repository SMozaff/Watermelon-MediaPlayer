package com.watermelon.mediatools.engine

import java.io.ByteArrayOutputStream
import kotlin.math.PI
import kotlin.math.sin
import org.junit.Assert.assertTrue
import org.junit.Test

class Mp3EncoderTest {

    @Test
    fun syntheticStereoPcmProducesMp3Frame() {
        val sampleRateHz = 44_100
        val pcm = sineWavePcm16(
            sampleRateHz = sampleRateHz,
            durationSeconds = 1,
            frequencyHz = 440.0,
            channels = 2
        )
        val encoded = ByteArrayOutputStream()
        val encoder = Mp3Encoder(sampleRateHz = sampleRateHz, numChannels = 2)

        try {
            encoder.encodeChunk(pcm, offset = 0, length = pcm.size, sink = encoded)
            encoder.flush(encoded)
        } finally {
            encoder.close()
        }

        val bytes = encoded.toByteArray()
        assertTrue("Synthetic PCM must produce encoded output", bytes.isNotEmpty())
        assertTrue(
            "Encoded output must contain an MPEG audio frame sync word",
            (0 until (bytes.size - 1)).any { index ->
                (bytes[index].toInt() and 0xFF) == 0xFF &&
                    (bytes[index + 1].toInt() and 0xE0) == 0xE0
            }
        )
    }

    private fun sineWavePcm16(
        sampleRateHz: Int,
        durationSeconds: Int,
        frequencyHz: Double,
        channels: Int
    ): ByteArray {
        val frames = sampleRateHz * durationSeconds
        return ByteArray(frames * channels * BYTES_PER_PCM16_SAMPLE).also { pcm ->
            var offset = 0
            for (frame in 0 until frames) {
                val sample = (
                    sin(2.0 * PI * frequencyHz * frame / sampleRateHz) *
                        Short.MAX_VALUE * 0.25
                    ).toInt().toShort()
                repeat(channels) {
                    pcm[offset++] = (sample.toInt() and 0xFF).toByte()
                    pcm[offset++] = ((sample.toInt() ushr 8) and 0xFF).toByte()
                }
            }
        }
    }

    private companion object {
        const val BYTES_PER_PCM16_SAMPLE = 2
    }
}
