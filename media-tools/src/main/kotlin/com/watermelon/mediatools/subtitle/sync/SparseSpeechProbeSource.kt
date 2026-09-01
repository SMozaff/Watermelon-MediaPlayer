package com.watermelon.mediatools.subtitle.sync

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.SystemClock
import com.watermelon.common.subtitle.sync.ActivitySignature
import com.watermelon.common.subtitle.sync.SpeechProbeResult
import com.watermelon.common.subtitle.sync.SpeechProbeSource
import com.watermelon.common.util.FileLogger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/**
 * Decodes only a bounded audio window from the video and immediately reduces it to a compact
 * speech-likelihood signal. PCM is never written to disk and the whole movie is never decoded.
 */
class SparseSpeechProbeSource(
    private val context: Context,
    private val dispatcher: CoroutineDispatcher = DECODER_DISPATCHER,
) : SpeechProbeSource {

    override suspend fun probe(
        mediaUri: String,
        targetPositionMs: Long,
        durationMs: Long,
        bucketMs: Int,
    ): SpeechProbeResult = DECODER_GATE.withPermit {
        withContext(dispatcher) {
            coroutineContext.ensureActive()
            decode(mediaUri, targetPositionMs, durationMs, bucketMs)
        }
    }

    private suspend fun decode(
        mediaUri: String,
        targetPositionMs: Long,
        durationMs: Long,
        bucketMs: Int,
    ): SpeechProbeResult {
        if (durationMs <= 0L || bucketMs <= 0) return SpeechProbeResult.Failure("invalid probe duration")

        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        val startedAt = SystemClock.elapsedRealtime()
        try {
            setDataSource(extractor, mediaUri)
            val track = findAudioTrack(extractor) ?: return SpeechProbeResult.Unsupported
            extractor.selectTrack(track.first)
            val inputFormat = track.second
            val mime = inputFormat.getString(MediaFormat.KEY_MIME) ?: return SpeechProbeResult.Unsupported
            var sampleRate = inputFormat.intOrNull(MediaFormat.KEY_SAMPLE_RATE) ?: return SpeechProbeResult.Unsupported
            var channels = inputFormat.intOrNull(MediaFormat.KEY_CHANNEL_COUNT) ?: return SpeechProbeResult.Unsupported
            var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT

            val targetStartUs = targetPositionMs.coerceAtLeast(0L) * 1_000L
            val targetEndUs = targetStartUs + durationMs * 1_000L
            val seekStartUs = (targetStartUs - SEEK_PREROLL_US).coerceAtLeast(0L)
            extractor.seekTo(seekStartUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

            val accumulator = SpeechLikelihoodAccumulator(
                targetStartUs = targetStartUs,
                durationMs = durationMs,
                bucketMs = bucketMs,
            )

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(inputFormat, null, null, 0)
            codec.start()

            val info = MediaCodec.BufferInfo()
            var inputEos = false
            var outputEos = false
            var fedPastTarget = false

            while (!outputEos) {
                coroutineContext.ensureActive()
                if (SystemClock.elapsedRealtime() - startedAt > MAX_WALL_TIME_MS) {
                    FileLogger.i(TAG, "probe exceeded wall budget at ${targetPositionMs}ms")
                    return SpeechProbeResult.ResourceDenied
                }

                if (!inputEos) {
                    val inputIndex = codec.dequeueInputBuffer(CODEC_TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputIndex)
                            ?: return SpeechProbeResult.Failure("decoder input buffer unavailable")
                        val sampleTimeUs = extractor.sampleTime
                        if (sampleTimeUs < 0L || fedPastTarget) {
                            codec.queueInputBuffer(
                                inputIndex, 0, 0,
                                if (sampleTimeUs >= 0L) sampleTimeUs else targetEndUs,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                            )
                            inputEos = true
                        } else {
                            inputBuffer.clear()
                            val size = extractor.readSampleData(inputBuffer, 0)
                            if (size < 0) {
                                codec.queueInputBuffer(
                                    inputIndex, 0, 0, sampleTimeUs.coerceAtLeast(targetEndUs),
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                                )
                                inputEos = true
                            } else {
                                codec.queueInputBuffer(inputIndex, 0, size, sampleTimeUs, 0)
                                extractor.advance()
                                val nextSample = extractor.sampleTime
                                fedPastTarget = nextSample < 0L || nextSample > targetEndUs + INPUT_TAIL_US
                            }
                        }
                    }
                }

                when (val outputIndex = codec.dequeueOutputBuffer(info, CODEC_TIMEOUT_US)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val output = codec.outputFormat
                        sampleRate = output.intOrNull(MediaFormat.KEY_SAMPLE_RATE) ?: sampleRate
                        channels = output.intOrNull(MediaFormat.KEY_CHANNEL_COUNT) ?: channels
                        pcmEncoding = output.intOrNull(MediaFormat.KEY_PCM_ENCODING)
                            ?: AudioFormat.ENCODING_PCM_16BIT
                        if (pcmEncoding != AudioFormat.ENCODING_PCM_16BIT &&
                            pcmEncoding != AudioFormat.ENCODING_PCM_FLOAT
                        ) {
                            FileLogger.i(TAG, "unsupported PCM encoding=$pcmEncoding")
                            return SpeechProbeResult.Unsupported
                        }
                    }
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    else -> if (outputIndex >= 0) {
                        try {
                            if (info.size > 0) {
                                val outputBuffer = codec.getOutputBuffer(outputIndex)
                                    ?: return SpeechProbeResult.Failure("decoder output buffer unavailable")
                                accumulator.consume(
                                    buffer = outputBuffer,
                                    bufferOffset = info.offset,
                                    bufferSize = info.size,
                                    presentationTimeUs = info.presentationTimeUs,
                                    sampleRate = sampleRate,
                                    channelCount = channels,
                                    encoding = if (pcmEncoding == AudioFormat.ENCODING_PCM_FLOAT) {
                                        PcmSampleEncoding.PCM_FLOAT
                                    } else {
                                        PcmSampleEncoding.PCM_16
                                    },
                                )
                            }
                        } finally {
                            codec.releaseOutputBuffer(outputIndex, false)
                        }
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            outputEos = true
                        }
                    }
                }
            }

            val signature = accumulator.finish()
            if (signature.occupancy.none { it > 0f } && accumulator.decodedSampleCount == 0L) {
                return SpeechProbeResult.Failure("no PCM decoded for requested probe")
            }
            return SpeechProbeResult.Success(
                signature = signature,
                actualStartMs = targetPositionMs.coerceAtLeast(0L),
                actualDurationMs = durationMs,
            )
        } catch (e: java.io.IOException) {
            return SpeechProbeResult.Failure(e.message ?: "audio probe I/O failure")
        } catch (e: MediaCodec.CodecException) {
            return SpeechProbeResult.Failure(e.diagnosticInfo ?: e.message ?: "audio decoder failure")
        } catch (e: IllegalArgumentException) {
            return SpeechProbeResult.Unsupported
        } catch (e: IllegalStateException) {
            return SpeechProbeResult.Failure(e.message ?: "audio probe state failure")
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    private fun setDataSource(extractor: MediaExtractor, value: String) {
        val uri = Uri.parse(value)
        when (uri.scheme) {
            "content", "android.resource" -> extractor.setDataSource(context, uri, null)
            "file" -> extractor.setDataSource(requireNotNull(uri.path))
            else -> extractor.setDataSource(value)
        }
    }

    private fun findAudioTrack(extractor: MediaExtractor): Pair<Int, MediaFormat>? {
        var firstAudio: Pair<Int, MediaFormat>? = null
        for (index in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(index)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
            if (!mime.startsWith("audio/")) continue
            if (firstAudio == null) firstAudio = index to format
            val isDefault = format.intOrNull(MediaFormat.KEY_IS_DEFAULT) == 1
            if (isDefault) return index to format
        }
        return firstAudio
    }

    private fun MediaFormat.intOrNull(key: String): Int? =
        if (containsKey(key)) runCatching { getInteger(key) }.getOrNull() else null

    companion object {
        private const val TAG = "SubtitleSpeechProbe"
        private const val CODEC_TIMEOUT_US = 10_000L
        private const val SEEK_PREROLL_US = 1_000_000L
        private const val INPUT_TAIL_US = 300_000L
        private const val MAX_WALL_TIME_MS = 12_000L

        private val DECODER_GATE = Semaphore(1)
        private val DECODER_DISPATCHER = Dispatchers.IO.limitedParallelism(1)
    }
}
