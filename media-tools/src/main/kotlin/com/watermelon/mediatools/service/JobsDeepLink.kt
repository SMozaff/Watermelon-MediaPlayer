package com.watermelon.mediatools.service

/**
 * Deep-link contract between [MediaJobService]'s notifications and the application's Jobs
 * destination (WM-403: "completion notifications open the relevant item in Jobs rather than a
 * generic or empty surface").
 *
 * Kept as a plain object of constants plus one pure function so both ends — the Service that
 * builds the PendingIntent and the MainActivity that interprets a delivered Intent — depend on
 * the same single definition, and so the routing decision is unit-testable without an
 * Android framework (Intent/Context are unavailable in JVM unit tests).
 */
object JobsDeepLink {

    /** Implicit action resolved by MainActivity's manifest intent-filter. */
    const val ACTION_OPEN_JOBS = "com.watermelon.mediatools.action.OPEN_JOBS"

    /** Extra carrying the job id the notification belonged to; absent means "just open Jobs". */
    const val EXTRA_FOCUS_JOB_ID = "com.watermelon.mediatools.extra.FOCUS_JOB_ID"

    /**
     * Where a delivered deep link should land:
     * - [Jobs]: the Jobs destination itself — used by the ongoing progress notification
     *   (an *active* outcome), where there is no single item to single out.
     * - [JobsItem]: the Jobs destination focused on one finished item — used by a
     *   completion notification, so the user lands on the outcome they were just told about.
     */
    sealed interface Destination {
        data object Jobs : Destination
        data class JobsItem(val jobId: String) : Destination
    }

    /**
     * Pure routing rule: [Destination] when [action] is the Jobs deep link, null otherwise
     * (an unrelated intent must never navigate). A blank/absent focus id resolves to the
     * plain destination instead of an item route that could not match anything.
     */
    fun destination(action: String?, focusJobId: String?): Destination? {
        if (action != ACTION_OPEN_JOBS) return null
        val focus = focusJobId?.trim()?.takeIf { it.isNotEmpty() }
        return if (focus == null) Destination.Jobs else Destination.JobsItem(focus)
    }
}
