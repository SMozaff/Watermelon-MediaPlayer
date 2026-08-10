package com.watermelon.mediatools.engine

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.AudioEncoderSettings
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.TransformationRequest
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.watermelon.common.util.FileLogger
import com.watermelon.mediatools.job.MediaJobManager
import com.watermelon.mediatools.job.MediaJobType
import com.watermelon.mediatools.output.OutputFileStore
import com.watermelon.mediatools.output.OutputNaming

private const val TAG = "VideoCompressor"

/**
 * Quick compressor: downscales resolution via [Presentation.createForShortSide] (confirmed
 * current API via docs this session) and re-encodes at a target audio/video bitrate.
 *
 * CONFIRMED via Context7 (Media3 docs, re-checked this session): [Presentation.createForShortSide],
 * [AudioEncoderSettings.Builder.setBitrate] (bits per second), the core
 * [DefaultEncoderFactory.Builder] pattern, [Transformer.Builder.setEncoderFactory], and
 * [DefaultEncoderFactory.Builder.setEnableFallback] (confirmed against
 * BitrateAnalysisTest.java, a real Media3 androidTest source file) — all match this file's
 * usage exactly.
 *
 * REAL BUG FIXED (this session): "compressed" output was landing bigger than the source.
 * Root cause was [DefaultEncoderFactory.Builder]'s enableFallback defaulting to true —
 * when a device's encoder can't exactly honor the requested bitrate, Transformer silently
 * substitutes different settings (sometimes a much higher bitrate) and still reports success
 * via onCompleted, not onFallbackApplied-as-a-failure. Fixed two ways: (1)
 * setEnableFallback(false) below turns an unhonorable request into a hard job failure instead
 * of a silent wrong success; (2) [MediaJobManager.onCompleted] independently rejects any
 * COMPRESS job whose real output size isn't smaller than the source, as a second guard against
 * any other path to oversized output (e.g. mux overhead on an already-minimal source).
 *
 * NOT run on-device.
 */
@UnstableApi
class VideoCompressor(private val context: Context, private val outputFileStore: OutputFileStore) {

    /**
     * Four tiers per product spec, distinct in *how* they shrink the file, not just how
     * much:
     * - HIGH_QUALITY: resolution downscale only (1080p -> 720p), bitrate stays high/near-
     *   original -- "smaller size with the same quality bitrate."
     * - MEDIUM: moderate downscale (720p) + moderate bitrate cut -- balanced size/quality.
     * - SMALL: more aggressive downscale (480p) + lower bitrate -- noticeably lower quality
     *   for a meaningfully smaller file.
     * - TINY: WhatsApp-style aggressive compression (480p, very low bitrate) -- prioritizes
     *   file size above all, still watchable but visibly compressed.
     *
     * Bitrate ranges are grounded in general H.264 VOD guidance (e.g. ~4-6 Mbps for good-
     * quality 720p, ~1-2 Mbps for acceptable 480p — checked via web search this session,
     * not an authoritative spec), not measured/tuned against real footage. Treat these as a
     * reasonable starting point, not final numbers -- adjust after seeing real output sizes
     * and quality on actual videos.
     */
    enum class Preset(
        val label: String,
        val targetShortSidePx: Int,
        val videoBitrateBps: Int,
        val audioBitrateBps: Int,
    ) {
        HIGH_QUALITY("High Quality", 720, 6_000_000, 192_000),
        MEDIUM("Medium", 720, 2_500_000, 128_000),
        SMALL("Small", 480, 1_000_000, 96_000),
        TINY("Tiny", 480, 400_000, 64_000),
    }

    fun compress(
        jobManager: MediaJobManager,
        inputUri: Uri,
        preset: Preset,
        originalDisplayName: String,
    ): String {
        val outputName = OutputNaming.compressedName(originalDisplayName)
        val outputPath = outputFileStore.stagingPathFor(MediaJobType.COMPRESS, outputName)

        // Second, independent guard against oversized output (belt-and-suspenders alongside
        // setEnableFallback(false) below): even with fallback disabled, container/mux
        // overhead could in theory still land the output slightly above source size on a
        // source that was already near-minimal bitrate. Source size is captured here, before
        // the job starts, and compared against the real output size in
        // MediaJobManager.onCompleted via exportResult.fileSizeBytes -- see that method.
        val sourceSizeBytes = queryFileSizeBytes(inputUri)

        val mediaItem = MediaItem.fromUri(inputUri)

        // Never upscale: Presentation.createForShortSide's own release note says it
        // "guarantees the shortest side always matches" the target -- read literally, that
        // implies it would upscale a source already smaller than the target (e.g. asking
        // for 720p on a 480p source), which would make the file BIGGER while looking no
        // better, directly contradicting "High Quality = smaller size, same quality."
        // Couldn't get a definitive answer from Context7 on this either way, so rather than
        // risk it: detect the source's actual short side via MediaMetadataRetriever, and
        // only apply the resolution effect if the source is genuinely larger than the
        // preset's target. If the source is already <= target, skip the resolution effect
        // entirely (bitrate/audio settings still apply as normal) -- per product decision.
        val sourceShortSide = detectShortSidePx(inputUri)
        val videoEffects: List<Effect> = when {
            sourceShortSide != null && sourceShortSide <= preset.targetShortSidePx -> {
                FileLogger.i(TAG, "source shortSide=$sourceShortSide <= preset target=${preset.targetShortSidePx}, skipping resolution effect")
                emptyList()
            }
            sourceShortSide == null -> {
                // Metadata read failed -- can't confirm source is smaller, so this could
                // still upscale in the rare case it actually is. Chose to apply the effect
                // anyway (fail-open) rather than silently skip compression's resolution
                // step entirely on every metadata-read failure; if this turns out wrong in
                // practice, flipping to fail-safe (skip the effect when unknown) is a
                // one-line change here.
                FileLogger.e(TAG, "source resolution unknown, applying resolution effect anyway (may upscale in rare cases)")
                listOf(Presentation.createForShortSide(preset.targetShortSidePx))
            }
            else -> listOf(Presentation.createForShortSide(preset.targetShortSidePx))
        }

        val editedMediaItem = EditedMediaItem.Builder(mediaItem)
            .setEffects(Effects(/* audioProcessors= */ emptyList(), videoEffects))
            .build()

        // REAL BUG FIX: DefaultEncoderFactory.Builder defaults enableFallback=true. When a
        // device's encoder can't honor the requested bitrate/resolution exactly, Transformer
        // silently falls back to different (often higher-bitrate) settings and still reports
        // success -- this is the confirmed root cause of "compressed" output landing bigger
        // than the source. setEnableFallback(false) (confirmed real Builder method via
        // Context7/BitrateAnalysisTest.java this session) makes that case a hard job failure
        // instead of a silent, wrong success. Per product decision: never allow oversized
        // output to reach the user, even if that means the job fails on some devices.
        val encoderFactory = DefaultEncoderFactory.Builder(context)
            .setRequestedVideoEncoderSettings(
                VideoEncoderSettings.Builder()
                    .setBitrate(preset.videoBitrateBps)
                    .build()
            )
            .setRequestedAudioEncoderSettings(
                AudioEncoderSettings.Builder()
                    .setBitrate(preset.audioBitrateBps)
                    .build()
            )
            .setEnableFallback(false)
            .build()

        lateinit var jobId: String
        val transformer = Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .setEncoderFactory(encoderFactory)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    jobManager.onCompleted(jobId, exportResult)
                }
                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException,
                ) {
                    jobManager.onError(jobId, exportException)
                }
                override fun onFallbackApplied(
                    composition: Composition,
                    originalRequest: TransformationRequest,
                    fallbackRequest: TransformationRequest,
                ) {
                    jobManager.onFallbackApplied(jobId, originalRequest, fallbackRequest)
                }
            })
            .build()

        jobId = jobManager.register(
            MediaJobType.COMPRESS,
            inputUri.toString(),
            outputPath,
            transformer,
            sourceSizeBytes = sourceSizeBytes,
        )
        transformer.start(editedMediaItem, outputPath)
        return jobId
    }

    /**
     * Queries the source content's size in bytes via ContentResolver, the standard way to
     * read a content:// Uri's size (OpenableColumns.SIZE is documented platform API -- same
     * general "query the resolver, don't assume File I/O works on a content Uri" approach
     * this file already takes with MediaMetadataRetriever.setDataSource(context, uri) below).
     * Returns null if the size can't be determined (cursor empty/column missing/query
     * throws) -- callers must treat null as "can't verify," not "size is zero."
     */
    private fun queryFileSizeBytes(uri: Uri): Long? {
        return try {
            context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                if (sizeIndex == -1 || !cursor.moveToFirst()) {
                    null
                } else {
                    cursor.getLong(sizeIndex).takeIf { it > 0 }
                }
            }
        } catch (e: Exception) {
            FileLogger.e(TAG, "queryFileSizeBytes failed for $uri", e)
            null
        }
    }

    /**
     * Returns the source video's shorter dimension in pixels, or null if it can't be
     * determined (corrupt/unreadable file, or extractMetadata returning null -- a real,
     * documented possibility for some files/codecs, not just a theoretical edge case).
     *
     * Uses MediaMetadataRetriever (confirmed real Android SDK API via web search this
     * session: setDataSource(context, uri) + extractMetadata(METADATA_KEY_VIDEO_WIDTH/HEIGHT)),
     * not Media3 -- this is plain platform metadata reading, done once before starting the
     * actual Transformer job.
     *
     * Note: some devices/videos report width/height pre-rotation (i.e. swapped from what
     * you'd see on screen for a portrait video) -- METADATA_KEY_VIDEO_ROTATION exists to
     * correct for this but isn't checked here, since we only need the SHORTER side for this
     * comparison and width/height being swapped doesn't change which one is shorter.
     */
    private fun detectShortSidePx(uri: Uri): Int? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
            if (width == null || height == null) {
                FileLogger.e(TAG, "could not read video width/height for $uri, extractMetadata returned null")
                null
            } else {
                minOf(width, height)
            }
        } catch (e: Exception) {
            FileLogger.e(TAG, "detectShortSidePx failed for $uri", e)
            null
        } finally {
            retriever.release()
        }
    }
}
