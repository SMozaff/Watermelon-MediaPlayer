package com.watermelon.ui.screens

import android.content.ContentResolver
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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
    var activeJobId by remember { mutableStateOf<String?>(null) }
    var pendingDeleteConsent by remember { mutableStateOf(false) }

    val jobs by mediaJobsViewModel.jobs.collectAsStateWithLifecycle()
    val activeJob: MediaJob? = jobs.find { it.id == activeJobId }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(WatermelonSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(WatermelonSpacing.md)
    ) {
        Text(
            "Choose a quality preset",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )

        VideoCompressor.Preset.entries.forEach { preset ->
            PresetCard(
                preset = preset,
                isSelected = selectedPreset == preset,
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
            Text("Compress")
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
                    onDismiss = { activeJobId = null; if (state is MediaJobState.Completed) onBack() },
                )
            }
        }
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
private fun PresetCard(preset: VideoCompressor.Preset, isSelected: Boolean, onClick: () -> Unit) {
    // Uses preset.label directly (added to the enum itself) rather than a separate
    // when-mapping here, so this file doesn't need updating every time presets change --
    // that mapping used to list SMALL/MEDIUM/ORIGINAL_QUALITY by name and would have gone
    // stale the moment the 4-tier redesign (HIGH_QUALITY/MEDIUM/SMALL/TINY) landed.
    androidx.compose.foundation.layout.Box(
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
    }
}
