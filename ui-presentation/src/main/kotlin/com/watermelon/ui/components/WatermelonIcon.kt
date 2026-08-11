package com.watermelon.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource

/** Keeps app-owned vector drawables on every Compose surface. */
@Composable
fun WatermelonIcon(
    @DrawableRes icon: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
    preserveArtworkColors: Boolean = true
) {
    // Image deliberately has no Material icon tint behaviour. These resources are illustrations
    // with independent rind, flesh, pith and seed paths, not monochrome glyphs.
    Image(
        painter = painterResource(icon),
        contentDescription = contentDescription,
        modifier = modifier,
        // App icons are multi-layer watermelon artwork. Applying a Compose tint replaces
        // every layer with one flat colour, which is why the rebuilt vectors looked unchanged.
        colorFilter = if (preserveArtworkColors) null else androidx.compose.ui.graphics.ColorFilter.tint(tint)
    )
}
