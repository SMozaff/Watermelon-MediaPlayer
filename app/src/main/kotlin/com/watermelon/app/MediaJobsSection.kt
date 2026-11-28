package com.watermelon.app

import android.net.Uri
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.watermelon.ui.components.activeMediaJobs

/**
 * Media-jobs shell surface: the always-visible progress bar while any media-tool job runs,
 * plus the keep-or-delete-original review dialog a completed job can request.
 *
 * The active/recent inspection surface itself moved to the persistent Jobs destination
 * (WM-403): "View jobs" on the bar navigates there, and the destination also shows the bounded
 * durable history after a restart. Keeping only the bar here avoids a second, in-memory-only
 * jobs sheet that could silently disagree with the durable one.
 *
 * Jobs are observed by id rather than by snapshot so the dialog follows every progress/state
 * update emitted by the application-scoped MediaJobManager.
 */
@Composable
internal fun MediaJobsSection(activity: MainActivity, navController: NavHostController) {
    val mediaJobs by activity.deps.mediaJobsViewModel.jobs.collectAsStateWithLifecycle()
    val activeMediaJobs = mediaJobs.activeMediaJobs()
    var globalOriginalDeletePending by mutableStateOf(false)

    if (activeMediaJobs.isNotEmpty()) {
        com.watermelon.ui.components.MediaJobsBar(
            activeJobs = activeMediaJobs,
            onOpenJobs = { navController.navigate(Routes.JOBS) { launchSingleTop = true } },
        )
    }

    // The dialog owns Activity-scoped infrastructure (system consent launcher, ContentResolver),
    // so its trigger lives on the Activity shell. The Jobs destination can request the same
    // review for a record that is still live.
    val reviewOriginalJobId = activity.originalReviewRequest
    val reviewOriginalJob = mediaJobs.find { it.id == reviewOriginalJobId }
    val reviewCompleted =
        reviewOriginalJob?.state as? com.watermelon.mediatools.job.MediaJobState.Completed
    LaunchedEffect(reviewOriginalJobId, reviewCompleted?.awaitingOriginalFileDecision) {
        if (reviewOriginalJobId != null &&
            (reviewCompleted == null || !reviewCompleted.awaitingOriginalFileDecision)
        ) {
            activity.originalReviewRequest = null
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
