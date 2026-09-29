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
    val surface = Color(0xFF101010).copy(alpha = 0.56f)
    val surfaceElevated = Color(0xFF101010).copy(alpha = 0.72f)
    val border = Color.White.copy(alpha = 0.18f)
    val highlight = Color.White.copy(alpha = 0.30f)
    val scrim = Color.Black.copy(alpha = 0.58f)
    val glow = WatermelonColors.Palette.WatermelonRed.copy(alpha = 0.30f)
    val disabled = Color.White.copy(alpha = 0.08f)

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
