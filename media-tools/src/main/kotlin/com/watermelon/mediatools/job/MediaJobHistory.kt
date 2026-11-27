package com.watermelon.mediatools.job

import com.watermelon.common.util.FileLogger
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val TAG = "MediaJobHistory"

/** Terminal outcome a finished media job ended in. Mirrors the terminal [MediaJobState] types. */
@Serializable
enum class MediaJobTerminalState { COMPLETED, FAILED, CANCELLED }

/**
 * One durable, terminal media-job record (WM-403). This is what a [MediaJob] looks like once it
 * is no longer running and must survive an app restart:
 *
 * - [type] / [inputUri]: operation and source; the UI derives the source label from [inputUri]
 *   the same way it does for live jobs.
 * - [finishedAtEpochMs]: when the job reached its terminal state; used for ordering and for the
 *   "3 minutes ago" line in the Jobs destination.
 * - [outputUri] / [failureReason] / [awaitingOriginalFileDecision]: safe action availability —
 *   Open result only when a Completed record carries an output, Review original only while the
 *   original-file decision is still pending on a live job, Change settings only for Failed.
 *
 * Deliberately does NOT store the trim range or output size: those only matter inside the
 * keep-or-delete-original dialog, which is driven by live [MediaJobState.Completed] state.
 */
@Serializable
data class MediaJobHistoryEntry(
    val id: String,
    val type: MediaJobType,
    val inputUri: String,
    val terminalState: MediaJobTerminalState,
    val finishedAtEpochMs: Long,
    val outputUri: String? = null,
    val failureReason: String? = null,
    val awaitingOriginalFileDecision: Boolean = false,
)

/** Read/write persistence for [MediaJobHistoryEntry] lists. Implementations must never throw out of [read]. */
interface MediaJobHistoryStore {
    fun read(): List<MediaJobHistoryEntry>
    fun write(entries: List<MediaJobHistoryEntry>)
}

/** Non-durable store; the default, so MediaJobHistory stays usable in tests and without storage. */
class InMemoryMediaJobHistoryStore : MediaJobHistoryStore {
    private var rows: List<MediaJobHistoryEntry> = emptyList()

    override fun read(): List<MediaJobHistoryEntry> = rows

    override fun write(entries: List<MediaJobHistoryEntry>) {
        rows = entries
    }
}

/**
 * Durable store backing the bounded Jobs history: one small JSON document in app-private
 * storage (filesDir), written via a temp-file rename so a crash mid-write cannot leave a
 * half-written history behind.
 *
 * A read failure (corrupt/partial/foreign file) degrades to an empty history rather than
 * crashing the app or wedging MediaJobManager — history is a convenience, never a gate on
 * running jobs.
 */
class JsonMediaJobHistoryStore(private val file: File) : MediaJobHistoryStore {

    private val json = Json { ignoreUnknownKeys = true }

    override fun read(): List<MediaJobHistoryEntry> {
        return try {
            if (!file.exists()) emptyList()
            else json.decodeFromString(file.readText())
        } catch (e: Exception) {
            FileLogger.e(TAG, "reading job history from ${file.name} failed -- starting empty", e)
            emptyList()
        }
    }

    override fun write(entries: List<MediaJobHistoryEntry>) {
        try {
            file.parentFile?.mkdirs()
            val temp = File(file.parentFile, "${file.name}.tmp")
            temp.writeText(json.encodeToString(entries))
            if (!temp.renameTo(file)) {
                // renameTo fails on cross-device parents or a read-only target; fall back to a
                // direct write so the record is still durable, then clean up the temp file.
                file.writeText(json.encodeToString(entries))
                temp.delete()
                FileLogger.w(TAG, "job history rename to ${file.name} fell back to a direct write")
            }
        } catch (e: Exception) {
            FileLogger.e(TAG, "writing job history to ${file.name} failed", e)
        }
    }
}

/**
 * Bounded, durable recent-history owner for terminal media jobs (WM-403).
 *
 * Ingests every job-state update via [onJobUpdated] and keeps only terminal records: the
 * transition into Completed/Failed/Cancelled inserts one entry (timestamped once), and a later
 * state repaint of the same job — e.g. the original-file decision being resolved — updates that
 * entry in place without disturbing its original timestamp. Queued/Running updates are ignored,
 * so progress ticks never write to disk.
 *
 * The list is ordered newest-first and capped at [maxEntries]; the oldest records are evicted.
 * Every mutation persists through [store] so the Jobs destination still shows recent outcomes
 * after a normal app restart, while an unwritable store degrades to in-memory-only history
 * (jobs themselves are unaffected).
 *
 * All mutation is serialized under one lock: terminal transitions can arrive concurrently from
 * the main thread (Transformer jobs) and Dispatchers.IO (coroutine jobs), and memory + disk must
 * not drift apart.
 */
class MediaJobHistory(
    private val store: MediaJobHistoryStore = InMemoryMediaJobHistoryStore(),
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val lock = Any()
    private val _entries = MutableStateFlow(load())
    val entries: StateFlow<List<MediaJobHistoryEntry>> = _entries.asStateFlow()

    /** True when this job state should be remembered in history (terminal, not active). */
    private fun terminalStateOf(state: MediaJobState): MediaJobTerminalState? = when (state) {
        is MediaJobState.Completed -> MediaJobTerminalState.COMPLETED
        is MediaJobState.Failed -> MediaJobTerminalState.FAILED
        is MediaJobState.Cancelled -> MediaJobTerminalState.CANCELLED
        else -> null
    }

    /**
     * Ingests one job-state update. Call it from the state owner (MediaJobManager.setState) so
     * every active-to-terminal transition is captured exactly once, and later updates to an
     * already-recorded job (e.g. `awaitingOriginalFileDecision` flipping to false) are merged
     * into the existing record instead of creating a second one.
     */
    fun onJobUpdated(job: MediaJob) {
        val terminal = terminalStateOf(job.state) ?: return
        val completed = job.state as? MediaJobState.Completed
        val failed = job.state as? MediaJobState.Failed

        synchronized(lock) {
            val next = _entries.updateAndGet { current ->
                val existing = current.firstOrNull { it.id == job.id }
                val record = MediaJobHistoryEntry(
                    id = job.id,
                    type = job.type,
                    inputUri = job.inputUri,
                    terminalState = terminal,
                    finishedAtEpochMs = existing?.finishedAtEpochMs ?: clock(),
                    outputUri = completed?.outputUri,
                    failureReason = failed?.reason,
                    awaitingOriginalFileDecision = completed?.awaitingOriginalFileDecision ?: false,
                )
                if (existing == null) {
                    (listOf(record) + current)
                        .sortedByDescending { it.finishedAtEpochMs }
                        .take(maxEntries)
                } else {
                    current.map { if (it.id == job.id) record else it }
                }
            }
            persist(next)
        }
    }

    /** Drops one record (its outcome was dismissed in the Jobs destination). */
    fun remove(id: String) {
        synchronized(lock) {
            val next = _entries.updateAndGet { current -> current.filterNot { it.id == id } }
            persist(next)
        }
    }

    /** Clears every record ("Clear history"). Live jobs are untouched; they re-record on their own. */
    fun clear() {
        synchronized(lock) {
            _entries.value = emptyList()
            persist(emptyList())
        }
    }

    private fun load(): List<MediaJobHistoryEntry> {
        return runCatching { store.read() }
            .getOrElse { e ->
                FileLogger.e(TAG, "loading job history failed -- starting empty", e)
                emptyList()
            }
            .sortedByDescending { it.finishedAtEpochMs }
            .take(maxEntries)
    }

    private fun persist(next: List<MediaJobHistoryEntry>) {
        runCatching { store.write(next) }
            .onFailure { e -> FileLogger.e(TAG, "persisting job history failed", e) }
    }

    companion object {
        /**
         * Upper bound on remembered outcomes. Old records are evicted oldest-first; the list is
         * small on purpose — this is "recent history for recovery", not a job archive.
         */
        const val DEFAULT_MAX_ENTRIES = 50
    }
}
