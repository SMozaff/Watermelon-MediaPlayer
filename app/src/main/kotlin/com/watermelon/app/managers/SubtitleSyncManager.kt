package com.watermelon.app.managers

import android.content.Context
import com.watermelon.common.model.MediaItem
import com.watermelon.common.model.ParsedSubtitle
import com.watermelon.common.subtitle.sync.SyncStatus
import com.watermelon.common.subtitle.sync.SubtitleSyncCoordinator
import com.watermelon.common.subtitle.sync.SubtitleSyncRequest
import com.watermelon.common.subtitle.sync.SubtitleSyncResult
import com.watermelon.mediatools.subtitle.sync.SparseSpeechProbeSource
import com.watermelon.storage.repository.SubtitleSyncRepositoryImpl
import com.watermelon.subtitle.hash.OpenSubtitlesHasher
import com.watermelon.subtitle.sync.ActivityCorrelatorImpl
import com.watermelon.subtitle.sync.OffsetConsensus
import com.watermelon.subtitle.sync.SubtitleActivityBuilderImpl
import com.watermelon.subtitle.sync.SubtitleFingerprintProvider
import com.watermelon.subtitle.sync.SubtitleProbeSelectorImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Handles subtitle auto-sync and manual nudge operations.
 */
class SubtitleSyncManager(
    private val context: Context,
    private val scope: CoroutineScope,
) {

    private val subtitleRepository = com.watermelon.subtitle.repository.SubtitleRepositoryImpl(context)
    val subtitleFingerprintProvider = SubtitleFingerprintProvider()
    private val subtitleSyncRepository = SubtitleSyncRepositoryImpl(
        com.watermelon.storage.db.WatermelonDatabase(context)
    )

    private val subtitleSyncCoordinator = SubtitleSyncCoordinator(
        repository = subtitleSyncRepository,
        probeSelector = SubtitleProbeSelectorImpl(),
        subtitleActivityBuilder = SubtitleActivityBuilderImpl(),
        speechProbeSource = SparseSpeechProbeSource(context),
        correlator = ActivityCorrelatorImpl(),
        consensus = OffsetConsensus(),
    )

    private var subtitleSyncSession = 0L
    var subtitleOffsetMs: Long = 0L
        private set
    var autoSyncStatus: SyncStatus = SyncStatus.IDLE
        private set

    fun triggerSubtitleAutoSync(
        mediaUri: String,
        mediaItem: MediaItem?,
        subtitle: ParsedSubtitle,
        durationMs: Long,
        onResult: (SyncStatus) -> Unit = {},
    ) {
        val sessionAtStart = subtitleSyncSession
        autoSyncStatus = SyncStatus.ANALYZING
        scope.launch {
            val fingerprint = subtitleFingerprintProvider.fingerprint(subtitle)
            val result = runCatching {
                subtitleSyncCoordinator.synchronize(
                    SubtitleSyncRequest(
                        mediaId = mediaUri,
                        mediaUri = mediaUri,
                        mediaFileSize = mediaItem?.fileSize ?: 0L,
                        mediaDurationMs = durationMs,
                        subtitleFingerprint = fingerprint,
                        subtitleLanguage = null,
                        subtitle = subtitle,
                        playbackSessionId = sessionAtStart,
                    )
                )
            }.getOrElse { SubtitleSyncResult.Failed(it.message ?: "error") }

            if (subtitleSyncSession != sessionAtStart) return@launch

            when (result) {
                is SubtitleSyncResult.Synchronized -> {
                    autoSyncStatus = SyncStatus.SYNCHRONIZED
                    subtitleOffsetMs = when (val model = result.model) {
                        is com.watermelon.common.subtitle.sync.SubtitleSyncModel.Offset -> model.offsetMs
                        is com.watermelon.common.subtitle.sync.SubtitleSyncModel.Affine -> model.offsetMs
                        else -> subtitleOffsetMs
                    }
                }
                is SubtitleSyncResult.ComplexDriftDetected ->
                    autoSyncStatus = SyncStatus.COMPLEX_DRIFT
                is SubtitleSyncResult.LowConfidence ->
                    autoSyncStatus = SyncStatus.LOW_CONFIDENCE
                SubtitleSyncResult.Unsupported ->
                    autoSyncStatus = SyncStatus.UNSUPPORTED
                SubtitleSyncResult.ResourceDenied ->
                    autoSyncStatus = SyncStatus.RESOURCE_DENIED
                SubtitleSyncResult.Cancelled ->
                    autoSyncStatus = SyncStatus.IDLE
                is SubtitleSyncResult.Failed ->
                    autoSyncStatus = SyncStatus.FAILED
            }
            onResult(autoSyncStatus)
        }
    }

    fun applySubtitleManualNudge(
        mediaUri: String,
        mediaItem: MediaItem?,
        subtitle: ParsedSubtitle,
        deltaMs: Long,
    ) {
        val newOffsetMs = subtitleOffsetMs + deltaMs
        subtitleOffsetMs = newOffsetMs
        autoSyncStatus = SyncStatus.IDLE
        val fileSize = mediaItem?.fileSize ?: return
        scope.launch {
            val fingerprint = subtitleFingerprintProvider.fingerprint(subtitle)
            runCatching {
                subtitleSyncRepository.setManualOffset(mediaUri, fileSize, fingerprint, newOffsetMs)
            }
        }
    }

    fun onNewMediaUri() {
        subtitleSyncSession++
        subtitleOffsetMs = 0L
        autoSyncStatus = SyncStatus.IDLE
    }
}