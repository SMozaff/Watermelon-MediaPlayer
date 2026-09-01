package com.watermelon.subtitle.sync

import com.watermelon.common.model.ParsedSubtitle
import com.watermelon.common.model.SubtitleCue
import com.watermelon.common.subtitle.sync.CorrelationConfig
import com.watermelon.common.subtitle.sync.ActivitySignature
import com.watermelon.common.subtitle.sync.ProbeCandidate
import com.watermelon.common.subtitle.sync.SpeechProbeResult
import com.watermelon.common.subtitle.sync.SpeechProbeSource
import com.watermelon.common.subtitle.sync.SubtitleProbeSelector
import com.watermelon.common.subtitle.sync.SubtitleSyncProfile
import com.watermelon.common.subtitle.sync.SubtitleSyncRepository
import com.watermelon.common.subtitle.sync.SubtitleSyncRequest
import com.watermelon.common.subtitle.sync.SubtitleSyncModel
import com.watermelon.common.subtitle.sync.SubtitleSyncResult
import com.watermelon.common.subtitle.sync.SyncMeasurement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.runBlocking
import kotlin.math.abs

class SubtitleSyncCoreTest {
    private val config = SubtitleSyncConfig(autoApplyThreshold = 0.80f)
    private val builder = SubtitleActivityBuilderImpl(config)
    private val correlator = ActivityCorrelatorImpl()

    @Test
    fun correlation_recoversPositiveConstantOffset() {
        val source = patternedSubtitle(shiftMs = 0L)
        val audioTimeline = patternedSubtitle(shiftMs = 2_500L)
        val subtitleSignal = builder.build(source, 0L, 80_000L, config.bucketMs)
        val audioSignal = builder.build(audioTimeline, 0L, 80_000L, config.bucketMs)

        val result = correlator.correlate(
            audio = audioSignal,
            subtitleContext = subtitleSignal,
            config = CorrelationConfig(-10_000L, 10_000L, config.bucketMs.toLong()),
        )

        assertTrue("offset=${result.offsetMs}", abs(result.offsetMs - 2_500L) <= config.bucketMs)
        assertTrue(result.peakMargin > 0.03f)
    }

    @Test
    fun correlation_recoversNegativeConstantOffset() {
        val source = patternedSubtitle(shiftMs = 4_000L)
        val audioTimeline = patternedSubtitle(shiftMs = 1_000L)
        val subtitleSignal = builder.build(source, 0L, 90_000L, config.bucketMs)
        val audioSignal = builder.build(audioTimeline, 0L, 90_000L, config.bucketMs)

        val result = correlator.correlate(
            audioSignal,
            subtitleSignal,
            CorrelationConfig(-10_000L, 10_000L, config.bucketMs.toLong()),
        )

        assertTrue("offset=${result.offsetMs}", abs(result.offsetMs + 3_000L) <= config.bucketMs)
    }

    @Test
    fun consensus_acceptsAgreement() {
        val result = OffsetConsensus(config).resolve(
            listOf(
                measurement(10_000L, 2_280L),
                measurement(40_000L, 2_360L),
                measurement(70_000L, 2_310L),
            )
        )
        assertTrue(result is SubtitleSyncResult.Synchronized)
        val model = (result as SubtitleSyncResult.Synchronized).model
        assertTrue(model is SubtitleSyncModel.Offset)
        assertEquals(2_310L, (model as SubtitleSyncModel.Offset).offsetMs)
    }

    @Test
    fun consensus_classifiesLinearDriftInsteadOfAveragingIt() {
        val result = OffsetConsensus(config).resolve(
            listOf(
                measurement(10_000L, 500L),
                measurement(40_000L, 2_200L),
                measurement(70_000L, 4_100L),
            )
        )
        assertTrue(result is SubtitleSyncResult.ComplexDriftDetected)
    }

    @Test
    fun consensus_refusesConflictingEvidence() {
        val result = OffsetConsensus(config).resolve(
            listOf(
                measurement(10_000L, 2_300L),
                measurement(40_000L, -8_200L),
                measurement(70_000L, 14_700L),
            )
        )
        assertTrue(result is SubtitleSyncResult.LowConfidence)
    }

    @Test
    fun sdhCuesAreDownWeightedNotDeleted() {
        val subtitle = ParsedSubtitle(
            listOf(
                SubtitleCue(1, 1_000, 2_000, "[DOOR SLAMS]"),
                SubtitleCue(2, 3_000, 4_000, "actual dialogue"),
            )
        )
        val signal = builder.build(subtitle, 0, 5_000, config.bucketMs)
        val sdhBucket = (1_200 / config.bucketMs)
        val dialogueBucket = (3_200 / config.bucketMs)
        assertTrue(signal.occupancy[sdhBucket] > 0f)
        assertTrue(signal.occupancy[sdhBucket] < signal.occupancy[dialogueBucket])
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
                val signature = builder.build(
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
            subtitleActivityBuilder = SubtitleActivityBuilderImpl(productionConfig),
            speechProbeSource = probe,
            correlator = ActivityCorrelatorImpl(),
            consensus = OffsetConsensus(productionConfig),
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
            probeSelector = SubtitleProbeSelectorImpl(),
            subtitleActivityBuilder = SubtitleActivityBuilderImpl(),
            speechProbeSource = probe,
            correlator = ActivityCorrelatorImpl(),
            consensus = OffsetConsensus(),
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

    private fun measurement(position: Long, offset: Long) = SyncMeasurement(
        subtitleProbeStartMs = position,
        audioProbeStartMs = position + offset,
        estimatedOffsetMs = offset,
        correlationScore = 0.92f,
        peakMargin = 0.18f,
        speechCoverage = 0.42f,
        transitionCount = 8,
        probeQuality = 0.98f,
    )

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
