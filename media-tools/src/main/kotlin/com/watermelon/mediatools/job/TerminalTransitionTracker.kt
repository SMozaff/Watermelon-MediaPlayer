package com.watermelon.mediatools.job

private val MediaJobState.isActive: Boolean
    get() = this is MediaJobState.Queued || this is MediaJobState.Running

/**
 * Watches successive snapshots of [MediaJobManager.jobs] and reports each job exactly once, at
 * the moment it transitions from active to terminal (WM-403 notification routing).
 *
 * Why a tracker instead of "post a notification when the state changes": the jobs StateFlow
 * re-emits on every 250ms progress tick, so a naive observer would post a result notification
 * per repaint. [update] compares against the previous snapshot, so a job is announced once,
 * never again while it stays terminal.
 *
 * Cancelled jobs are excluded on purpose — the user pressed Cancel themselves and does not
 * need a notification telling them so. A job that disappears from a snapshot (dismissed) is
 * forgotten: ids are freshly generated per run, but forgetting keeps the contract exact.
 */
internal class TerminalTransitionTracker {

    private var observed: Map<String, MediaJobState> = emptyMap()

    /**
     * Feeds the latest snapshot and returns the jobs that just turned terminal
     * (Completed or Failed), in the order they appear in [jobs].
     */
    fun update(jobs: List<MediaJob>): List<MediaJob> {
        val previous = observed
        val newlyTerminal = jobs.filter { job ->
            val before = previous[job.id] ?: return@filter false
            before.isActive && !job.state.isActive && job.state !is MediaJobState.Cancelled
        }
        observed = jobs.associate { it.id to it.state }
        return newlyTerminal
    }
}
