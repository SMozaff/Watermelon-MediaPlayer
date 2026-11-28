package com.watermelon.mediatools.job

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Notification-routing evidence (WM-403): a result is announced exactly once, on the
 * active-to-terminal transition — progress repaints must not spam the notification shade.
 */
class TerminalTransitionTrackerTest {

    private fun job(id: String, state: MediaJobState) = MediaJob(
        id = id,
        type = MediaJobType.COMPRESS,
        inputUri = "content://media/video/$id",
        outputPath = "/tmp/$id.mp4",
        state = state,
    )

    @Test
    fun `active to completed is reported once and never again on repaints`() {
        val tracker = TerminalTransitionTracker()
        assertTrue(tracker.update(listOf(job("1", MediaJobState.Queued))).isEmpty())
        assertTrue(tracker.update(listOf(job("1", MediaJobState.Running))).isEmpty())
        assertTrue(
            tracker.update(
                listOf(job("1", MediaJobState.Running).copy(progressPercent = 42))
            ).isEmpty()
        )

        val completed = job("1", MediaJobState.Completed("content://out.mp4"))
        assertEquals(listOf("1"), tracker.update(listOf(completed)).map { it.id })
        assertTrue(tracker.update(listOf(completed)).isEmpty())
        assertTrue(tracker.update(listOf(completed.copy(progressPercent = 100))).isEmpty())
    }

    @Test
    fun `cancelled jobs are not announced`() {
        val tracker = TerminalTransitionTracker()
        tracker.update(listOf(job("1", MediaJobState.Running)))

        assertTrue(tracker.update(listOf(job("1", MediaJobState.Cancelled))).isEmpty())
    }

    @Test
    fun `failed jobs are announced once, independently of other jobs`() {
        val tracker = TerminalTransitionTracker()
        tracker.update(
            listOf(job("1", MediaJobState.Running), job("2", MediaJobState.Running))
        )

        val failed = job("2", MediaJobState.Failed("out of space"))
        assertEquals(listOf("2"), tracker.update(listOf(job("1", MediaJobState.Running), failed)).map { it.id })
        assertEquals(
            listOf("1"),
            tracker.update(
                listOf(job("1", MediaJobState.Completed("content://out1")), failed)
            ).map { it.id },
        )
    }

    @Test
    fun `a terminal job first seen already finished is never announced`() {
        val tracker = TerminalTransitionTracker()
        assertTrue(
            tracker.update(listOf(job("1", MediaJobState.Completed("content://out")))).isEmpty()
        )
    }

    @Test
    fun `a dismissed job is forgotten, so a reappearing id is not re-announced`() {
        val tracker = TerminalTransitionTracker()
        tracker.update(listOf(job("1", MediaJobState.Running)))

        assertTrue(tracker.update(emptyList()).isEmpty())
        assertTrue(
            tracker.update(listOf(job("1", MediaJobState.Failed("late result")))).isEmpty()
        )
    }
}
