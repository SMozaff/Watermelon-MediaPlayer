package com.watermelon.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.watermelon.ui.theme.WatermelonColors
import kotlin.math.cos
import kotlin.math.sin

/** Animated watermelon play-disc based on the product loading mark. */
@Composable
fun VideoLoadingAnimation(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "video-loader")
    val angle by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(2100, easing = LinearEasing)), label = "seed-ring")
    val pulse by transition.animateFloat(
        .94f, 1.02f,
        infiniteRepeatable(tween(820), RepeatMode.Reverse),
        label = "watermelon-pulse"
    )
    Canvas(modifier = modifier) {
        val centre = center
        val radius = size.minDimension * .37f * pulse
        drawCircle(Color(0x22000000), radius = radius * 1.14f, center = centre)
        drawCircle(Color(0xFF197A58), radius = radius, center = centre)
        drawCircle(Color(0xFFB7EA83), radius = radius * .84f, center = centre)
        drawCircle(WatermelonColors.Accent, radius = radius * .73f, center = centre)
        rotate(angle, centre) {
            repeat(12) { index ->
                val theta = Math.toRadians((index * 30.0) - 90.0)
                val seedCentre = Offset(
                    centre.x + cos(theta).toFloat() * radius * .54f,
                    centre.y + sin(theta).toFloat() * radius * .54f
                )
                drawCircle(Color(0xFF202124), radius = radius * .062f, center = seedCentre)
            }
            drawArc(
                Color(0x55FFFFFF), -145f, 88f, false,
                Offset(centre.x - radius * .62f, centre.y - radius * .62f),
                Size(radius * 1.24f, radius * 1.24f),
                style = Stroke(radius * .09f, cap = StrokeCap.Round)
            )
        }
        val playSize = radius * .36f
        val play = Path().apply {
            moveTo(centre.x - playSize * .46f, centre.y - playSize * .66f)
            lineTo(centre.x + playSize * .72f, centre.y)
            lineTo(centre.x - playSize * .46f, centre.y + playSize * .66f)
            close()
        }
        drawPath(play, Color(0xFFF7FAF4))
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
