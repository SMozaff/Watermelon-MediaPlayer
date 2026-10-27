package com.watermelon.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.watermelon.ui.components.activeMediaJobs

/**
 * Media-jobs surface, extracted from MainActivity in Phase 2b: the always-visible
 * progress bar while any media-tool job runs, the full jobs sheet, and the
 * keep-or-delete-original review dialog a completed job can request.
 *
 * Jobs are kept by id rather than by a snapshot of MediaJob so the sheet follows
 * every progress/state update emitted by the application-scoped MediaJobManager.
 */
@Composable
internal fun MediaJobsSection(activity: MainActivity, navController: NavHostController) {
    val mediaJobs by activity.deps.mediaJobsViewModel.jobs.collectAsStateWithLifecycle()
    val activeMediaJobs = remember(mediaJobs) {
        mediaJobs.activeMediaJobs()
    }
    var showJobsSheet by remember { mutableStateOf(false) }
    // Kept by id rather than by a snapshot of MediaJob so the review dialog follows
    // every state update of the job it refers to.
    var reviewOriginalJobId by remember { mutableStateOf<String?>(null) }
    var globalOriginalDeletePending by remember { mutableStateOf(false) }

    if (activeMediaJobs.isNotEmpty()) {
        com.watermelon.ui.components.MediaJobsBar(
            activeJobs = activeMediaJobs,
            onOpenJobs = { showJobsSheet = true },
        )
    }

    if (showJobsSheet) {
        com.watermelon.ui.components.MediaJobsSheet(
            jobs = mediaJobs,
            onCancel = { job -> activity.deps.mediaJobsViewModel.cancel(job.id) },
            onDismissJob = { job -> activity.deps.mediaJobsViewModel.dismiss(job.id) },
            onOpenResult = { job ->
                val completed = job.state as? com.watermelon.mediatools.job.MediaJobState.Completed
                val outputUri = completed?.outputUri
                if (outputUri == null) return@MediaJobsSheet
                runCatching {
                    val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(
                            Uri.parse(outputUri),
                            activity.contentResolver.getType(Uri.parse(outputUri))
                        )
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    activity.startActivity(viewIntent)
                }.onFailure {
                    android.widget.Toast.makeText(
                        activity,
                        activity.getString(com.watermelon.ui.R.string.jobs_error_open_output),
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
                }
            },
            onOpenSettings = {
                showJobsSheet = false
                navController.navigate(Routes.SETTINGS) { launchSingleTop = true }
            },
            onReviewOriginal = { job ->
                showJobsSheet = false
                reviewOriginalJobId = job.id
            },
            onDismiss = { showJobsSheet = false },
        )
    }

    val reviewOriginalJob = mediaJobs.find { it.id == reviewOriginalJobId }
    val reviewCompleted = reviewOriginalJob?.state as? com.watermelon.mediatools.job.MediaJobState.Completed
    LaunchedEffect(reviewOriginalJobId, reviewCompleted?.awaitingOriginalFileDecision) {
        if (reviewOriginalJobId != null &&
            (reviewCompleted == null || !reviewCompleted.awaitingOriginalFileDecision)
        ) {
            reviewOriginalJobId = null
            globalOriginalDeletePending = false
        }
    }
    if (reviewOriginalJob != null && reviewCompleted?.awaitingOriginalFileDecision == true) {
        com.watermelon.ui.components.KeepOrDeleteOriginalDialog(
            originalFileName = com.watermelon.ui.components.jobSourceLabel(reviewOriginalJob),
            outputFileName = Uri.decode(reviewCompleted.outputUri).substringAfterLast('/'),
            isTrim = reviewOriginalJob.type == com.watermelon.mediatools.job.MediaJobType.TRIM,
            isPendingSystemConsent = globalOriginalDeletePending,
            actualTrimRangeMs = reviewCompleted.actualTrimRangeMs,
            compressionSizeBytes = reviewOriginalJob.sourceSizeBytes?.let { originalSize ->
                reviewCompleted.outputSizeBytes?.let { outputSize -> originalSize to outputSize }
            },
            onKeepOriginal = {
                activity.deps.mediaJobsViewModel.resolveOriginalFileDecision(
                    reviewOriginalJob.id,
                    deleteOriginal = false,
                    contentResolver = activity.contentResolver,
                )
            },
            onDeleteOriginal = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    globalOriginalDeletePending = true
                    activity.originalFileDeleter.requestDelete(
                        reviewOriginalJob.id,
                        Uri.parse(reviewOriginalJob.inputUri),
                        activity.contentResolver,
                    )
                } else {
                    activity.deps.mediaJobsViewModel.resolveOriginalFileDecision(
                        reviewOriginalJob.id,
                        deleteOriginal = true,
                        contentResolver = activity.contentResolver,
                    )
                }
            },
        )
    }
}
