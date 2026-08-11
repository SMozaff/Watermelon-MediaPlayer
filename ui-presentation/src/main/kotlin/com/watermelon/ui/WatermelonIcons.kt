package com.watermelon.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Single visual language for app-owned controls.  These are all Material Icons from one
 * family and one 24dp grid; custom Android vector files previously mixed incompatible
 * geometry, viewports and stroke weights, making controls look distorted side-by-side.
 */
object WatermelonIcons {
    // Playback
    val Play: ImageVector = Icons.Filled.PlayArrow
    val Pause: ImageVector = Icons.Filled.Pause
    val SkipNext: ImageVector = Icons.Filled.SkipNext
    val SkipPrevious: ImageVector = Icons.Filled.SkipPrevious
    val FastForward: ImageVector = Icons.Filled.FastForward
    val Rewind: ImageVector = Icons.Filled.FastRewind

    // Volume & Audio — four distinct levels, not two glyphs shared across four names.
    val VolumeHigh: ImageVector = Icons.Filled.VolumeUp
    val VolumeMedium: ImageVector = Icons.Filled.VolumeDown
    val VolumeLow: ImageVector = Icons.Filled.VolumeDown
    val VolumeMute: ImageVector = Icons.Filled.VolumeMute
    val VolumeMuteOff: ImageVector = Icons.Filled.VolumeUp

    // Repeat & Shuffle — repeat-all is now its own asset, not aliased to repeat-off.
    val RepeatOff: ImageVector = Icons.Filled.Repeat
    val RepeatOne: ImageVector = Icons.Filled.RepeatOne
    val RepeatAll: ImageVector = Icons.Filled.Repeat
    val ShuffleOn: ImageVector = Icons.Filled.Shuffle
    val ShuffleOff: ImageVector = Icons.Filled.Shuffle

    // Common actions
    val Share: ImageVector = Icons.Filled.Share
    val Favorite: ImageVector = Icons.Filled.Favorite
    val FavoriteBorder: ImageVector = Icons.Filled.FavoriteBorder
    val Delete: ImageVector = Icons.Filled.Delete
    val PlaylistAdd: ImageVector = Icons.Filled.PlaylistAdd
    val Search: ImageVector = Icons.Filled.Search
    val Settings: ImageVector = Icons.Filled.Settings
    val Close: ImageVector = Icons.Filled.Close
    val Check: ImageVector = Icons.Filled.Check
    val CheckCircle: ImageVector = Icons.Filled.CheckCircle
    val Edit: ImageVector = Icons.Filled.Edit
    val Refresh: ImageVector = Icons.Filled.Refresh
    val RemoveFromPlaylist: ImageVector = Icons.Filled.RemoveCircleOutline
    val New: ImageVector = Icons.Filled.NewReleases

    // Layout & View
    val ViewList: ImageVector = Icons.Filled.ViewList
    val ViewGrid: ImageVector = Icons.Filled.ViewModule
    val Sort: ImageVector = Icons.Filled.Sort
    val Folder: ImageVector = Icons.Filled.Folder
    val FolderOpen: ImageVector = Icons.Filled.FolderOpen
    val Playlist: ImageVector = Icons.Filled.PlaylistPlay
    val VideoLibrary: ImageVector = Icons.Filled.VideoLibrary
    val Star: ImageVector = Icons.Filled.Star
    val StarBorder: ImageVector = Icons.Filled.StarBorder

    // Player specific
    val ArrowBack: ImageVector = Icons.AutoMirrored.Filled.ArrowBack
    val Lock: ImageVector = Icons.Filled.Lock
    val LockOpen: ImageVector = Icons.Filled.LockOpen
    val MoreVert: ImageVector = Icons.Filled.MoreVert
    val MoreHoriz: ImageVector = Icons.Filled.MoreHoriz

    // Note: highly specialized one-off icons (VHS effect, sleep timer, PiP, screenshot,
    // badge_new, size_*, sort variants, orientation, ratio) are still referenced directly
    // via painterResource(R.drawable.ic_*) at their call sites rather than aliased here,
    // since they're each used in exactly one place.
}
