package com.watermelon.common.subtitle.sync

import com.watermelon.common.model.ParsedSubtitle

/** Compact timing signal shared by subtitle and audio analysis. Values are normalized 0f..1f. */
data class ActivitySignature(
    val startMs: Long,
    val bucketMs: Int,
    val occupancy: FloatArray,
    val onsets: FloatArray,
) {
    init {
        require(bucketMs > 0) { "bucketMs must be > 0" }
        require(occupancy.size == onsets.size) { "occupancy/onsets size mismatch" }
    }

    val durationMs: Long get() = occupancy.size.toLong() * bucketMs
}

sealed interface SubtitleSyncModel {
    data object Identity : SubtitleSyncModel
    data class Offset(val offsetMs: Long) : SubtitleSyncModel
    data class Affine(val scale: Double, val offsetMs: Long) : SubtitleSyncModel
    data class Piecewise(val anchors: List<SyncAnchor>) : SubtitleSyncModel
}

data class SyncAnchor(val subtitleTimeMs: Long, val mediaTimeMs: Long)

data class SubtitleSyncRequest(
    val mediaId: String,
    val mediaUri: String,
    val mediaFileSize: Long,
    val mediaDurationMs: Long,
    val subtitleFingerprint: String,
    val subtitleLanguage: String?,
    val subtitle: ParsedSubtitle,
    val playbackSessionId: Long,
)

data class SyncMeasurement(
    val subtitleProbeStartMs: Long,
    val audioProbeStartMs: Long,
    val estimatedOffsetMs: Long,
    val correlationScore: Float,
    val peakMargin: Float,
    val speechCoverage: Float,
    val transitionCount: Int,
    val probeQuality: Float,
)

sealed interface SubtitleSyncResult {
    data class Synchronized(
        val model: SubtitleSyncModel,
        val confidence: Float,
        val measurements: List<SyncMeasurement>,
    ) : SubtitleSyncResult

    data class ComplexDriftDetected(
        val measurements: List<SyncMeasurement>,
    ) : SubtitleSyncResult

    data class LowConfidence(
        val measurements: List<SyncMeasurement>,
    ) : SubtitleSyncResult

    data object Unsupported : SubtitleSyncResult
    data object ResourceDenied : SubtitleSyncResult
    data object Cancelled : SubtitleSyncResult
    data class Failed(val reason: String) : SubtitleSyncResult
}

enum class SyncSource { AUTO, MANUAL }

enum class SyncStatus {
    IDLE,
    CHECKING_CACHE,
    ANALYZING,
    SYNCHRONIZED,
    LOW_CONFIDENCE,
    COMPLEX_DRIFT,
    UNSUPPORTED,
    RESOURCE_DENIED,
    FAILED,
}

data class SubtitleSyncProfile(
    val mediaId: String,
    val mediaFileSize: Long,
    val subtitleFingerprint: String,
    val subtitleLanguage: String? = null,
    val autoModel: SubtitleSyncModel? = null,
    val autoConfidence: Float? = null,
    val autoEngineVersion: Int? = null,
    val autoUpdatedAt: Long? = null,
    val manualOffsetMs: Long? = null,
    val manualUpdatedAt: Long? = null,
) {
    fun effectiveModel(): SubtitleSyncModel =
        manualOffsetMs?.let(SubtitleSyncModel::Offset)
            ?: autoModel
            ?: SubtitleSyncModel.Identity

    fun effectiveOffsetMs(): Long = when (val model = effectiveModel()) {
        SubtitleSyncModel.Identity -> 0L
        is SubtitleSyncModel.Offset -> model.offsetMs
        is SubtitleSyncModel.Affine -> model.offsetMs
        is SubtitleSyncModel.Piecewise -> 0L
    }
}

data class ProbeCandidate(
    /** Source-subtitle timeline position. Audio is requested at this time + provisional offset. */
    val subtitleStartMs: Long,
    val durationMs: Long,
    val distinctiveness: Float,
)

data class CorrelationConfig(
    val minOffsetMs: Long,
    val maxOffsetMs: Long,
    val stepMs: Long,
    val onsetWeight: Float = 0.60f,
    val occupancyWeight: Float = 0.35f,
    val silenceWeight: Float = 0.05f,
    val independentPeakDistanceMs: Long = 1_000L,
)

data class CorrelationResult(
    val offsetMs: Long,
    val peakScore: Float,
    val secondPeakScore: Float,
    val peakMargin: Float,
)

interface SubtitleActivityBuilder {
    fun build(
        subtitle: ParsedSubtitle,
        startMs: Long,
        endMs: Long,
        bucketMs: Int,
    ): ActivitySignature
}

interface SubtitleProbeSelector {
    fun select(
        subtitle: ParsedSubtitle,
        mediaDurationMs: Long,
        maxCount: Int,
    ): List<ProbeCandidate>
}

interface ActivityCorrelator {
    fun correlate(
        audio: ActivitySignature,
        subtitleContext: ActivitySignature,
        config: CorrelationConfig,
    ): CorrelationResult
}

sealed interface SpeechProbeResult {
    data class Success(
        val signature: ActivitySignature,
        val actualStartMs: Long,
        val actualDurationMs: Long,
    ) : SpeechProbeResult

    data object Unsupported : SpeechProbeResult
    data object ResourceDenied : SpeechProbeResult
    data object Cancelled : SpeechProbeResult
    data class Failure(val reason: String) : SpeechProbeResult
}

interface SpeechProbeSource {
    suspend fun probe(
        mediaUri: String,
        targetPositionMs: Long,
        durationMs: Long,
        bucketMs: Int,
    ): SpeechProbeResult
}

interface SubtitleSyncRepository {
    suspend fun get(
        mediaId: String,
        mediaFileSize: Long,
        subtitleFingerprint: String,
    ): SubtitleSyncProfile?

    suspend fun saveAuto(
        mediaId: String,
        mediaFileSize: Long,
        subtitleFingerprint: String,
        subtitleLanguage: String?,
        model: SubtitleSyncModel,
        confidence: Float,
        engineVersion: Int,
    )

    suspend fun setManualOffset(
        mediaId: String,
        mediaFileSize: Long,
        subtitleFingerprint: String,
        offsetMs: Long,
    )

    suspend fun clearManualOffset(
        mediaId: String,
        mediaFileSize: Long,
        subtitleFingerprint: String,
    )

    suspend fun clearAutoResult(
        mediaId: String,
        mediaFileSize: Long,
        subtitleFingerprint: String,
    )
}
