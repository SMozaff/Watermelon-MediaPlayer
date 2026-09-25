package com.watermelon.ui.viewmodel

import androidx.annotation.OptIn
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.media3.common.util.UnstableApi
import com.watermelon.mediatools.engine.VideoCompressor
import com.watermelon.mediatools.job.MediaJobManager

@OptIn(UnstableApi::class)
class CompressViewModel(
    private val jobManager: MediaJobManager,
    private val compressor: VideoCompressor,
) : ViewModel() {

    fun startCompress(inputUri: Uri, originalDisplayName: String, preset: VideoCompressor.Preset): String {
        return compressor.compress(jobManager, inputUri, preset, originalDisplayName)
    }

    fun startTargetSizeCompress(inputUri: Uri, originalDisplayName: String, targetSizeMb: Int): String {
        return compressor.compressToTargetSize(jobManager, inputUri, targetSizeMb, originalDisplayName)
    }
}
