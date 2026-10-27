package com.watermelon.app

import android.content.Context
import kotlinx.coroutines.CoroutineScope

/**
 * Composition root for the Activity-scoped dependency graph, extracted from
 * MainActivity in Phase 2b. This app deliberately has no DI framework (see
 * WatermelonApplication's doc), so this is a plain container of lazy singletons
 * matching the style that already existed as a wall of `by lazy` fields.
 *
 * Everything here is owned by exactly one MainActivity instance and lives no
 * longer than it; the Application-scoped media-tools singletons
 * (MediaJobManager/OutputFileStore) are read from WatermelonApplication rather
 * than constructed, exactly as before.
 *
 * @param context any context; only applicationContext is retained.
 * @param app the Application instance (for the media-tools singletons and BuildConfig).
 * @param scope the Activity's lifecycleScope, given to the components that launch
 *   coroutines which must be cancelled when the Activity goes away.
 */
internal class AppDependencies(
    context: Context,
    app: WatermelonApplication,
    scope: CoroutineScope,
) {
    private val appContext = context.applicationContext
    private val application = app

    val database by lazy { com.watermelon.storage.db.WatermelonDatabase(appContext) }
    val settingsStore by lazy { com.watermelon.storage.prefs.FolderVisibilityStoreImpl(appContext) }

    // media-tools: reuse the singleton MediaJobManager/OutputFileStore owned by
    // WatermelonApplication (see that class's doc). OutputFileStore reads the same
    // watermelon_prefs keys written by SettingsPersistence, so exports use the
    // destination currently shown in the Settings screen.
    val mediaJobManager by lazy { application.mediaJobManager }
    val outputFileStore by lazy { application.outputFileStore }
    val audioExtractor by lazy { com.watermelon.mediatools.engine.AudioExtractor(appContext) }
    val videoTrimmer by lazy {
        com.watermelon.mediatools.engine.VideoTrimmer(appContext, outputFileStore)
    }
    val videoCompressor by lazy {
        com.watermelon.mediatools.engine.VideoCompressor(appContext, outputFileStore)
    }
    // Backs TrimScreen's redesigned UX (filmstrip + haptic keyframe snapping) --
    // see TrimViewModel's class doc.
    val keyframeIndexer by lazy { com.watermelon.mediatools.engine.KeyframeIndexer(appContext) }
    val filmstripExtractor by lazy { com.watermelon.mediatools.engine.FilmstripExtractor(appContext) }

    // Reversed-playback audio for the VHS effect; a lifecycle-scoped sound pool.
    val vhsReverseSound by lazy { VhsReverseSound() }

    val subtitleRepository by lazy {
        val openSubtitlesProvider = com.watermelon.subtitle.provider.opensubtitles.OpenSubtitlesProvider(
            apiKey = BuildConfig.OPEN_SUBTITLES_API_KEY,
            userAgent = "WatermelonMediaPlayer/1.0.0"
        )
        val providerRegistry = com.watermelon.subtitle.provider.registry.SubtitleProviderRegistry(
            listOf(openSubtitlesProvider)
        )
        com.watermelon.subtitle.repository.SubtitleRepositoryImpl(
            appContext,
            providerRegistry
        )
    }

    private val phase1Sweep by lazy { com.watermelon.storage.indexer.Phase1Sweep(appContext.contentResolver) }

    val indexer by lazy {
        com.watermelon.storage.indexer.MediaStoreIndexer(
            phase1Sweep = phase1Sweep,
            phase2Extractor = com.watermelon.storage.indexer.Phase2Extractor(appContext, database),
            mediaUriProvider = { phase1Sweep.lastSweepUris() }
        )
    }

    val mediaRepository by lazy { com.watermelon.storage.repository.MediaRepositoryImpl(database, indexer) }
    val folderRepository by lazy { com.watermelon.storage.repository.FolderRepositoryImpl(indexer) }
    val playlistRepository by lazy {
        com.watermelon.storage.repository.PlaylistRepositoryImpl(database, mediaRepository, settingsStore)
    }
    val playbackPositionRepository by lazy {
        com.watermelon.storage.repository.PlaybackPositionRepositoryImpl(database)
    }

    val subtitleFingerprintProvider by lazy { com.watermelon.subtitle.sync.SubtitleFingerprintProvider() }
    val subtitleSyncRepository by lazy { com.watermelon.storage.repository.SubtitleSyncRepositoryImpl(database) }
    val subtitleSyncCoordinator by lazy {
        com.watermelon.subtitle.sync.SubtitleSyncCoordinator(
            repository = subtitleSyncRepository,
            probeSelector = com.watermelon.subtitle.sync.SubtitleProbeSelectorImpl(),
            subtitleActivityBuilder = com.watermelon.subtitle.sync.SubtitleActivityBuilderImpl(),
            speechProbeSource = com.watermelon.mediatools.subtitle.sync.SparseSpeechProbeSource(appContext),
            correlator = com.watermelon.subtitle.sync.ActivityCorrelatorImpl(),
            consensus = com.watermelon.subtitle.sync.OffsetConsensus(),
        )
    }

    val subtitleSyncEngine by lazy {
        SubtitleSyncEngine(
            scope = scope,
            fingerprintProvider = subtitleFingerprintProvider,
            syncRepository = subtitleSyncRepository,
            coordinator = subtitleSyncCoordinator,
        )
    }

    val mediaJobsViewModel by lazy {
        com.watermelon.ui.viewmodel.MediaJobsViewModel(mediaJobManager)
    }
}
