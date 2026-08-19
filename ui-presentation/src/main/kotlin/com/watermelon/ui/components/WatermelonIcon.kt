package com.watermelon.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource

/**
 * Renders a stateful operational icon. The supplied [tint] is intentionally applied to every
 * path so selected, inactive, disabled, and focused states are communicated predictably.
 */
@Composable
fun WatermelonGlyph(
    @DrawableRes icon: Int,
    contentDescription: String?,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Image(
        painter = painterResource(icon),
        contentDescription = contentDescription,
        modifier = modifier,
        colorFilter = ColorFilter.tint(tint),
    )
}

/**
 * Renders authored multi-colour Watermelon artwork without allowing component tint to flatten it.
 * Use for feature art, branded play moments, logo treatments, and decorative illustrations.
 */
@Composable
fun WatermelonArtwork(
    @DrawableRes icon: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    Image(
        painter = painterResource(icon),
        contentDescription = contentDescription,
        modifier = modifier,
    )
}

/**
 * Backward-compatible bridge for existing call sites.
 *
 * A supplied tint now always selects the operational-glyph path; omitting tint preserves the
 * drawable's authored colours as artwork. New code should use [WatermelonGlyph] or
 * [WatermelonArtwork] directly so the intended rendering model is obvious in review.
 */
@Deprecated(
    message = "Use WatermelonGlyph for stateful controls or WatermelonArtwork for authored artwork.",
    replaceWith = ReplaceWith("WatermelonArtwork(icon, contentDescription, modifier)")
)
@Composable
fun WatermelonIcon(
    @DrawableRes icon: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
) {
    if (tint == Color.Unspecified) {
        WatermelonArtwork(icon = icon, contentDescription = contentDescription, modifier = modifier)
    } else {
        WatermelonGlyph(icon = icon, contentDescription = contentDescription, tint = tint, modifier = modifier)
    }
}
