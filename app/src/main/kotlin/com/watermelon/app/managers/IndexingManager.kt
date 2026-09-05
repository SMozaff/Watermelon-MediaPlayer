package com.watermelon.app.managers

import android.content.Context
import android.os.Build
import com.watermelon.common.repository.MediaRepository
import com.watermelon.storage.db.WatermelonDatabase
import com.watermelon.storage.indexer.MediaStoreIndexer
import com.watermelon.storage.indexer.Phase1Sweep
import com.watermelon.storage.indexer.Phase2Extractor
import com.watermelon.storage.repository.FolderRepository
import com.watermelon.storage.repository.MediaRepositoryImpl
import com.watermelon.storage.repository.FolderRepositoryImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Handles media library indexing: Phase 1 sweep, Phase 2 extraction, and repository setup.
 * Encapsulates all indexing-related logic and state.
 */
class IndexingManager(
    private val context: Context,
    private val database: WatermelonDatabase,
    private val scope: CoroutineScope,
) {

    private val phase1Sweep = Phase1Sweep(context.contentResolver)
    private val indexer = MediaStoreIndexer(
        phase1Sweep = phase1Sweep,
        phase2Extractor = Phase2Extractor(context, database),
        mediaUriProvider = { phase1Sweep.lastSweepUris() }
    )

    val mediaRepository: MediaRepository = MediaRepositoryImpl(database, indexer)
    val folderRepository: FolderRepository = FolderRepositoryImpl(indexer)

    /**
     * Triggers an initial index if not already in progress.
     * Safe to call multiple times - indexing is idempotent.
     */
    fun triggerInitialIndex(onError: (String) -> Unit) {
        scope.launch {
            runCatching { mediaRepository.refreshIndex() }
                .onFailure { error ->
                    onError("initial library index failed: ${error.message ?: error::class.java.simpleName}")
                }
        }
    }
}