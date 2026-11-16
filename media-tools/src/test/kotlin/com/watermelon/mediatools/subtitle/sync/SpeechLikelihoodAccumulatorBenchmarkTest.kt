package com.watermelon.mediatools.subtitle.sync

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

/**
 * Non-gating JVM microbenchmark for consume + finish on an 8-second, 16 kHz, mono PCM16 buffer.
 * Skipped unless -Dwatermelon.benchmarks=true is present in the test JVM.
 */
class SpeechLikelihoodAccumulatorBenchmarkTest {

    @Test
    fun measureSpeechLikelihoodAccumulatorBaseline() {
        assumeTrue(
            "Set -Dwatermelon.benchmarks=true to opt in",
            System.getProperty(BENCHMARK_PROPERTY) == "true",
        )

        val pcm = syntheticPcm16(sampleRate = 16_000, seconds = 8)
        report("speech_accumulator_pcm16_8s_16khz_mono") {
            val accumulator = SpeechLikelihoodAccumulator(
                targetStartUs = 0L,
                durationMs = 8_000L,
                bucketMs = 40,
            )
            accumulator.consume(
                buffer = pcm,
                bufferOffset = 0,
                bufferSize = pcm.limit(),
                presentationTimeUs = 0L,
                sampleRate = 16_000,
                channelCount = 1,
                encoding = PcmSampleEncoding.PCM_16,
            )
            accumulator.finish()
        }
    }

    private fun syntheticPcm16(sampleRate: Int, seconds: Int): ByteBuffer {
        val frameCount = sampleRate * seconds
        val buffer = ByteBuffer.allocate(frameCount * 2).order(ByteOrder.nativeOrder())
        repeat(frameCount) { sampleIndex ->
            val timeSeconds = sampleIndex.toDouble() / sampleRate
            val active = timeSeconds in 0.5..2.0 ||
                timeSeconds in 3.0..4.2 ||
                timeSeconds in 5.5..7.2
            val value = if (active) {
                val fundamental = 0.22 * sin(2.0 * PI * 170.0 * timeSeconds)
                val harmonic = 0.10 * sin(2.0 * PI * 370.0 * timeSeconds)
                ((fundamental + harmonic) * Short.MAX_VALUE).toInt().toShort()
            } else {
                0
            }
            buffer.putShort(value)
        }
        return buffer.flip() as ByteBuffer
    }

    private fun report(name: String, action: () -> Any) {
        repeat(WARMUP_ITERATIONS) { blackhole = action() }
        val raw = LongArray(TIMED_ITERATIONS) {
            val start = System.nanoTime()
            blackhole = action()
            System.nanoTime() - start
        }
        val sorted = raw.sorted()
        val median = if (sorted.size % 2 == 0) {
            sorted[sorted.size / 2 - 1] / 2L + sorted[sorted.size / 2] / 2L
        } else {
            sorted[sorted.size / 2]
        }
        println(
            "BENCH name=$name warmup=$WARMUP_ITERATIONS timed=$TIMED_ITERATIONS " +
                "raw_ns=${raw.joinToString(",")} median_ns=$median " +
                "mean_ns=${raw.average().toLong()} checksum=${blackhole?.hashCode()}"
        )
    }

    companion object {
        private const val BENCHMARK_PROPERTY = "watermelon.benchmarks"
        private const val WARMUP_ITERATIONS = 5
        private const val TIMED_ITERATIONS = 20

        @Volatile
        private var blackhole: Any? = null
    }
}
