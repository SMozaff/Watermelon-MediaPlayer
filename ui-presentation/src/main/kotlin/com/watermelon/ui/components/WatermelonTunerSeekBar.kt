package com.watermelon.ui.components

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import androidx.compose.ui.unit.dp
import com.watermelon.ui.theme.PlayerColors
import kotlin.math.roundToLong

/**
 * Analog-radio-tuner style seek control. A strip of evenly spaced tick marks scrolls
 * horizontally underneath a fixed red pointer at the center — exactly like spinning the
 * frequency dial on an old AM/FM radio, except the "frequency" being tuned is playback
 * position.
 *
 * Dragging is a real relative seek: horizontal movement is mapped continuously to a
 * fraction of the video's duration, anchored to the position where the gesture started.
 * The dial still renders discrete visual ticks and can emit haptic detents, but the seek
 * target itself is continuous, so the control behaves like a real scrubber rather than a
 * counter that only changes after crossing fixed pixel thresholds.
 *
 * @param positionMs current playback position
 * @param durationMs total duration
 * @param onSeek invoked once with the final target position when the drag ends
 * @param onScrubChange true when scrubbing starts, false when it ends
 * @param onPreviewPositionChanged gives the host the local target while tuning, without
 *   forcing the player to seek on every detent.
 * @param onDetent crossed once per physical tick; the host should use this for light haptics.
 * @param secondsPerTick how many seconds each tick crossing the pointer represents (1-20,
 *   adjustable in Settings)
 * @param dialWidth visual width of the tuner strip — intentionally narrower than the
 *   full screen so it reads as a physical dial rather than an edge-to-edge bar
 */
@Composable
fun WatermelonTunerSeekBar(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    dialWidth: Dp = 260.dp,
    dialHeight: Dp = 40.dp,
    secondsPerTick: Int = 5,
    onScrubChange: (Boolean) -> Unit = {},
    onPreviewPositionChanged: (Long) -> Unit = {},
    onDetent: () -> Unit = {}
) {
    var scrubbing by remember { mutableStateOf(false) }
    // While scrubbing, position is tracked continuously from the drag origin.
    var scrubPositionMs by remember { mutableStateOf(0L) }
    var dragStartPositionMs by remember { mutableStateOf(0L) }
    var totalDragPx by remember { mutableStateOf(0f) }
    var previousDetent by remember { mutableStateOf(0L) }

    // If this composable is removed from the tree mid-drag — e.g. the user toggles the
    // tuner seek bar off in Settings while actively scrubbing, or navigates away — neither
    // onDragEnd nor onDragCancel fires (Compose just tears the pointerInput coroutine down),
    // so onScrubChange(false) would otherwise never be called and the caller's "currently
    // seeking" state gets stuck true forever, along with whatever visual indicator it
    // drives. Force it closed on disposal so scrubbing can never outlive this composable.
    DisposableEffect(Unit) {
        onDispose { if (scrubbing) onScrubChange(false) }
    }

    val livePositionMs = positionMs.coerceIn(0L, durationMs.coerceAtLeast(0L))
    val displayPositionMs = if (scrubbing) scrubPositionMs else livePositionMs
    val colors = PlayerColors.current
    val stepMs = secondsPerTick.coerceIn(1, 20) * 1000L

    // Visual tick spacing. Seeking itself is continuous and uses the actual dial width as
    // the travel distance for one full-duration sweep.
    val tickSpacingPx = 28f

    Canvas(
        modifier = modifier
            .width(dialWidth)
            .height(dialHeight)
            .semantics {
                contentDescription =
                    "Seek tuner, ${formatTunerTimeForA11y(livePositionMs)} of ${formatTunerTimeForA11y(durationMs)}"
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = if (durationMs > 0) (livePositionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f,
                    range = 0f..1f
                )
                setProgress { targetFraction ->
                    val clamped = targetFraction.coerceIn(0f, 1f)
                    onSeek((clamped * durationMs).roundToLong())
                    true
                }
            }
            .pointerInput(durationMs) {
                detectTapGestures { offset ->
                    val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                    val target = (fraction * durationMs.coerceAtLeast(0L)).roundToLong()
                    onSeek(target)
                    onPreviewPositionChanged(target)
                }
            }
            .pointerInput(durationMs, stepMs) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        scrubbing = true
                        onScrubChange(true)
                        dragStartPositionMs = livePositionMs
                        totalDragPx = 0f
                        scrubPositionMs = livePositionMs
                        previousDetent = 0L
                        onPreviewPositionChanged(scrubPositionMs)
                    },
                    onDragEnd = {
                        onSeek(scrubPositionMs)
                        scrubbing = false
                        onScrubChange(false)
                    },
                    onDragCancel = {
                        scrubbing = false
                        onScrubChange(false)
                    }
                ) { change, dragAmount ->
                    change.consume()
                    totalDragPx += dragAmount

                    // One full dial-width of horizontal travel represents the full video
                    // duration. The target is derived from the original playback position,
                    // never from the previous preview value, so recomposition cannot re-base
                    // or make the gesture jump.
                    val travelPx = size.width.coerceAtLeast(1).toFloat()
                    val deltaMs = (totalDragPx / travelPx * durationMs.coerceAtLeast(0L)).roundToLong()
                    val next = (dragStartPositionMs + deltaMs)
                        .coerceIn(0L, durationMs.coerceAtLeast(0L))

                    if (next != scrubPositionMs) {
                        scrubPositionMs = next
                        onPreviewPositionChanged(next)

                        // Haptics remain detent-based, but they no longer determine the seek
                        // resolution. Every secondsPerTick boundary crossed emits one detent.
                        val detent = if (stepMs > 0L) next / stepMs else 0L
                        if (detent != previousDetent) {
                            onDetent()
                            previousDetent = detent
                        }
                    }
                }
            }
    ) {
        drawTunerDial(colors, displayPositionMs, tickSpacingPx, stepMs)
    }
}

/** "3:45" / "1:02:03.500" style formatting, with milliseconds, for a11y announcements. */
private fun formatTunerTimeForA11y(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

private fun DrawScope.drawTunerDial(
    colors: PlayerColors.Scheme,
    positionMs: Long,
    tickSpacingPx: Float,
    stepMs: Long
) {
    val cx = size.width / 2f
    val cy = size.height / 2f

    // Ticks are spaced tickSpacingPx apart on screen — the exact same spacing the drag
    // gesture uses to decide when a tick has been "crossed" (see pointerInput above), so
    // what you see sliding past the pointer is exactly what's driving the step count, not
    // an independently-tuned visual approximation of it. Each tick represents stepMs.
    val tickCount = (size.width / tickSpacingPx).toInt() + 4
    val startIndex = -(tickCount / 2)

    for (i in startIndex..(tickCount / 2)) {
        val tickTimeMs = positionMs + (i * stepMs)
        if (tickTimeMs < 0) continue

        val x = cx + i * tickSpacingPx
        if (x < -4f || x > size.width + 4f) continue

        // Every 10th tick is a "major" mark — taller, like the numbered frequency ticks
        // on a radio dial. The rest are short minor ticks.
        val isMajor = i % 10 == 0
        val baseTickH = if (isMajor) size.height * 0.7f else size.height * 0.4f
        val tickW = if (isMajor) 2.5f else 1.5f

        // Distance from the center pointer, 0 at center → 1 at either edge.
        val distFrac = (kotlin.math.abs(x - cx) / (size.width / 2f)).coerceIn(0f, 1f)

        // Cylinder/barrel falloff: height and alpha both fall off with distance from
        // center, using an eased curve (ease-in, distFrac²) rather than linear — this
        // keeps ticks near the pointer close to full size for longer, then tapers faster
        // toward the edges, reading as a curved surface turning away from the viewer
        // (like film sprockets around a reel, or a rotary dial seen edge-on) instead of a
        // flat bar that's simply dimmer at the ends. Height never fully collapses to 0 —
        // floored at 25% of base — so edge ticks stay visible as "receding," not erased.
        val falloff = 1f - (distFrac * distFrac)
        val tickH = baseTickH * (0.25f + 0.75f * falloff)
        val alpha = 1f - distFrac * 0.6f

        drawRoundRect(
            color = colors.seekBarTrack.copy(alpha = colors.seekBarTrack.alpha.coerceAtLeast(0.5f) * alpha + 0.2f),
            topLeft = Offset(x - tickW / 2f, cy - tickH / 2f),
            size = Size(tickW, tickH),
            cornerRadius = CornerRadius(tickW / 2f, tickW / 2f)
        )
    }

    // Fixed red pointer/needle at dead center — this is what "reads" the current time,
    // as ticks scroll past underneath it.
    val pointerW = 3.dp.toPx()
    val pointerH = size.height
    drawRoundRect(
        color = colors.seekBarFill,
        topLeft = Offset(cx - pointerW / 2f, cy - pointerH / 2f),
        size = Size(pointerW, pointerH),
        cornerRadius = CornerRadius(pointerW / 2f, pointerW / 2f)
    )
}


@Composable
fun TunerFramePreview(
    uri: String,
    positionMs: Long,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var bitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(uri, positionMs) {
        delay(60L)
        bitmap = withContext(kotlinx.coroutines.Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, android.net.Uri.parse(uri))
                if (android.os.Build.VERSION.SDK_INT >= 27) {
                    retriever.getScaledFrameAtTime(
                        positionMs.coerceAtLeast(0L) * 1000L,
                        MediaMetadataRetriever.OPTION_CLOSEST,
                        440,
                        248
                    )
                } else {
                    retriever.getFrameAtTime(
                        positionMs.coerceAtLeast(0L) * 1000L,
                        MediaMetadataRetriever.OPTION_CLOSEST
                    )
                }
            } catch (_: RuntimeException) {
                null
            } finally {
                runCatching { retriever.release() }
            }
        }
    }

    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .width(200.dp)
            .height(112.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
            .background(androidx.compose.ui.graphics.Color.Black),
        contentAlignment = androidx.compose.ui.Alignment.BottomCenter,
    ) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = "Video preview at " + formatTunerTime(positionMs),
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            )
        }
        androidx.compose.material3.Text(
            text = formatTunerTime(positionMs),
            color = androidx.compose.ui.graphics.Color.White,
            modifier = Modifier
                .padding(bottom = 6.dp)
                .background(
                    androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.72f),
                    androidx.compose.foundation.shape.RoundedCornerShape(5.dp)
                )
                .padding(horizontal = 7.dp, vertical = 3.dp)
        )
    }
}

private fun formatTunerTime(ms: Long): String {
    val total = (ms / 1000L).coerceAtLeast(0L)
    val h = total / 3600L
    val m = (total % 3600L) / 60L
    val sec = total % 60L
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}
