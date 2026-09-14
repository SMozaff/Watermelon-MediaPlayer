package com.watermelon.app

import com.watermelon.common.model.ParsedSubtitle
import com.watermelon.common.model.MediaItem
import com.watermelon.common.subtitle.sync.*
import com.watermelon.subtitle.provider.opensubtitles.OpenSubtitlesProvider
import com.watermelon.subtitle.provider.registry.SubtitleProviderRegistry
import com.watermelon.subtitle.repository.SubtitleRepositoryImpl
import com.watermelon.subtitle.sync.*
import com.watermelon.subtitle.sync.SubtitleFingerprintProvider
import com.watermelon.subtitle.sync.SubtitleSyncCoordinator
import com.watermelon.subtitle.sync.SubtitleProbeSelectorImpl
import com.watermelon.subtitle.sync.SubtitleActivityBuilderImpl
import com.watermelon.mediatools.subtitle.sync.SparseSpeechProbeSource
import com.watermelon.subtitle.sync.OffsetConsensus
import com.watermelon.subtitle.sync.SubtitleSyncRepositoryImpl

val subtitleRepository: SubtitleRepositoryImpl by lazy {
    val openSubtitlesProvider = OpenSubtitlesProvider(
        apiKey = BuildConfig.OPEN_SUBTITLES_API_KEY,
        userAgent = "WatermelonMediaPlayer/1.0.0"
    )
    val providerRegistry = SubtitleProviderRegistry(listOf(openSubtitlesProvider))
    SubtitleRepositoryImpl(
        applicationContext,
        providerRegistry
    )
}

private val subtitleFingerprintProvider = SubtitleFingerprintProvider()
private val subtitleSyncRepository = SubtitleSyncRepositoryImpl(database)
private val phase1Sweep = Phase1Sweep(contentResolver)
private val indexer = MediaStoreIndexer(
    phase1Sweep = phase1Sweep,
    phase2Extractor = Phase2Extractor(applicationContext, database),
    mediaUriProvider = { phase1Sweep.lastSweepUris() }
)

private val subtitleSyncCoordinator: SubtitleSyncCoordinator = SubtitleSyncCoordinator(
    repository = subtitleSyncRepository,
    probeSelector = SubtitleProbeSelectorImpl(),
    subtitleActivityBuilder = SubtitleActivityBuilderImpl(),
    speechProbeSource = SparseSpeechProbeSource(applicationContext),
    correlator = ActivityCorrelatorImpl(),
    consensus = OffsetConsensus(),
)

fun triggerSubtitleAutoSync(
    mediaUri: String,
    mediaItem: MediaItem?,
    subtitle: ParsedSubtitle,
    durationMs: Long,
) {
    val sessionAtStart = subtitleSyncSession
    autoSyncStatus = SyncStatus.ANALYZING
    lifecycleScope.launch {
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
                    is SubtitleSyncResult.Offset -> model.offsetMs
                    is SubtitleSyncResult.Affine -> model.offsetMs
                    else -> subtitleOffsetMs
                }
            }
            is SubtitleSyncResult.ComplexDriftDetected -> autoSyncStatus = SyncStatus.COMPLEX_DRIFT
            is SubtitleSyncResult.LowConfidence -> autoSyncStatus = SyncStatus.LOW_CONFIDENCE
            is SubtitleSyncResult.Unsupported -> autoSyncStatus = SyncStatus.UNSUPPORTED
            is SubtitleSyncResult.ResourceDenied -> autoSyncStatus = SyncStatus.RESOURCE_DENIED
            is SubtitleSyncResult.Cancelled -> autoSyncStatus = SyncStatus.IDLE
            is SubtitleSyncResult.Failed -> autoSyncStatus = SyncStatus.FAILED
        }
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
    lifecycleScope.launch {
        val fingerprint = subtitleFingerprintProvider.fingerprint(subtitle)
        runCatching {
            subtitleSyncRepository.setManualOffset(mediaUri, fileSize, fingerprint, newOffsetMs)
        }
    }
}

fun discoverSubtitle(uri: String): ParsedSubtitle? {
    val item = runCatching { mediaRepository.getByUri(uri) }.getOrNull() ?: return null
    return subtitleRepository.parsedFor(
        mediaItem = item,
        preferredLanguages = PLAYER_SUBTITLE_LANGUAGE_PRIORITY
    )
}