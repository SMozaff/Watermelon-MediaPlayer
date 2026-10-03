package com.watermelon.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Watermelon Glass semantic tokens.
 *
 * Glass is a hierarchy layer over the Watermelon palette, not a blanket transparency treatment.
 * Cards may remain translucent, while controls, popups, and modal surfaces use progressively
 * more opaque fills so foreground content remains readable without relying on backdrop blur.
 *
 * Backdrop blur is intentionally not faked here. Compose blur affects the composable being
 * rendered, not arbitrary content behind it, so these tokens provide a deterministic fallback.
 */
object WatermelonGlass {
    val background = Color(0xFF0D0D0D)

    /** Legacy/base card surface. Prefer semantic roles for new UI. */
    val surface = Color(0xFF101010).copy(alpha = 0.56f)

    /** Legacy elevated surface. Prefer [popupSurface] or [modalSurface] for overlays. */
    val surfaceElevated = Color(0xFF101010).copy(alpha = 0.72f)

    val border = Color.White.copy(alpha = 0.18f)
    val highlight = Color.White.copy(alpha = 0.30f)
    val scrim = Color.Black.copy(alpha = 0.62f)
    val glow = WatermelonColors.Palette.WatermelonRed.copy(alpha = 0.30f)
    val disabled = Color.White.copy(alpha = 0.08f)

    @Composable
    private fun isDarkTheme(): Boolean =
        MaterialTheme.colorScheme.background == WatermelonColors.DarkBackground

    @Composable
    fun cardSurface(): Color =
        if (isDarkTheme()) Color(0xFF101010).copy(alpha = 0.58f)
        else Color.White.copy(alpha = 0.88f)

    @Composable
    fun controlSurface(): Color =
        if (isDarkTheme()) Color(0xFF151515).copy(alpha = 0.78f)
        else Color.White.copy(alpha = 0.94f)

    @Composable
    fun popupSurface(): Color =
        if (isDarkTheme()) Color(0xFF181818).copy(alpha = 0.94f)
        else Color.White.copy(alpha = 0.97f)

    @Composable
    fun modalSurface(): Color =
        if (isDarkTheme()) Color(0xFF181818).copy(alpha = 0.96f)
        else Color.White.copy(alpha = 0.98f)

    @Composable
    fun borderColor(): Color =
        if (isDarkTheme()) Color.White.copy(alpha = 0.18f)
        else Color.Black.copy(alpha = 0.14f)

    @Composable
    fun highlightColor(): Color =
        if (isDarkTheme()) Color.White.copy(alpha = 0.30f)
        else Color.White.copy(alpha = 0.70f)

    @Composable
    fun disabledSurface(): Color =
        if (isDarkTheme()) Color.White.copy(alpha = 0.08f)
        else Color.Black.copy(alpha = 0.06f)

    /**
     * Semantic Glass roles. New surfaces should choose a role instead of reaching for
     * legacy surface tokens directly.
     */
    enum class Role {
        Background,
        Card,
        Control,
        Popup,
        Modal,
        ThumbnailOverlay,
        Selected,
        Disabled
    }

    @Composable
    fun surfaceFor(role: Role): Color = when (role) {
        Role.Background -> background
        Role.Card -> cardSurface()
        Role.Control -> controlSurface()
        Role.Popup -> popupSurface()
        Role.Modal -> modalSurface()
        Role.ThumbnailOverlay -> if (isDarkTheme()) Color(0xFF101010).copy(alpha = 0.84f)
            else Color.White.copy(alpha = 0.92f)
        Role.Selected -> WatermelonColors.Accent.copy(alpha = if (isDarkTheme()) 0.22f else 0.14f)
        Role.Disabled -> disabledSurface()
    }

    @Composable
    fun borderFor(role: Role): Color = when (role) {
        Role.Selected -> WatermelonColors.Accent.copy(alpha = 0.70f)
        Role.Disabled -> borderColor().copy(alpha = 0.50f)
        Role.Popup, Role.Modal -> highlightColor()
        else -> borderColor()
    }

    fun shadowElevationFor(role: Role): Dp = when (role) {
        Role.Background, Role.Disabled -> 0.dp
        Role.Card -> 3.dp
        Role.Control, Role.ThumbnailOverlay -> 4.dp
        Role.Popup -> 8.dp
        Role.Modal -> 10.dp
        Role.Selected -> 4.dp
    }
}
