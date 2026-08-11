package com.watermelon.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import com.watermelon.ui.R
import com.watermelon.ui.theme.WatermelonColors
import com.watermelon.ui.theme.WatermelonTypography

/**
 * Bottom navigation bar for Watermelon MediaPlayer.
 * Implements the "Bottom navigation" requirement from the UI Design System.
 */
enum class BottomNavItem(
    val route: String,
    val iconRes: Int,
    val selectedIconRes: Int,
    val label: String
) {
    FOLDERS(
        route = "folders",
        iconRes = R.drawable.ic_folder,
        selectedIconRes = R.drawable.ic_folder_open,
        label = "Folders"
    ),
    VIDEOS(
        route = "all_videos",
        iconRes = R.drawable.ic_video_file,
        selectedIconRes = R.drawable.ic_video_file,
        label = "Videos"
    ),
    PLAYLISTS(
        route = "playlists",
        iconRes = R.drawable.ic_playlist,
        selectedIconRes = R.drawable.ic_playlist,
        label = "Playlists"
    ),
    FAVORITES(
        route = "favorites",
        iconRes = R.drawable.ic_star_off,
        selectedIconRes = R.drawable.ic_star,
        label = "Favorites"
    ),
    SETTINGS(
        route = "settings",
        iconRes = R.drawable.ic_settings,
        selectedIconRes = R.drawable.ic_settings,
        label = "Settings"
    )
}

@Composable
fun WatermelonBottomNavigation(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    val items = listOf(
        BottomNavItem.FOLDERS,
        BottomNavItem.VIDEOS,
        BottomNavItem.PLAYLISTS,
        BottomNavItem.FAVORITES,
        BottomNavItem.SETTINGS
    )

    NavigationBar(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        items.forEach { item ->
            val selected = navController.currentBackStackEntry?.destination?.hierarchy?.any {
                it.route == item.route
            } == true

            NavigationBarItem(
                selected = selected,
                onClick = {
                    navController.navigate(item.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = {
                    Icon(
                        painter = painterResource(
                            if (selected) item.selectedIconRes else item.iconRes
                        ),
                        contentDescription = item.label,
                        tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                label = {
                    Text(
                        text = item.label,
                        style = WatermelonTypography.typography.labelSmall,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                )
            )
        }
    }
}
