package com.watermelon.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.watermelon.mediatools.job.MediaJob
import com.watermelon.ui.components.KeepOrDeleteOriginalDialog

/**
 * Progress sheet for extract audio, trim, and compress jobs.
 *
 * Shows job type, progress percentage, and allows cancellation.
 * Called from screens when a job is active and needs user intervention.
 *
 * @param job The current MediaJob instance
 * @param onCancel Callback when user cancels the job
 * @param onDismiss Callback when user dismisses the job
 */
@Composable
fun MediaJobProgressSheet(
    job: MediaJob,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = { /* no-op while running; dismissed via button */ },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(composer.color.surface, composer.color.sheet)
                .padding(composer.spacing.lg),
            contentAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(composer.spacing.md),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    jobTypeLabel(job.type),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )

                when (val state = job.state) {
                    is MediaJobState.Queued or is MediaJobState.Running -> {
                        LinearProgressIndicator(
                            progress = job.progressPercent / 100f,
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "${job.progressPercent}% · ${jobProgressLabel(job.type)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = onCancel,
                            shape = composer.shapes.control,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Cancel")
                        }
                        TextButton(
                            onClick = onDismiss,
                            shape = composer.shapes.control,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("OK")
                        }
                    }

                    is MediaJobState.Completed -> {
                        Text(
                            if (job.type == MediaJobType.EXTRACT_AUDIO) "MP3 ready" else "Done",
                            style = MaterialTheme.typography.bodyLarge,
                            color = composer.color.textPrimary
                        )
                        if (job.type == MediaJobType.EXTRACT_AUDIO && job.outputLocation != null) {
                            Text(
                                "Saved to $job.outputLocation",
                                style = MaterialTheme.typography.bodyMedium,
                                color = composer.color.onSurface
                            )
                            Text(
                                "You can change this folder in Settings › Media tools.",
                                style = MaterialTheme.typography.bodySmall,
                                color = composer.color.onSurfaceVariant
                            )
                            TextButton(
                                onClick = onDismiss,
                                shape = composer.shapes.control,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = composer.color.primary,
                                    contentColor = composer.color.onPrimary
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("OK")
                            }
                        }
                    }

                    is MediaJobState.Failed -> {
                        Text(
                            if (job.type == MediaJobType.EXTRACT_AUDIO)
                                "MP3 conversion failed"
                            else
                                "Couldn't finish",
                            style = MaterialTheme.typography.bodyLarge,
                            color = composer.color.textPrimary
                        )
                        Text(
                            state.reason,
                            style = MaterialTheme.typography.bodyMedium,
                            color = composer.color.textWarning
                        )
                        Button(
                            onClick = onDismiss,
                            shape = composer.shapes.control,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = composer.color.surfaceVariant,
                                contentColor = composer.color.onSurfaceVariant
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Dismiss")
                        }
                    }

                    is MediaJobState.Cancelled -> {
                        // Dismiss immediately for cancelled jobs
                    }
                }
            }
        }
    }
}

private fun jobTypeLabel(type: MediaJobType): String = when (type) {
    MediaJobType.EXTRACT_AUDIO -> "Converting to MP3"
    MediaJobType.TRIM -> "Trimming Video"
    MediaJobType.COMPRESS -> "Compressing Video"
}

private fun jobProgressLabel(type: MediaJobType): String = when (type) {
    MediaJobType.EXTRACT_AUDIO -> "Encoding audio"
    MediaJobType.TRIM -> "Processing video"
    MediaJobType.COMPRESS -> "Compressing video"
}