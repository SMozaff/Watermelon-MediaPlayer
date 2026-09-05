package com.watermelon.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.align
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.watermelon.ui.components.LevelIndicator
import com.watermelon.ui.components.LockOverlay
import com.watermelon.ui.player.VhsEffectController
import com.watermelon.ui.theme.PlayerColors
import com.watermelon.ui.WatermelonIcons
import com.watermelon.ui.components.WatermelonGlyph

@Composable
fun TransientIndicatorsLayer(
    state: PlayerScreenState,
    ui: PlayerUiState,
    vhs: VhsEffectController,
) {
    if (state.isHolding) {
        Row(
            modifier = Modifier.align(Alignment.Center)
                .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(8.dp))
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (state.holdIsLeft) WatermelonGlyph(WatermelonIcons.Rewind, null, tint = PlayerColors.current.iconDefault, modifier = Modifier.width(24.dp).height(24.dp))
            Text("${state.holdSpeed.toInt()}×", color = PlayerColors.current.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            if (!state.holdIsLeft) WatermelonGlyph(WatermelonIcons.FastForward, null, tint = PlayerColors.current.iconDefault, modifier = Modifier.width(24.dp).height(24.dp))
        }
    }
    if (state.showVolumeIndicator) {
        LevelIndicator(
            fraction = state.volumeFraction,
            icon = if (state.currentVolume == 0) WatermelonIcons.VolumeMute else WatermelonIcons.VolumeHigh,
            contentDescription = "Volume",
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 24.dp)
        )
    }
    if (state.showBrightnessIndicator) {
        LevelIndicator(
            fraction = state.currentBrightness,
            icon = WatermelonIcons.BrightnessHigh,
            contentDescription = "Brightness",
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 24.dp)
        )
    }
    state.screenshotMessage?.let { msg ->
        Box(
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 60.dp)
                .background(Color.Black.copy(alpha = 0.78f), RoundedCornerShape(6.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) { Text(msg, color = PlayerColors.current.textPrimary) }
    }

    if (ui.isLocked) {
        LockOverlay(
            onUnlock = { ui.unlock(); state.onLockChanged?.invoke(false) },
            modifier = Modifier.fillMaxSize()
        )
    }
}