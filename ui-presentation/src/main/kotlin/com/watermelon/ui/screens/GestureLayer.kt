package com.watermelon.ui.screens

import android.app.Activity
import android.media.AudioManager
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitPointerEvent
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import com.watermelon.common.model.UserIntent
import com.watermelon.ui.player.VhsEffectController
import kotlinx.coroutines.delay
import kotlin.math.abs

@Composable
fun GestureLayer(
    state: PlayerScreenState,
    ui: PlayerUiState,
    viewModel: com.watermelon.ui.viewmodel.PlayerViewModel,
    durationMs: Long,
    position: Long,
    isPlaying: Boolean,
    audioManager: AudioManager,
    maxVolume: Int,
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback,
    activity: Activity?,
    vhs: VhsEffectController,
    onBrightnessChange: ((Float) -> Unit)?,
) {
    Box(
        Modifier.fillMaxSize()
            .pointerInput(durationMs, ui.gesturesEnabled, state.isPlayerSheetOpen) {
                if (!ui.gesturesEnabled || state.isPlayerSheetOpen) return@pointerInput
                awaitEachGesture {
                    val firstDown = awaitFirstDown()
                    val holdOriginX = firstDown.position.x
                    state.holdIsLeft = firstDown.position.x < size.width / 2f
                    state.isPointerDown = true
                    state.isGestureMoving = false
                    var isHorizontal: Boolean? = null
                    var isMultiTouch = false
                    state.seekFrac = if (durationMs > 0) position.toFloat() / durationMs else 0f

                    do {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        val pointerCount = pressed.size

                        if (state.isHolding && pointerCount == 1) {
                            val dx = abs(pressed.first().position.x - holdOriginX)
                            val frac = (dx / size.width).coerceIn(0f, 1f)
                            state.holdSpeed = when {
                                frac < 0.12f -> 2f
                                frac < 0.28f -> 3f
                                frac < 0.5f -> 4f
                                else -> 8f
                            }
                        }

                        if (pointerCount >= 2) {
                            isMultiTouch = true
                            state.isGestureMoving = true
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            if (zoom != 1f) state.scale = (state.scale * zoom).coerceIn(1f, 4f)
                            state.panOffset = if (state.scale > 1f) Offset(state.panOffset.x + pan.x, state.panOffset.y + pan.y) else Offset.Zero
                            event.changes.forEach { it.consume() }
                        } else if (pointerCount == 1 && !isMultiTouch) {
                            val change = pressed.first()
                            val drag = change.positionChange()
                            if (isHorizontal == null && (abs(drag.x) > 10f || abs(drag.y) > 10f)) {
                                isHorizontal = abs(drag.x) > abs(drag.y)
                                state.isGestureMoving = true
                            }
                            when (isHorizontal) {
                                true -> {
                                    state.seekFrac = (state.seekFrac + drag.x / size.width.toFloat() * 0.3f).coerceIn(0f, 1f)
                                    viewModel.onIntent(UserIntent.Seek((state.seekFrac * durationMs).toLong()))
                                    change.consume()
                                }
                                false -> {
                                    if (change.position.x > size.width / 2f) {
                                        state.volumeFraction = (state.volumeFraction - drag.y / size.height * 1.5f).coerceIn(0f, 1f)
                                        val newVol = (state.volumeFraction * maxVolume).toInt().coerceIn(0, maxVolume)
                                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
                                        state.currentVolume = newVol; state.showVolumeIndicator = true
                                    } else {
                                        val newBright = (state.currentBrightness - drag.y / size.height).coerceIn(0.01f, 1f)
                                        state.currentBrightness = newBright
                                        activity?.window?.let { win ->
                                            val a = win.attributes; a.screenBrightness = newBright; win.attributes = a
                                        }
                                        onBrightnessChange?.invoke(newBright)
                                        state.showBrightnessIndicator = true
                                    }
                                    change.consume()
                                }
                                null -> {}
                            }
                        }
                    } while (event.changes.any { it.pressed })

                    state.isPointerDown = false
                    val isTap = !state.isGestureMoving && !isMultiTouch && !state.isHolding
                    val now = System.nanoTime()
                    if (isTap && now - state.lastGestureTapNanos < 300_000_000L) {
                        viewModel.onIntent(if (isPlaying) UserIntent.Pause else UserIntent.Resume)
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        state.lastGestureTapNanos = 0L
                    } else if (isTap) {
                        state.lastGestureTapNanos = now
                        ui.showControls()
                    }
                    state.lastInteraction = now
                }
            }
    )
}