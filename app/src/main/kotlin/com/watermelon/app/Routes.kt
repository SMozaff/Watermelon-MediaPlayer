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

    /** Route pattern for the full player; compare destinations against this, never
     *  against a concrete "player/<uri>" instance. */
    const val PLAYER = "player/{uri}"
}
