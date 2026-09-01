package com.watermelon.mediatools.subtitle.sync

import com.watermelon.common.subtitle.sync.ActivitySignature
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt

internal enum class PcmSampleEncoding(val bytesPerSample: Int) {
    PCM_16(2),
    PCM_FLOAT(4),
}

/**
 * Streaming, language-independent speech-likelihood estimator. It intentionally avoids ASR or a
 * neural model: the features are short-time energy, zero-crossing behavior and temporal
 * derivative energy. False positives are tolerated because final sync requires independent probe
 * agreement and a unique correlation peak.
 */
internal class SpeechLikelihoodAccumulator(
    private val targetStartUs: Long,
    durationMs: Long,
    private val bucketMs: Int,
) {
    private val bucketCount = ceil(durationMs.toDouble() / bucketMs).toInt().coerceAtLeast(1)
    private val sumSquares = DoubleArray(bucketCount)
    private val diffSquares = DoubleArray(bucketCount)
    private val zeroCrossings = LongArray(bucketCount)
    private val sampleCounts = LongArray(bucketCount)
    private val priorSample = DoubleArray(bucketCount)
    private val hasPrior = BooleanArray(bucketCount)

    var decodedSampleCount: Long = 0L
        private set

    fun consume(
        buffer: ByteBuffer,
        bufferOffset: Int,
        bufferSize: Int,
        presentationTimeUs: Long,
        sampleRate: Int,
        channelCount: Int,
        encoding: PcmSampleEncoding,
    ) {
        if (sampleRate <= 0 || channelCount <= 0 || bufferSize <= 0) return
        val frameBytes = encoding.bytesPerSample * channelCount
        val frameCount = bufferSize / frameBytes
        if (frameCount <= 0) return
        val duplicate = buffer.duplicate().order(ByteOrder.nativeOrder())

        for (frame in 0 until frameCount) {
            val frameTimeUs = presentationTimeUs + (frame.toLong() * 1_000_000L / sampleRate)
            val relativeUs = frameTimeUs - targetStartUs
            if (relativeUs < 0L) continue
            val bucket = (relativeUs / (bucketMs * 1_000L)).toInt()
            if (bucket !in 0 until bucketCount) break

            val base = bufferOffset + frame * frameBytes
            if (base < 0 || base + frameBytes > duplicate.limit()) continue
            var mono = 0.0
            for (channel in 0 until channelCount) {
                val index = base + channel * encoding.bytesPerSample
                mono += when (encoding) {
                    PcmSampleEncoding.PCM_16 -> duplicate.getShort(index) / 32768.0
                    PcmSampleEncoding.PCM_FLOAT -> duplicate.getFloat(index).toDouble().coerceIn(-1.0, 1.0)
                }
            }
            mono = (mono / channelCount).coerceIn(-1.0, 1.0)
            sumSquares[bucket] += mono * mono
            if (hasPrior[bucket]) {
                val diff = mono - priorSample[bucket]
                diffSquares[bucket] += diff * diff
                if ((mono >= 0.0) != (priorSample[bucket] >= 0.0)) zeroCrossings[bucket]++
            }
            priorSample[bucket] = mono
            hasPrior[bucket] = true
            sampleCounts[bucket]++
            decodedSampleCount++
        }
    }

    fun finish(): ActivitySignature {
        val rms = DoubleArray(bucketCount)
        val diffRms = DoubleArray(bucketCount)
        val zcr = DoubleArray(bucketCount)
        for (i in 0 until bucketCount) {
            val n = sampleCounts[i]
            if (n <= 0L) continue
            rms[i] = sqrt(sumSquares[i] / n)
            diffRms[i] = sqrt(diffSquares[i] / max(1L, n - 1L))
            zcr[i] = zeroCrossings[i].toDouble() / max(1L, n - 1L).toDouble()
        }

        val validRms = rms.indices.filter { sampleCounts[it] > 0L }.map { rms[it] }.sorted()
        val p20 = if (validRms.isEmpty()) 0.0 else validRms[((validRms.size - 1) * 0.20).toInt()]
        val noiseFloor = max(0.003, p20)
        val raw = FloatArray(bucketCount)

        for (i in 0 until bucketCount) {
            if (sampleCounts[i] == 0L) continue
            val energyRatio = (rms[i] + 1e-9) / (noiseFloor * 1.25 + 1e-9)
            val energy = (ln(energyRatio) / ln(5.0)).coerceIn(0.0, 1.0)
            if (energy < 0.08) continue

            val zcrScore = trapezoidScore(zcr[i], 0.008, 0.025, 0.22, 0.38)
            val derivativeRatio = diffRms[i] / (rms[i] + 1e-9)
            val derivativeScore = trapezoidScore(derivativeRatio, 0.03, 0.10, 1.10, 1.90)
            raw[i] = (energy * (0.65 + 0.20 * zcrScore + 0.15 * derivativeScore))
                .toFloat().coerceIn(0f, 1f)
        }

        val occupancy = smooth(raw)
        val onsets = FloatArray(bucketCount)
        for (i in occupancy.indices) {
            val previous = if (i == 0) 0f else occupancy[i - 1]
            val rise = (occupancy[i] - previous).coerceAtLeast(0f)
            if (rise < 0.12f) continue
            for (d in -3..3) {
                val target = i + d
                if (target !in onsets.indices) continue
                val kernel = when (kotlin.math.abs(d)) {
                    0 -> 1f
                    1 -> 0.65f
                    2 -> 0.35f
                    else -> 0.15f
                }
                onsets[target] = max(onsets[target], rise * kernel)
            }
        }

        return ActivitySignature(
            startMs = targetStartUs / 1_000L,
            bucketMs = bucketMs,
            occupancy = occupancy,
            onsets = onsets,
        )
    }

    private fun smooth(input: FloatArray): FloatArray {
        if (input.size < 3) return input.copyOf()
        val out = FloatArray(input.size)
        for (i in input.indices) {
            var weighted = input[i] * 0.50f
            var total = 0.50f
            if (i > 0) { weighted += input[i - 1] * 0.25f; total += 0.25f }
            if (i + 1 < input.size) { weighted += input[i + 1] * 0.25f; total += 0.25f }
            out[i] = (weighted / total).coerceIn(0f, 1f)
        }
        for (i in 1 until out.lastIndex) {
            if (out[i] > 0.45f && out[i - 1] < 0.10f && out[i + 1] < 0.10f) {
                out[i] *= 0.35f
            }
        }
        return out
    }

    private fun trapezoidScore(
        value: Double,
        low0: Double,
        low1: Double,
        high1: Double,
        high0: Double,
    ): Double = when {
        value <= low0 || value >= high0 -> 0.0
        value < low1 -> (value - low0) / (low1 - low0)
        value <= high1 -> 1.0
        else -> (high0 - value) / (high0 - high1)
    }.coerceIn(0.0, 1.0)
}
