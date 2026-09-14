package com.watermelon.app

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest.Builder
import androidx.compose.material3.AlertDialog
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.watermelon.mediatools.engine.AudioExtractor
import com.watermelon.mediatools.engine.KeyframeIndexer
import com.watermelon.mediatools.engine.VideoCompressor
import com.watermelon.mediatools.engine.VideoTrimmer
import com.watermelon.mediatools.job.MediaJobState
import com.watermelon.mediatools.output.OriginalFileDeleter
import com.watermelon.ui.viewmodel.CompressViewModel
import com.watermelon.ui.viewmodel.TrimViewModel
import com.watermelon.ui.viewmodel.MediaJobsViewModel
import com.watermelon.common.util.FileLogger
import com.watermelon.storage.db.WatermelonDatabase
import com.watermelon.storage.repository.MediaRepositoryImpl
import com.watermelon.storage.repository.MediaJobsViewModel

fun requestPlayerDelete(
    target: PlayerDeleteTarget,
    activityResultLauncher: ActivityResultLauncher<IntentSenderRequest>,
    contentResolver: ContentResolver,
    mediaRepository: MediaRepositoryImpl,
    mediaJobManager: /* media job manager type */,
    originalFileDeleter: OriginalFileDeleter,
    onDelete: () -> Unit
) {
    val mediaUri = uri -> {
        val id = android.content.ContentUris.parseId(Uri.parse(uri))
        android.content.ContentUris.withAppendedId(
            android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            id
        )
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        lifecycleScope.launch {
            runCatching {
                val request = android.provider.MediaStore.createDeleteRequest(
                    contentResolver,
                    listOf(mediaUri(target.uri))
                )
                activityResultLauncher.launch(
                    androidx.activity.result.IntentSenderRequest.Builder(request.intentSender).build()
                )
            }.onFailure { error ->
                pendingPlayerDelete = null
                playerDeleteOutcome = PlayerDeleteOutcome.Failed(
                    target,
                    error.message ?: "Android could not start the delete request."
                )
            }
        }
        return
    }

    lifecycleScope.launch {
        try {
            val rowsDeleted = withContext(Dispatchers.IO) {
                contentResolver.delete(mediaUri(target.uri), null, null)
            }
            pendingPlayerDelete = null
            playerDeleteOutcome = if (rowsDeleted > 0) {
                PlayerDeleteOutcome.Deleted(target)
            } else {
                PlayerDeleteOutcome.Failed(target, "Watermelon could not delete this video.")
            }
        } catch (error: SecurityException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                error is android.app.RecoverableSecurityException
            ) {
                runCatching {
                    activityResultLauncher.launch(
                        androidx.activity.result.IntentSenderRequest.Builder(
                            error.userAction.actionIntent.intentSender
                        ).build()
                    )
                }.onFailure { launchError ->
                    pendingPlayerDelete = null
                    playerDeleteOutcome = PlayerDeleteOutcome.Failed(
                        target,
                        launchError.message ?: "Android could not start the delete request."
                    )
                }
                return@launch
            }
            pendingPlayerDelete = null
            playerDeleteOutcome = PlayerDeleteOutcome.Failed(
                target,
                error.message ?: "Watermelon does not have permission to delete this video."
            )
        } catch (error: Throwable) {
            pendingPlayerDelete = null
            playerDeleteOutcome = PlayerDeleteOutcome.Failed(
                target,
                error.message ?: "Watermelon could not delete this video."
            )
        }
    }
}

fun triggerExtractAudio(
    audioExtractor: AudioExtractor,
    mediaUri: String,
    displayName: String,
    mediaJobManager: /* media job manager type */,
    kbps: Int
) {
    lifecycleScope.launch {
        mediaJobManager.extractAudio(audioExtractor, mediaUri, displayName, kbps)
    }
}

fun triggerTrimVideo(
    videoTrimmer: VideoTrimmer,
    keyframeIndexer: KeyframeIndexer,
    filmstripExtractor: /* filmstrip extractor type */,
    mediaJobManager: /* media job manager type */,
    contentResolver: ContentResolver,
    mediaUri: Uri,
    originalDisplayName: String,
    durationMs: Long,
    onBack: () -> Unit
) {
    val trimVm = TrimViewModel(mediaJobManager, videoTrimmer, keyframeIndexer, filmstripExtractor)
    // Navigate to trim screen - handled by NavHost
}

fun triggerCompressVideo(
    videoCompressor: VideoCompressor,
    mediaJobManager: /* media job manager type */,
    contentResolver: ContentResolver,
    mediaUri: Uri,
    originalDisplayName: String,
    isPremiumUnlocked: Boolean,
    onBack: () -> Unit
) {
    val compressVm = CompressViewModel(mediaJobManager, videoCompressor)
    // Navigate to compress screen - handled by NavHost
}