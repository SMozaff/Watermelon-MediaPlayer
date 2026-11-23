package com.watermelon.app.migration

import com.watermelon.common.model.ParsedSubtitle
import com.watermelon.common.model.SubtitleCue
import com.watermelon.common.subtitle.SrtParserPort
import com.watermelon.common.subtitle.SubtitleHasherPort
import com.watermelon.common.subtitle.SubtitleOffsetConsensus
import com.watermelon.common.subtitle.sync.ActivityCorrelator
import com.watermelon.common.subtitle.sync.ActivitySignature
import com.watermelon.common.subtitle.sync.CorrelationConfig
import com.watermelon.common.subtitle.sync.CorrelationResult
import com.watermelon.common.subtitle.sync.ProbeCandidate
import com.watermelon.common.subtitle.sync.SubtitleActivityBuilder
import com.watermelon.common.subtitle.sync.SubtitleProbeSelector
import com.watermelon.common.subtitle.sync.SubtitleSyncModel
import com.watermelon.common.subtitle.sync.SubtitleSyncResult
import com.watermelon.common.subtitle.sync.SyncAnchor
import com.watermelon.common.subtitle.sync.SyncMeasurement
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.ByteBuffer
import uniffi.watermelon_ffi.activityBuildJson
import uniffi.watermelon_ffi.consensusHasUsefulProvisionalOffsetJson
import uniffi.watermelon_ffi.consensusResolveJson
import uniffi.watermelon_ffi.correlateJson
import uniffi.watermelon_ffi.openSubtitlesHashChunks
import uniffi.watermelon_ffi.probeSelectJson
import uniffi.watermelon_ffi.speechAccumulatorJson
import uniffi.watermelon_ffi.srtParseJson
import uniffi.watermelon_ffi.uniffiEnsureInitialized

/**
 * Thin adapters from the app's domain interfaces to the parity-tested Rust core.
 *
 * There is deliberately no Kotlin fallback here: a native failure propagates to the caller,
 * which the Auto Sync engine already turns into a `Failed` result. Rollback is a release/Git
 * decision (owner decision D-8), not a silent runtime switch.
 */
internal object RustAdapters {
    val json = Json { ignoreUnknownKeys = true }

    fun ensureLoaded() = uniffiEnsureInitialized()

    /** FFI error envelopes use this shape; parity responses never carry `error`. */
    fun rejectError(raw: String) {
        if (raw.startsWith("{")) {
            val envelope = runCatching { json.decodeFromString<WireError>(raw) }.getOrNull()
            if (envelope?.error != null) error("Rust core: ${envelope.error}")
        }
    }
}

@Serializable private data class WireError(val error: String? = null)

@Serializable
private data class WireCue(
    val index: Int,
    val startMs: Long,
    val endMs: Long,
    val rawText: String,
    val baseRtl: Boolean,
    val displayText: String,
)

@Serializable
private data class WireParsed(
    val language: String? = null,
    val sourceId: String = "local",
    val offsetMs: Long = 0L,
    val cues: List<WireCue> = emptyList(),
)

/** The oracle encodes every derived float as a decimal plus raw IEEE-754 bits (D-3). */
@Serializable
private data class WireFloat(val decimal: String, val bits: String) {
    fun value(): Float = decimal.toFloatOrNull()
        ?: java.lang.Float.intBitsToFloat(bits.toLong(16).toInt())
}

@Serializable
private data class WireAnchor(val subtitleTimeMs: Long, val mediaTimeMs: Long)

@Serializable
private data class WireModel(
    val kind: String,
    val offsetMs: Long = 0L,
    val scale: WireFloat? = null,
    val anchors: List<WireAnchor> = emptyList(),
) {
    fun toDomain(): SubtitleSyncModel = when (kind) {
        "Identity" -> SubtitleSyncModel.Identity
        "Offset" -> SubtitleSyncModel.Offset(offsetMs)
        "Affine" -> SubtitleSyncModel.Affine(scale?.decimal?.toDoubleOrNull() ?: 1.0, offsetMs)
        "Piecewise" -> SubtitleSyncModel.Piecewise(anchors.map { SyncAnchor(it.subtitleTimeMs, it.mediaTimeMs) })
        else -> error("unknown Rust sync model: $kind")
    }
}

@Serializable
private data class WireMeasurement(
    val subtitleProbeStartMs: Long,
    val audioProbeStartMs: Long,
    val estimatedOffsetMs: Long,
    val correlationScore: WireFloat,
    val peakMargin: WireFloat,
    val speechCoverage: WireFloat,
    val transitionCount: Int,
    val probeQuality: WireFloat,
) {
    fun toDomain() = SyncMeasurement(
        subtitleProbeStartMs = subtitleProbeStartMs,
        audioProbeStartMs = audioProbeStartMs,
        estimatedOffsetMs = estimatedOffsetMs,
        correlationScore = correlationScore.value(),
        peakMargin = peakMargin.value(),
        speechCoverage = speechCoverage.value(),
        transitionCount = transitionCount,
        probeQuality = probeQuality.value(),
    )
}

@Serializable
private data class WireConsensusResult(
    val kind: String,
    val model: WireModel? = null,
    val confidence: WireFloat? = null,
    val measurements: List<WireMeasurement> = emptyList(),
    val reason: String? = null,
) {
    fun toDomain(): SubtitleSyncResult = when (kind) {
        "Synchronized" -> SubtitleSyncResult.Synchronized(
            model = requireNotNull(model) { "Rust Synchronized result has no model" }.toDomain(),
            confidence = requireNotNull(confidence) { "Rust Synchronized result has no confidence" }.value(),
            measurements = measurements.map { it.toDomain() },
        )
        "ComplexDriftDetected" -> SubtitleSyncResult.ComplexDriftDetected(measurements.map { it.toDomain() })
        "LowConfidence" -> SubtitleSyncResult.LowConfidence(measurements.map { it.toDomain() })
        else -> SubtitleSyncResult.Failed(reason ?: "unknown Rust consensus result: $kind")
    }
}

@Serializable
private data class WireSignature(
    val startMs: Long,
    val bucketMs: Int,
    val occupancy: List<Float>,
    val onsets: List<Float>,
) {
    fun toDomain() = ActivitySignature(startMs, bucketMs, occupancy.toFloatArray(), onsets.toFloatArray())
}

@Serializable
private data class WireCorrelation(
    val offsetMs: Long,
    val peakScore: Float,
    val secondPeakScore: Float,
    val peakMargin: Float,
) {
    fun toDomain() = CorrelationResult(offsetMs, peakScore, secondPeakScore, peakMargin)
}

@Serializable
private data class WireCandidate(
    val subtitleStartMs: Long,
    val durationMs: Long,
    val distinctiveness: Float,
) {
    fun toDomain() = ProbeCandidate(subtitleStartMs, durationMs, distinctiveness)
}

private fun f32(value: Float) = WireFloat(
    decimal = value.toString(),
    bits = java.lang.Integer.toHexString(java.lang.Float.floatToRawIntBits(value)).padStart(8, '0'),
)

private fun f64(value: Double) = WireFloat(
    decimal = value.toString(),
    bits = java.lang.Long.toHexString(java.lang.Double.doubleToRawLongBits(value)).padStart(16, '0'),
)

private fun cueJson(cue: SubtitleCue) = WireCue(
    cue.index, cue.startMs, cue.endMs, cue.rawText, cue.baseRtl, cue.displayText,
)

private fun subtitleJson(subtitle: ParsedSubtitle) = WireParsed(
    language = subtitle.language,
    sourceId = subtitle.sourceId,
    offsetMs = subtitle.offsetMs,
    cues = subtitle.cues.map(::cueJson),
)

private fun signatureJson(signature: ActivitySignature) = WireSignature(
    signature.startMs,
    signature.bucketMs,
    signature.occupancy.toList(),
    signature.onsets.toList(),
)

private fun configJson(config: CorrelationConfig) = kotlinx.serialization.json.buildJsonObject {
    put("minOffsetMs", kotlinx.serialization.json.JsonPrimitive(config.minOffsetMs))
    put("maxOffsetMs", kotlinx.serialization.json.JsonPrimitive(config.maxOffsetMs))
    put("stepMs", kotlinx.serialization.json.JsonPrimitive(config.stepMs))
    put("onsetWeight", RustAdapters.json.encodeToJsonElement(f32(config.onsetWeight)))
    put("occupancyWeight", RustAdapters.json.encodeToJsonElement(f32(config.occupancyWeight)))
    put("silenceWeight", RustAdapters.json.encodeToJsonElement(f32(config.silenceWeight)))
    put(
        "independentPeakDistanceMs",
        kotlinx.serialization.json.JsonPrimitive(config.independentPeakDistanceMs),
    )
}

private fun measurementJson(value: SyncMeasurement) = kotlinx.serialization.json.buildJsonObject {
    put("subtitleProbeStartMs", kotlinx.serialization.json.JsonPrimitive(value.subtitleProbeStartMs))
    put("audioProbeStartMs", kotlinx.serialization.json.JsonPrimitive(value.audioProbeStartMs))
    put("estimatedOffsetMs", kotlinx.serialization.json.JsonPrimitive(value.estimatedOffsetMs))
    put("correlationScore", RustAdapters.json.encodeToJsonElement(f32(value.correlationScore)))
    put("peakMargin", RustAdapters.json.encodeToJsonElement(f32(value.peakMargin)))
    put("speechCoverage", RustAdapters.json.encodeToJsonElement(f32(value.speechCoverage)))
    put("transitionCount", kotlinx.serialization.json.JsonPrimitive(value.transitionCount))
    put("probeQuality", RustAdapters.json.encodeToJsonElement(f32(value.probeQuality)))
}

internal object RustSrtParser : SrtParserPort {
    override fun parse(content: String, language: String?): ParsedSubtitle {
        RustAdapters.ensureLoaded()
        val raw = srtParseJson(content, language)
        RustAdapters.rejectError(raw)
        val decoded = RustAdapters.json.decodeFromString<WireParsed>(raw)
        return ParsedSubtitle(
            cues = decoded.cues.map {
                SubtitleCue(it.index, it.startMs, it.endMs, it.rawText, it.baseRtl, it.displayText)
            },
            language = decoded.language,
            sourceId = decoded.sourceId,
            offsetMs = decoded.offsetMs,
        )
    }
}

internal object RustSubtitleHasher : SubtitleHasherPort {
    override fun hash(fileSize: Long, head: ByteArray, tail: ByteArray): String {
        RustAdapters.ensureLoaded()
        val raw = openSubtitlesHashChunks(fileSize.toULong(), head, tail)
        RustAdapters.rejectError(raw)
        return raw
    }
}

internal object RustOffsetConsensus : SubtitleOffsetConsensus {
    override fun resolve(measurements: List<SyncMeasurement>): SubtitleSyncResult {
        RustAdapters.ensureLoaded()
        val payload = RustAdapters.json.encodeToString(
            kotlinx.serialization.serializer<List<kotlinx.serialization.json.JsonObject>>(),
            measurements.map(::measurementJson),
        )
        val raw = consensusResolveJson(payload)
        RustAdapters.rejectError(raw)
        return RustAdapters.json.decodeFromString<WireConsensusResult>(raw).toDomain()
    }

    override fun hasUsefulProvisionalOffset(measurements: List<SyncMeasurement>): Boolean {
        RustAdapters.ensureLoaded()
        val payload = RustAdapters.json.encodeToString(
            kotlinx.serialization.serializer<List<kotlinx.serialization.json.JsonObject>>(),
            measurements.map(::measurementJson),
        )
        return consensusHasUsefulProvisionalOffsetJson(payload)
    }
}

internal object RustProbeSelector : SubtitleProbeSelector {
    override fun select(
        subtitle: ParsedSubtitle,
        mediaDurationMs: Long,
        maxCount: Int,
    ): List<ProbeCandidate> {
        RustAdapters.ensureLoaded()
        val payload = RustAdapters.json.encodeToString(subtitleJson(subtitle))
        val raw = probeSelectJson(payload, mediaDurationMs, maxCount.toUInt())
        RustAdapters.rejectError(raw)
        return RustAdapters.json.decodeFromString<List<WireCandidate>>(raw).map { it.toDomain() }
    }
}

internal object RustActivityBuilder : SubtitleActivityBuilder {
    override fun build(
        subtitle: ParsedSubtitle,
        startMs: Long,
        endMs: Long,
        bucketMs: Int,
    ): ActivitySignature {
        RustAdapters.ensureLoaded()
        val payload = RustAdapters.json.encodeToString(subtitleJson(subtitle))
        val raw = activityBuildJson(payload, startMs, endMs, bucketMs)
        RustAdapters.rejectError(raw)
        return RustAdapters.json.decodeFromString<WireSignature>(raw).toDomain()
    }
}

internal object RustCorrelator : ActivityCorrelator {
    override fun correlate(
        audio: ActivitySignature,
        subtitleContext: ActivitySignature,
        config: CorrelationConfig,
    ): CorrelationResult {
        RustAdapters.ensureLoaded()
        val raw = correlateJson(
            RustAdapters.json.encodeToString(signatureJson(audio)),
            RustAdapters.json.encodeToString(signatureJson(subtitleContext)),
            configJson(config).toString(),
        )
        RustAdapters.rejectError(raw)
        return RustAdapters.json.decodeFromString<WireCorrelation>(raw).toDomain()
    }
}

internal object RustSpeechAccumulatorFactory :
    com.watermelon.common.subtitle.SpeechAccumulatorFactory {

    override fun create(targetStartUs: Long, durationMs: Long, bucketMs: Int) =
        RustSpeechAccumulator(targetStartUs, durationMs, bucketMs)
}

private class RustSpeechAccumulator(
    private val targetStartUs: Long,
    private val durationMs: Long,
    private val bucketMs: Int,
) : com.watermelon.common.subtitle.SpeechAccumulator {
    private val chunks = mutableListOf<uniffi.watermelon_ffi.PcmChunk>()

    override fun consume(
        buffer: ByteBuffer,
        bufferOffset: Int,
        bufferSize: Int,
        presentationTimeUs: Long,
        sampleRate: Int,
        channelCount: Int,
        encoding: String,
    ) {
        require(bufferSize >= 0 && bufferOffset >= 0 && bufferOffset + bufferSize <= buffer.limit()) {
            "invalid PCM buffer window"
        }
        val bytes = ByteArray(bufferSize)
        val view = buffer.duplicate()
        view.position(bufferOffset)
        view.limit(bufferOffset + bufferSize)
        view.get(bytes)
        chunks += uniffi.watermelon_ffi.PcmChunk(
            bytes, presentationTimeUs, sampleRate, channelCount, encoding,
        )
    }

    override fun finish(): ActivitySignature {
        RustAdapters.ensureLoaded()
        val raw = speechAccumulatorJson(targetStartUs, durationMs, bucketMs, chunks)
        RustAdapters.rejectError(raw)
        val decoded = RustAdapters.json.decodeFromString<WireSignature>(raw)
        return ActivitySignature(decoded.startMs, decoded.bucketMs, decoded.occupancy.toFloatArray(), decoded.onsets.toFloatArray())
    }
}
