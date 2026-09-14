package com.watermelon.app

import androidx.compose.foundation.layout.Modifier
import androidx.navigation.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navArgument
import com.watermelon.ui.screens.DesignSystemScreen
import com.watermelon.ui.screens.FolderBrowserScreen
import com.watermelon.ui.screens.FolderVisibilityScreen
import com.watermelon.ui.screens.PlaylistsScreen
import com.watermelon.ui.screens.SettingsScreen
import com.watermelon.ui.tv.TvPlayerScreen
import com.watermelon.ui.tv.TvSettingsScreen

private object Routes {
    const val FOLDERS = "folders"
    const val ALL_VIDEOS = "all_videos"
    const val SETTINGS = "settings"
    const val FOLDER_VISIBILITY = "folder_visibility"
    const val DESIGN_SYSTEM = "design_system"
    const val PLAYLISTS = "playlists"
    const val FAVORITES = "favorites"
}

@Composable
fun WatermelonNavHost(
    navController: androidx.navigation.NavHostController,
    pureDarkTheme: Boolean,
    onPureDarkThemeChange: (Boolean) -> Unit,
    onForcedRtlChange: (Boolean) -> Unit,
    onPlayerUriChanged: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Routes.FOLDERS,
        modifier = modifier
    ) {
        composable(Routes.FOLDERS) {
            // Folder/playlist browser screen
        }
        composable(Routes.ALL_VIDEOS) {
            // All videos screen
        }
        composable(
            route = "videos/{folderPath}?isPlaylist={isPlaylist}",
            arguments = listOf(
                navArgument("folderPath") { type = androidx.navigation.NavType.StringType },
                navArgument("isPlaylist") { type = androidx.navigation.NavType.BoolType; defaultValue = false }
            )
        ) { backStackEntry ->
            // Folder-scoped videos screen
        }
        composable(Routes.PLAYLISTS) {
            // Playlists screen
        }
        composable(Routes.FAVORITES) {
            // Favorites screen
        }
        composable(Routes.SETTINGS) {
            // Settings screen
        }
        composable(Routes.FOLDER_VISIBILITY) {
            // Folder visibility screen
        }
        composable(Routes.DESIGN_SYSTEM) {
            DesignSystemScreen(onBack = { navController.popBackStack() })
        }
    }
}