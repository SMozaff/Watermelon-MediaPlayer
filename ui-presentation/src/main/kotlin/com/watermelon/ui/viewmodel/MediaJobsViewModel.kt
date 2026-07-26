package com.watermelon.ui.viewmodel

import androidx.lifecycle.ViewModel
import android.content.ContentResolver
import com.watermelon.mediatools.job.MediaJob
import com.watermelon.mediatools.job.MediaJobManager
import kotlinx.coroutines.flow.StateFlow

/**
 * Thin pass-through so screens/components observe MediaJobManager without each one holding
 * a direct reference to it. Constructed the same way FolderViewModel etc. are -- a plain
 * constructor call from MainActivity (no ViewModelProvider.Factory in this codebase), with
 * mediaJobManager itself coming from WatermelonApplication's singleton (see that class).
 */
class MediaJobsViewModel(private val jobManager: MediaJobManager) : ViewModel() {

    val jobs: StateFlow<List<MediaJob>> = jobManager.jobs

    fun cancel(jobId: String) {
        jobManager.cancel(jobId)
    }

    fun resolveOriginalFileDecision(jobId: String, deleteOriginal: Boolean, contentResolver: ContentResolver) {
        jobManager.resolveOriginalFileDecision(jobId, deleteOriginal, contentResolver)
    }
}
