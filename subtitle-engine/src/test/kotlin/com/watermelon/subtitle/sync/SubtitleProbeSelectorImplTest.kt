package com.watermelon.subtitle.sync

import com.watermelon.common.model.ParsedSubtitle
import com.watermelon.common.model.SubtitleCue
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleProbeSelectorImplTest {

    private val selector = SubtitleProbeSelectorImpl()

    @Test
    fun returnsEmptyForEmptySubtitleNonPositiveDurationOrNonPositiveMaxCount() {
        val subtitle = subtitle(60_000L)
        assertTrue(selector.select(ParsedSubtitle(emptyList()), 60_000L, 3).isEmpty())
        assertTrue(selector.select(subtitle, 0L, 3).isEmpty())
        assertTrue(selector.select(subtitle, -1L, 3).isEmpty())
        assertTrue(selector.select(subtitle, 60_000L, 0).isEmpty())
        assertTrue(selector.select(subtitle, 60_000L, -1).isEmpty())
    }

    @Test
    fun clampsProbeDurationToShortMediaDuration() {
        // Four 500 ms cues in a 3,000 ms file: occupancy 2,000/3,000 = 0.67, in the accepted band.
        val subtitle = subtitle(
            mediaDurationMs = 3_000L,
            count = 4,
            cueLengthMs = 500L,
            strideMs = 700L,
        )
        val selected = selector.select(subtitle, 3_000L, 3)
        assertTrue(selected.isNotEmpty())
        assertTrue(selected.all { it.durationMs == 3_000L })
        assertTrue(selected.all { it.subtitleStartMs == 0L })
    }

    @Test
    fun neverReturnsMoreCandidatesThanMaxCount() {
        // 200 cues every 2 s keep each 8 s window above the transition minimum.
        val subtitle = subtitle(
            mediaDurationMs = 600_000L,
            count = 200,
            cueLengthMs = 1_200L,
            strideMs = 2_000L,
        )
        assertTrue(selector.select(subtitle, 600_000L, 2).isNotEmpty())
        assertTrue(selector.select(subtitle, 600_000L, 2).size <= 2)
        assertTrue(selector.select(subtitle, 600_000L, 8).size <= 8)
    }

    @Test
    fun skipsWindowsWithTooFewTransitions() {
        val single = ParsedSubtitle(listOf(SubtitleCue(0, 1_000L, 3_000L, "alone")))
        assertTrue(selector.select(single, 60_000L, 3).isEmpty())
    }

    private fun subtitle(
        mediaDurationMs: Long,
        count: Int = 10,
        cueLengthMs: Long = 1_000L,
        strideMs: Long = 5_000L,
    ): ParsedSubtitle {
        val cues = (0 until count).map { index ->
            val start = index * strideMs
            SubtitleCue(index, start, start + cueLengthMs, "Dialogue $index")
        }
        check(cues.last().endMs <= mediaDurationMs)
        return ParsedSubtitle(cues)
    }
}
