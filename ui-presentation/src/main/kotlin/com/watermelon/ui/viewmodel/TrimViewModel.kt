package com.watermelon.ui.viewmodel

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.watermelon.mediatools.engine.FilmstripExtractor
import com.watermelon.mediatools.engine.KeyframeIndexer
import com.watermelon.mediatools.engine.VideoTrimmer
import com.watermelon.mediatools.job.MediaJobManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Backs TrimScreen. Constructed the same plain-constructor way as FolderViewModel etc.
 * (no ViewModelProvider.Factory in this codebase).
 *
 * [keyframeIndexer]/[filmstripExtractor] back TrimScreen's redesigned UX (filmstrip +
 * haptic keyframe snapping): both do real file I/O / decode work, so they're only ever
 * invoked from [loadTrimAids] on [viewModelScope] (Dispatchers.IO), never on the calling
 * thread directly.
 */
@UnstableApi
class TrimViewModel(
    private val jobManager: MediaJobManager,
    private val trimmer: VideoTrimmer,
    private val keyframeIndexer: KeyframeIndexer,
    private val filmstripExtractor: FilmstripExtractor,
) : ViewModel() {

    private val _keyframeTimestampsMs = MutableStateFlow<List<Long>>(emptyList())
    val keyframeTimestampsMs: StateFlow<List<Long>> = _keyframeTimestampsMs.asStateFlow()

    private val _filmstripFrames = MutableStateFlow<List<Bitmap?>>(emptyList())
    val filmstripFrames: StateFlow<List<Bitmap?>> = _filmstripFrames.asStateFlow()

    private val _filmstripLoading = MutableStateFlow(false)
    val filmstripLoading: StateFlow<Boolean> = _filmstripLoading.asStateFlow()

    /**
     * Kicks off both keyframe indexing and filmstrip extraction for [inputUri] concurrently
     * on [viewModelScope]. Call once when TrimScreen is first shown for a given video --
     * TrimScreen's own LaunchedEffect(inputUri) is the intended caller, keyed on inputUri so
     * it only re-runs if the video itself changes, not on every recomposition.
     */
    fun loadTrimAids(inputUri: Uri, durationMs: Long, filmstripFrameCount: Int = 12) {
        viewModelScope.launch(Dispatchers.IO) {
            _keyframeTimestampsMs.value = keyframeIndexer.findKeyframeTimestampsMs(inputUri)
        }
        viewModelScope.launch(Dispatchers.IO) {
            _filmstripLoading.value = true
            _filmstripFrames.value = filmstripExtractor.extractFilmstrip(inputUri, durationMs, filmstripFrameCount)
            _filmstripLoading.value = false
        }
    }

    /** Returns the new job's id; caller shows MediaJobProgressSheet for it. */
    fun startTrim(inputUri: Uri, originalDisplayName: String, startMs: Long, endMs: Long): String {
        return trimmer.trim(jobManager, inputUri, originalDisplayName, startMs, endMs)
    }
}
