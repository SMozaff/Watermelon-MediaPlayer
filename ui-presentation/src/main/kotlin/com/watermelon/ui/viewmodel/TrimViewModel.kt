package com.watermelon.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.media3.common.util.UnstableApi
import com.watermelon.mediatools.engine.VideoTrimmer
import com.watermelon.mediatools.job.MediaJobManager

/**
 * Backs TrimScreen. Constructed the same plain-constructor way as FolderViewModel etc.
 * (no ViewModelProvider.Factory in this codebase).
 */
@UnstableApi
class TrimViewModel(
    private val jobManager: MediaJobManager,
    private val trimmer: VideoTrimmer,
) : ViewModel() {

    /** Returns the new job's id; caller shows MediaJobProgressSheet for it. */
    fun startTrim(inputUri: Uri, originalDisplayName: String, startMs: Long, endMs: Long): String {
        return trimmer.trim(jobManager, inputUri, originalDisplayName, startMs, endMs)
    }
}
