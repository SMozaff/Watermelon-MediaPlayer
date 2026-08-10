package com.watermelon.mediatools.engine

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.ExperimentalFrameExtractor
import com.watermelon.common.util.FileLogger
import kotlinx.coroutines.guava.await

private const val TAG = "FilmstripExtractor"

/**
 * Extracts a row of evenly-spaced decoded thumbnail frames for TrimScreen's filmstrip.
 *
 * CONFIRMED API/package for this project's pinned Media3 version (1.8.0, per
 * gradle/libs.versions.toml): [androidx.media3.transformer.ExperimentalFrameExtractor] --
 * NOT androidx.media3.effect (an incorrect first guess this session, corrected after
 * checking the real 1.6.0 GitHub source path), and NOT the newer
 * androidx.media3.inspector.frame.FrameExtractor (a Context7 query this session kept
 * surfacing only that class, which lives in the separate media3-inspector-frame module
 * that Media3's own 1.9.0/1.10.0/1.11.0 release notes confirm didn't exist until 1.9.0 and
 * only fully replaced ExperimentalFrameExtractor at 1.11.0 -- this project's pinned 1.8.0 is
 * before both changes, confirmed by 1.8.0's own release notes not mentioning either).
 * `ExperimentalFrameExtractor` was introduced in 1.6.0 still under
 * androidx.media3.transformer and stayed there through 1.8.0. Usage per the real source
 * (fetched directly this session, not just the announcement blog):
 * `ExperimentalFrameExtractor(context, configuration)`, `.setMediaItem(mediaItem, effects)`,
 * `.getFrame(positionMs).await()` -- positionMs is milliseconds, confirmed against the
 * method's own javadoc, not the us/ms-ambiguous blog snippet -- returning a Frame with a
 * `.bitmap` field, then `.release()`.
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
     */
    suspend fun extractFilmstrip(
        uri: Uri,
        durationMs: Long,
        frameCount: Int,
        targetShortSidePx: Int = 180,
    ): List<Bitmap?> {
        if (durationMs <= 0 || frameCount <= 0) return emptyList()

        val mediaItem = MediaItem.fromUri(uri)
        val configuration = ExperimentalFrameExtractor.Configuration.Builder().build()
        val extractor = ExperimentalFrameExtractor(context, configuration)

        return try {
            extractor.setMediaItem(mediaItem, listOf(Presentation.createForShortSide(targetShortSidePx)))

            // Evenly spaced across the full duration, including both ends, so the strip's
            // first/last thumbnails represent the actual start/end of the source -- matters
            // for trim specifically since the handles range over [0, durationMs].
            val stepMs = if (frameCount == 1) 0L else durationMs / (frameCount - 1)
            (0 until frameCount).map { i ->
                val timestampMs = (i * stepMs).coerceIn(0L, durationMs)
                try {
                    // getFrame takes positionMs, NOT microseconds -- confirmed directly from
                    // ExperimentalFrameExtractor.java's real source (getFrame(long positionMs)
                    // javadoc), not inferred from the announcement blog's ambiguous variable
                    // naming ("timestamps"), which could have been misread as microseconds.
                    extractor.getFrame(timestampMs).await().bitmap
                } catch (e: Exception) {
                    FileLogger.e(TAG, "frame extraction failed at ${timestampMs}ms for $uri", e)
                    null
                }
            }
        } catch (e: Exception) {
            FileLogger.e(TAG, "extractFilmstrip failed for $uri", e)
            emptyList()
        } finally {
            extractor.release()
        }
    }
}
