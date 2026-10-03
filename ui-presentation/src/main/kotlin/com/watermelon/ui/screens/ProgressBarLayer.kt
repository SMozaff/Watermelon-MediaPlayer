package com.watermelon.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import com.watermelon.ui.theme.PlayerColors

@Composable
fun ProgressBarLayer(
    tunerSeekBarEnabled: Boolean,
    durationMs: Long,
    position: Long,
) {
    if (tunerSeekBarEnabled && durationMs > 0) {
        val watchedFraction = (position.toFloat() / durationMs).coerceIn(0f, 1f)
        val scheme = PlayerColors.current
        // Full-size scope so .align(BottomCenter) resolves, as in the original root Box.
        Box(Modifier.fillMaxSize()) {
        Canvas(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp)
        ) {
            // Track first, watched fill second. This layer previously drew a full-width
            // Color.Red bar and then a white bar over the *watched* portion, so the watched
            // region rendered white and the unwatched region rendered red — inverted from the
            // design spec's "brand red fill over a dark track", and off-brand besides
            // (Color.Red #FF0000 is not the Watermelon Red #E63946).
            drawRect(color = scheme.seekBarTrack, size = size)
            drawRect(
                color = scheme.seekBarFill,
                size = Size(size.width * watchedFraction, size.height)
            )
        }
        }
    }
}