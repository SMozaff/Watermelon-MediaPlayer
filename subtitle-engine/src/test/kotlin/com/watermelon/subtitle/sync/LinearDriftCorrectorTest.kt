package com.watermelon.subtitle.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class LinearDriftCorrectorTest {

    private val corrector = LinearDriftCorrector()

    @Test
    fun appliesPositiveOffsetToEveryCueAndPreservesText() {
        val cues = listOf(
            LinearDriftCorrector.Cue(1_000L, 2_000L, "first"),
            LinearDriftCorrector.Cue(3_000L, 4_000L, "second"),
        )
        assertEquals(
            listOf(
                LinearDriftCorrector.Cue(1_500L, 2_500L, "first"),
                LinearDriftCorrector.Cue(3_500L, 4_500L, "second"),
            ),
            corrector.apply(cues, 500L),
        )
    }

    @Test
    fun clampsNegativeResultsAtZeroAndHandlesEmptyInput() {
        assertEquals(
            listOf(LinearDriftCorrector.Cue(0L, 500L, "clamped")),
            corrector.apply(listOf(LinearDriftCorrector.Cue(1_000L, 1_500L, "clamped")), -1_000L),
        )
        assertEquals(emptyList<LinearDriftCorrector.Cue>(), corrector.apply(emptyList(), 500L))
    }

    @Test
    fun applyToTimestampClampsAtZero() {
        assertEquals(2_000L, corrector.applyToTimestamp(1_500L, 500L))
        assertEquals(0L, corrector.applyToTimestamp(1_000L, -2_000L))
        assertEquals(1_000L, corrector.applyToTimestamp(1_000L, 0L))
    }
}
