package com.watermelon.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Watermelon Glass semantic tokens.
 *
 * These are deliberately semantic rather than component-specific. Glass is a hierarchy
 * layer over the existing Watermelon palette; it is not a global transparency treatment.
 */
object WatermelonGlass {
    val background = Color(0xFF0D0D0D)
    val surface = Color.White.copy(alpha = 0.08f)
    val surfaceElevated = Color.White.copy(alpha = 0.12f)
    val border = Color.White.copy(alpha = 0.16f)
    val highlight = Color.White.copy(alpha = 0.24f)
    val scrim = Color.Black.copy(alpha = 0.42f)
    val glow = WatermelonColors.Palette.WatermelonRed.copy(alpha = 0.30f)
    val disabled = Color.White.copy(alpha = 0.05f)

    enum class Blur(val radius: Dp) {
        None(0.dp),
        Subtle(8.dp),
        Medium(16.dp),
        Strong(24.dp)
    }

    enum class Depth {
        Flat,
        Control,
        Card,
        Floating,
        Modal
    }
}
