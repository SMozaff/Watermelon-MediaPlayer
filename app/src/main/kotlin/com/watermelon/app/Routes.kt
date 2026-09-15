package com.watermelon.app

import androidx.navigation.NavController
import androidx.navigation.navigate
import android.net.Uri

/**
 * Builder functions for navigation routes to eliminate duplicate route string construction.
 * 
 * Duplicate patterns found in MainActivity.kt:
 *   navController.navigate("trim/${Uri.encode(item.uri)}/${Uri.encode(item.displayName)}/${item.durationMs}")
 *   navController.navigate("compress/${Uri.encode(item.uri)}/${Uri.encode(item.displayName)}")
 */
object Routes {

    const val FOLDERS = "folders"
    const val ALL_VIDEOS = "all_videos"
    const val SETTINGS = "settings"
    const val FOLDER_VISIBILITY = "folder_visibility"
    const val DESIGN_SYSTEM = "design_system"
    const val PLAYLISTS = "playlists"
    const val FAVORITES = "favorites"

    const val PLAYER = "player/{uri}"
    const val TRIM = "trim/{uri}/{displayName}/{durationMs}"
    const val COMPRESS = "compress/{uri}/{displayName}"

    /**
     * Navigates to the trim screen with the given media item.
     */
    fun navigateTrim(navController: NavController, uri: String, displayName: String, durationMs: Long) {
        navigate(
            navController,
            "trim/${Uri.encode(uri)}/${Uri.encode(displayName)}/${durationMs}"
        ) {
            popUpTo("player/{uri}") { inclusive = true }
        }
    }

    /**
     * Navigates to the compress screen with the given media item.
     */
    fun navigateCompress(navController: NavController, uri: String, displayName: String) {
        navigate(
            navController,
            "compress/${Uri.encode(uri)}/${Uri.encode(displayName)}"
        ) {
            popUpTo("player/{uri}") { inclusive = true }
        }
    }

    /**
     * Navigates to the player screen with the given URI.
     */
    fun navigatePlayer(navController: NavController, uri: String) {
        navigate(
            navController,
            "player/{uri}"
        ) {
            popUpTo("player/{uri}") { inclusive = true }
        }
    }

    /**
     * Navigates to folders screen.
     */
    fun navigateFolders(navController: NavController) {
        navigate(navController, FOLDERS) { popUpTo(FOLDERS) { inclusive = true } }
    }

    /**
     * Navigates to all videos screen.
     */
    fun navigateAllVideos(navController: NavController) {
        navigate(navController, ALL_VIDEOS) { popUpTo(ALL_VIDEOS) { inclusive = true } }
    }
}

/**
 * Extension function for NavController to simplify route navigation with popUpTo.
 */
fun NavController.navigate(
    route: String,
    popUpTo: (() -> Unit)? = null,
    args: Map<String, Any?> = emptyMap()
) {
    if (popUpTo != null) {
        popUpTo(Unit) {
            navigate(route, args)
        }
    } else {
        navigate(route, args)
    }
}