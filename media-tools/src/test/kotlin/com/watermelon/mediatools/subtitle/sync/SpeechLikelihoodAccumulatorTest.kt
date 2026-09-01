package com.watermelon.mediatools.subtitle.sync

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

class SpeechLikelihoodAccumulatorTest {
    @Test
    fun silenceAndSpeechLikeBurstsProduceDistinctActivity() {
        val sampleRate = 16_000
        val seconds = 8
        val samples = sampleRate * seconds
        val pcm = ByteBuffer.allocate(samples * 2).order(ByteOrder.nativeOrder())
        for (i in 0 until samples) {
            val t = i.toDouble() / sampleRate
            val active = t in 1.0..2.0 || t in 3.0..4.2 || t in 5.5..6.4
            val value = if (active) {
                val fundamental = 0.22 * sin(2.0 * PI * 170.0 * t)
                val harmonic = 0.10 * sin(2.0 * PI * 370.0 * t)
                ((fundamental + harmonic) * 32767.0).toInt().toShort()
            } else 0
            pcm.putShort(value)
        }
        pcm.flip()

        val accumulator = SpeechLikelihoodAccumulator(0L, seconds * 1_000L, 40)
        accumulator.consume(
            pcm, 0, pcm.remaining(), 0L, sampleRate, 1, PcmSampleEncoding.PCM_16
        )
        val signature = accumulator.finish()

        assertTrue(signature.occupancy.take(20).maxOrNull()!! < 0.20f)
        assertTrue(signature.occupancy.count { it > 0.35f } > 40)
        assertTrue(signature.onsets.count { it > 0.10f } > 2)
    }
}
