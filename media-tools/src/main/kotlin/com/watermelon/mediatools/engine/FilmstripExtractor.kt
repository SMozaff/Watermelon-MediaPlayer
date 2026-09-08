package com.watermelon.mediatools.engine

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.inspector.frame.FrameExtractor
import com.watermelon.common.util.FileLogger
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.withContext

private const val TAG = "FilmstripExtractor"

/**
 * Extracts a row of evenly-spaced decoded thumbnail frames for TrimScreen's filmstrip.
 *
 * Uses Media3 1.11.0's [FrameExtractor] API. FrameExtractor requires construction,
 * frame requests, and close operations for an instance to be accessed from one application
 * thread. Each extraction session therefore owns a dedicated single-thread dispatcher.
 *
 * Frames are decoded rather than read as metadata, so extraction is intentionally performed
 * off the main thread. One FrameExtractor instance is reused across all requested timestamps.
 *
 * NOT run on-device -- signature/shape confirmed via docs, not verified against a real
 * device or emulator this session.
 */
@UnstableApi
class FilmstripExtractor(private val context: Context) {

    /**
     * Extracts [frameCount] thumbnails evenly spaced across [0, durationMs], downscaled via
     * [Presentation.createForShortSide] to reduce memory/decode cost versus full-resolution
     * frames.
     *
     * Returns bitmaps in timestamp order; a null entry means that specific frame failed to
     * extract. Callers should render a placeholder for null entries rather than treating one
     * failed timestamp as fatal to the entire filmstrip.
     */
    suspend fun extractFilmstrip(
        uri: Uri,
        durationMs: Long,
        frameCount: Int,
        targetShortSidePx: Int = 180,
    ): List<Bitmap?> {
        if (durationMs <= 0 || frameCount <= 0) return emptyList()

        val mediaItem = MediaItem.fromUri(uri)
        val dispatcher = Executors
            .newSingleThreadExecutor()
            .asCoroutineDispatcher()

        return try {
            withContext(dispatcher) {
                val extractor = FrameExtractor.Builder(context, mediaItem)
                    .setEffects(listOf(Presentation.createForShortSide(targetShortSidePx)))
                    .build()

                try {
                    // Evenly spaced across the full duration, including both ends, so the strip's
                    // first/last thumbnails represent the actual start/end of the source.
                    val stepMs = if (frameCount == 1) 0L else durationMs / (frameCount - 1)

                    (0 until frameCount).map { i ->
                        val timestampMs = (i * stepMs).coerceIn(0L, durationMs)
                        try {
                            // FrameExtractor.getFrame uses milliseconds.
                            extractor.getFrame(timestampMs).await().bitmap
                        } catch (e: Exception) {
                            FileLogger.e(
                                TAG,
                                "frame extraction failed at ${timestampMs}ms for $uri",
                                e,
                            )
                            null
                        }
                    }
                } finally {
                    extractor.close()
                }
            }
        } catch (e: Exception) {
            FileLogger.e(TAG, "extractFilmstrip failed for $uri", e)
            emptyList()
        } finally {
            dispatcher.close()
        }
    }
}
