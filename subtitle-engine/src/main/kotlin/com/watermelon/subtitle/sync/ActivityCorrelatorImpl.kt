package com.watermelon.subtitle.sync

import com.watermelon.common.subtitle.sync.ActivityCorrelator
import com.watermelon.common.subtitle.sync.ActivitySignature
import com.watermelon.common.subtitle.sync.CorrelationConfig
import com.watermelon.common.subtitle.sync.CorrelationResult
import kotlin.math.abs
import kotlin.math.min

/** Time-domain correlator intentionally optimized for short sparse probe signatures. */
class ActivityCorrelatorImpl : ActivityCorrelator {

    override fun correlate(
        audio: ActivitySignature,
        subtitleContext: ActivitySignature,
        config: CorrelationConfig,
    ): CorrelationResult {
        require(audio.bucketMs == subtitleContext.bucketMs) { "bucket sizes must match" }
        require(config.maxOffsetMs >= config.minOffsetMs)
        require(config.stepMs >= audio.bucketMs && config.stepMs % audio.bucketMs == 0L) {
            "stepMs must be a multiple of bucketMs"
        }

        val firstOffset = ceilToStep(config.minOffsetMs, config.stepMs)
        val lastOffset = floorToStep(config.maxOffsetMs, config.stepMs)
        if (firstOffset > lastOffset) return CorrelationResult(0L, 0f, 0f, 0f)

        var bestOffset = firstOffset
        var bestScore = Float.NEGATIVE_INFINITY
        val scored = ArrayList<Pair<Long, Float>>()

        var offset = firstOffset
        while (offset <= lastOffset) {
            val score = scoreAtOffset(audio, subtitleContext, offset, config)
            scored += offset to score
            if (score > bestScore) {
                bestScore = score
                bestOffset = offset
            }
            offset += config.stepMs
        }

        val second = scored.asSequence()
            .filter { abs(it.first - bestOffset) >= config.independentPeakDistanceMs }
            .maxOfOrNull { it.second } ?: 0f

        return CorrelationResult(
            offsetMs = bestOffset,
            peakScore = bestScore.coerceIn(0f, 1f),
            secondPeakScore = second.coerceIn(0f, 1f),
            peakMargin = (bestScore - second).coerceIn(0f, 1f),
        )
    }

    private fun scoreAtOffset(
        audio: ActivitySignature,
        subtitle: ActivitySignature,
        offsetMs: Long,
        config: CorrelationConfig,
    ): Float {
        var occupancyRaw = 0f
        var onsetRaw = 0f
        var silenceRaw = 0f
        var weightedSamples = 0f

        for (i in audio.occupancy.indices) {
            val mediaTimeMs = audio.startMs + i.toLong() * audio.bucketMs
            // Positive offset means subtitles are rendered later: media = source subtitle + offset.
            val sourceSubtitleTimeMs = mediaTimeMs - offsetMs
            val relative = sourceSubtitleTimeMs - subtitle.startMs
            if (relative < 0L) continue
            val subtitleIndex = (relative / subtitle.bucketMs).toInt()
            if (subtitleIndex !in subtitle.occupancy.indices) continue

            val a = audio.occupancy[i].coerceIn(0f, 1f)
            val s = subtitle.occupancy[subtitleIndex].coerceIn(0f, 1f)
            val ao = audio.onsets[i].coerceIn(0f, 1f)
            val so = subtitle.onsets[subtitleIndex].coerceIn(0f, 1f)

            val speechAgreement = min(a, s)
            val speechMismatch = abs(a - s)
            occupancyRaw += speechAgreement - 0.90f * speechMismatch

            val onsetAgreement = min(ao, so)
            val onsetMismatch = abs(ao - so)
            onsetRaw += onsetAgreement - 0.65f * onsetMismatch

            // Silence is deliberately a tiny contributor. Otherwise long quiet scenes can win.
            silenceRaw += (1f - a) * (1f - s)
            weightedSamples += 1f
        }

        if (weightedSamples <= 0f) return 0f
        val occupancy = normalizeSigned(occupancyRaw / weightedSamples, -0.90f, 1f)
        val onset = normalizeSigned(onsetRaw / weightedSamples, -0.65f, 1f)
        val silence = (silenceRaw / weightedSamples).coerceIn(0f, 1f)
        val totalWeight = config.onsetWeight + config.occupancyWeight + config.silenceWeight
        if (totalWeight <= 0f) return 0f

        return (
            config.onsetWeight * onset +
                config.occupancyWeight * occupancy +
                config.silenceWeight * silence
            ) / totalWeight
    }

    private fun normalizeSigned(value: Float, minValue: Float, maxValue: Float): Float =
        ((value - minValue) / (maxValue - minValue)).coerceIn(0f, 1f)

    private fun floorToStep(value: Long, step: Long): Long = Math.floorDiv(value, step) * step

    private fun ceilToStep(value: Long, step: Long): Long = -Math.floorDiv(-value, step) * step
}
