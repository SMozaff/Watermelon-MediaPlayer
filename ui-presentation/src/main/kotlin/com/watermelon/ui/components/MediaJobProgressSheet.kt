package com.watermelon.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.watermelon.mediatools.job.MediaJob
import com.watermelon.mediatools.job.MediaJobState
import com.watermelon.mediatools.job.MediaJobType
import com.watermelon.ui.theme.PlayerColors
import com.watermelon.ui.theme.WatermelonShapes
import com.watermelon.ui.theme.WatermelonSpacing

/**
 * Shared progress/notification UI for Extract Audio / Trim / Compress jobs (blueprint
 * Phase 1 requirement -- one component, reused everywhere). Mirrors SleepTimerDialog's
 * visual style (Dialog + Box + WatermelonShapes.sheet).
 *
 * This is a pure display component: it renders whatever [job] currently is and calls back
 * on user actions. The caller (a screen or MediaJobsViewModel-backed host) is responsible
 * for collecting the actual MediaJob out of MediaJobManager.jobs by id and for deciding
 * when to stop showing this (e.g. dismiss after Cancelled, or once Completed's
 * awaitingOriginalFileDecision has been resolved to false and the caller wants to close).
 *
 * @param job the current job state to render.
 * @param onCancel called when the user taps Cancel while Queued/Running.
 * @param onDismiss called when the user dismisses a Failed/simple-success state.
 *   Not called for Completed jobs where awaitingOriginalFileDecision is true -- the caller
 *   should show KeepOrDeleteOriginalDialog instead in that case (see that component).
 */
@Composable
fun MediaJobProgressSheet(
    job: MediaJob,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = { /* no-op while running; Failed/simple-success dismiss via button */ },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .background(MaterialTheme.colorScheme.surface, WatermelonShapes.sheet)
                .padding(WatermelonSpacing.lg),
            contentAlignment = Alignment.Center
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(WatermelonSpacing.md),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    jobTypeLabel(job.type),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )

                when (val state = job.state) {
                    is MediaJobState.Queued, is MediaJobState.Running -> {
                        LinearProgressIndicator(
                            progress = { job.progressPercent / 100f },
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            "${job.progressPercent}%",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = onCancel,
                            shape = WatermelonShapes.control,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Cancel")
                        }
                    }

                    is MediaJobState.Completed -> {
                        // Caller is expected to show KeepOrDeleteOriginalDialog instead when
                        // awaitingOriginalFileDecision is true (trim/compress) -- this branch
                        // covers the simple-success case (extract audio, or after the
                        // decision has already been resolved).
                        Text(
                            "Done",
                            style = MaterialTheme.typography.bodyLarge,
                            color = PlayerColors.current.textPrimary
                        )
                        Button(
                            onClick = onDismiss,
                            shape = WatermelonShapes.control,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = PlayerColors.current.textPrimary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("OK")
                        }
                    }

                    is MediaJobState.Failed -> {
                        Text(
                            state.reason,
                            style = MaterialTheme.typography.bodyMedium,
                            color = PlayerColors.current.warning
                        )
                        Button(
                            onClick = onDismiss,
                            shape = WatermelonShapes.control,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Dismiss")
                        }
                    }

                    is MediaJobState.Cancelled -> {
                        // Per UI manifest: dismiss immediately, no lingering UI. Caller
                        // should stop showing this sheet on Cancelled; nothing to render.
                    }
                }
            }
        }
    }
}

private fun jobTypeLabel(type: MediaJobType): String = when (type) {
    MediaJobType.EXTRACT_AUDIO -> "Extracting Audio"
    MediaJobType.TRIM -> "Trimming Video"
    MediaJobType.COMPRESS -> "Compressing Video"
}
