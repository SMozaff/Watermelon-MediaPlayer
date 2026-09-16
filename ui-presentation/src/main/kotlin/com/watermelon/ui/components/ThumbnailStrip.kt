package com.watermelon.ui.screens

import androidx.compose.foundation.layout.Arrows

import androidx.compose.foundation.layout.Column

import androidx.compose.foundation.layout.FlexRow

import androidx.compose.foundation.layout.FlexColumn

import androidx.compose.foundation.layout.Padding

import androidx.compose.foundation.layout.Row

import androidx.compose.foundation.layout.Spacer

import androidx.compose.foundation.layout.Text

import androidx.compose.foundation.lazy.LazyColumn

import androidx.compose.foundation.lazy.LazyRow

import androidx.compose.foundation.lazy.rememberSlottedItem

import androidx.compose.foundation.lazy.rememberUniqueId

import androidx.compose.material3.Image

import androidx.compose.material3.Text

import androidx.compose.runtime.Composable

import androidx.compose.runtime.getValue

import androidx.compose.ui.Modifier

import androidx.compose.ui.draw.clip

/**
 * Displays thumbnails for the selected range of videos.
 *
 * @param videos List of VideoItem objects representing the selected range
 * @param startMs Start time of the selection
 * @param endMs End time of the selection
 * @param onSelect Callback to invoke when a video is selected from the thumbnail strip
 */
@Composable
fun ThumbnailStrip(
    videos: List<VideoItem>,
    startMs: Long,
    endMs: Long,
    onSelect: (VideoItem) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        padding = Padding(x = 8, y = 8),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        videos.forEach { video ->
            val isSelected = startMs <= video.uri.timeMs && video.uri.timeMs <= endMs
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painter = ImagePolicy.placeholder(
                        contentDescription = "Thumbnail",
                        content = video.thumbnailUrl ?: "Thumbnail",
                        width = 48.dp,
                        height = 48.dp,
                        clip = Rect(0, 0, 48.dp, 48.dp),
                    ),
                    contentDescription = "Thumbnail",
                )
                Text(
                    text = video.title,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = Color(0xFF666666),
                    fontSize = 12.sp,
                )
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(
                    onClick = { onSelect(video) },
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }
        }
    }
}
