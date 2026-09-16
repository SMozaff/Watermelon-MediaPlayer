package com.watermelon.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.Image
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.widthIn
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.watermelon.common.model.MediaItem
import com.watermelon.ui.theme.WatermelonColors
import com.watermelon.ui.theme.WatermelonShapes
import com.watermelon.ui.theme.WatermelonSpacing
import com.watermelon.ui.theme.WatermelonTypography

/**
 * Displays thumbnail previews for a list of videos in a horizontal strip.
 */
@Composable
fun ThumbnailStrip(
    videos: List<MediaItem>,
    onSelect: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(WatermelonSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        contentPadding = Padding(horizontal = WatermelonSpacing.md, vertical = WatermelonSpacing.sm),
    ) {
        videos.forEach { video ->
            Row(
                modifier = Modifier
                    .clickable { onSelect(video) }
                    .padding(WatermelonSpacing.xs)
                    .clip(WatermelonShapes.small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painter = painterResource(android.R.drawable.ic_media_play),
                    contentDescription = "Thumbnail for ${video.displayName}",
                    modifier = Modifier
                        .size(48.dp)
                        .clip(WatermelonShapes.small)
                        .background(WatermelonColors.Palette.SurfaceVariant),
                    contentScale = ContentScale.Crop,
                )
                Spacer(modifier = Modifier.width(WatermelonSpacing.sm))
                Column(modifier = Modifier.widthIn(max = 120.dp)) {
                    Text(
                        text = video.displayName,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = WatermelonTypography.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}
