package com.watermelon.mediatools.job

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * WM-403 durable-history coverage: pure-JVM paths only (a real file for the JSON store,
 * injected clock for stable timestamps). No Android APIs are touched, so these run in
 * :media-tools:testDebugUnitTest on CI.
 */
class MediaJobHistoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun job(
        id: String,
        state: MediaJobState,
        type: MediaJobType = MediaJobType.TRIM,
    ) = MediaJob(
        id = id,
        type = type,
        inputUri = "content://media/external/video/$id",
        outputPath = "/staging/$id.mp4",
        state = state,
    )

    private fun history(
        store: MediaJobHistoryStore = InMemoryMediaJobHistoryStore(),
        maxEntries: Int = MediaJobHistory.DEFAULT_MAX_ENTRIES,
        clock: () -> Long = { 1_000L },
    ) = MediaJobHistory(store = store, maxEntries = maxEntries, clock = clock)

    @Test
    fun `queued and running updates are not written to history`() {
        val history = history()
        val queued = job("1", MediaJobState.Queued)
        val running = queued.copy(state = MediaJobState.Running, progressPercent = 40)
        history.onJobUpdated(queued)
        history.onJobUpdated(running)

        assertTrue(history.entries.value.isEmpty())
    }

    @Test
    fun `first terminal transition records one dated completion with its output`() {
        val history = history(clock = { 1_234L })
        history.onJobUpdated(job("1", MediaJobState.Running))
        history.onJobUpdated(
            job(
                "1",
                MediaJobState.Completed(
                    outputUri = "content://media/external/video/1/out.mp4",
                    awaitingOriginalFileDecision = true,
                )
            )
        )

        val entry = history.entries.value.single()
        assertEquals("1", entry.id)
        assertEquals(MediaJobType.TRIM, entry.type)
        assertEquals(MediaJobTerminalState.COMPLETED, entry.terminalState)
        assertEquals("content://media/external/video/1/out.mp4", entry.outputUri)
        assertTrue(entry.awaitingOriginalFileDecision)
        assertEquals(1_234L, entry.finishedAtEpochMs)
    }

    @Test
    fun `later update merges into the same record and keeps its original timestamp`() {
        var now = 1_000L
        val history = history(clock = { now })
        val completed = MediaJobState.Completed(
            outputUri = "content://out.mp4", awaitingOriginalFileDecision = true
        )
        history.onJobUpdated(job("1", completed))
        now = 9_999L
        history.onJobUpdated(job("1", completed.copy(awaitingOriginalFileDecision = false)))

        val entry = history.entries.value.single()
        assertEquals(1, history.entries.value.size)
        assertEquals(1_000L, entry.finishedAtEpochMs)
        assertEquals(false, entry.awaitingOriginalFileDecision)
    }

    @Test
    fun `failed and cancelled outcomes keep their terminal state and failure reason`() {
        val history = history()
        history.onJobUpdated(job("f", MediaJobState.Failed("encoder unavailable")))
        history.onJobUpdated(job("c", MediaJobState.Cancelled))

        val byId = history.entries.value.associateBy { it.id }
        assertEquals(MediaJobTerminalState.FAILED, byId.getValue("f").terminalState)
        assertEquals("encoder unavailable", byId.getValue("f").failureReason)
        assertEquals(null, byId.getValue("f").outputUri)
        assertEquals(MediaJobTerminalState.CANCELLED, byId.getValue("c").terminalState)
    }

    @Test
    fun `history is newest first and evicts the oldest beyond its bound`() {
        var now = 100L
        val history = history(maxEntries = 3, clock = { now })
        listOf("a", "b", "c", "d").forEach { id ->
            history.onJobUpdated(job(id, MediaJobState.Completed("content://$id")))
            now += 10L
        }

        assertEquals(listOf("d", "c", "b"), history.entries.value.map { it.id })
        assertEquals(listOf(130L, 120L, 110L), history.entries.value.map { it.finishedAtEpochMs })
    }

    @Test
    fun `dismiss and clear remove the same records that were persisted`() {
        val file = File(tempFolder.root, "history.json")
        val store = JsonMediaJobHistoryStore(file)
        val history = history(store = store)
        history.onJobUpdated(
            job("a", MediaJobState.Completed("content://a"), MediaJobType.EXTRACT_AUDIO)
        )
        history.onJobUpdated(job("b", MediaJobState.Failed("no space")))

        history.remove("a")
        assertEquals(listOf("b"), history.entries.value.map { it.id })
        assertEquals(listOf("b"), store.read().map { it.id })

        history.clear()
        assertTrue(history.entries.value.isEmpty())
        assertTrue(store.read().isEmpty())
    }

    @Test
    fun `a fresh history reloads persisted records after an app restart`() {
        val file = File(tempFolder.root, "history.json")
        val store = JsonMediaJobHistoryStore(file)
        val before = history(store = store, clock = { 5_000L })
        before.onJobUpdated(
            job(
                "1",
                MediaJobState.Completed("content://out.mp4", awaitingOriginalFileDecision = true)
            )
        )
        before.onJobUpdated(job("2", MediaJobState.Cancelled))

        val after = MediaJobHistory(store = store)
        assertEquals(
            listOf("1", "2"),
            // Same insertion time for both (a fixed clock) — compare the id set, then check
            // the fields that must survive the JSON round-trip.
            after.entries.value.map { it.id }.sorted(),
        )
        val first = after.entries.value.first { it.id == "1" }
        assertEquals(MediaJobType.TRIM, first.type)
        assertEquals(5_000L, first.finishedAtEpochMs)
        assertEquals("content://out.mp4", first.outputUri)
        assertTrue(first.awaitingOriginalFileDecision)
        assertEquals(
            MediaJobTerminalState.CANCELLED,
            after.entries.value.first { it.id == "2" }.terminalState,
        )
    }

    @Test
    fun `a corrupt file loads as empty and the next write replaces it`() {
        val file = File(tempFolder.root, "history.json")
        file.writeText("{not json at all")
        val history = history(store = JsonMediaJobHistoryStore(file))
        assertTrue(history.entries.value.isEmpty())

        history.onJobUpdated(job("new", MediaJobState.Completed("content://new")))
        assertEquals(listOf("new"), MediaJobHistory(JsonMediaJobHistoryStore(file)).entries.value.map { it.id })
    }

    @Test
    fun `an unwritable store still keeps history in memory`() {
        val failingStore = object : MediaJobHistoryStore {
            override fun read() = emptyList<MediaJobHistoryEntry>()
            override fun write(entries: List<MediaJobHistoryEntry>) = throw IllegalStateException("disk full")
        }
        val history = history(store = failingStore)

        history.onJobUpdated(job("1", MediaJobState.Failed("boom")))

        assertEquals(MediaJobTerminalState.FAILED, history.entries.value.single().terminalState)
    }
}
