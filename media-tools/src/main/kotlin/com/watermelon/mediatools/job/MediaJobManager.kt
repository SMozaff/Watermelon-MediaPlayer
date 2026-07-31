package com.watermelon.mediatools.job

import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.TransformationRequest
import androidx.media3.transformer.Transformer
import com.watermelon.common.util.FileLogger
import com.watermelon.mediatools.output.OutputFileStore
import com.watermelon.mediatools.output.OutputNaming
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

private const val TAG = "MediaJobManager"
private const val PROGRESS_POLL_MS = 250L

/**
 * Single owner of media-processing job state, exposed as [StateFlow] — same shape as
 * [com.watermelon.playback.controller.PlaybackControllerImpl]'s `_playbackState`, not
 * [com.watermelon.playback.service.PlaybackConnection] (that class only holds a
 * MediaController reference; it doesn't own or publish state).
 *
 * Two registration paths, since not all engines use Transformer:
 * - [register]: Transformer-backed jobs (VideoTrimmer, VideoCompressor). Caller builds a
 *   Transformer + EditedMediaItem, registers it here, then calls `transformer.start(...)`
 *   itself; progress comes from polling `Transformer.getProgress()`.
 * - [registerCoroutineJob]: non-Transformer jobs (AudioExtractor — see its class doc for
 *   why it bypasses Transformer). Caller supplies a suspend block; this class runs it on
 *   Dispatchers.IO and reports progress via callback.
 * Neither engine talks to UI directly — both push through this manager's [jobs] StateFlow.
 */
@UnstableApi
class MediaJobManager(
    private val outputFileStore: OutputFileStore,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
    /**
     * Optional application Context, used only to start MediaJobService's foreground
     * notification when a job begins. Nullable/optional so this class stays constructible
     * without Android context (e.g. in tests) -- if null, jobs still run correctly, just
     * without the persistent notification / foreground-process protection, which is a real
     * but non-fatal degradation (see startJobServiceIfNeeded's doc).
     */
    private val appContext: android.content.Context? = null,
) {
    private val _jobs = MutableStateFlow<List<MediaJob>>(emptyList())
    val jobs: StateFlow<List<MediaJob>> = _jobs.asStateFlow()

    private val transformers = mutableMapOf<String, Transformer>()
    private val progressPollers = mutableMapOf<String, Job>()

    /**
     * Registers a new job and starts polling its progress. The caller (an engine class)
     * is responsible for building the [Transformer] with a listener that calls
     * [onCompleted]/[onError]/[onFallbackApplied] below, and for calling `transformer.start(...)`.
     */
    fun register(
        type: MediaJobType,
        inputUri: String,
        outputPath: String,
        transformer: Transformer,
        requestedStartMs: Long? = null,
    ): String {
        val id = UUID.randomUUID().toString()
        transformers[id] = transformer
        _jobs.update { it + MediaJob(id, type, inputUri, outputPath, MediaJobState.Queued, requestedStartMs = requestedStartMs) }
        setState(id, MediaJobState.Running)
        pollProgress(id, transformer)
        startJobServiceIfNeeded()
        FileLogger.i(TAG, "job registered id=$id type=$type")
        return id
    }

    /**
     * Registers a job for engines that don't use Transformer (currently: AudioExtractor,
     * which decodes via plain MediaCodec — see its class doc for why). The caller runs its
     * own work on [scope] via [work], reporting progress through the given callback and
     * returning the finished output path on success (must throw on failure/let CancellationException propagate).
     */
    fun registerCoroutineJob(
        type: MediaJobType,
        inputUri: String,
        outputPath: String,
        work: suspend (onProgress: (Int) -> Unit) -> Unit,
    ): String {
        val id = UUID.randomUUID().toString()
        _jobs.update { it + MediaJob(id, type, inputUri, outputPath, MediaJobState.Queued) }
        setState(id, MediaJobState.Running)
        startJobServiceIfNeeded()
        FileLogger.i(TAG, "coroutine job registered id=$id type=$type")

        progressPollers[id] = scope.launch(Dispatchers.IO) {
            try {
                work { pct ->
                    _jobs.update { list ->
                        list.map { if (it.id == id) it.copy(progressPercent = pct.coerceIn(0, 100)) else it }
                    }
                }
                completeJob(id)
            } catch (e: kotlinx.coroutines.CancellationException) {
                failCleanup(id)
                setState(id, MediaJobState.Cancelled)
                FileLogger.i(TAG, "coroutine job cancelled id=$id")
                throw e
            } catch (e: Exception) {
                failCleanup(id)
                setState(id, MediaJobState.Failed(e.message ?: "Unknown error"))
                FileLogger.e(TAG, "coroutine job failed id=$id", e)
            }
        }
        return id
    }

    private fun completeJob(id: String) {
        val job = _jobs.value.find { it.id == id } ?: run {
            FileLogger.e(TAG, "completeJob for unknown job id=$id")
            return
        }

        // For TRIM jobs, read the staging file's REAL duration before publish() deletes it
        // -- the requested cut may have shifted slightly due to keyframe snapping (see
        // VideoTrimmer), and this is the only reliable way to know the actual result
        // (ExportResult's own field for this wasn't confirmed via Context7 this session, so
        // reading the real output file directly is the safer ground-truth approach).
        val actualTrimRangeMs: Pair<Long, Long>? =
            if (job.type == MediaJobType.TRIM && job.requestedStartMs != null) {
                readActualTrimRange(job.outputPath, job.requestedStartMs)
            } else null

        val displayName = File(job.outputPath).name
        val publishedUri = outputFileStore.publish(job.type, job.outputPath, displayName)
        if (publishedUri != null) {
            val awaitingDecision = job.type == MediaJobType.TRIM || job.type == MediaJobType.COMPRESS
            setState(id, MediaJobState.Completed(publishedUri.toString(), awaitingDecision, actualTrimRangeMs))
            FileLogger.i(TAG, "job completed id=$id uri=$publishedUri awaitingDecision=$awaitingDecision actualTrimRangeMs=$actualTrimRangeMs")
        } else {
            setState(id, MediaJobState.Failed("Job finished but publishing the output file failed"))
            FileLogger.e(TAG, "publish failed after job completed id=$id")
        }
    }

    /**
     * Reads the trimmed staging file's real duration via MediaMetadataRetriever (same
     * confirmed-real API used in VideoCompressor for source-resolution detection) and
     * derives (actualStartMs, actualEndMs). The output file's own timeline always starts at
     * 0, so we can't recover "where did the start land" from the file alone -- this assumes
     * requestedStartMs is a close-enough proxy for the actual start (keyframe snapping
     * typically lands within one GOP, usually well under a second), and reports
     * actualEndMs = requestedStartMs + realDurationMs. If that assumption turns out wrong in
     * practice, this is the one place to revisit.
     */
    private fun readActualTrimRange(stagingPath: String, requestedStartMs: Long): Pair<Long, Long>? {
        val retriever = android.media.MediaMetadataRetriever()
        return try {
            retriever.setDataSource(stagingPath)
            val durationMs = retriever.extractMetadata(
                android.media.MediaMetadataRetriever.METADATA_KEY_DURATION
            )?.toLongOrNull()
            if (durationMs == null) {
                FileLogger.e(TAG, "could not read output duration for $stagingPath")
                null
            } else {
                requestedStartMs to (requestedStartMs + durationMs)
            }
        } catch (e: Exception) {
            FileLogger.e(TAG, "readActualTrimRange failed for $stagingPath", e)
            null
        } finally {
            retriever.release()
        }
    }

    /**
     * Called once the user answers the "keep or delete the original?" prompt shown for a
     * completed TRIM/COMPRESS job (see [MediaJobState.Completed.awaitingOriginalFileDecision]).
     * If [deleteOriginal] is true, deletes [MediaJob.inputUri] via MediaStore.
     *
     * On API 29+, a direct [android.content.ContentResolver.delete] on a MediaStore item this
     * app didn't itself insert (the normal case here — these are pre-existing library videos,
     * not media-tools output) would throw RecoverableSecurityException. That path is what
     * [com.watermelon.mediatools.output.OriginalFileDeleter] exists for: it drives the real
     * MediaStore.createDeleteRequest -> system consent dialog flow and calls this method
     * itself once the user answers. TrimScreen/CompressScreen route "Delete Original" through
     * OriginalFileDeleter on API 29+ and call this method directly only on API < 29 (see
     * those screens for the actual UI wiring).
     */
    fun resolveOriginalFileDecision(id: String, deleteOriginal: Boolean, contentResolver: android.content.ContentResolver) {
        val job = _jobs.value.find { it.id == id }
        val completed = job?.state as? MediaJobState.Completed
        if (completed == null || !completed.awaitingOriginalFileDecision) {
            FileLogger.e(TAG, "resolveOriginalFileDecision called for job not awaiting a decision, id=$id")
            return
        }

        if (deleteOriginal) {
            runCatching {
                contentResolver.delete(android.net.Uri.parse(job.inputUri), null, null)
            }.onFailure { e ->
                FileLogger.e(TAG, "failed to delete original for job id=$id", e)
            }.onSuccess {
                FileLogger.i(TAG, "deleted original inputUri=${job.inputUri} for job id=$id")
            }
        } else {
            FileLogger.i(TAG, "user chose to keep original for job id=$id")
        }

        setState(id, completed.copy(awaitingOriginalFileDecision = false))
    }

    private fun failCleanup(id: String) {
        _jobs.value.find { it.id == id }?.let { outputFileStore.deleteStaging(it.outputPath) }
    }

    fun onCompleted(id: String, exportResult: ExportResult) {
        stopPolling(id)
        FileLogger.i(TAG, "transformer job export done id=$id sizeBytes=${exportResult.fileSizeBytes}")
        completeJob(id)
        transformers.remove(id)
    }

    fun onError(id: String, exportException: ExportException) {
        stopPolling(id)
        failCleanup(id)
        setState(id, MediaJobState.Failed(exportException.message ?: "Unknown export error"))
        FileLogger.e(TAG, "job failed id=$id", exportException)
        transformers.remove(id)
    }

    /**
     * Transformer silently falls back to a slower re-encode path when the fast/lossless
     * path isn't achievable for the input (see blueprint §2). We only log for now —
     * surfacing this into MediaJob's state (e.g. a wasFallback flag) is a Phase 3 UI
     * decision, not something to guess at here.
     */
    fun onFallbackApplied(
        id: String,
        originalRequest: TransformationRequest,
        fallbackRequest: TransformationRequest,
    ) {
        FileLogger.w(TAG, "job id=$id fell back: $originalRequest -> $fallbackRequest")
    }

    fun cancel(id: String) {
        transformers[id]?.cancel()
        transformers.remove(id)
        progressPollers.remove(id)?.cancel() // for coroutine jobs, this cancels `work` itself
        _jobs.value.find { it.id == id }?.let { outputFileStore.deleteStaging(it.outputPath) }
        setState(id, MediaJobState.Cancelled)
        FileLogger.i(TAG, "job cancelled id=$id")
    }

    /**
     * Convenience entry point for "Extract Audio": stages an .mp3 output path via
     * [outputFileStore], then runs [AudioExtractor] as a coroutine job. This is what the
     * video-list/player overflow action (blueprint §3 UI) should call.
     */
    fun extractAudio(
        extractor: com.watermelon.mediatools.engine.AudioExtractor,
        inputPath: String,
        originalDisplayName: String,
        bitrateKbps: Int = com.watermelon.mediatools.engine.AudioExtractor.BitratePreset.STANDARD.kbps,
    ): String {
        val outputName = OutputNaming.extractedAudioName(originalDisplayName)
        val stagingPath = outputFileStore.stagingPathFor(MediaJobType.EXTRACT_AUDIO, outputName)
        return registerCoroutineJob(MediaJobType.EXTRACT_AUDIO, inputPath, stagingPath) { onProgress ->
            extractor.extractSuspending(inputPath, stagingPath, bitrateKbps, onProgress = onProgress)
        }
    }

    private fun pollProgress(id: String, transformer: Transformer) {
        progressPollers[id] = scope.launch {
            val holder = ProgressHolder()
            while (isActive) {
                val progressState = transformer.getProgress(holder)
                if (progressState == Transformer.PROGRESS_STATE_NOT_STARTED) break
                _jobs.update { list ->
                    list.map { if (it.id == id) it.copy(progressPercent = holder.progress) else it }
                }
                delay(PROGRESS_POLL_MS)
            }
        }
    }

    private fun stopPolling(id: String) {
        progressPollers.remove(id)?.cancel()
    }

    /**
     * Starts MediaJobService so it can show its progress/cancel notification and hold a
     * foreground-process lifetime while this job runs. Safe to call on every job start --
     * MediaJobService's onStartCommand only sets up its jobs-observer once
     * (observerJob == null check), so redundant starts are harmless.
     *
     * If [appContext] wasn't supplied, this silently does nothing -- jobs still complete
     * correctly, just without the notification/foreground protection. That's a real
     * degradation (the process could be killed if backgrounded mid-job), not a cosmetic one,
     * so callers should supply appContext in production; this fallback exists only so
     * MediaJobManager stays constructible without Android context (e.g. tests).
     */
    private fun startJobServiceIfNeeded() {
        val context = appContext ?: run {
            FileLogger.e(TAG, "no appContext supplied to MediaJobManager -- MediaJobService will not start; job will run without foreground protection")
            return
        }
        val intent = android.content.Intent(context, com.watermelon.mediatools.service.MediaJobService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    private fun setState(id: String, state: MediaJobState) {
        _jobs.update { list -> list.map { if (it.id == id) it.copy(state = state) else it } }
    }
}
