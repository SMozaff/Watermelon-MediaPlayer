package com.watermelon.subtitle.sync

import com.watermelon.common.model.ParsedSubtitle
import com.watermelon.common.subtitle.sync.ProbeCandidate
import com.watermelon.common.subtitle.sync.SubtitleProbeSelector
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

class SubtitleProbeSelectorImpl(
    private val config: SubtitleSyncConfig = SubtitleSyncConfig(),
) : SubtitleProbeSelector {

    override fun select(
        subtitle: ParsedSubtitle,
        mediaDurationMs: Long,
        maxCount: Int,
    ): List<ProbeCandidate> {
        if (subtitle.cues.isEmpty() || mediaDurationMs <= 0 || maxCount <= 0) return emptyList()

        val duration = config.probeDurationMs.coerceAtMost(mediaDurationMs)
        val half = duration / 2
        val edgeGuard = max(5_000L, (mediaDurationMs * 0.02).toLong())
        val maxStart = (mediaDurationMs - duration).coerceAtLeast(0L)

        val scored = subtitle.cues.asSequence()
            .map { (it.startMs - half).coerceIn(0L, maxStart) }
            .distinct()
            .mapNotNull { start -> scoreWindow(subtitle, start, duration, mediaDurationMs, edgeGuard) }
            .sortedByDescending { it.distinctiveness }
            .toList()

        if (scored.isEmpty()) return emptyList()

        val minSeparation = min(
            max(60_000L, (mediaDurationMs * 0.18).toLong()),
            (mediaDurationMs / maxCount.coerceAtLeast(1)).coerceAtLeast(duration),
        )
        val selected = mutableListOf<ProbeCandidate>()
        for (candidate in scored) {
            if (selected.all { abs(it.subtitleStartMs - candidate.subtitleStartMs) >= minSeparation }) {
                selected += candidate
                if (selected.size == maxCount) return selected
            }
        }

        // Short media or sparse subtitles may not permit ideal spacing; fill from the remaining
        // strongest candidates rather than returning fewer probes than are available.
        for (candidate in scored) {
            if (candidate !in selected) {
                selected += candidate
                if (selected.size == maxCount) break
            }
        }
        return selected
    }

    private fun scoreWindow(
        subtitle: ParsedSubtitle,
        start: Long,
        duration: Long,
        mediaDurationMs: Long,
        edgeGuard: Long,
    ): ProbeCandidate? {
        val end = start + duration
        val cues = subtitle.cues.filter { it.endMs > start && it.startMs < end }
        if (cues.size < config.minimumTransitions) return null

        val occupiedMs = cues.sumOf { cue ->
            (min(cue.endMs, end) - max(cue.startMs, start)).coerceAtLeast(0L).toDouble()
        }.coerceAtMost(duration.toDouble())
        val occupancy = (occupiedMs / duration.toDouble()).toFloat()
        if (occupancy < 0.10f || occupancy > 0.85f) return null

        val dialogueRatio = cues.count { !looksNonDialogue(it.rawText) }.toFloat() / cues.size
        val gaps = cues.zipWithNext { a, b -> (b.startMs - a.endMs).coerceAtLeast(0L) }
        val entropy = normalizedGapEntropy(gaps)
        val transitionQuality = (cues.size / 8f).coerceIn(0f, 1f)
        val occupancyBalance = (1f - abs(occupancy - 0.45f) / 0.45f).coerceIn(0f, 1f)
        val edgeQuality = if (start < edgeGuard || end > mediaDurationMs - edgeGuard) 0.45f else 1f

        val score = edgeQuality * (
            0.35f * transitionQuality +
                0.25f * occupancyBalance +
                0.25f * entropy +
                0.15f * dialogueRatio
            )

        return ProbeCandidate(
            subtitleStartMs = start,
            durationMs = duration,
            distinctiveness = score.coerceIn(0f, 1f),
        )
    }

    private fun normalizedGapEntropy(gaps: List<Long>): Float {
        if (gaps.size < 2) return 0f
        val bins = gaps.groupingBy { (it / 400L).coerceIn(0L, 10L) }.eachCount()
        val total = gaps.size.toDouble()
        var entropy = 0.0
        for (count in bins.values) {
            val p = count / total
            entropy -= p * ln(p)
        }
        val maxEntropy = ln(min(gaps.size, 11).toDouble()).coerceAtLeast(1e-6)
        return (entropy / maxEntropy).toFloat().coerceIn(0f, 1f)
    }

    private fun looksNonDialogue(text: String): Boolean {
        val value = text.trim()
        return value.isEmpty() ||
            (value.startsWith("[") && value.endsWith("]")) ||
            (value.startsWith("(") && value.endsWith(")")) ||
            value.indexOf('♪') >= 0 || value.indexOf('♫') >= 0
    }
}
