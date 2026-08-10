package com.watermelon.mediatools.job

/**
 * One in-flight or finished media processing job (extract audio / trim / compress).
 *
 * [progressPercent] comes from polling `Transformer.getProgress()` on a timer while
 * [state] is [MediaJobState.Running] — Transformer has no push-based progress stream.
 *
 * [requestedStartMs]: TRIM-only, null for other job types. The start time the user actually
 * asked for, before keyframe-snapping — needed by MediaJobManager to compute
 * [MediaJobState.Completed.actualTrimRangeMs] once the real output duration is known.
 *
 * [sourceSizeBytes]: COMPRESS-only, null for other job types. The source file's size at job
 * start, used by MediaJobManager.onCompleted to reject any output that isn't actually
 * smaller than the source (see VideoCompressor's class doc for why this check exists in
 * addition to setEnableFallback(false)).
 */
data class MediaJob(
    val id: String,
    val type: MediaJobType,
    val inputUri: String,
    val outputPath: String,
    val state: MediaJobState,
    val progressPercent: Int = 0,
    val requestedStartMs: Long? = null,
    val sourceSizeBytes: Long? = null,
)

enum class MediaJobType { EXTRACT_AUDIO, TRIM, COMPRESS }

sealed class MediaJobState {
    data object Queued : MediaJobState()
    data object Running : MediaJobState()

    /**
     * [awaitingOriginalFileDecision]: true only for TRIM/COMPRESS jobs, right after
     * completion — UI should show a "keep or delete the original video?" prompt bound to
     * [MediaJob.inputUri]. False for EXTRACT_AUDIO (source video isn't replaced) and false
     * again once the user has answered (see MediaJobManager.resolveOriginalFileDecision).
     *
     * [actualTrimRangeMs]: TRIM-only, null for other job types. Populated with the real
     * (startMs, endMs) of the output file after a keyframe-snapped cut, which can differ
     * slightly from what the user selected (see VideoTrimmer). UI should show this instead
     * of silently assuming the exact requested range was honored.
     */
    data class Completed(
        val outputUri: String,
        val awaitingOriginalFileDecision: Boolean = false,
        val actualTrimRangeMs: Pair<Long, Long>? = null,
    ) : MediaJobState()
    data class Failed(val reason: String) : MediaJobState()
    data object Cancelled : MediaJobState()
}
