package com.watermelon.ui

import androidx.annotation.DrawableRes
import com.watermelon.ui.R

/**
 * App-owned icons. Every entry resolves to a purpose-built 24dp VectorDrawable, so playback,
 * library and action surfaces share the Watermelon red / rind green visual language instead of
 * falling back to unrelated system glyphs.
 */
object WatermelonIcons {
    @DrawableRes val Play = R.drawable.ic_play
    @DrawableRes val Pause = R.drawable.ic_pause
    @DrawableRes val SkipNext = R.drawable.ic_skip_next
    @DrawableRes val SkipPrevious = R.drawable.ic_skip_previous
    @DrawableRes val FastForward = R.drawable.ic_fast_forward
    @DrawableRes val Rewind = R.drawable.ic_rewind

    @DrawableRes val VolumeHigh = R.drawable.ic_volume_high
    @DrawableRes val VolumeMedium = R.drawable.ic_volume_medium
    @DrawableRes val VolumeLow = R.drawable.ic_volume_low
    @DrawableRes val VolumeMute = R.drawable.ic_volume_mute
    @DrawableRes val VolumeMuteOff = R.drawable.ic_volume_high

    @DrawableRes val RepeatOff = R.drawable.ic_repeat_off
    @DrawableRes val RepeatOne = R.drawable.ic_repeat_one
    @DrawableRes val RepeatAll = R.drawable.ic_repeat_all
    @DrawableRes val ShuffleOn = R.drawable.ic_shuffle_on
    @DrawableRes val ShuffleOff = R.drawable.ic_shuffle_off

    @DrawableRes val Share = R.drawable.ic_share
    @DrawableRes val Favorite = R.drawable.ic_favorite
    @DrawableRes val FavoriteBorder = R.drawable.ic_favorite_off
    @DrawableRes val Delete = R.drawable.ic_delete
    @DrawableRes val PlaylistAdd = R.drawable.ic_playlist_add
    @DrawableRes val Search = R.drawable.ic_search
    @DrawableRes val Settings = R.drawable.ic_settings
    @DrawableRes val Close = R.drawable.ic_close
    @DrawableRes val Check = R.drawable.ic_confirm
    @DrawableRes val CheckCircle = R.drawable.ic_check_circle
    @DrawableRes val Edit = R.drawable.ic_edit
    @DrawableRes val Refresh = R.drawable.ic_refresh
    @DrawableRes val RemoveFromPlaylist = R.drawable.ic_playlist_remove
    @DrawableRes val New = R.drawable.ic_badge_new

    @DrawableRes val ViewList = R.drawable.ic_view_list
    @DrawableRes val ViewGrid = R.drawable.ic_view_grid
    @DrawableRes val Sort = R.drawable.ic_sort_ascending
    @DrawableRes val Folder = R.drawable.ic_folder
    @DrawableRes val FolderOpen = R.drawable.ic_folder_open
    @DrawableRes val Playlist = R.drawable.ic_playlist
    @DrawableRes val VideoLibrary = R.drawable.ic_video_file
    @DrawableRes val Star = R.drawable.ic_star
    @DrawableRes val StarBorder = R.drawable.ic_star_off

    @DrawableRes val ArrowBack = R.drawable.ic_arrow_back
    @DrawableRes val Lock = R.drawable.ic_lock
    @DrawableRes val LockOpen = R.drawable.ic_lock_open
    @DrawableRes val MoreVert = R.drawable.ic_more_vertical
    @DrawableRes val MoreHoriz = R.drawable.ic_more_horizontal
}
