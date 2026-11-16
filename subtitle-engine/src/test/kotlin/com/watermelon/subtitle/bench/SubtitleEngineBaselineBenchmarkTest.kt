package com.watermelon.subtitle.bench

import com.watermelon.common.model.ParsedSubtitle
import com.watermelon.common.model.SubtitleCue
import com.watermelon.common.subtitle.sync.ActivitySignature
import com.watermelon.common.subtitle.sync.CorrelationConfig
import com.watermelon.common.subtitle.sync.SyncMeasurement
import com.watermelon.subtitle.parser.SrtParser
import com.watermelon.subtitle.sync.ActivityCorrelatorImpl
import com.watermelon.subtitle.sync.OffsetConsensus
import com.watermelon.subtitle.sync.SubtitleSyncConfig
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sin

/**
 * Non-gating JVM microbenchmarks for the Kotlin implementations that are candidates for the
 * Kotlin-to-Rust migration. Skipped unless -Dwatermelon.benchmarks=true is present in the test JVM.
 *
 * These numbers are comparative local JVM observations, not Android ART/device measurements.
 */
class SubtitleEngineBaselineBenchmarkTest {

    @Test
    fun measureSubtitleEngineBaseline() {
        assumeTrue(
            "Set -Dwatermelon.benchmarks=true to opt in",
            System.getProperty(BENCHMARK_PROPERTY) == "true",
        )

        val config = SubtitleSyncConfig()
        val bucketMs = config.bucketMs
        val audioStartMs = 1_000_000L
        val audioBucketCount = (config.probeDurationMs / bucketMs).toInt()
        val audio = signature(audioStartMs, bucketMs, audioBucketCount)
        val correlator = ActivityCorrelatorImpl()

        // SubtitleProbeSelectorImpl chooses config.probeDurationMs (8 s) and up to
        // config.maxProbeCount (3) probes. SubtitleSyncCoordinator uses a +/- 30 s primary
        // search at bucketMs granularity, followed when needed by +/- 180 s at 200 ms
        // granularity and a +/- 2 s refinement at bucketMs granularity.
        val primaryContext = signature(
            startMs = audioStartMs - config.primarySearchMs,
            bucketMs = bucketMs,
            bucketCount = ((config.probeDurationMs + 2L * config.primarySearchMs) / bucketMs).toInt(),
        )
        val primarySearch = CorrelationConfig(
            minOffsetMs = -config.primarySearchMs,
            maxOffsetMs = config.primarySearchMs,
            stepMs = bucketMs.toLong(),
        )
        report("correlator.primary_8s_1700_buckets_1501_offsets") {
            correlator.correlate(audio, primaryContext, primarySearch)
        }

        val expandedContext = signature(
            startMs = audioStartMs - config.expandedSearchMs,
            bucketMs = bucketMs,
            bucketCount = ((config.probeDurationMs + 2L * config.expandedSearchMs) / bucketMs).toInt(),
        )
        val expandedSearch = CorrelationConfig(
            minOffsetMs = -config.expandedSearchMs,
            maxOffsetMs = config.expandedSearchMs,
            stepMs = config.expandedSearchStepMs,
        )
        report("correlator.expanded_8s_9200_buckets_1801_offsets") {
            correlator.correlate(audio, expandedContext, expandedSearch)
        }

        // Refinement is measured independently around a representative coarse estimate.
        val refinementSearch = CorrelationConfig(
            minOffsetMs = -config.refinementRadiusMs,
            maxOffsetMs = config.refinementRadiusMs,
            stepMs = bucketMs.toLong(),
        )
        report("correlator.refinement_8s_9200_buckets_101_offsets") {
            correlator.correlate(audio, expandedContext, refinementSearch)
        }

        val consensus = OffsetConsensus(config)
        report("consensus_3_measurements") { consensus.resolve(measurements(3)) }
        report("consensus_5_measurements") { consensus.resolve(measurements(5)) }

        val srt = syntheticSrt(cueCount = 2_000)
        report("srt_parser_2000_cues") { SrtParser.parse(srt, "en") }
    }

    private fun signature(startMs: Long, bucketMs: Int, bucketCount: Int): ActivitySignature {
        val occupancy = FloatArray(bucketCount) { index ->
            ((index * 37) % 101) / 100f
        }
        val onsets = FloatArray(bucketCount) { index ->
            ((index * 17) % 67) / 66f
        }
        return ActivitySignature(startMs, bucketMs, occupancy, onsets)
    }

    private fun measurements(count: Int): List<SyncMeasurement> =
        (0 until count).map { index ->
            val subtitleStartMs = 10_000L + index * 40_000L
            val offsetMs = 2_400L + index * 40L
            SyncMeasurement(
                subtitleProbeStartMs = subtitleStartMs,
                audioProbeStartMs = subtitleStartMs + offsetMs,
                estimatedOffsetMs = offsetMs,
                correlationScore = 0.92f,
                peakMargin = 0.12f,
                speechCoverage = 0.42f,
                transitionCount = 8,
                probeQuality = 0.95f,
            )
        }

    private fun syntheticSrt(cueCount: Int): String = buildString(cueCount * 72) {
        repeat(cueCount) { index ->
            val startMs = index * 1_400L
            val endMs = startMs + 900L
            append(index + 1)
            append('\n')
            append(timestamp(startMs))
            append(" --> ")
            append(timestamp(endMs))
            append('\n')
            append("Synthetic dialogue cue ")
            append(index + 1)
            append("\n\n")
        }
    }

    private fun timestamp(totalMs: Long): String {
        val hours = totalMs / 3_600_000L
        val minutes = (totalMs / 60_000L) % 60L
        val seconds = (totalMs / 1_000L) % 60L
        val millis = totalMs % 1_000L
        return String.format(Locale.ROOT, "%02d:%02d:%02d,%03d", hours, minutes, seconds, millis)
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
