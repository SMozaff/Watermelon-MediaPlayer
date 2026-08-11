package com.watermelon.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.watermelon.ui.theme.WatermelonColors

/** A coloured cassette/reel sweep for video thumbnails and video-list indexing. */
@Composable
fun VideoLoadingAnimation(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "video-loader")
    val angle by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(1150, easing = LinearEasing)), label = "reel")
    Canvas(modifier = modifier) {
        val stroke = size.minDimension * .09f
        val centre = center
        val radius = size.minDimension * .34f
        drawCircle(WatermelonColors.Palette.DeepCarbon, radius = radius + stroke * 1.6f, center = centre)
        rotate(angle, centre) {
            drawArc(WatermelonColors.Accent, -22f, 155f, false, Offset(centre.x - radius, centre.y - radius), Size(radius * 2, radius * 2), style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(WatermelonColors.Palette.SoftTeal, 158f, 120f, false, Offset(centre.x - radius, centre.y - radius), Size(radius * 2, radius * 2), style = Stroke(stroke, cap = StrokeCap.Round))
        }
        drawCircle(WatermelonColors.Palette.PaperWhite, radius = stroke * .72f, center = centre)
        drawCircle(WatermelonColors.Accent, radius = stroke * .31f, center = centre)
    }
}

/** A colourful Watermelon folder sweep for media-library discovery. */
@Composable
fun FolderLoadingAnimation(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "folder-loader")
    val sweep by transition.animateFloat(-.2f, 1.2f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "scan")
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val outline = Stroke(size.minDimension * .045f, cap = StrokeCap.Round)
        drawRoundRect(WatermelonColors.Accent, Offset(w * .12f, h * .30f), Size(w * .76f, h * .50f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(h * .10f))
        drawRoundRect(Color(0xFF46C97B), Offset(w * .12f, h * .23f), Size(w * .34f, h * .16f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(h * .07f))
        drawLine(Color(0xFF0D0D0D), Offset(w * .20f, h * .53f), Offset(w * .80f, h * .53f), strokeWidth = outline.width, cap = StrokeCap.Round)
        drawLine(Color(0xFF0D0D0D), Offset(w * .20f, h * .67f), Offset(w * .57f, h * .67f), strokeWidth = outline.width, cap = StrokeCap.Round)
        val x = (w * sweep).coerceIn(w * .13f, w * .87f)
        drawLine(WatermelonColors.Palette.WarningYellow, Offset(x, h * .34f), Offset(x, h * .77f), strokeWidth = size.minDimension * .035f, cap = StrokeCap.Round)
    }
}

/** Backward-compatible name for older call sites. */
@Composable
fun WatermelonLoadingAnimation(modifier: Modifier = Modifier) = VideoLoadingAnimation(modifier)
