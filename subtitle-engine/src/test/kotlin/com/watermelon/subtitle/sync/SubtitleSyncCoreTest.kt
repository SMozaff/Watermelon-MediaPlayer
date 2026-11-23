package com.watermelon.subtitle.sync

import com.watermelon.common.model.ParsedSubtitle
import com.watermelon.common.model.SubtitleCue
import com.watermelon.common.subtitle.SubtitleOffsetConsensus
import com.watermelon.common.subtitle.sync.ActivityCorrelator
import com.watermelon.common.subtitle.sync.ActivitySignature
import com.watermelon.common.subtitle.sync.CorrelationConfig
import com.watermelon.common.subtitle.sync.CorrelationResult
import com.watermelon.common.subtitle.sync.ProbeCandidate
import com.watermelon.common.subtitle.sync.SpeechProbeResult
import com.watermelon.common.subtitle.sync.SpeechProbeSource
import com.watermelon.common.subtitle.sync.SubtitleActivityBuilder
import com.watermelon.common.subtitle.sync.SubtitleProbeSelector
import com.watermelon.common.subtitle.sync.SubtitleSyncProfile
import com.watermelon.common.subtitle.sync.SubtitleSyncRepository
import com.watermelon.common.subtitle.sync.SubtitleSyncRequest
import com.watermelon.common.subtitle.sync.SubtitleSyncModel
import com.watermelon.common.subtitle.sync.SubtitleSyncResult
import com.watermelon.common.subtitle.sync.SyncMeasurement
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Kotlin-side coverage that remains after the Rust cutover. Correlation, activity building,
 * probe selection and consensus characterization moved with their ports to Rust; the fakes
 * below only supply those ports to the coordinator orchestration tests.
 */
class SubtitleSyncCoreTest {
    private val fakeBuilder = object : SubtitleActivityBuilder {
        override fun build(
            subtitle: ParsedSubtitle,
            startMs: Long,
            endMs: Long,
            bucketMs: Int,
        ): ActivitySignature = ActivitySignature(
            startMs = startMs,
            bucketMs = bucketMs,
            occupancy = FloatArray(1) { 1f },
            onsets = FloatArray(1),
        )
    }

    private val fakeCorrelator = object : ActivityCorrelator {
        override fun correlate(
            audio: ActivitySignature,
            subtitleContext: ActivitySignature,
            config: CorrelationConfig,
        ): CorrelationResult = CorrelationResult(
            offsetMs = 2_500L,
            peakScore = 0.9f,
            secondPeakScore = 0.1f,
            peakMargin = 0.18f,
        )
    }

    private val fakeConsensus = object : SubtitleOffsetConsensus {
        override fun resolve(measurements: List<SyncMeasurement>): SubtitleSyncResult =
            if (measurements.size >= 2) {
                SubtitleSyncResult.Synchronized(
                    model = SubtitleSyncModel.Offset(2_500L),
                    confidence = 0.9f,
                    measurements = measurements,
                )
            } else {
                SubtitleSyncResult.LowConfidence(measurements)
            }

        override fun hasUsefulProvisionalOffset(measurements: List<SyncMeasurement>): Boolean =
            measurements.lastOrNull()?.estimatedOffsetMs == 2_500L
    }

    @Test
    fun fingerprint_isStableAndTimingSensitive() {
        val provider = SubtitleFingerprintProvider()
        assertEquals(provider.fingerprint(patternedSubtitle(0)), provider.fingerprint(patternedSubtitle(0)))
        assertTrue(provider.fingerprint(patternedSubtitle(0)) != provider.fingerprint(patternedSubtitle(40)))
    }

    @Test
    fun coordinator_usesAdaptiveSecondProbeAndPersistsAcceptedOffset() = runBlocking {
        val longSubtitle = longPatternedSubtitle(0L)
        val shiftedAudioTimeline = longPatternedSubtitle(2_500L)
        val repo = FakeSyncRepository()
        val probeStarts = mutableListOf<Long>()
        val probe = object : SpeechProbeSource {
            override suspend fun probe(
                mediaUri: String, targetPositionMs: Long, durationMs: Long, bucketMs: Int
            ): SpeechProbeResult {
                probeStarts += targetPositionMs
                val signature = fakeBuilder.build(
                    shiftedAudioTimeline, targetPositionMs, targetPositionMs + durationMs, bucketMs
                )
                return SpeechProbeResult.Success(signature, targetPositionMs, durationMs)
            }
        }
        val selector = object : SubtitleProbeSelector {
            override fun select(
                subtitle: ParsedSubtitle, mediaDurationMs: Long, maxCount: Int
            ): List<ProbeCandidate> = listOf(
                ProbeCandidate(17_000L, 8_000L, 0.95f),
                ProbeCandidate(49_000L, 8_000L, 0.95f),
                ProbeCandidate(80_000L, 8_000L, 0.95f),
            )
        }
        val productionConfig = SubtitleSyncConfig()
        val coordinator = SubtitleSyncCoordinator(
            repository = repo,
            probeSelector = selector,
            subtitleActivityBuilder = fakeBuilder,
            speechProbeSource = probe,
            correlator = fakeCorrelator,
            consensus = fakeConsensus,
            config = productionConfig,
        )

        val result = coordinator.synchronize(
            SubtitleSyncRequest(
                mediaId = "media",
                mediaUri = "media",
                mediaFileSize = 1L,
                mediaDurationMs = 110_000L,
                subtitleFingerprint = "fp",
                subtitleLanguage = "en",
                subtitle = longSubtitle,
                playbackSessionId = 1L,
            )
        )

        assertTrue(result is SubtitleSyncResult.Synchronized)
        val model = (result as SubtitleSyncResult.Synchronized).model as SubtitleSyncModel.Offset
        assertTrue(abs(model.offsetMs - 2_500L) <= 80L)
        assertEquals(1, repo.saveCount)
        assertTrue(probeStarts.size >= 2)
        // After the first useful estimate, the second probe targets subtitle time + provisional offset.
        assertTrue(probeStarts[1] > 49_000L)
    }

    @Test
    fun coordinator_manualProfileWinsWithoutAudioProbe() = runBlocking {
        val repo = FakeSyncRepository().apply {
            profile = SubtitleSyncProfile(
                mediaId = "media",
                mediaFileSize = 1L,
                subtitleFingerprint = "fp",
                manualOffsetMs = -1_200L,
            )
        }
        var probeCalls = 0
        val probe = object : SpeechProbeSource {
            override suspend fun probe(
                mediaUri: String, targetPositionMs: Long, durationMs: Long, bucketMs: Int
            ): SpeechProbeResult {
                probeCalls++
                return SpeechProbeResult.Unsupported
            }
        }
        val coordinator = SubtitleSyncCoordinator(
            repository = repo,
            probeSelector = object : SubtitleProbeSelector {
                override fun select(
                    subtitle: ParsedSubtitle, mediaDurationMs: Long, maxCount: Int
                ): List<ProbeCandidate> = emptyList()
            },
            subtitleActivityBuilder = fakeBuilder,
            speechProbeSource = probe,
            correlator = fakeCorrelator,
            consensus = fakeConsensus,
        )
        val result = coordinator.synchronize(
            SubtitleSyncRequest(
                "media", "media", 1L, 80_000L, "fp", "en", patternedSubtitle(0L), 1L
            )
        )
        assertTrue(result is SubtitleSyncResult.Synchronized)
        assertEquals(-1_200L, ((result as SubtitleSyncResult.Synchronized).model as SubtitleSyncModel.Offset).offsetMs)
        assertEquals(0, probeCalls)
    }

    private fun patternedSubtitle(shiftMs: Long): ParsedSubtitle {
        val starts = listOf(10_000L, 13_400L, 18_200L, 26_900L, 31_100L, 42_600L, 51_300L, 63_700L)
        val durations = listOf(1_200L, 2_100L, 900L, 2_700L, 1_400L, 3_200L, 800L, 2_000L)
        return ParsedSubtitle(
            cues = starts.indices.map { i ->
                SubtitleCue(
                    index = i + 1,
                    startMs = starts[i] + shiftMs,
                    endMs = starts[i] + shiftMs + durations[i],
                    rawText = "dialogue-$i",
                )
            },
            language = "en",
            sourceId = "test",
        )
    }

    private fun longPatternedSubtitle(shiftMs: Long): ParsedSubtitle {
        val starts = listOf(
            5_000L, 8_300L, 12_200L, 17_100L, 21_400L, 27_600L, 31_900L, 38_100L,
            42_700L, 49_600L, 54_300L, 61_200L, 67_600L, 73_900L, 80_200L, 87_100L,
            93_400L, 101_300L
        )
        val durations = listOf(
            900L, 1_400L, 600L, 2_200L, 1_000L, 1_700L, 800L, 2_500L, 700L,
            1_300L, 2_000L, 900L, 1_600L, 1_100L, 2_600L, 700L, 1_400L, 1_800L
        )
        return ParsedSubtitle(
            cues = starts.indices.map { i ->
                SubtitleCue(i + 1, starts[i] + shiftMs, starts[i] + shiftMs + durations[i], "dialogue-$i")
            },
            language = "en",
            sourceId = "test-long",
        )
    }

    private class FakeSyncRepository : SubtitleSyncRepository {
        var profile: SubtitleSyncProfile? = null
        var saveCount: Int = 0

        override suspend fun get(
            mediaId: String, mediaFileSize: Long, subtitleFingerprint: String
        ): SubtitleSyncProfile? = profile

        override suspend fun saveAuto(
            mediaId: String,
            mediaFileSize: Long,
            subtitleFingerprint: String,
            subtitleLanguage: String?,
            model: SubtitleSyncModel,
            confidence: Float,
            engineVersion: Int,
        ) {
            saveCount++
            profile = SubtitleSyncProfile(
                mediaId, mediaFileSize, subtitleFingerprint, subtitleLanguage,
                model, confidence, engineVersion
            )
        }

        override suspend fun setManualOffset(
            mediaId: String, mediaFileSize: Long, subtitleFingerprint: String, offsetMs: Long
        ) = Unit

        override suspend fun clearManualOffset(
            mediaId: String, mediaFileSize: Long, subtitleFingerprint: String
        ) = Unit

        override suspend fun clearAutoResult(
            mediaId: String, mediaFileSize: Long, subtitleFingerprint: String
        ) = Unit
    }
}
