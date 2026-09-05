package com.watermelon.ui.screens

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.contentAlignment
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.align
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import com.watermelon.common.model.SubtitlePosition
import com.watermelon.ui.components.SubtitleOverlay
import com.watermelon.ui.player.VhsEffectController
import com.watermelon.ui.theme.PlayerColors
import kotlin.math.roundToPx

@Composable
fun VideoSurfaceLayer(
    state: PlayerScreenState,
    vhs: VhsEffectController,
    vhsEnabled: Boolean,
    vhsIntensity: Float,
    durationMs: Long,
    surface: @Composable (Modifier) -> Unit,
    subtitleTrack: com.watermelon.common.model.ParsedSubtitle?,
    subtitleStyle: com.watermelon.common.model.SubtitleStyle,
    subtitleOffsetMs: Long,
    position: Long,
    ui: PlayerUiState,
) {
    vhs.configure(vhsEnabled, vhsIntensity)
    vhs.DriveAnimation()

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val surfaceMod = when (state.currentRatio) {
            VideoRatio.FILL, VideoRatio.ORIGINAL -> Modifier.fillMaxSize()
            else -> state.currentRatio.ratio?.let { Modifier.fillMaxWidth().aspectRatio(it) } ?: Modifier.fillMaxSize()
        }
        surface(
            surfaceMod
                .onSizeChanged { vhs.onSurfaceSize(it.width.toFloat(), it.height.toFloat()) }
                .graphicsLayer {
                    scaleX = state.scale; scaleY = state.scale
                    translationX = state.panOffset.x; translationY = state.panOffset.y
                    renderEffect = vhs.effectOrNull()?.asComposeRenderEffect()
                }
        )

        if (vhs.usesLegacyOverlay) {
            androidx.compose.foundation.Canvas(
                surfaceMod
                    .graphicsLayer {
                        scaleX = state.scale; scaleY = state.scale
                        translationX = state.panOffset.x; translationY = state.panOffset.y
                    }
            ) {
                val lineSpacingPx = 6.dp.roundToPx()
                val lineHeightPx = 2.dp.roundToPx()
                val offsetPx = vhs.scanlinePhase * lineSpacingPx
                val alpha = vhs.overlayAlpha
                var y = -lineSpacingPx + offsetPx
                while (y < size.height) {
                    if (y >= -lineHeightPx) {
                        drawRect(
                            color = androidx.compose.ui.graphics.Color.White.copy(alpha = alpha),
                            topLeft = androidx.compose.ui.geometry.Offset(0f, y),
                            size = androidx.compose.ui.geometry.Size(size.width, lineHeightPx.toFloat())
                        )
                    }
                    y += lineSpacingPx
                }
            }
        }
    }

    val effectiveSubtitle = androidx.compose.runtime.remember(subtitleTrack, subtitleOffsetMs) {
        subtitleTrack?.copy(offsetMs = subtitleOffsetMs)
    }
    val activeCue = androidx.compose.runtime.remember(effectiveSubtitle, position) { effectiveSubtitle?.cueAt(position) }
    val subAtTop = subtitleStyle.position == SubtitlePosition.TOP
    SubtitleOverlay(
        text = activeCue?.displayText,
        isRtl = activeCue?.baseRtl ?: false,
        style = subtitleStyle,
        modifier = Modifier
            .align(if (subAtTop) Alignment.TopCenter else Alignment.BottomCenter)
            .padding(
                top = if (subAtTop) (if (ui.controlsVisible) 96.dp else 32.dp) else 0.dp,
                bottom = if (!subAtTop) (if (ui.controlsVisible) 80.dp else 24.dp) else 0.dp
            )
    )
}