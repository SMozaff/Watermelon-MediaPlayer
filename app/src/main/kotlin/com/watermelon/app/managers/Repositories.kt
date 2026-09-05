package com.watermelon.app.managers

import android.content.Context
import com.watermelon.common.repository.FolderRepository
import com.watermelon.common.repository.MediaRepository
import com.watermelon.common.repository.PlaylistRepository
import com.watermelon.storage.db.WatermelonDatabase
import com.watermelon.storage.prefs.FolderVisibilityStoreImpl
import com.watermelon.storage.indexer.MediaStoreIndexer
import com.watermelon.storage.indexer.Phase1Sweep
import com.watermelon.storage.indexer.Phase2Extractor
import com.watermelon.storage.repository.FolderRepositoryImpl
import com.watermelon.storage.repository.MediaRepositoryImpl
import com.watermelon.storage.repository.PlaylistRepositoryImpl
import com.watermelon.storage.repository.PlaybackPositionRepositoryImpl
import com.watermelon.subtitle.repository.SubtitleRepositoryImpl
import com.watermelon.storage.repository.SubtitleSyncRepositoryImpl
import com.watermelon.subtitle.sync.SubtitleSyncCoordinator
import com.watermelon.subtitle.sync.SubtitleProbeSelectorImpl
import com.watermelon.subtitle.sync.SubtitleActivityBuilderImpl
import com.watermelon.subtitle.sync.ActivityCorrelatorImpl
import com.watermelon.subtitle.sync.OffsetConsensus
import com.watermelon.mediatools.subtitle.sync.SparseSpeechProbeSource
import com.watermelon.subtitle.sync.SubtitleFingerprintProvider
import kotlinx.coroutines.CoroutineScope

/**
 * Holder for all repository instances. Centralizes repository creation and dependency wiring.
 */
class Repositories(
    private val context: Context,
    private val database: com.watermelon.storage.db.WatermelonDatabase,
    private val scope: CoroutineScope,
) {

    private val settingsStore = FolderVisibilityStoreImpl(context)
    private val phase1Sweep = Phase1Sweep(context.contentResolver)

    val indexer = MediaStoreIndexer(
        phase1Sweep = phase1Sweep,
        phase2Extractor = Phase2Extractor(context, database),
        mediaUriProvider = { phase1Sweep.lastSweepUris() }
    )

    val mediaRepository: MediaRepository = MediaRepositoryImpl(database, indexer)
    val folderRepository: FolderRepository = FolderRepositoryImpl(indexer)
    val playlistRepository: PlaylistRepository = PlaylistRepositoryImpl(database, mediaRepository, settingsStore)
    val playbackPositionRepository = PlaybackPositionRepositoryImpl(database)
    val subtitleRepository = SubtitleRepositoryImpl(context)
    val subtitleSyncRepository = SubtitleSyncRepositoryImpl(database)
    val subtitleFingerprintProvider = SubtitleFingerprintProvider()

    val subtitleSyncCoordinator = SubtitleSyncCoordinator(
        repository = subtitleSyncRepository,
        probeSelector = SubtitleProbeSelectorImpl(),
        subtitleActivityBuilder = SubtitleActivityBuilderImpl(),
        speechProbeSource = SparseSpeechProbeSource(context),
        correlator = ActivityCorrelatorImpl(),
        consensus = OffsetConsensus(),
    )

    val settingsStoreInstance: FolderVisibilityStoreImpl = settingsStore
}