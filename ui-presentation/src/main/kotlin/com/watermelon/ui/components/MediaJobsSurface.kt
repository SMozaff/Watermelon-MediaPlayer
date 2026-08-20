package com.watermelon.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.watermelon.mediatools.job.MediaJob
import com.watermelon.mediatools.job.MediaJobState
import com.watermelon.mediatools.job.MediaJobType
import com.watermelon.ui.theme.WatermelonSpacing

/** Jobs that are still performing work and must remain visible outside their initiating screen. */
fun List<MediaJob>.activeMediaJobs(): List<MediaJob> = filter {
    it.state is MediaJobState.Queued || it.state is MediaJobState.Running
}

/**
 * Compact, persistent entry point rendered above normal app content whenever one or more media
 * jobs are active. It deliberately summarizes every active operation rather than silently
 * privileging the first started task.
 */
@Composable
fun MediaJobsBar(
    activeJobs: List<MediaJob>,
    onOpenJobs: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (activeJobs.isEmpty()) return
    val primary = activeJobs.first()
    val headline = if (activeJobs.size == 1) {
        "${jobVerb(primary.type)}: ${jobSourceLabel(primary)}"
    } else {
        "${activeJobs.size} media jobs running"
    }
    val progressLabel = if (activeJobs.size == 1) {
        "${primary.progressPercent}% complete"
    } else {
        "${activeJobs.count { it.state is MediaJobState.Running }} running · " +
            "${activeJobs.count { it.state is MediaJobState.Queued }} queued"
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = WatermelonSpacing.sm, vertical = WatermelonSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(WatermelonSpacing.sm),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = headline,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Text(
                text = progressLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        TextButton(onClick = onOpenJobs) { Text("View jobs") }
    }
}

/** Application-scoped active and recent media-job view. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaJobsSheet(
    jobs: List<MediaJob>,
    onCancel: (MediaJob) -> Unit,
    onDismissJob: (MediaJob) -> Unit,
    onOpenResult: (MediaJob) -> Unit,
    onOpenSettings: () -> Unit,
    onReviewOriginal: (MediaJob) -> Unit,
    onDismiss: () -> Unit,
) {
    val activeJobs = jobs.activeMediaJobs()
    val recentJobs = jobs.filterNot { it in activeJobs }.takeLast(4).reversed()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = "Media jobs",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = WatermelonSpacing.lg, vertical = WatermelonSpacing.sm),
        )
        if (activeJobs.isEmpty() && recentJobs.isEmpty()) {
            Text(
                text = "No media jobs have started in this session.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = WatermelonSpacing.lg, vertical = WatermelonSpacing.md),
            )
        }
        if (activeJobs.isNotEmpty()) {
            Text(
                text = "ACTIVE",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = WatermelonSpacing.lg, vertical = WatermelonSpacing.xs),
            )
            activeJobs.forEachIndexed { index, job ->
                MediaJobRow(job = job, onCancel = { onCancel(job) })
                if (index < activeJobs.lastIndex || recentJobs.isNotEmpty()) HorizontalDivider()
            }
        }
        if (recentJobs.isNotEmpty()) {
            Text(
                text = "RECENT",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = WatermelonSpacing.lg, vertical = WatermelonSpacing.xs),
            )
            recentJobs.forEachIndexed { index, job ->
                MediaJobOutcomeRow(
                    job = job,
                    onDismiss = { onDismissJob(job) },
                    onOpenResult = { onOpenResult(job) },
                    onOpenSettings = onOpenSettings,
                    onReviewOriginal = { onReviewOriginal(job) },
                )
                if (index < recentJobs.lastIndex) HorizontalDivider()
            }
        }
        Spacer(modifier = Modifier.padding(bottom = WatermelonSpacing.lg))
    }
}

@Composable
private fun MediaJobRow(job: MediaJob, onCancel: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = WatermelonSpacing.lg, vertical = WatermelonSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(WatermelonSpacing.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = jobVerb(job.type),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = jobSourceLabel(job),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            TextButton(onClick = onCancel) { Text("Cancel") }
        }
        LinearProgressIndicator(
            progress = { job.progressPercent.coerceIn(0, 100) / 100f },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = if (job.state is MediaJobState.Queued) "Queued" else "${job.progressPercent}% complete",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MediaJobOutcomeRow(
    job: MediaJob,
    onDismiss: () -> Unit,
    onOpenResult: () -> Unit,
    onOpenSettings: () -> Unit,
    onReviewOriginal: () -> Unit,
) {
    val state = job.state
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = WatermelonSpacing.lg, vertical = WatermelonSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(WatermelonSpacing.xs),
    ) {
        Text(
            text = when (state) {
                is MediaJobState.Completed -> "${jobPastTense(job.type)} complete"
                is MediaJobState.Failed -> "${jobVerb(job.type)} failed"
                is MediaJobState.Cancelled -> "${jobVerb(job.type)} cancelled"
                else -> jobVerb(job.type)
            },
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = jobSourceLabel(job),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        when (state) {
            is MediaJobState.Completed -> {
                Text(
                    text = "Output: ${outputName(state.outputUri)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (state.awaitingOriginalFileDecision) {
                    TextButton(onClick = onReviewOriginal) { Text("Review original") }
                } else {
                    Row {
                        TextButton(onClick = onOpenResult) { Text("Open result") }
                        if (job.type == MediaJobType.EXTRACT_AUDIO) {
                            TextButton(onClick = onOpenSettings) { Text("Media tool settings") }
                        }
                        TextButton(onClick = onDismiss) { Text("Dismiss") }
                    }
                }
            }
            is MediaJobState.Failed -> {
                Text(
                    text = state.reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Row {
                    TextButton(onClick = onOpenSettings) { Text("Change settings") }
                    TextButton(onClick = onDismiss) { Text("Dismiss") }
                }
            }
            is MediaJobState.Cancelled -> {
                TextButton(onClick = onDismiss) { Text("Dismiss") }
            }
            else -> Unit
        }
    }
}

fun jobVerb(type: MediaJobType): String = when (type) {
    MediaJobType.EXTRACT_AUDIO -> "Extracting audio"
    MediaJobType.TRIM -> "Trimming video"
    MediaJobType.COMPRESS -> "Compressing video"
}

private fun jobPastTense(type: MediaJobType): String = when (type) {
    MediaJobType.EXTRACT_AUDIO -> "Audio extraction"
    MediaJobType.TRIM -> "Trim"
    MediaJobType.COMPRESS -> "Compression"
}

fun jobSourceLabel(job: MediaJob): String =
    android.net.Uri.decode(job.inputUri).substringAfterLast('/').ifBlank { "Media file" }

private fun outputName(uri: String): String =
    android.net.Uri.decode(uri).substringAfterLast('/').ifBlank { "Media output" }
