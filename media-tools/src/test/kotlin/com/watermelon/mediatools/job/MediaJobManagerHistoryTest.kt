package com.watermelon.mediatools.job

import com.watermelon.mediatools.output.MediaJobOutputStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WM-403 evidence that the *real* MediaJobManager state machine feeds durable history:
 * an active-to-terminal transition is recorded, failures carry their reason, and dismissals
 * remove the record even when the job only survives in history (after a restart).
 *
 * The fake output store replaces the MediaStore/Context-backed OutputFileStore (which cannot
 * be constructed on the JVM) without changing anything about job state transitions.
 */
class MediaJobManagerHistoryTest {

    private class FakeOutputStore : MediaJobOutputStore {
        override fun stagingPathFor(type: MediaJobType, displayName: String) = "/tmp/$displayName"
        override fun publish(
            type: MediaJobType,
            stagingPath: String,
            displayName: String,
        ) = null // deliberately: publishing failure is itself a real terminal path
        override fun deleteStaging(stagingPath: String) = Unit
    }

    private fun manager(history: MediaJobHistory = MediaJobHistory()) = MediaJobManager(
        outputFileStore = FakeOutputStore(),
        scope = CoroutineScope(SupervisorJob() + kotlinx.coroutines.Dispatchers.IO),
        appContext = null, // no foreground service in JVM tests
        jobHistory = history,
    )

    private suspend fun awaitRecord(history: MediaJobHistory) {
        val deadline = System.currentTimeMillis() + 5_000L
        while (history.entries.value.isEmpty() && System.currentTimeMillis() < deadline) {
            delay(20L)
        }
    }

    @Test
    fun `a running job is not in history until it is cancelled`() = runBlocking {
        val history = MediaJobHistory()
        val jobs = manager(history)
        val id = jobs.registerCoroutineJob(
            MediaJobType.COMPRESS,
            "content://media/video/1",
            "/tmp/out.mp4",
        ) { _ -> delay(60_000L) }

        assertEquals(1, jobs.jobs.value.size)
        assertTrue(history.entries.value.isEmpty())

        jobs.cancel(id)

        val entry = history.entries.value.single()
        assertEquals(id, entry.id)
        assertEquals(MediaJobTerminalState.CANCELLED, entry.terminalState)
        assertTrue(entry.finishedAtEpochMs > 0L)
    }

    @Test
    fun `a coroutine job that throws is recorded as failed with its reason`() = runBlocking {
        val history = MediaJobHistory()
        val jobs = manager(history)
        jobs.registerCoroutineJob(
            MediaJobType.EXTRACT_AUDIO,
            "content://media/video/2",
            "/tmp/out.mp3",
        ) { _ -> throw IllegalStateException("encoder unavailable") }

        awaitRecord(history)

        val entry = history.entries.value.single()
        assertEquals(MediaJobTerminalState.FAILED, entry.terminalState)
        assertEquals("encoder unavailable", entry.failureReason)
        assertEquals(null, entry.outputUri)
        assertTrue(jobs.jobs.value.single().state is MediaJobState.Failed)
    }

    @Test
    fun `a job whose output cannot be published is recorded as a terminal failure`() = runBlocking {
        val history = MediaJobHistory()
        val jobs = manager(history)
        jobs.registerCoroutineJob(
            MediaJobType.EXTRACT_AUDIO,
            "content://media/video/3",
            "/tmp/out.mp3",
        ) { _ -> Unit }

        awaitRecord(history)

        val entry = history.entries.value.single()
        assertEquals(MediaJobTerminalState.FAILED, entry.terminalState)
        assertTrue(entry.failureReason!!.contains("publishing the output file failed"))
    }

    @Test
    fun `dismiss removes a live terminal job and a history-only record after restart`() = runBlocking {
        val history = MediaJobHistory()
        val jobs = manager(history)
        val id = jobs.registerCoroutineJob(
            MediaJobType.TRIM,
            "content://media/video/4",
            "/tmp/out.mp4",
        ) { _ -> delay(60_000L) }
        jobs.cancel(id)
        assertEquals(1, history.entries.value.size)

        jobs.dismiss(id)
        assertTrue(jobs.jobs.value.isEmpty())
        assertTrue(history.entries.value.isEmpty())

        // A record restored from disk has no live job behind it: Dismiss must still work.
        history.onJobUpdated(
            MediaJob(
                id = "stale",
                type = MediaJobType.COMPRESS,
                inputUri = "content://media/video/5",
                outputPath = "/tmp/stale.mp4",
                state = MediaJobState.Completed("content://media/video/5/out.mp4"),
            )
        )
        jobs.dismiss("stale")
        assertTrue(history.entries.value.isEmpty())
    }

    @Test
    fun `clearing history does not cancel or drop live jobs`() = runBlocking {
        val history = MediaJobHistory()
        val jobs = manager(history)
        jobs.registerCoroutineJob(
            MediaJobType.COMPRESS,
            "content://media/video/6",
            "/tmp/out.mp4",
        ) { _ -> delay(60_000L) }
        history.onJobUpdated(
            MediaJob(
                id = "old",
                type = MediaJobType.TRIM,
                inputUri = "content://media/video/7",
                outputPath = "/tmp/old.mp4",
                state = MediaJobState.Cancelled,
            )
        )

        jobs.clearHistory()

        assertTrue(history.entries.value.isEmpty())
        assertEquals(1, jobs.jobs.value.size)
    }
}
