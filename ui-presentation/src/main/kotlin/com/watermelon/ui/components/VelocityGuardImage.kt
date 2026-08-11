package com.watermelon.ui.components

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** Distinguishes "still extracting" from "extraction failed" — both used to collapse to null. */
private sealed interface ThumbnailResult {
    object Loading : ThumbnailResult
    data class Loaded(val bitmap: Bitmap) : ThumbnailResult
    object Failed : ThumbnailResult
}

/**
 * Thumbnail loader that always shows the same frame (10% into the video) regardless of
 * scroll speed. Uses an in-memory [LruCache] so each frame is extracted only once —
 * subsequent loads are instant, and no thumbnail ever changes during scroll. Requests are
 * measured from each card's pixel bounds, so large grid/list cards do not stretch a 128px bitmap.
 * Portrait and landscape frames are fitted inside the card rather than cropped into a false
 * 16:9 image; the black matte keeps the video's native composition intact.
 *
 * Replaces the previous fast/slow dual-source approach which caused jarring thumbnail
 * switches because MediaStore and Coil extracted different frames.
 *
 * [isScrollingFast] is kept for API compatibility but no longer changes behavior.
 * [durationMs] is used to calculate the 10% frame time. Defaults to 3 seconds if 0.
 */
@Composable
fun VelocityGuardImage(
    uri: String?,
    modifier: Modifier = Modifier,
    durationMs: Long = 0L,
    isScrollingFast: Boolean = false
) {
    val context = LocalContext.current
    var requestedEdgePx by remember(uri) { mutableIntStateOf(256) }

    @Suppress("ProduceStateDoesNotAssignValue")
    val result by produceState<ThumbnailResult>(
        initialValue = ThumbnailResult.Loading,
        uri,
        durationMs,
        requestedEdgePx
    ) {
        value = ThumbnailResult.Loading
        value = loadThumbnail(context, uri, durationMs, requestedEdgePx)
            ?.let { ThumbnailResult.Loaded(it) }
            ?: ThumbnailResult.Failed
    }

    Box(
        modifier = modifier
            .background(Color.Black)
            .onSizeChanged { size ->
                val desired = (maxOf(size.width, size.height) * 1.25f).toInt()
                    .coerceIn(192, 1280)
                val quantized = ((desired + 63) / 64) * 64
                if (quantized != requestedEdgePx) requestedEdgePx = quantized
            },
        contentAlignment = Alignment.Center
    ) {
        when (val r = result) {
            is ThumbnailResult.Loaded -> Image(
                bitmap             = r.bitmap.asImageBitmap(),
                contentDescription = null,
                // Phone videos are often 9:16 while the thumbnail slots are wider. Fitting
                // preserves what was recorded; unused space becomes a deliberate black matte.
                contentScale       = ContentScale.Fit,
                modifier           = Modifier.fillMaxSize()
            )
            ThumbnailResult.Loading -> CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ThumbnailResult.Failed -> WatermelonIcon(
                icon = com.watermelon.ui.WatermelonIcons.VideoUnavailable,
                contentDescription = "Thumbnail unavailable",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

/**
 * Process-wide LRU cache for extracted video thumbnails. 100 entries × ~40 KB each ≈ 4 MB.
 * Cleared automatically when the process is under memory pressure.
 */
private object ThumbnailCache {
    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }
    fun get(key: String): Bitmap? = cache.get(key)
    fun put(key: String, bitmap: Bitmap) = cache.put(key, bitmap)
}

private suspend fun loadThumbnail(
    context: android.content.Context,
    uri: String?,
    durationMs: Long,
    requestedEdgePx: Int
): android.graphics.Bitmap? {
    if (uri.isNullOrEmpty()) return null
    val edge = requestedEdgePx.coerceIn(192, 1280)
    val cacheKey = "$uri@$edge"
    ThumbnailCache.get(cacheKey)?.let { return it }
    return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            val retriever = android.media.MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, android.net.Uri.parse(uri))
                val frameTimeMicros = if (durationMs > 0L) durationMs * 100L else 3_000_000L
                val rotationDegrees = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION
                )?.toIntOrNull() ?: 0
                val sourceWidth = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH
                )?.toIntOrNull()?.coerceAtLeast(1) ?: edge
                val sourceHeight = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT
                )?.toIntOrNull()?.coerceAtLeast(1) ?: edge
                val orientedWidth = if (rotationDegrees % 180 == 0) sourceWidth else sourceHeight
                val orientedHeight = if (rotationDegrees % 180 == 0) sourceHeight else sourceWidth
                val scale = minOf(edge.toFloat() / orientedWidth, edge.toFloat() / orientedHeight, 1f)
                val decodedWidth = (sourceWidth * scale).toInt().coerceAtLeast(1)
                val decodedHeight = (sourceHeight * scale).toInt().coerceAtLeast(1)
                val raw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    retriever.getScaledFrameAtTime(
                        frameTimeMicros,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                        decodedWidth,
                        decodedHeight
                    )
                } else {
                    retriever.getFrameAtTime(
                        frameTimeMicros,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                    )
                }
                raw?.let {
                    // getFrameAtTime() returns the frame as decoded, ignoring the video's
                    // rotation metadata. A portrait phone video is typically stored as a
                    // landscape frame (e.g. 1920x1080) plus a 90°/270° rotation flag — without
                    // correcting for it here, `it.width`/`it.height` describe the un-rotated
                    // landscape frame, so the "aspect ratio preserving" scale below preserves
                    // the *wrong* ratio and the result comes out sideways/squashed once shown
                    // upright. Rotate first so width/height (and everything downstream) reflect
                    // the video's actual on-screen orientation.
                    val oriented = if (rotationDegrees != 0) {
                        val matrix = android.graphics.Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                        val rotated = android.graphics.Bitmap.createBitmap(
                            it, 0, 0, it.width, it.height, matrix, true
                        )
                        if (rotated !== it) it.recycle()
                        rotated
                    } else {
                        it
                    }
                    // Preserve aspect ratio — fit the measured card request, never stretch.
                    // API 27+ is already decoded close to this size; API 23–26 is reduced here.
                    val ratio = minOf(edge.toFloat() / oriented.width, edge.toFloat() / oriented.height, 1f)
                    val w = (oriented.width * ratio).toInt().coerceAtLeast(1)
                    val h = (oriented.height * ratio).toInt().coerceAtLeast(1)
                    val scaled = android.graphics.Bitmap.createScaledBitmap(oriented, w, h, true)
                    if (scaled !== oriented) oriented.recycle()
                    ThumbnailCache.put(cacheKey, scaled)
                    scaled
                }
            } finally {
                retriever.release()
            }
        }.getOrNull()
    }
}
