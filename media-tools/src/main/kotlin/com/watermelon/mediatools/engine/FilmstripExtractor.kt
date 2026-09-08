package com.watermelon.mediatools.engine

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.inspector.frame.FrameExtractor
import com.watermelon.common.util.FileLogger
import kotlinx.coroutines.guava.await
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "FilmstripExtractor"

/**
 * Extracts a row of evenly-spaced decoded thumbnail frames for TrimScreen's filmstrip.
 *
 * Uses Media3 1.11.0's [FrameExtractor] API which replaced ExperimentalFrameExtractor.
 * The new API requires all FrameExtractor operations (construction, getFrame, close)
 * to be accessed from a single application thread. This implementation uses a dedicated
 * single-thread dispatcher to ensure thread-safe access throughout the extraction session.
 *
 * Frames are decoded, not just metadata reads (unlike [VideoCompressor.detectShortSidePx]'s
 * MediaMetadataRetriever use) -- this is real GPU/CPU decode work per frame, done here
 * off-main-thread by the caller (TrimViewModel), one extractor instance reused across all
 * requested timestamps rather than one per frame.
 *
 * NOT run on-device -- signature/shape confirmed via docs, not verified against a real
 * device or emulator this session.
 */
@UnstableApi
class FilmstripExtractor(private val context: Context) {

    /**
     * Extracts [frameCount] thumbnails evenly spaced across [0, durationMs], downscaled via
     * [Presentation.createForShortSide] (the same confirmed-for-1.8.0 method
     * [VideoCompressor] already uses -- `createForHeight` surfaced only in newer-version
     * docs this session and wasn't confirmed against 1.8.0, so it's deliberately not used
     * here) to reduce memory/decode cost versus full-resolution frames.
     *
     * Returns bitmaps in timestamp order; a null entry means that specific frame failed to
     * extract (e.g. an unreadable timestamp near a corrupt GOP) -- callers should render a
     * placeholder for null entries rather than treating any single failure as fatal to the
     * whole strip.
     *
     * All FrameExtractor operations are executed on a dedicated single-thread dispatcher
     * to satisfy the API requirement that instances must be accessed from a single thread.
     */
    suspend fun extractFilmstrip(
        uri: Uri,
        durationMs: Long,
        frameCount: Int,
        targetShortSidePx: Int = 180,
    ): List<Bitmap?> {
        if (durationMs <= 0 || frameCount <= 0) return emptyList()

        val mediaItem = MediaItem.fromUri(uri)
        
        // Use a dedicated single-thread dispatcher to ensure all FrameExtractor operations
        // happen on the same OS thread, as required by the FrameExtractor API.
        val singleThreadDispatcher = Dispatchers.IO.limitedParallelism(1)
        
        return try {
            withContext(singleThreadDispatcher) {
                val extractor = FrameExtractor.Builder(context, mediaItem)
                    .setEffects(listOf(Presentation.createForShortSide(targetShortSidePx)))
                    .build()

                try {
                    // Evenly spaced across the full duration, including both ends, so the strip's
                    // first/last thumbnails represent the actual start/end of the source -- matters
                    // for trim specifically since the handles range over [0, durationMs].
                    val stepMs = if (frameCount == 1) 0L else durationMs / (frameCount - 1)
                    (0 until frameCount).map { i ->
                        val timestampMs = (i * stepMs).coerceIn(0L, durationMs)
                        try {
                            // getFrame takes positionMs, NOT microseconds -- confirmed directly from
                            // FrameExtractor.java's real source (getFrame(long positionMs)
                            // javadoc), not inferred from the announcement blog's ambiguous variable
                            // naming ("timestamps"), which could have been misread as microseconds.
                            extractor.getFrame(timestampMs).await().bitmap
                        } catch (e: Exception) {
                            FileLogger.e(TAG, "frame extraction failed at ${timestampMs}ms for $uri", e)
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
        }
    }
}
