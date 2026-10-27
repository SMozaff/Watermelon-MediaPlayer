package com.watermelon.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.watermelon.common.subtitle.sync.SyncStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Owns the subtitle Auto Sync state machine, extracted from MainActivity in Phase 2b.
 *
 * A result computed for one video must never be mistaken for another's, so
 * [beginSession] is bumped every time the player's mediaUri changes; [trigger]
 * captures the session id at launch and discards its result if playback has since
 * moved on to a different video (e.g. the user backed out to the library and opened
 * another one while the probe/correlation coroutine was still running).
 *
 * The two state fields are Compose state so player surfaces recompose when an
 * analysis finishes or an offset is nudged.
 */
internal class SubtitleSyncEngine(
    private val scope: CoroutineScope,
    private val fingerprintProvider: com.watermelon.subtitle.sync.SubtitleFingerprintProvider,
    private val syncRepository: com.watermelon.storage.repository.SubtitleSyncRepositoryImpl,
    private val coordinator: com.watermelon.subtitle.sync.SubtitleSyncCoordinator,
) {
    /** Manual or auto-applied offset for the current subtitle track, in milliseconds. */
    var offsetMs by mutableStateOf(0L)
        private set

    /** Latest Auto Sync analysis state for the current video. */
    var status by mutableStateOf(SyncStatus.IDLE)
        private set

    private var session = 0L

    /** Starts a new playback session: invalidates stale results and resets state. */
    fun beginSession() {
        session += 1
        offsetMs = 0L
        status = SyncStatus.IDLE
    }

    /** Restores a previously persisted offset for the current video's subtitle track. */
    fun restoreOffset(offset: Long) {
        offsetMs = offset
    }

    /** Looks up the persisted effective offset for [subtitle], or null when none exists. */
    suspend fun storedOffsetFor(mediaUri: String, fileSize: Long, subtitle: com.watermelon.common.model.ParsedSubtitle): Long? {
        val fingerprint = fingerprintProvider.fingerprint(subtitle)
        return runCatching { syncRepository.get(mediaUri, fileSize, fingerprint) }
            .getOrNull()
            ?.effectiveOffsetMs()
    }

    /**
     * Runs Auto Sync for [subtitle] against [mediaUri]/[mediaItem], applying the
     * result only if playback hasn't moved on to a different video in the meantime.
     */
    fun trigger(
        mediaUri: String,
        mediaItem: com.watermelon.common.model.MediaItem?,
        subtitle: com.watermelon.common.model.ParsedSubtitle,
        durationMs: Long,
    ) {
        val sessionAtStart = session
        status = SyncStatus.ANALYZING
        scope.launch {
            val fingerprint = fingerprintProvider.fingerprint(subtitle)
            val result = runCatching {
                coordinator.synchronize(
                    com.watermelon.common.subtitle.sync.SubtitleSyncRequest(
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
            }.getOrElse { com.watermelon.common.subtitle.sync.SubtitleSyncResult.Failed(it.message ?: "error") }

            // Stale: the user has since opened a different video. Discard silently.
            if (session != sessionAtStart) return@launch

            when (result) {
                is com.watermelon.common.subtitle.sync.SubtitleSyncResult.Synchronized -> {
                    status = SyncStatus.SYNCHRONIZED
                    offsetMs = when (val model = result.model) {
                        is com.watermelon.common.subtitle.sync.SubtitleSyncModel.Offset -> model.offsetMs
                        is com.watermelon.common.subtitle.sync.SubtitleSyncModel.Affine -> model.offsetMs
                        else -> offsetMs
                    }
                }
                is com.watermelon.common.subtitle.sync.SubtitleSyncResult.ComplexDriftDetected ->
                    status = SyncStatus.COMPLEX_DRIFT
                is com.watermelon.common.subtitle.sync.SubtitleSyncResult.LowConfidence ->
                    status = SyncStatus.LOW_CONFIDENCE
                com.watermelon.common.subtitle.sync.SubtitleSyncResult.Unsupported ->
                    status = SyncStatus.UNSUPPORTED
                com.watermelon.common.subtitle.sync.SubtitleSyncResult.ResourceDenied ->
                    status = SyncStatus.RESOURCE_DENIED
                com.watermelon.common.subtitle.sync.SubtitleSyncResult.Cancelled ->
                    status = SyncStatus.IDLE
                is com.watermelon.common.subtitle.sync.SubtitleSyncResult.Failed ->
                    status = SyncStatus.FAILED
            }
        }
    }

    /** Persists a manual offset nudge and reflects it immediately in the UI. */
    fun nudge(
        mediaUri: String,
        mediaItem: com.watermelon.common.model.MediaItem?,
        subtitle: com.watermelon.common.model.ParsedSubtitle,
        deltaMs: Long,
    ) {
        val newOffsetMs = offsetMs + deltaMs
        offsetMs = newOffsetMs
        status = SyncStatus.IDLE
        val fileSize = mediaItem?.fileSize ?: return
        scope.launch {
            val fingerprint = fingerprintProvider.fingerprint(subtitle)
            runCatching {
                syncRepository.setManualOffset(mediaUri, fileSize, fingerprint, newOffsetMs)
            }
        }
    }
}
