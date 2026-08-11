package com.watermelon.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.watermelon.ui.R
import com.watermelon.ui.WatermelonIcons
import com.watermelon.ui.theme.WatermelonColors
import com.watermelon.ui.theme.WatermelonShapes
import com.watermelon.ui.theme.WatermelonSpacing
import com.watermelon.ui.theme.WatermelonTypography

/**
 * Status badge for folder/video list items — the "status badges (new / favorite /
 * duration)" component called out in the UI Design System spec. Previously this had
 * no real component: a bare [R.drawable.ic_badge_new] icon was inlined ad hoc at each
 * call site with hand-picked size/padding/tint, and [R.drawable.ic_star] existed in
 * `res/drawable/` completely unreferenced. This gives every call site the same shape,
 * spacing, and type-scale tokens instead of re-deriving them per screen.
 *
 * Three variants:
 * - [StatusBadge.New] — unplayed / newly indexed item. Uses the Watermelon Red accent,
 *   matching the pre-existing (correct) meaning of "new" in this app; the spec's
 *   Warning Yellow token is reserved for buffering/error states (Team 4), not this.
 * - [StatusBadge.Favorite] — starred item. Uses the existing but previously-unused
 *   `ic_star` asset.
 * - [StatusBadge.Duration] — text chip for a formatted duration (e.g. grid thumbnails
 *   with no room for a separate label row).
 */
object StatusBadge {

    @Composable
    fun New(modifier: Modifier = Modifier, compact: Boolean = false) {
        IconBadge(
            icon = WatermelonIcons.New,
            contentDescription = "New",
            tint = WatermelonColors.Accent,
            compact = compact,
            modifier = modifier
        )
    }

    @Composable
    fun Favorite(modifier: Modifier = Modifier, compact: Boolean = false) {
        IconBadge(
            icon = WatermelonIcons.Star,
            contentDescription = "Favorite",
            tint = WatermelonColors.Warning,
            compact = compact,
            modifier = modifier
        )
    }

    @Composable
    fun Duration(text: String, modifier: Modifier = Modifier) {
        Text(
            text = text,
            style = WatermelonTypography.timecode,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = modifier
                .clip(WatermelonShapes.small)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.88f))
                .padding(horizontal = WatermelonSpacing.xs, vertical = WatermelonSpacing.xs / 2)
        )
    }

    /** Row that lays out any combination of badges with consistent spacing. */
    @Composable
    fun Row(
        isNew: Boolean,
        isFavorite: Boolean,
        modifier: Modifier = Modifier,
        compact: Boolean = false
    ) {
        if (!isNew && !isFavorite) return
        androidx.compose.foundation.layout.Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(WatermelonSpacing.xs / 2),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isFavorite) Favorite(compact = compact)
            if (isNew) New(compact = compact)
        }
    }

    @Composable
    private fun IconBadge(
        icon: Int,
        contentDescription: String,
        tint: Color,
        compact: Boolean,
        modifier: Modifier = Modifier
    ) {
        WatermelonIcon(
            icon = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = modifier.size(if (compact) 12.dp else 14.dp)
        )
    }
}
