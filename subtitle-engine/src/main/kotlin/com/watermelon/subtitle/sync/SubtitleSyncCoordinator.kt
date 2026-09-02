package com.watermelon.subtitle.sync

import com.watermelon.common.subtitle.sync.ActivityCorrelator
import com.watermelon.common.subtitle.sync.CorrelationConfig
import com.watermelon.common.subtitle.sync.CorrelationResult
import com.watermelon.common.subtitle.sync.SpeechProbeResult
import com.watermelon.common.subtitle.sync.SpeechProbeSource
import com.watermelon.common.subtitle.sync.SubtitleActivityBuilder
import com.watermelon.common.subtitle.sync.SubtitleProbeSelector
import com.watermelon.common.subtitle.sync.SubtitleSyncModel
import com.watermelon.common.subtitle.sync.SubtitleSyncRepository
import com.watermelon.common.subtitle.sync.SubtitleSyncRequest
import com.watermelon.common.subtitle.sync.SubtitleSyncResult
import com.watermelon.common.subtitle.sync.SyncMeasurement
import com.watermelon.common.util.FileLogger
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.math.abs

/** Orchestrates cache/manual precedence, sparse probing, correlation and confidence. */
class SubtitleSyncCoordinator(
    private val repository: SubtitleSyncRepository,
    private val probeSelector: SubtitleProbeSelector,
    private val subtitleActivityBuilder: SubtitleActivityBuilder,
    private val speechProbeSource: SpeechProbeSource,
    private val correlator: ActivityCorrelator,
    private val consensus: OffsetConsensus,
    private val config: SubtitleSyncConfig = SubtitleSyncConfig(),
) {
    suspend fun synchronize(request: SubtitleSyncRequest): SubtitleSyncResult {
        currentCoroutineContext().ensureActive()
        val stored = repository.get(request.mediaId, request.mediaFileSize, request.subtitleFingerprint)

        stored?.manualOffsetMs?.let { manual ->
            FileLogger.i(TAG, "manual override hit media=${safeId(request.mediaId)} offsetMs=$manual")
            return SubtitleSyncResult.Synchronized(
                SubtitleSyncModel.Offset(manual),
                confidence = 1f,
                measurements = emptyList(),
            )
        }

        val cachedAutoModel = stored?.autoModel
        if (stored?.autoEngineVersion == SUBTITLE_SYNC_ENGINE_VERSION && cachedAutoModel != null) {
            FileLogger.i(TAG, "auto cache hit media=${safeId(request.mediaId)}")
            return SubtitleSyncResult.Synchronized(
                cachedAutoModel,
                stored.autoConfidence ?: 1f,
                emptyList(),
            )
        }

        val candidates = probeSelector.select(request.subtitle, request.mediaDurationMs, config.maxProbeCount)
        if (candidates.isEmpty()) return SubtitleSyncResult.LowConfidence(emptyList())

        val measurements = mutableListOf<SyncMeasurement>()
        var provisionalOffsetMs = 0L

        for (candidate in candidates) {
            currentCoroutineContext().ensureActive()
            // Once one good measurement exists, aim subsequent audio probes at where the chosen
            // subtitle pattern is predicted to occur in the media. This makes confirmation much
            // stronger than repeatedly probing the uncorrected subtitle timestamp.
            val audioTargetMs = (candidate.subtitleStartMs + provisionalOffsetMs)
                .coerceIn(0L, (request.mediaDurationMs - candidate.durationMs).coerceAtLeast(0L))

            FileLogger.i(TAG, "probe start media=${safeId(request.mediaId)} atMs=$audioTargetMs")
            when (val audio = speechProbeSource.probe(
                mediaUri = request.mediaUri,
                targetPositionMs = audioTargetMs,
                durationMs = candidate.durationMs,
                bucketMs = config.bucketMs,
            )) {
                SpeechProbeResult.Cancelled -> return SubtitleSyncResult.Cancelled
                SpeechProbeResult.Unsupported -> return SubtitleSyncResult.Unsupported
                SpeechProbeResult.ResourceDenied -> return SubtitleSyncResult.ResourceDenied
                is SpeechProbeResult.Failure -> {
                    FileLogger.e(TAG, "probe failure: ${audio.reason}")
                    continue
                }
                is SpeechProbeResult.Success -> {
                    val correlation = correlateHierarchical(request, audio, provisionalOffsetMs)
                    val measurement = toMeasurement(candidate, audio, correlation)
                    measurements += measurement
                    if (consensus.hasUsefulProvisionalOffset(listOf(measurement))) {
                        provisionalOffsetMs = measurement.estimatedOffsetMs
                    }
                    FileLogger.i(
                        TAG,
                        "probe result offsetMs=${measurement.estimatedOffsetMs} " +
                            "score=${measurement.correlationScore} margin=${measurement.peakMargin}"
                    )
                }
            }

            val resolved = consensus.resolve(measurements)
            if (resolved is SubtitleSyncResult.Synchronized) {
                persist(request, resolved)
                return resolved
            }
        }

        val resolved = consensus.resolve(measurements)
        if (resolved is SubtitleSyncResult.Synchronized) persist(request, resolved)
        return resolved
    }

    private fun correlateHierarchical(
        request: SubtitleSyncRequest,
        audio: SpeechProbeResult.Success,
        provisionalOffsetMs: Long,
    ): CorrelationResult {
        val primaryMin = provisionalOffsetMs - config.primarySearchMs
        val primaryMax = provisionalOffsetMs + config.primarySearchMs
        val primaryContext = subtitleActivityBuilder.build(
            request.subtitle,
            (audio.actualStartMs - primaryMax).coerceAtLeast(0L),
            (audio.actualStartMs + audio.actualDurationMs - primaryMin)
                .coerceAtMost(request.mediaDurationMs + config.expandedSearchMs),
            config.bucketMs,
        )
        val primary = correlator.correlate(
            audio.signature,
            primaryContext,
            CorrelationConfig(
                minOffsetMs = primaryMin,
                maxOffsetMs = primaryMax,
                stepMs = config.bucketMs.toLong(),
            ),
        )
        if (isStrong(primary)) return primary

        // Same decoded audio is reused for a much wider coarse search. Expanded search costs CPU
        // only; it does not decode more media.
        val expandedContext = subtitleActivityBuilder.build(
            request.subtitle,
            (audio.actualStartMs - config.expandedSearchMs).coerceAtLeast(0L),
            (audio.actualStartMs + audio.actualDurationMs + config.expandedSearchMs)
                .coerceAtMost(request.mediaDurationMs + config.expandedSearchMs),
            config.bucketMs,
        )
        val coarse = correlator.correlate(
            audio.signature,
            expandedContext,
            CorrelationConfig(
                minOffsetMs = -config.expandedSearchMs,
                maxOffsetMs = config.expandedSearchMs,
                stepMs = config.expandedSearchStepMs,
            ),
        )
        val refined = correlator.correlate(
            audio.signature,
            expandedContext,
            CorrelationConfig(
                minOffsetMs = coarse.offsetMs - config.refinementRadiusMs,
                maxOffsetMs = coarse.offsetMs + config.refinementRadiusMs,
                stepMs = config.bucketMs.toLong(),
            ),
        )
        return if (refined.peakScore >= primary.peakScore) refined else primary
    }

    private fun isStrong(result: CorrelationResult): Boolean =
        result.peakScore >= config.minimumCorrelationScore &&
            result.peakMargin >= config.minimumPeakMargin

    private fun toMeasurement(
        candidate: com.watermelon.common.subtitle.sync.ProbeCandidate,
        audio: SpeechProbeResult.Success,
        correlation: CorrelationResult,
    ): SyncMeasurement {
        val speechCoverage = if (audio.signature.occupancy.isEmpty()) 0f else
            audio.signature.occupancy.count { it >= 0.45f }.toFloat() / audio.signature.occupancy.size
        val transitions = audio.signature.onsets.count { it >= 0.35f }
        val coverageQuality = when {
            speechCoverage < 0.08f -> speechCoverage / 0.08f
            speechCoverage > 0.85f -> ((1f - speechCoverage) / 0.15f).coerceIn(0f, 1f)
            else -> 1f
        }
        val transitionQuality = (transitions / 8f).coerceIn(0f, 1f)
        val scoreQuality = ((correlation.peakScore - 0.45f) / 0.45f).coerceIn(0f, 1f)
        val marginQuality = (correlation.peakMargin / 0.20f).coerceIn(0f, 1f)
        val quality = (
            0.25f * candidate.distinctiveness +
                0.25f * scoreQuality +
                0.20f * marginQuality +
                0.15f * coverageQuality +
                0.15f * transitionQuality
            ).coerceIn(0f, 1f)

        return SyncMeasurement(
            subtitleProbeStartMs = candidate.subtitleStartMs,
            audioProbeStartMs = audio.actualStartMs,
            estimatedOffsetMs = correlation.offsetMs,
            correlationScore = correlation.peakScore,
            peakMargin = correlation.peakMargin,
            speechCoverage = speechCoverage,
            transitionCount = transitions,
            probeQuality = quality,
        )
    }

    private suspend fun persist(request: SubtitleSyncRequest, result: SubtitleSyncResult.Synchronized) {
        repository.saveAuto(
            mediaId = request.mediaId,
            mediaFileSize = request.mediaFileSize,
            subtitleFingerprint = request.subtitleFingerprint,
            subtitleLanguage = request.subtitleLanguage,
            model = result.model,
            confidence = result.confidence,
            engineVersion = SUBTITLE_SYNC_ENGINE_VERSION,
        )
        FileLogger.i(TAG, "persisted auto sync media=${safeId(request.mediaId)} confidence=${result.confidence}")
    }

    private fun safeId(value: String): String = value.hashCode().toUInt().toString(16)

    companion object {
        private const val TAG = "SubtitleAutoSync"
    }
}
