package com.watermelon.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.watermelon.ui.WatermelonIcons
import com.watermelon.ui.theme.WatermelonColors
import com.watermelon.ui.theme.WatermelonShapes
import com.watermelon.ui.theme.WatermelonSpacing
import com.watermelon.ui.theme.WatermelonTypography

/**
 * Batch controls that state the action in words rather than relying on an icon-only dock. The
 * vertical grouping deliberately prevents narrow phones or large text from hiding high-impact
 * actions, and keeps playlist membership removal distinct from deleting media from the device.
 */
@Composable
fun MultiSelectionDock(
    selectedCount: Int,
    onDeselectAll: () -> Unit,
    onDelete: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
    visible: Boolean = false,
    onRemoveFromPlaylist: (() -> Unit)? = null,
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(WatermelonColors.DarkSurface)
                .border(
                    width = 1.dp,
                    color = WatermelonColors.DarkOutline,
                    shape = WatermelonShapes.sharp,
                )
                .padding(WatermelonSpacing.md),
            verticalArrangement = Arrangement.spacedBy(WatermelonSpacing.xs),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WatermelonIcon(
                    icon = WatermelonIcons.CheckCircle,
                    contentDescription = "Selection active",
                    tint = WatermelonColors.Accent,
                )
                Text(
                    text = "$selectedCount selected",
                    style = WatermelonTypography.typography.labelLarge,
                    color = WatermelonColors.DarkOnSurface,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = WatermelonSpacing.sm),
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDeselectAll) {
                    Text("Clear selection", color = WatermelonColors.DarkOnSurface)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(WatermelonSpacing.xs),
            ) {
                TextButton(
                    onClick = onAddToPlaylist,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Add to playlist", color = WatermelonColors.DarkOnSurface)
                }
                TextButton(
                    onClick = onShare,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Share", color = WatermelonColors.DarkOnSurface)
                }
            }

            onRemoveFromPlaylist?.let { remove ->
                TextButton(
                    onClick = remove,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Remove from this playlist", color = WatermelonColors.DarkOnSurface)
                }
            }

            TextButton(
                onClick = onDelete,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Delete from device", color = WatermelonColors.Error)
            }
        }
    }
}

@Preview
@Composable
private fun MultiSelectionDockPreview() {
    MultiSelectionDock(
        selectedCount = 3,
        onDeselectAll = {},
        onDelete = {},
        onAddToPlaylist = {},
        onShare = {},
        visible = true,
        onRemoveFromPlaylist = {},
    )
}
