package com.watermelon.ui.screens

import androidx.annotation.OptIn
import android.content.ContentResolver
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.watermelon.mediatools.engine.VideoCompressor
import com.watermelon.mediatools.job.MediaJob
import com.watermelon.mediatools.job.MediaJobState
import com.watermelon.ui.R
import com.watermelon.ui.components.KeepOrDeleteOriginalDialog
import com.watermelon.ui.components.MediaJobProgressSheet
import com.watermelon.ui.theme.WatermelonGlass
import com.watermelon.ui.theme.WatermelonShapes
import com.watermelon.ui.theme.WatermelonSpacing
import com.watermelon.ui.viewmodel.CompressViewModel
import com.watermelon.ui.viewmodel.MediaJobsViewModel

/**
 * Per UI_MANIFEST.md §4. Preset cards only -- no raw bitrate/resolution controls exposed,
 * per blueprint's "quick" framing. No size estimate shown (nice-to-have, skipped for v1,
 * per manifest §4).
 */
@OptIn(UnstableApi::class)
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
        customTargetMb.isNotBlank() && parsedTargetMb == null -> stringResource(R.string.compress_error_whole_number)
        parsedTargetMb != null && parsedTargetMb < 2 -> stringResource(R.string.compress_error_min_size)
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
            TextButton(onClick = requestExit) { Text(stringResource(R.string.action_back)) }
            Spacer(Modifier.weight(1f))
            Text(
                stringResource(R.string.compress_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(vertical = WatermelonSpacing.sm),
            )
            Spacer(Modifier.weight(1f))
        }

        Text(
            stringResource(R.string.compress_headline),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )

        Text(
            stringResource(R.string.compress_intro),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        VideoCompressor.Preset.entries.forEach { preset ->
            PresetCard(
                preset = preset,
                isSelected = selectedPreset == preset,
                estimateMb = estimatedPresetOutputMb(sourceDurationMs, preset),
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
            Text(stringResource(R.string.compress_with_preset))
        }

        OutlinedTextField(
            value = customTargetMb,
            onValueChange = { value -> customTargetMb = value.filter { it.isDigit() }.take(4) },
            label = { Text(stringResource(R.string.compress_custom_target_label)) },
            supportingText = {
                Text(
                    customTargetError
                        ?: stringResource(R.string.compress_bitrate_hint)
                )
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = WatermelonGlass.controlSurface(),
                unfocusedContainerColor = WatermelonGlass.controlSurface(),
                focusedBorderColor = WatermelonGlass.highlightColor(),
                unfocusedBorderColor = WatermelonGlass.borderColor(),
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
            )
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
            Text(stringResource(R.string.compress_to_target))
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
            containerColor = WatermelonGlass.surfaceFor(WatermelonGlass.Role.Modal),
            tonalElevation = 0.dp,
            onDismissRequest = { showDiscardChangesDialog = false },
            title = { Text(stringResource(R.string.compress_discard_title)) },
            text = { Text(stringResource(R.string.compress_discard_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardChangesDialog = false
                    onBack()
                }) { Text(stringResource(R.string.action_discard_changes)) }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardChangesDialog = false }) { Text(stringResource(R.string.action_continue_editing)) }
            },
        )
    }

    if (showBackgroundExitDialog) {
        AlertDialog(
            containerColor = WatermelonGlass.surfaceFor(WatermelonGlass.Role.Modal),
            tonalElevation = 0.dp,
            onDismissRequest = { showBackgroundExitDialog = false },
            title = { Text(stringResource(R.string.compress_continue_title)) },
            text = {
                Text(
                    stringResource(R.string.compress_continue_body)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showBackgroundExitDialog = false
                    activeJobId = null
                    onBack()
                }) { Text(stringResource(R.string.action_keep_running)) }
            },
            dismissButton = {
                TextButton(onClick = { showBackgroundExitDialog = false }) { Text(stringResource(R.string.action_stay)) }
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

// @OptIn(UnstableApi::class) required: references VideoCompressor.Preset, a nested type inside the
// @OptIn(UnstableApi::class)-annotated VideoCompressor class. CompressScreen's own @OptIn(UnstableApi::class) doesn't
// propagate to this separate private function -- same class of issue caught earlier in
// MediaJobService/MediaJobsViewModel, just discovered here via a real CI lint failure
// instead of proactive audit.
@OptIn(UnstableApi::class)
@Composable
private fun PresetCard(
    preset: VideoCompressor.Preset,
    isSelected: Boolean,
    estimateMb: Double?,
    onClick: () -> Unit,
) {
    androidx.compose.foundation.layout.Column(
        modifier = Modifier
            .fillMaxWidth()
             .background(if (isSelected) WatermelonGlass.controlSurface() else WatermelonGlass.cardSurface(), WatermelonShapes.card)
            .border(1.dp, if (isSelected) WatermelonGlass.highlightColor() else WatermelonGlass.borderColor(), WatermelonShapes.card)
            .clickable(onClick = onClick)
            .padding(WatermelonSpacing.md)
    ) {
        Text(
            stringResource(presetLabelRes(preset)),
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            stringResource(presetDescriptionRes(preset)),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = WatermelonSpacing.xs)
        )
        val estimateText = estimateMb?.let { mb ->
            stringResource(R.string.compress_estimate, mb, preset.targetShortSidePx)
        } ?: stringResource(R.string.compress_estimate_unavailable)
        Text(
            estimateText,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = WatermelonSpacing.xs)
        )
    }
}

private fun presetLabelRes(preset: VideoCompressor.Preset): Int = when (preset) {
    VideoCompressor.Preset.BEST_QUALITY -> R.string.compress_preset_best_quality
    VideoCompressor.Preset.BALANCED -> R.string.compress_preset_balanced
    VideoCompressor.Preset.SMALL_FILE -> R.string.compress_preset_small_file
    VideoCompressor.Preset.SHARE_READY -> R.string.compress_preset_share_ready
}

private fun presetDescriptionRes(preset: VideoCompressor.Preset): Int = when (preset) {
    VideoCompressor.Preset.BEST_QUALITY -> R.string.compress_preset_best_quality_desc
    VideoCompressor.Preset.BALANCED -> R.string.compress_preset_balanced_desc
    VideoCompressor.Preset.SMALL_FILE -> R.string.compress_preset_small_file_desc
    VideoCompressor.Preset.SHARE_READY -> R.string.compress_preset_share_ready_desc
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

@OptIn(UnstableApi::class)
private fun estimatedPresetOutputMb(
    durationMs: Long?,
    preset: VideoCompressor.Preset,
): Double? {
    val durationSeconds = durationMs?.toDouble()?.div(1_000.0) ?: return null
    val bytes = durationSeconds * (preset.videoBitrateBps + preset.audioBitrateBps) / 8.0
    return bytes / (1024.0 * 1024.0)
}
