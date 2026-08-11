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

/** A rotating watermelon slice: rind, cream pith, red flesh and three moving seeds. */
@Composable
fun VideoLoadingAnimation(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "video-loader")
    val angle by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(1300, easing = LinearEasing)), label = "watermelon-slice")
    Canvas(modifier = modifier) {
        val stroke = size.minDimension * .105f
        val centre = center
        val radius = size.minDimension * .33f
        drawCircle(WatermelonColors.Palette.DeepCarbon, radius = radius + stroke * 1.75f, center = centre)
        rotate(angle, centre) {
            val bounds = Offset(centre.x - radius, centre.y - radius)
            val diameter = Size(radius * 2, radius * 2)
            drawArc(Color(0xFF1F8B68), -35f, 282f, false, bounds, diameter, style = Stroke(stroke * 1.75f, cap = StrokeCap.Round))
            drawArc(WatermelonColors.Palette.PaperWhite, -35f, 282f, false, bounds, diameter, style = Stroke(stroke * 1.1f, cap = StrokeCap.Round))
            drawArc(WatermelonColors.Accent, -35f, 282f, false, bounds, diameter, style = Stroke(stroke * .67f, cap = StrokeCap.Round))
            drawCircle(Color(0xFF101614), radius = stroke * .33f, center = Offset(centre.x, centre.y - radius * .45f))
            drawCircle(Color(0xFF101614), radius = stroke * .33f, center = Offset(centre.x - radius * .43f, centre.y + radius * .18f))
            drawCircle(Color(0xFF101614), radius = stroke * .33f, center = Offset(centre.x + radius * .43f, centre.y + radius * .18f))
        }
        drawCircle(Color(0xFF101614), radius = stroke * .48f, center = centre)
    }
}

/** A media-folder loader built around the brand's watermelon-in-a-folder mark. */
@Composable
fun FolderLoadingAnimation(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "folder-loader")
    val sweep by transition.animateFloat(-.2f, 1.2f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "scan")
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val outline = Stroke(size.minDimension * .04f, cap = StrokeCap.Round)
        drawRoundRect(Color(0xFF1F8B68), Offset(w * .10f, h * .29f), Size(w * .80f, h * .54f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(h * .11f))
        drawRoundRect(Color(0xFF1F8B68), Offset(w * .12f, h * .20f), Size(w * .32f, h * .18f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(h * .07f))
        val melonBounds = Offset(w * .22f, h * .38f)
        val melonSize = Size(w * .56f, h * .48f)
        drawArc(WatermelonColors.Palette.PaperWhite, 180f, 180f, true, melonBounds, melonSize)
        drawArc(WatermelonColors.Accent, 180f, 180f, true, Offset(w * .25f, h * .41f), Size(w * .50f, h * .40f))
        drawCircle(Color(0xFF101614), size.minDimension * .027f, Offset(w * .43f, h * .60f))
        drawCircle(Color(0xFF101614), size.minDimension * .027f, Offset(w * .57f, h * .60f))
        drawCircle(Color(0xFF101614), size.minDimension * .027f, Offset(w * .50f, h * .69f))
        val x = (w * sweep).coerceIn(w * .13f, w * .87f)
        drawLine(WatermelonColors.Palette.WarningYellow, Offset(x, h * .32f), Offset(x, h * .79f), strokeWidth = outline.width, cap = StrokeCap.Round)
    }
}

/** Backward-compatible name for older call sites. */
@Composable
fun WatermelonLoadingAnimation(modifier: Modifier = Modifier) = VideoLoadingAnimation(modifier)
