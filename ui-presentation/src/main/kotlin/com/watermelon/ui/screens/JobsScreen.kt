package com.watermelon.ui.screens

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.watermelon.mediatools.job.MediaJob
import com.watermelon.mediatools.job.MediaJobHistoryEntry
import com.watermelon.mediatools.job.MediaJobState
import com.watermelon.mediatools.job.MediaJobTerminalState
import com.watermelon.ui.R
import com.watermelon.ui.components.MediaJobRow
import com.watermelon.ui.components.WatermelonHeader
import com.watermelon.ui.components.activeMediaJobs
import com.watermelon.ui.components.jobPastTense
import com.watermelon.ui.components.jobVerb
import com.watermelon.ui.components.outputNameFor
import com.watermelon.ui.components.sourceLabelFor
import com.watermelon.ui.theme.WatermelonSpacing

/**
 * The persistent **Jobs** destination (WM-403): one surface for active work and for the
 * bounded, durable record of recent terminal outcomes.
 *
 * - Active rows come straight from [MediaJobManager][com.watermelon.mediatools.job.MediaJobManager]'s
 *   StateFlow, so progress stays live and a job can never appear twice after navigation or
 *   rotation — there is exactly one source of truth for "what is running".
 * - Recent rows come from the persisted history, which is why they survive the active bar
 *   disappearing *and* a normal app restart.
 * - Terminal actions degrade instead of failing: Open result is only offered for a record that
 *   still has an output and the shell re-checks it before launching (see JobOutputOpener);
 *   Review original is only offered while the original-file decision is still live in memory,
 *   because the dialog it opens needs the running job to resolve against.
 *
 * @param focusJobId item a notification deep-linked to; that row is scrolled into view and
 *   highlighted so the user lands on the outcome they were just told about.
 */
@Composable
fun JobsScreen(
    liveJobs: List<MediaJob>,
    recentEntries: List<MediaJobHistoryEntry>,
    focusJobId: String?,
    onCancel: (String) -> Unit,
    onDismiss: (String) -> Unit,
    onClearHistory: () -> Unit,
    onOpenResult: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onReviewOriginal: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeJobs = liveJobs.activeMediaJobs()
    val isEmpty = activeJobs.isEmpty() && recentEntries.isEmpty()
    val listState = rememberLazyListState()
    val focusIndex = jobsFocusIndex(
        activeIds = activeJobs.map { it.id },
        recentIds = recentEntries.map { it.id },
        focusJobId = focusJobId,
    )
    LaunchedEffect(focusIndex) {
        if (focusIndex != null) listState.scrollToItem(focusIndex)
    }

    Column(modifier = modifier.fillMaxSize()) {
        WatermelonHeader(
            title = stringResource(R.string.jobs_title),
            showBackButton = true,
            onBackClick = onBack,
            modifier = Modifier.fillMaxWidth(),
        )

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
        ) {
            if (isEmpty) {
                item(key = "empty") {
                    Text(
                        text = stringResource(R.string.jobs_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(
                            horizontal = WatermelonSpacing.lg,
                            vertical = WatermelonSpacing.md,
                        ),
                    )
                }
            }

            if (activeJobs.isNotEmpty()) {
                item(key = "active-header") {
                    SectionLabel(text = stringResource(R.string.jobs_section_active))
                }
                items(activeJobs, key = { "active-${it.id}" }) { job ->
                    MediaJobRow(job = job, onCancel = { onCancel(job.id) })
                    HorizontalDivider()
                }
            }

            if (recentEntries.isNotEmpty()) {
                item(key = "recent-header") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = WatermelonSpacing.lg),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.jobs_section_recent),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = onClearHistory) {
                            Text(stringResource(R.string.jobs_clear_history))
                        }
                    }
                }
                items(recentEntries, key = { "recent-${it.id}" }) { entry ->
                    MediaJobHistoryRow(
                        entry = entry,
                        highlighted = entry.id == focusJobId,
                        liveJob = liveJobs.firstOrNull { it.id == entry.id },
                        onDismiss = { onDismiss(entry.id) },
                        onOpenResult = { entry.outputUri?.let(onOpenResult) },
                        onOpenSettings = onOpenSettings,
                        onReviewOriginal = { onReviewOriginal(entry.id) },
                    )
                    HorizontalDivider()
                }
            }

            item(key = "bottom-spacer") { Spacer(modifier = Modifier.padding(bottom = WatermelonSpacing.lg)) }
        }
    }
}

/**
 * Index of the deep-linked item within [JobsScreen]'s lazy list, or null when there is nothing
 * to focus. Mirrors the item order exactly — optional Active header, active rows, Recent header,
 * recent rows — so a notification lands the user on the row they were told about rather than
 * at the top of an unrelated surface.
 */
internal fun jobsFocusIndex(
    activeIds: List<String>,
    recentIds: List<String>,
    focusJobId: String?,
): Int? {
    if (focusJobId == null) return null
    var index = 0
    if (activeIds.isNotEmpty()) {
        index++ // Active section header
        val position = activeIds.indexOf(focusJobId)
        if (position >= 0) return index + position
        index += activeIds.size
    }
    if (recentIds.isNotEmpty()) {
        index++ // Recent section header
        val position = recentIds.indexOf(focusJobId)
        if (position >= 0) return index + position
    }
    return null
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(
            horizontal = WatermelonSpacing.lg,
            vertical = WatermelonSpacing.xs,
        ),
    )
}

/**
 * One durable terminal outcome: status, source, when it finished, output/failure detail, and
 * only the actions that are actually safe for that record.
 */
@Composable
private fun MediaJobHistoryRow(
    entry: MediaJobHistoryEntry,
    highlighted: Boolean,
    liveJob: MediaJob?,
    onDismiss: () -> Unit,
    onOpenResult: () -> Unit,
    onOpenSettings: () -> Unit,
    onReviewOriginal: () -> Unit,
) {
    // The keep-or-delete-original dialog resolves against the live job in MediaJobManager, so
    // after a restart (record present, job gone) the decision can no longer be reviewed and
    // the action must not be offered — a stale button that could not resolve would be a lie.
    val decisionLive = liveJob != null &&
        (liveJob.state as? MediaJobState.Completed)?.awaitingOriginalFileDecision == true

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (highlighted) MaterialTheme.colorScheme.primaryContainer
                else Color.Transparent
            )
            .padding(horizontal = WatermelonSpacing.lg, vertical = WatermelonSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(WatermelonSpacing.xs),
    ) {
        Text(
            text = when (entry.terminalState) {
                MediaJobTerminalState.COMPLETED ->
                    stringResource(R.string.jobs_status_complete, jobPastTense(entry.type))
                MediaJobTerminalState.FAILED ->
                    stringResource(R.string.jobs_status_failed, jobVerb(entry.type))
                MediaJobTerminalState.CANCELLED ->
                    stringResource(R.string.jobs_status_cancelled, jobVerb(entry.type))
            },
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = sourceLabelFor(entry.inputUri),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Text(
            text = DateUtils.getRelativeTimeSpanString(entry.finishedAtEpochMs).toString(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        when (entry.terminalState) {
            MediaJobTerminalState.COMPLETED -> {
                entry.outputUri?.let { outputUri ->
                    Text(
                        text = stringResource(R.string.jobs_output_name, outputNameFor(outputUri)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!decisionLive) {
                        TextButton(onClick = onOpenResult) {
                            Text(stringResource(R.string.jobs_open_result))
                        }
                    }
                }
                if (decisionLive) {
                    TextButton(onClick = onReviewOriginal) {
                        Text(stringResource(R.string.jobs_review_original))
                    }
                }
                DismissButton(onDismiss = onDismiss)
            }
            MediaJobTerminalState.FAILED -> {
                entry.failureReason?.let { reason ->
                    Text(
                        text = reason,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Row {
                    TextButton(onClick = onOpenSettings) {
                        Text(stringResource(R.string.jobs_change_settings))
                    }
                    DismissButton(onDismiss = onDismiss)
                }
            }
            MediaJobTerminalState.CANCELLED -> DismissButton(onDismiss = onDismiss)
        }
    }
}

@Composable
private fun DismissButton(onDismiss: () -> Unit) {
    TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_dismiss)) }
}
