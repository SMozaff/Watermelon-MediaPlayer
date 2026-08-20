package com.watermelon.ui.screens

import android.content.ContentResolver
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.watermelon.common.model.PlaybackState
import com.watermelon.common.model.UserIntent
import com.watermelon.mediatools.job.MediaJob
import com.watermelon.mediatools.job.MediaJobState
import com.watermelon.ui.components.KeepOrDeleteOriginalDialog
import com.watermelon.ui.components.MediaJobProgressSheet
import com.watermelon.ui.components.TrimRangeScrubber
import com.watermelon.ui.theme.WatermelonShapes
import com.watermelon.ui.theme.WatermelonSpacing
import com.watermelon.ui.viewmodel.MediaJobsViewModel
import com.watermelon.ui.viewmodel.PlayerViewModel
import com.watermelon.ui.viewmodel.TrimViewModel

private const val MIN_TRIM_RANGE_MS = 500L

/**
 * Per UI_MANIFEST.md §3. Reuses PlayerViewModel (via UserIntent.Seek/Play/Pause) for the
 * live range preview -- no new playback primitive, same mechanism PhonePlayerScreen uses,
 * just driven through the lighter MVI surface rather than PhonePlayerScreen's full UI.
 *
 * Filmstrip + haptic keyframe-snapping (both real, decoded/extracted data -- see
 * KeyframeIndexer/FilmstripExtractor) are now built and passed into TrimRangeScrubber below.
 * This corrects an earlier doc comment here that said the filmstrip was deliberately out of
 * scope for v1 -- that was true when TrimScreen only had a plain scrubber; it's stale now
 * that TrimViewModel.loadTrimAids actually populates both.
 *
 * @param surface the video render surface, same pattern as PhonePlayerScreen's `surface` param
 *   (caller supplies the actual Compose surface hookup; this screen doesn't own that).
 */
@UnstableApi
@Composable
fun TrimScreen(
    playerViewModel: PlayerViewModel,
    trimViewModel: TrimViewModel,
    mediaJobsViewModel: MediaJobsViewModel,
    contentResolver: ContentResolver,
    originalFileDeleter: com.watermelon.mediatools.output.OriginalFileDeleter,
    inputUri: Uri,
    originalDisplayName: String,
    durationMs: Long,
    surface: @Composable (Modifier) -> Unit,
    isPremiumUnlocked: Boolean,
    onRequestUpsell: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var startMs by remember { mutableStateOf(0L) }
    var endMs by remember { mutableStateOf(durationMs) }
    var activeJobId by remember { mutableStateOf<String?>(null) }
    var pendingDeleteConsent by remember { mutableStateOf(false) }
    var showDiscardChangesDialog by remember { mutableStateOf(false) }
    var showBackgroundExitDialog by remember { mutableStateOf(false) }

    // Kicks off keyframe indexing + filmstrip extraction once per (inputUri, durationMs) --
    // see TrimViewModel.loadTrimAids's doc for why this is the intended call site.
    LaunchedEffect(inputUri, durationMs) {
        trimViewModel.loadTrimAids(inputUri, durationMs)
    }
    val keyframeTimestampsMs by trimViewModel.keyframeTimestampsMs.collectAsStateWithLifecycle()
    val filmstripFrames by trimViewModel.filmstripFrames.collectAsStateWithLifecycle()

    val position by playerViewModel.currentPositionMs.collectAsStateWithLifecycle()
    val playbackState by playerViewModel.playbackState.collectAsStateWithLifecycle()
    val jobs by mediaJobsViewModel.jobs.collectAsStateWithLifecycle()
    val activeJob: MediaJob? = jobs.find { it.id == activeJobId }
        ?: jobs.lastOrNull { job ->
            val state = job.state
            job.type == com.watermelon.mediatools.job.MediaJobType.TRIM &&
                job.inputUri == inputUri.toString() &&
                (state is MediaJobState.Queued ||
                    state is MediaJobState.Running ||
                    (state is MediaJobState.Completed && state.awaitingOriginalFileDecision))
        }
    val hasSelectedCut = startMs > 0L || endMs < durationMs
    val hasRunningJob = activeJob?.state is MediaJobState.Queued || activeJob?.state is MediaJobState.Running
    val requestExit = {
        when {
            hasRunningJob -> showBackgroundExitDialog = true
            hasSelectedCut -> showDiscardChangesDialog = true
            else -> onBack()
        }
    }
    BackHandler(onBack = requestExit)

    // Auto-pause at endMs while previewing the selected range -- same seek/play/pause
    // mechanism PhonePlayerScreen already uses, just driven from this screen's own state.
    LaunchedEffect(position, endMs, playbackState) {
        if (playbackState == PlaybackState.PLAYING && position >= endMs) {
            playerViewModel.onIntent(UserIntent.Pause)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(WatermelonSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(WatermelonSpacing.md)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = requestExit) { Text("Back") }
            Spacer(Modifier.weight(1f))
            Text(
                text = "Trim",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(vertical = WatermelonSpacing.sm),
            )
            Spacer(Modifier.weight(1f))
        }
        surface(Modifier.fillMaxWidth().padding(bottom = WatermelonSpacing.md))

        Text(
            "Trim is instant -- no re-encoding. Cut points snap to the nearest keyframe, " +
                "so the exact start/end may shift slightly.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        TrimRangeScrubber(
            startMs = startMs,
            endMs = endMs,
            durationMs = durationMs,
            onRangeChange = { newStart, newEnd ->
                startMs = newStart
                endMs = newEnd
                playerViewModel.onIntent(UserIntent.Seek(newStart))
            },
            keyframeTimestampsMs = keyframeTimestampsMs,
            filmstripFrames = filmstripFrames,
            modifier = Modifier.fillMaxWidth()
        )

        Row_TimeLabels(startMs, endMs)

        Button(
            onClick = {
                // Premium gating temporarily disabled -- everything is fully unlocked for
                // now per product decision; isPremiumUnlocked/onRequestUpsell stay wired
                // through the call chain so re-enabling later is a one-line change here,
                // not a re-plumbing job.
                activeJobId = trimViewModel.startTrim(inputUri, originalDisplayName, startMs, endMs)
            },
            enabled = hasSelectedCut && (endMs - startMs) >= MIN_TRIM_RANGE_MS,
            shape = WatermelonShapes.control,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Trim")
        }
    }

    activeJob?.let { job ->
        val state = job.state
        when {
            state is MediaJobState.Completed && state.awaitingOriginalFileDecision -> {
                KeepOrDeleteOriginalDialog(
                    originalFileName = originalDisplayName,
                    outputFileName = state.outputUri.substringAfterLast('/'),
                    isTrim = true,
                    isPendingSystemConsent = pendingDeleteConsent,
                    actualTrimRangeMs = state.actualTrimRangeMs,
                    onKeepOriginal = {
                        mediaJobsViewModel.resolveOriginalFileDecision(job.id, deleteOriginal = false, contentResolver)
                        activeJobId = null
                        onBack()
                    },
                    onDeleteOriginal = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            // API 30+: real system consent dialog via OriginalFileDeleter
                            // (MediaStore.createDeleteRequest requires API 30, not 29 --
                            // this boundary was wrong before, caught via lint's NewApi check).
                            // Its result callback (registered once in MainActivity) calls
                            // MediaJobManager.resolveOriginalFileDecision itself once the
                            // user answers -- this composable just watches the job's state
                            // for awaitingOriginalFileDecision to flip false (see below)
                            // rather than needing its own separate callback wiring here.
                            pendingDeleteConsent = true
                            originalFileDeleter.requestDelete(job.id, Uri.parse(job.inputUri), contentResolver)
                        } else {
                            // API < 30: direct delete works without a consent dialog.
                            mediaJobsViewModel.resolveOriginalFileDecision(job.id, deleteOriginal = true, contentResolver)
                            activeJobId = null
                            onBack()
                        }
                    },
                )
            }
            else -> {
                MediaJobProgressSheet(
                    job = job,
                    onCancel = { mediaJobsViewModel.cancel(job.id) },
                    onDismiss = {
                        if (state is MediaJobState.Queued || state is MediaJobState.Running) {
                            showBackgroundExitDialog = true
                        } else {
                            activeJobId = null
                            if (state is MediaJobState.Completed) onBack()
                        }
                    },
                )
            }
        }
    }

    if (showDiscardChangesDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardChangesDialog = false },
            title = { Text("Discard trim changes?") },
            text = { Text("Your selected trim range has not been saved. You can continue editing or discard it and return.") },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardChangesDialog = false
                    onBack()
                }) { Text("Discard changes") }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardChangesDialog = false }) { Text("Continue editing") }
            },
        )
    }

    if (showBackgroundExitDialog) {
        AlertDialog(
            onDismissRequest = { showBackgroundExitDialog = false },
            title = { Text("Trim will continue") },
            text = {
                Text(
                    "This trim is still running in the background. You can reopen Trim from File actions to inspect its progress."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showBackgroundExitDialog = false
                    activeJobId = null
                    onBack()
                }) { Text("Keep running") }
            },
            dismissButton = {
                TextButton(onClick = { showBackgroundExitDialog = false }) { Text("Stay") }
            },
        )
    }

    // Once OriginalFileDeleter's async result lands, MediaJobManager.resolveOriginalFileDecision
    // has already run and flipped awaitingOriginalFileDecision to false -- that's this
    // composable's signal the consent flow finished (accepted or dismissed; per UI_MANIFEST.md
    // §5.1, a dismissed/cancelled consent dialog is treated the same as Keep, not an error).
    LaunchedEffect(activeJob) {
        val state = activeJob?.state
        if (pendingDeleteConsent && state is MediaJobState.Completed && !state.awaitingOriginalFileDecision) {
            pendingDeleteConsent = false
            activeJobId = null
            onBack()
        }
    }
}

@Composable
private fun Row_TimeLabels(startMs: Long, endMs: Long) {
    Text(
        "${formatMs(startMs)}  -  ${formatMs(endMs)}",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface
    )
}

private fun formatMs(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
