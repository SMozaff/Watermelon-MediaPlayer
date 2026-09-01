package com.watermelon.subtitle.sync

import com.watermelon.common.subtitle.sync.SubtitleSyncModel
import com.watermelon.common.subtitle.sync.SubtitleSyncResult
import com.watermelon.common.subtitle.sync.SyncMeasurement
import kotlin.math.abs
import kotlin.math.sqrt

class OffsetConsensus(
    private val config: SubtitleSyncConfig = SubtitleSyncConfig(),
) {

    fun resolve(measurements: List<SyncMeasurement>): SubtitleSyncResult {
        val good = measurements.filter(::isEligible).sortedBy { it.subtitleProbeStartMs }
        if (good.size < config.minimumGoodProbes) {
            return SubtitleSyncResult.LowConfidence(measurements)
        }

        val offset = weightedMedian(good)
        val spread = good.maxOf { it.estimatedOffsetMs } - good.minOf { it.estimatedOffsetMs }

        if (spread <= config.maximumConsensusSpreadMs) {
            // Two-probe acceptance is deliberately stricter. With three independent movie
            // regions, tight agreement itself becomes strong evidence even when one individual
            // correlation peak is somewhat ambiguous.
            val uniquePeaks = good.count { it.peakMargin >= config.minimumPeakMargin }
            if ((good.size == 2 && uniquePeaks < 2) || (good.size >= 3 && uniquePeaks < 1)) {
                return SubtitleSyncResult.LowConfidence(measurements)
            }

            val ratio = spread.toFloat() / config.maximumConsensusSpreadMs.coerceAtLeast(1L)
            val agreement = (1f - ratio * ratio).coerceIn(0f, 1f)
            val evidence = good.map { it.probeQuality }.average().toFloat().coerceIn(0f, 1f)
            val uniqueness = good.map { (it.peakMargin / 0.20f).coerceIn(0f, 1f) }
                .average().toFloat()
            val multiProbeBonus = if (good.size >= 3) 0.05f else 0f
            val confidence = (
                0.68f * agreement +
                    0.22f * evidence +
                    0.10f * uniqueness +
                    multiProbeBonus
                ).coerceIn(0f, 1f)
            if (confidence < config.autoApplyThreshold) {
                return SubtitleSyncResult.LowConfidence(measurements)
            }

            val model = if (abs(offset) <= config.identityToleranceMs) {
                SubtitleSyncModel.Identity
            } else {
                SubtitleSyncModel.Offset(offset)
            }
            return SubtitleSyncResult.Synchronized(model, confidence, measurements)
        }

        if (good.size >= 3 && good.any { it.peakMargin >= config.minimumPeakMargin } && isLikelyLinearDrift(good)) {
            return SubtitleSyncResult.ComplexDriftDetected(measurements)
        }

        return SubtitleSyncResult.LowConfidence(measurements)
    }

    fun hasUsefulProvisionalOffset(measurements: List<SyncMeasurement>): Boolean =
        measurements.lastOrNull()?.let {
            isEligible(it) && it.peakMargin >= config.minimumPeakMargin * 0.5f
        } == true

    private fun isEligible(it: SyncMeasurement): Boolean =
        it.probeQuality >= config.minimumProbeQuality &&
            it.correlationScore >= config.minimumCorrelationScore

    private fun weightedMedian(measurements: List<SyncMeasurement>): Long {
        val ordered = measurements.sortedBy { it.estimatedOffsetMs }
        val totalWeight = ordered.sumOf { it.probeQuality.toDouble() }.coerceAtLeast(1e-6)
        var running = 0.0
        for (measurement in ordered) {
            running += measurement.probeQuality
            if (running >= totalWeight / 2.0) return measurement.estimatedOffsetMs
        }
        return ordered.last().estimatedOffsetMs
    }

    /** Least-squares trend check prevents random conflicting offsets being mislabeled as drift. */
    private fun isLikelyLinearDrift(measurements: List<SyncMeasurement>): Boolean {
        val xs = measurements.map { it.subtitleProbeStartMs.toDouble() }
        val ys = measurements.map { it.estimatedOffsetMs.toDouble() }
        val xMean = xs.average()
        val yMean = ys.average()
        val ssX = xs.sumOf { (it - xMean) * (it - xMean) }
        if (ssX <= 0.0) return false
        val slope = xs.indices.sumOf { i -> (xs[i] - xMean) * (ys[i] - yMean) } / ssX
        val intercept = yMean - slope * xMean
        val ssTot = ys.sumOf { (it - yMean) * (it - yMean) }
        if (ssTot <= 0.0) return false
        val ssRes = xs.indices.sumOf { i ->
            val residual = ys[i] - (slope * xs[i] + intercept)
            residual * residual
        }
        val rSquared = (1.0 - ssRes / ssTot).coerceIn(0.0, 1.0)
        val spanMs = xs.maxOrNull()!! - xs.minOrNull()!!
        val predictedDriftMs = abs(slope * spanMs)
        return rSquared >= 0.85 && predictedDriftMs >= config.maximumConsensusSpreadMs * 2.0
    }
}
