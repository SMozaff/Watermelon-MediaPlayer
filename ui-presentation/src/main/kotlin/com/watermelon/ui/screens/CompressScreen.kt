package com.watermelon.ui.screens

import android.content.ContentResolver
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.watermelon.mediatools.engine.VideoCompressor
import com.watermelon.mediatools.job.MediaJob
import com.watermelon.mediatools.job.MediaJobState
import com.watermelon.ui.components.KeepOrDeleteOriginalDialog
import com.watermelon.ui.components.MediaJobProgressSheet
import com.watermelon.ui.theme.WatermelonShapes
import com.watermelon.ui.theme.WatermelonSpacing
import com.watermelon.ui.viewmodel.CompressViewModel
import com.watermelon.ui.viewmodel.MediaJobsViewModel

/**
 * Per UI_MANIFEST.md §4. Preset cards only -- no raw bitrate/resolution controls exposed,
 * per blueprint's "quick" framing. No size estimate shown (nice-to-have, skipped for v1,
 * per manifest §4).
 */
@UnstableApi
@Composable
fun CompressScreen(
    compressViewModel: CompressViewModel,
    mediaJobsViewModel: MediaJobsViewModel,
    contentResolver: ContentResolver,
    originalFileDeleter: com.watermelon.mediatools.output.OriginalFileDeleter,
    inputUri: Uri,
    originalDisplayName: String,
    isPremiumUnlocked: Boolean,
    onRequestUpsell: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedPreset by remember { mutableStateOf<VideoCompressor.Preset?>(null) }
    var customTargetMb by remember { mutableStateOf("") }
    var activeJobId by remember { mutableStateOf<String?>(null) }
    var pendingDeleteConsent by remember { mutableStateOf(false) }
    var showDiscardChangesDialog by remember { mutableStateOf(false) }
    var showBackgroundExitDialog by remember { mutableStateOf(false) }

    val jobs by mediaJobsViewModel.jobs.collectAsStateWithLifecycle()
    val activeJob: MediaJob? = jobs.find { it.id == activeJobId }
        ?: jobs.lastOrNull { job ->
            val state = job.state
            job.type == com.watermelon.mediatools.job.MediaJobType.COMPRESS &&
                job.inputUri == inputUri.toString() &&
                (state is MediaJobState.Queued ||
                    state is MediaJobState.Running ||
                    (state is MediaJobState.Completed && state.awaitingOriginalFileDecision))
        }
    val parsedTargetMb = customTargetMb.toIntOrNull()
    val context = LocalContext.current
    val sourceDurationMs = remember(inputUri) { readDurationForEstimate(context, inputUri) }
    val customTargetError = when {
        customTargetMb.isNotBlank() && parsedTargetMb == null -> "Enter a whole number of MB."
        parsedTargetMb != null && parsedTargetMb < 2 -> "Choose at least 2 MB so video and audio can be encoded reliably."
        else -> null
    }
    val hasUnsavedConfiguration = selectedPreset != null || customTargetMb.isNotBlank()
    val hasRunningJob = activeJob?.state is MediaJobState.Queued || activeJob?.state is MediaJobState.Running
    val requestExit = {
        when {
            hasRunningJob -> showBackgroundExitDialog = true
            hasUnsavedConfiguration -> showDiscardChangesDialog = true
            else -> onBack()
        }
    }
    BackHandler(onBack = requestExit)

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
                "Compress",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(vertical = WatermelonSpacing.sm),
            )
            Spacer(Modifier.weight(1f))
        }

        Text(
            "Compress video",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )

        Text(
            "Choose a smart preset, or set a target size when you need the output under a specific MB.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        VideoCompressor.Preset.entries.forEach { preset ->
            PresetCard(
                preset = preset,
                isSelected = selectedPreset == preset,
                estimate = estimatedPresetOutput(sourceDurationMs, preset),
                onClick = { selectedPreset = preset }
            )
        }

        Button(
            onClick = {
                val preset = selectedPreset ?: return@Button
                // Premium gating temporarily disabled -- see TrimScreen's identical note.
                activeJobId = compressViewModel.startCompress(inputUri, originalDisplayName, preset)
            },
            enabled = selectedPreset != null,
            shape = WatermelonShapes.control,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Compress with preset")
        }

        OutlinedTextField(
            value = customTargetMb,
            onValueChange = { value -> customTargetMb = value.filter { it.isDigit() }.take(4) },
            label = { Text("Custom target size (MB)") },
            supportingText = {
                Text(
                    customTargetError
                        ?: "Watermelon calculates bitrate from duration, then validates the final size."
                )
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = {
                val targetMb = parsedTargetMb ?: return@Button
                activeJobId = compressViewModel.startTargetSizeCompress(inputUri, originalDisplayName, targetMb)
            },
            enabled = parsedTargetMb != null && parsedTargetMb > 0 && customTargetError == null,
            shape = WatermelonShapes.control,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Compress to target size")
        }
    }

    activeJob?.let { job ->
        val state = job.state
        when {
            state is MediaJobState.Completed && state.awaitingOriginalFileDecision -> {
                KeepOrDeleteOriginalDialog(
                    originalFileName = originalDisplayName,
                    outputFileName = state.outputUri.substringAfterLast('/'),
                    isTrim = false,
                    isPendingSystemConsent = pendingDeleteConsent,
                    compressionSizeBytes = job.sourceSizeBytes?.let { originalSize ->
                        state.outputSizeBytes?.let { outputSize -> originalSize to outputSize }
                    },
                    onKeepOriginal = {
                        mediaJobsViewModel.resolveOriginalFileDecision(job.id, deleteOriginal = false, contentResolver)
                        activeJobId = null
                        onBack()
                    },
                    onDeleteOriginal = {
                        // API 30+ required for MediaStore.createDeleteRequest -- see
                        // OriginalFileDeleter/TrimScreen's identical fix for why this is R,
                        // not Q.
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            pendingDeleteConsent = true
                            originalFileDeleter.requestDelete(job.id, Uri.parse(job.inputUri), contentResolver)
                        } else {
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
                    onContinueInBackground = {
                        activeJobId = null
                        onBack()
                    },
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
            title = { Text("Discard compression changes?") },
            text = { Text("Your selected preset or target size has not been used yet. You can continue editing or discard it and return.") },
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
            title = { Text("Compression will continue") },
            text = {
                Text(
                    "This compression is still running in the background. You can reopen Compress from File actions to inspect its progress."
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

    androidx.compose.runtime.LaunchedEffect(activeJob) {
        val state = activeJob?.state
        if (pendingDeleteConsent && state is MediaJobState.Completed && !state.awaitingOriginalFileDecision) {
            pendingDeleteConsent = false
            activeJobId = null
            onBack()
        }
    }
}

// @UnstableApi required: references VideoCompressor.Preset, a nested type inside the
// @UnstableApi-annotated VideoCompressor class. CompressScreen's own @UnstableApi doesn't
// propagate to this separate private function -- same class of issue caught earlier in
// MediaJobService/MediaJobsViewModel, just discovered here via a real CI lint failure
// instead of proactive audit.
@UnstableApi
@Composable
private fun PresetCard(
    preset: VideoCompressor.Preset,
    isSelected: Boolean,
    estimate: String?,
    onClick: () -> Unit,
) {
    androidx.compose.foundation.layout.Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, WatermelonShapes.card)
            .clickable(onClick = onClick)
            .padding(WatermelonSpacing.md)
    ) {
        Text(
            preset.label,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            preset.description,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = WatermelonSpacing.xs)
        )
        Text(
            estimate ?: "Estimated output is unavailable for this video.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = WatermelonSpacing.xs)
        )
    }
}

private fun readDurationForEstimate(
    context: android.content.Context,
    uri: Uri,
): Long? {
    val retriever = android.media.MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, uri)
        retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull()
    } catch (_: Exception) {
        null
    } finally {
        retriever.release()
    }
}

@UnstableApi
private fun estimatedPresetOutput(
    durationMs: Long?,
    preset: VideoCompressor.Preset,
): String? {
    val durationSeconds = durationMs?.toDouble()?.div(1_000.0) ?: return null
    val bytes = durationSeconds * (preset.videoBitrateBps + preset.audioBitrateBps) / 8.0
    val mb = bytes / (1024.0 * 1024.0)
    val resolution = "up to ${preset.targetShortSidePx}p"
    return "Estimate: about ${"%.1f".format(mb)} MB · $resolution"
}
