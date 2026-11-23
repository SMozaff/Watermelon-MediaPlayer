package com.watermelon.common.subtitle

import com.watermelon.common.model.ParsedSubtitle
import com.watermelon.common.subtitle.sync.ActivitySignature
import com.watermelon.common.subtitle.sync.SubtitleSyncResult
import com.watermelon.common.subtitle.sync.SyncMeasurement
import java.nio.ByteBuffer

/**
 * Native-backed replacements for the Kotlin implementations that were removed in the full
 * cutover. These interfaces live in common-interfaces so `subtitle-engine` and `media-tools`
 * can depend on the Rust adapters supplied by the app composition root without depending on
 * the app module (which owns the generated UniFFI binding).
 */
fun interface SrtParserPort {
    fun parse(content: String, language: String?): ParsedSubtitle
}

/**
 * OpenSubtitles hashing over only the first and last 64 KiB of a media file. Passing whole files
 * through FFI is not acceptable for multi-gigabyte videos; small callers can pass the same byte
 * array as both chunks when the file is exactly 128 KiB.
 */
fun interface SubtitleHasherPort {
    fun hash(fileSize: Long, head: ByteArray, tail: ByteArray): String
}

/** Replaces the former concrete `OffsetConsensus` class in subtitle-engine. */
interface SubtitleOffsetConsensus {
    fun resolve(measurements: List<SyncMeasurement>): SubtitleSyncResult

    fun hasUsefulProvisionalOffset(measurements: List<SyncMeasurement>): Boolean
}

/** One decoded PCM buffer handed to the native speech accumulator. */
data class PcmChunk(
    val bytes: ByteArray,
    val presentationTimeUs: Long,
    val sampleRate: Int,
    val channelCount: Int,
    /** `PCM_16` or `PCM_FLOAT`, matching the frozen golden-vector encoding names. */
    val encoding: String,
)

interface SpeechAccumulator {
    fun consume(
        buffer: ByteBuffer,
        bufferOffset: Int,
        bufferSize: Int,
        presentationTimeUs: Long,
        sampleRate: Int,
        channelCount: Int,
        encoding: String,
    )

    fun finish(): ActivitySignature
}

fun interface SpeechAccumulatorFactory {
    fun create(targetStartUs: Long, durationMs: Long, bucketMs: Int): SpeechAccumulator
}
