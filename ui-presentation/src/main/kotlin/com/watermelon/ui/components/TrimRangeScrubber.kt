package com.watermelon.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.watermelon.ui.theme.PlayerColors
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Dual-handle range scrubber for TrimScreen -- no existing component in this codebase
 * covers a two-handle range (WatermelonSeekBar/WatermelonTunerSeekBar are both
 * single-position), so this is new, built to match their exact Canvas + PlayerColors
 * drawing style and gesture-detection pattern rather than introduce a different look.
 *
 * REDESIGNED this session: adds a thumbnail filmstrip backdrop (real decoded frames from
 * [com.watermelon.mediatools.engine.FilmstripExtractor], not placeholder tiles) so the user
 * can see what they're actually trimming, and haptic-confirmed snapping to real keyframe
 * positions (from [com.watermelon.mediatools.engine.KeyframeIndexer]) since VideoTrimmer's
 * hard requirement (setStartsAtKeyFrame(true)) means the actual cut always lands on a
 * keyframe regardless of where the user drags -- showing and snapping to those exact points
 * makes the UI honest about where the cut will really happen, instead of letting the user
 * drag to an arbitrary point that then silently shifts.
 *
 * @param startMs / endMs current trim range.
 * @param durationMs total video duration -- the scrubbable range is always [0, durationMs].
 * @param onRangeChange invoked with (startMs, endMs) as either handle is dragged.
 * @param minRangeMs the smallest allowed gap between handles (TrimScreen enforces its own
 *   minimum for enabling the Trim button; this just stops the handles crossing entirely).
 * @param keyframeTimestampsMs real keyframe positions from [com.watermelon.mediatools.engine.KeyframeIndexer.findKeyframeTimestampsMs],
 *   empty until that async load completes -- snapping/tick-marks are simply skipped while empty,
 *   not treated as an error state (a keyframe-free video would be corrupt, not just "loading").
 * @param filmstripFrames real decoded thumbnails from [com.watermelon.mediatools.engine.FilmstripExtractor.extractFilmstrip],
 *   in left-to-right time order; a null entry renders as an empty tile rather than skipping
 *   that slot, so the strip's spacing stays visually even.
 * @param snapThresholdMs how close (in ms) a drag needs to land to a keyframe before it snaps
 *   to it, expressed in time rather than pixels since the same visual proximity means a very
 *   different time gap depending on zoom/duration.
 */
@Composable
fun TrimRangeScrubber(
    startMs: Long,
    endMs: Long,
    durationMs: Long,
    onRangeChange: (startMs: Long, endMs: Long) -> Unit,
    modifier: Modifier = Modifier,
    trackHeight: Dp = 4.dp,
    handleRadius: Dp = 9.dp,
    minRangeMs: Long = 200L,
    keyframeTimestampsMs: List<Long> = emptyList(),
    filmstripFrames: List<Bitmap?> = emptyList(),
    snapThresholdMs: Long = 250L,
) {
    var draggingHandle by remember { mutableStateOf<Handle?>(null) }
    val colors = PlayerColors.current
    val haptic = LocalHapticFeedback.current

    val startFraction = if (durationMs > 0) (startMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val endFraction = if (durationMs > 0) (endMs.toFloat() / durationMs).coerceIn(0f, 1f) else 1f

    Column(modifier = modifier.fillMaxWidth()) {
        if (filmstripFrames.isNotEmpty()) {
            FilmstripRow(filmstripFrames)
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(maxOf(handleRadius * 2 + 8.dp, 48.dp))
                .semantics {
                    contentDescription = "Trim range, start ${formatMs(startMs)}, end ${formatMs(endMs)}"
                }
                .pointerInput(durationMs, keyframeTimestampsMs) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            val usableW = size.width - 2 * handleRadius.toPx()
                            val left = handleRadius.toPx()
                            val startX = left + usableW * startFraction
                            val endX = left + usableW * endFraction
                            // Whichever handle is closer to the touch point starts dragging.
                            draggingHandle = if (abs(offset.x - startX) <= abs(offset.x - endX)) {
                                Handle.START
                            } else {
                                Handle.END
                            }
                        },
                        onDragEnd = { draggingHandle = null },
                        onDragCancel = { draggingHandle = null }
                    ) { change, _ ->
                        val usableW = (size.width - 2 * handleRadius.toPx()).coerceAtLeast(1f)
                        val left = handleRadius.toPx()
                        val f = ((change.position.x - left) / usableW).coerceIn(0f, 1f)
                        val rawMs = (f * durationMs).roundToLong()

                        // Snap to the nearest keyframe within snapThresholdMs, if any --
                        // matches where VideoTrimmer's actual cut will land, per this
                        // component's class doc. Snapping fires a haptic tick so the user
                        // feels the snap happen, same UX pattern as e.g. a ruler/protractor
                        // snapping to marked increments.
                        val nearestKeyframe = keyframeTimestampsMs.minByOrNull { abs(it - rawMs) }
                        val ms = if (nearestKeyframe != null && abs(nearestKeyframe - rawMs) <= snapThresholdMs) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            nearestKeyframe
                        } else {
                            rawMs
                        }

                        when (draggingHandle) {
                            Handle.START -> {
                                val newStart = ms.coerceIn(0L, endMs - minRangeMs)
                                onRangeChange(newStart, endMs)
                            }
                            Handle.END -> {
                                val newEnd = ms.coerceIn(startMs + minRangeMs, durationMs)
                                onRangeChange(startMs, newEnd)
                            }
                            null -> Unit
                        }
                    }
                }
        ) {
            drawTrimRange(
                colors,
                startFraction,
                endFraction,
                trackHeight.toPx(),
                handleRadius.toPx(),
                keyframeTimestampsMs,
                durationMs,
            )
        }
    }
}

/**
 * Row of filmstrip thumbnails behind the scrubber track. Uses plain Compose Image (via
 * Bitmap.asImageBitmap) rather than Coil/AsyncImage -- these are already-decoded in-memory
 * Bitmaps from FilmstripExtractor, not a URL/URI Coil would need to fetch, so routing them
 * through Coil would add no value and just mismatch this codebase's actual data flow. A null
 * frame renders as an empty (colors.seekBarTrack-tinted) box so the strip's spacing stays
 * even even if one specific frame failed to extract.
 */
@Composable
private fun FilmstripRow(frames: List<Bitmap?>) {
    val colors = PlayerColors.current
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(8.dp)),
    ) {
        frames.forEach { bitmap ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                if (bitmap != null) {
                    androidx.compose.foundation.Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .background(colors.seekBarTrack.copy(alpha = 0.4f))
                    )
                }
            }
        }
    }
}

private enum class Handle { START, END }

private fun formatMs(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

private fun DrawScope.drawTrimRange(
    colors: PlayerColors.Scheme,
    startFraction: Float,
    endFraction: Float,
    trackH: Float,
    handleR: Float,
    keyframeTimestampsMs: List<Long>,
    durationMs: Long,
) {
    val cy = size.height / 2f
    val left = handleR
    val right = size.width - handleR
    val usableW = (right - left).coerceAtLeast(0f)
    val startX = left + usableW * startFraction
    val endX = left + usableW * endFraction

    // Full track (dim) -- untrimmed portions
    drawRoundRect(
        color = colors.seekBarTrack,
        topLeft = Offset(left, cy - trackH / 2f),
        size = Size(usableW, trackH),
        cornerRadius = CornerRadius(trackH / 2f, trackH / 2f)
    )
    // Selected range (matches WatermelonSeekBar's played-progress fill color)
    drawRoundRect(
        color = colors.seekBarFill,
        topLeft = Offset(startX, cy - trackH / 2f),
        size = Size((endX - startX).coerceAtLeast(0f), trackH),
        cornerRadius = CornerRadius(trackH / 2f, trackH / 2f)
    )
    // Keyframe tick marks -- small ticks below the track showing exactly where
    // VideoTrimmer's actual cut can land (its setStartsAtKeyFrame(true) requirement snaps
    // to one of these regardless of where the user drags). Drawn thin/short so they read as
    // reference marks, not a second track. Skipped entirely if empty (still loading, or
    // this specific video happens to have no readable keyframes) -- not an error state, see
    // this component's class doc.
    if (durationMs > 0) {
        val tickTop = cy + trackH / 2f + 2f
        val tickBottom = tickTop + 6f
        keyframeTimestampsMs.forEach { kfMs ->
            val fraction = (kfMs.toFloat() / durationMs).coerceIn(0f, 1f)
            val x = left + usableW * fraction
            drawLine(
                color = colors.seekBarFill.copy(alpha = 0.5f),
                start = Offset(x, tickTop),
                end = Offset(x, tickBottom),
                strokeWidth = 1.5f,
            )
        }
    }
    // Start handle
    drawCircle(color = colors.seekBarFill, radius = handleR, center = Offset(startX, cy))
    drawCircle(color = colors.background, radius = handleR * 0.4f, center = Offset(startX, cy))
    // End handle
    drawCircle(color = colors.seekBarFill, radius = handleR, center = Offset(endX, cy))
    drawCircle(color = colors.background, radius = handleR * 0.4f, center = Offset(endX, cy))
}
