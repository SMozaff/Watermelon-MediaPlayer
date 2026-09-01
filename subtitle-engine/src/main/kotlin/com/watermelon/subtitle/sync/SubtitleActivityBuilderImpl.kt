package com.watermelon.subtitle.sync

import com.watermelon.common.model.ParsedSubtitle
import com.watermelon.common.model.SubtitleCue
import com.watermelon.common.subtitle.sync.ActivitySignature
import com.watermelon.common.subtitle.sync.SubtitleActivityBuilder
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

class SubtitleActivityBuilderImpl(
    private val config: SubtitleSyncConfig = SubtitleSyncConfig(),
) : SubtitleActivityBuilder {

    override fun build(
        subtitle: ParsedSubtitle,
        startMs: Long,
        endMs: Long,
        bucketMs: Int,
    ): ActivitySignature {
        require(endMs > startMs) { "endMs must be greater than startMs" }
        require(bucketMs > 0) { "bucketMs must be > 0" }

        val bucketCount = ceil((endMs - startMs).toDouble() / bucketMs)
            .toInt().coerceAtLeast(1)
        val occupancy = FloatArray(bucketCount)
        val onsets = FloatArray(bucketCount)
        val blurBuckets = ceil(config.subtitleOnsetBlurMs.toDouble() / bucketMs)
            .toInt().coerceAtLeast(1)

        subtitle.cues.forEach { cue ->
            if (cue.endMs <= startMs || cue.startMs >= endMs) return@forEach
            val weight = if (isLikelyNonDialogue(cue)) config.sdhWeight else 1f

            val first = floor((max(cue.startMs, startMs) - startMs).toDouble() / bucketMs)
                .toInt().coerceIn(0, bucketCount - 1)
            val lastExclusive = ceil((minOf(cue.endMs, endMs) - startMs).toDouble() / bucketMs)
                .toInt().coerceIn(first + 1, bucketCount)

            for (i in first until lastExclusive) {
                occupancy[i] = max(occupancy[i], weight)
            }

            val onset = floor((cue.startMs - startMs).toDouble() / bucketMs).toInt()
            if (onset in 0 until bucketCount) {
                for (d in -blurBuckets..blurBuckets) {
                    val i = onset + d
                    if (i !in 0 until bucketCount) continue
                    val kernel = 1f - kotlin.math.abs(d).toFloat() / (blurBuckets + 1).toFloat()
                    onsets[i] = max(onsets[i], weight * kernel)
                }
            }
        }

        return ActivitySignature(
            startMs = startMs,
            bucketMs = bucketMs,
            occupancy = occupancy,
            onsets = onsets,
        )
    }

    internal fun isLikelyNonDialogue(cue: SubtitleCue): Boolean =
        isLikelyNonDialogue(cue.rawText)

    internal fun isLikelyNonDialogue(text: String): Boolean {
        val value = text.trim()
        if (value.isEmpty()) return true
        if ((value.startsWith("[") && value.endsWith("]")) ||
            (value.startsWith("(") && value.endsWith(")"))
        ) return true
        return value.indexOf('♪') >= 0 || value.indexOf('♫') >= 0
    }
}
