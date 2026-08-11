package com.watermelon.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.material3.Icon
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
    Icon(
        painter = painterResource(icon),
        contentDescription = contentDescription,
        modifier = modifier,
        // App icons are multi-layer watermelon artwork. Applying a Compose tint replaces
        // every layer with one flat colour, which is why the rebuilt vectors looked unchanged.
        tint = if (preserveArtworkColors) Color.Unspecified else tint
    )
}
