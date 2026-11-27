package com.watermelon.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.watermelon.mediatools.job.MediaJob
import com.watermelon.mediatools.job.MediaJobState
import com.watermelon.mediatools.job.MediaJobType
import com.watermelon.ui.R
import com.watermelon.ui.theme.WatermelonSpacing

/** Jobs that are still performing work and must remain visible outside their initiating screen. */
fun List<MediaJob>.activeMediaJobs(): List<MediaJob> = filter {
    it.state is MediaJobState.Queued || it.state is MediaJobState.Running
}

/**
 * Compact, persistent entry point rendered above normal app content whenever one or more media
 * jobs are active. It deliberately summarizes every active operation rather than silently
 * privileging the first started task.
 *
 * "View jobs" navigates to the Jobs destination (WM-403) rather than opening a second,
 * in-memory-only jobs surface: one place shows active work and the bounded durable history.
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
        TextButton(onClick = onOpenJobs) { Text(stringResource(R.string.jobs_view)) }
    }
}

/**
 * One active job: operation, source, progress and Cancel. Rendered by the Jobs destination's
 * Active section; shared with it (rather than duplicated) so both places stay identical as the
 * row grows.
 */
@Composable
internal fun MediaJobRow(job: MediaJob, onCancel: () -> Unit) {
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
            TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
        }
        LinearProgressIndicator(
            progress = { job.progressPercent.coerceIn(0, 100) / 100f },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = if (job.state is MediaJobState.Queued) stringResource(R.string.jobs_queued)
            else stringResource(R.string.jobs_progress_percent, job.progressPercent),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun jobVerb(type: MediaJobType): String = stringResource(
    when (type) {
        MediaJobType.EXTRACT_AUDIO -> R.string.jobs_verb_extract_audio
        MediaJobType.TRIM -> R.string.jobs_verb_trim
        MediaJobType.COMPRESS -> R.string.jobs_verb_compress
    }
)

/** Past-tense operation label ("Audio extraction", "Trim", "Compression") for terminal rows. */
@Composable
internal fun jobPastTense(type: MediaJobType): String = stringResource(
    when (type) {
        MediaJobType.EXTRACT_AUDIO -> R.string.jobs_past_extract_audio
        MediaJobType.TRIM -> R.string.jobs_past_trim
        MediaJobType.COMPRESS -> R.string.jobs_past_compress
    }
)

@Composable
fun jobSourceLabel(job: MediaJob): String = sourceLabelFor(job.inputUri)

/**
 * Source label for a raw input uri, shared by live jobs and durable history records so a
 * restart cannot change how the same job's source reads.
 */
@Composable
internal fun sourceLabelFor(inputUri: String): String =
    android.net.Uri.decode(inputUri).substringAfterLast('/').ifBlank {
        stringResource(R.string.jobs_source_fallback)
    }

/** Display name of a job's output uri, shared by terminal rows. */
@Composable
internal fun outputNameFor(uri: String): String =
    android.net.Uri.decode(uri).substringAfterLast('/').ifBlank {
        stringResource(R.string.jobs_output_fallback)
    }
