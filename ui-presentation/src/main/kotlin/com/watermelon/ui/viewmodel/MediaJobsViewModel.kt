package com.watermelon.ui.viewmodel

import androidx.lifecycle.ViewModel
import android.content.ContentResolver
import androidx.media3.common.util.UnstableApi
import com.watermelon.mediatools.job.MediaJob
import com.watermelon.mediatools.job.MediaJobManager
import kotlinx.coroutines.flow.StateFlow

/**
 * Thin pass-through so screens/components observe MediaJobManager without each one holding
 * a direct reference to it. Constructed the same way FolderViewModel etc. are -- a plain
 * constructor call from MainActivity (no ViewModelProvider.Factory in this codebase), with
 * mediaJobManager itself coming from WatermelonApplication's singleton (see that class).
 *
 * @UnstableApi required: holds a MediaJobManager field, and MediaJobManager itself is
 * @UnstableApi-annotated (same reasoning as MediaJobService -- see that class's doc, where
 * lint caught this exact class of error first).
 */
@UnstableApi
class MediaJobsViewModel(private val jobManager: MediaJobManager) : ViewModel() {

    val jobs: StateFlow<List<MediaJob>> = jobManager.jobs

    fun cancel(jobId: String) {
        jobManager.cancel(jobId)
    }

    fun dismiss(jobId: String) {
        jobManager.dismiss(jobId)
    }

    fun resolveOriginalFileDecision(jobId: String, deleteOriginal: Boolean, contentResolver: ContentResolver) {
        jobManager.resolveOriginalFileDecision(jobId, deleteOriginal, contentResolver)
    }
}
