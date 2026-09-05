package com.watermelon.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.align
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp

@Composable
fun ProgressBarLayer(
    tunerSeekBarEnabled: Boolean,
    durationMs: Long,
    position: Long,
) {
    if (tunerSeekBarEnabled && durationMs > 0) {
        val watchedFraction = (position.toFloat() / durationMs).coerceIn(0f, 1f)
        Canvas(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp)
        ) {
            drawRect(color = Color.Red, size = size)
            drawRect(
                color = Color.White,
                size = Size(size.width * watchedFraction, size.height)
            )
        }
    }
}