package com.watermelon.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.media3.common.util.UnstableApi
import com.watermelon.mediatools.engine.VideoCompressor
import com.watermelon.mediatools.job.MediaJobManager

@UnstableApi
class CompressViewModel(
    private val jobManager: MediaJobManager,
    private val compressor: VideoCompressor,
) : ViewModel() {

    fun startCompress(inputUri: Uri, originalDisplayName: String, preset: VideoCompressor.Preset): String {
        return compressor.compress(jobManager, inputUri, preset, originalDisplayName)
    }
}
