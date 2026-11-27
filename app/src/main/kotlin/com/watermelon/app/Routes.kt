package com.watermelon.app

/**
 * Central route registry for the navigation graph, bottom bar, and mini-player
 * restore logic. Extracted from MainActivity in Phase 2b so [AppNavGraph] owns
 * navigation while the Activity shell owns only the chrome around it.
 *
 * Argument-bearing routes keep their inline argument encoding at the call sites
 * (Uri.encode of the path segment); only the route patterns live here, because
 * `popUpTo`/`currentDestination` comparisons must match the registered pattern
 * string exactly.
 */
internal object Routes {
    const val FOLDERS = "folders"
    const val ALL_VIDEOS = "all_videos"
    const val SETTINGS = "settings"
    const val FOLDER_VISIBILITY = "folder_visibility"
    const val DESIGN_SYSTEM = "design_system"
    const val PLAYLISTS = "playlists"
    const val FAVORITES = "favorites"

    /**
     * Jobs destination (WM-403), in two patterns: [JOBS] opens the surface itself, while
     * [JOBS_ITEM] opens it focused on one record — that is what a completion notification's
     * content intent deep-links to. Both patterns render the same JobsScreen; a concrete
     * item route is built with [jobsItem] so the pattern and the instance can never drift.
     */
    const val JOBS = "jobs"
    const val JOBS_ITEM = "jobs/{jobId}"

    /** Concrete route for [JOBS_ITEM]; job ids are UUIDs (path-safe), so no encoding is needed. */
    fun jobsItem(jobId: String): String = "jobs/$jobId"

    /** Route pattern for the full player; compare destinations against this, never
     *  against a concrete "player/<uri>" instance. */
    const val PLAYER = "player/{uri}"
}
