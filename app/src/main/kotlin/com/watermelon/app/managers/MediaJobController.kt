package com.watermelon.app.managers

import android.app.Activity
import android.content.ContentResolver
import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.watermelon.mediatools.engine.AudioExtractor
import com.watermelon.mediatools.engine.FilmstripExtractor
import com.watermelon.mediatools.engine.KeyframeIndexer
import com.watermelon.mediatools.engine.VideoCompressor
import com.watermelon.mediatools.engine.VideoTrimmer
import com.watermelon.mediatools.job.MediaJobManager
import com.watermelon.mediatools.output.OriginalFileDeleter
import com.watermelon.mediatools.output.OutputFileStore
import com.watermelon.ui.viewmodel.MediaJobsViewModel
import kotlinx.coroutines.CoroutineScope

/**
 * Manages media tools (trim, compress, extract audio) and media job lifecycle.
 */
class MediaJobController(
    private val activity: Activity,
    private val mediaJobManager: MediaJobManager,
    private val outputFileStore: OutputFileStore,
    private val scope: CoroutineScope,
) {

    val audioExtractor = AudioExtractor(activity)
    val videoTrimmer = VideoTrimmer(activity, outputFileStore)
    val videoCompressor = VideoCompressor(activity, outputFileStore)
    val keyframeIndexer = KeyframeIndexer(activity)
    val filmstripExtractor = FilmstripExtractor(activity)

    val mediaJobsViewModel = MediaJobsViewModel(mediaJobManager)

    private lateinit var originalFileDeleter: OriginalFileDeleter
    private lateinit var playerDeleteLauncher: ActivityResultLauncher<androidx.activity.result.IntentSenderRequest>

    init {
        originalFileDeleter = OriginalFileDeleter(activity) { jobId, deleted ->
            mediaJobManager.resolveOriginalFileDecision(jobId, deleteOriginal = deleted, activity.contentResolver)
        }
        playerDeleteLauncher = activity.registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult()
        ) { result ->
            // Handled by caller via callback
        }
    }

    fun getOriginalFileDeleter(): OriginalFileDeleter = originalFileDeleter
    fun getPlayerDeleteLauncher(): ActivityResultLauncher<androidx.activity.result.IntentSenderRequest> = playerDeleteLauncher
}