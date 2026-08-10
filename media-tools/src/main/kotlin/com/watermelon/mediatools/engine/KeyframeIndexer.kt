package com.watermelon.mediatools.engine

import android.content.Context
import android.media.MediaExtractor
import android.net.Uri
import com.watermelon.common.util.FileLogger

private const val TAG = "KeyframeIndexer"

/**
 * Reads every video-track keyframe (sync sample) timestamp from a source file, for
 * TrimScreen's handle-snapping UX: since [VideoTrimmer] always snaps the actual cut to the
 * nearest preceding keyframe (setStartsAtKeyFrame(true) — a hard product requirement), the
 * UI should show the user where those snap points actually are while dragging, not let them
 * drag freely and only discover the snap after the fact.
 *
 * Confirmed real platform API (web search this session, cross-referenced across Android SDK
 * docs and independent real-world usage writeups, since Context7 has no MediaExtractor
 * coverage per prior session notes): [MediaExtractor.SAMPLE_FLAG_SYNC] marks a sample as a
 * sync/keyframe sample; walking the track with
 * `seekTo(sampleTime + 1, MediaExtractor.SEEK_TO_NEXT_SYNC)` from each found keyframe is the
 * standard way to collect every one without decoding actual frame data. This is plain
 * platform I/O, not Media3 -- same tier as [VideoCompressor.detectShortSidePx]'s use of
 * MediaMetadataRetriever elsewhere in this module.
 *
 * NOT run on-device.
 */
class KeyframeIndexer(private val context: Context) {

    /**
     * Returns all keyframe timestamps in milliseconds, ascending, or an empty list if the
     * file can't be read or has no video track. Should be called off the main thread --
     * this does real file I/O and, for long videos, walks every sync sample in the track.
     */
    fun findKeyframeTimestampsMs(uri: Uri): List<Long> {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(context, uri, null)
            val videoTrackIndex = (0 until extractor.trackCount).firstOrNull { i ->
                extractor.getTrackFormat(i).getString(android.media.MediaFormat.KEY_MIME)
                    ?.startsWith("video/") == true
            } ?: run {
                FileLogger.e(TAG, "no video track found in $uri")
                return emptyList()
            }
            extractor.selectTrack(videoTrackIndex)

            val timestampsUs = mutableListOf<Long>()
            // Seed at t=0 with CLOSEST_SYNC to land on the first keyframe, then walk forward
            // via NEXT_SYNC. seekTo returning without moving (or landing before the previous
            // position) signals end-of-track for this walk pattern -- guarded explicitly
            // below rather than relying on an unbounded loop.
            extractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            var lastSampleTimeUs = -1L
            while (true) {
                val sampleTimeUs = extractor.sampleTime
                if (sampleTimeUs < 0 || sampleTimeUs == lastSampleTimeUs) break
                val isSync = (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0
                if (isSync) timestampsUs.add(sampleTimeUs)
                lastSampleTimeUs = sampleTimeUs
                extractor.seekTo(sampleTimeUs + 1, MediaExtractor.SEEK_TO_NEXT_SYNC)
            }
            timestampsUs.map { it / 1000 }
        } catch (e: Exception) {
            FileLogger.e(TAG, "findKeyframeTimestampsMs failed for $uri", e)
            emptyList()
        } finally {
            extractor.release()
        }
    }
}
