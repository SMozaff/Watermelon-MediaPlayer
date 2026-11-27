package com.watermelon.mediatools.service

import androidx.annotation.OptIn
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.media3.common.util.UnstableApi
import com.watermelon.common.util.FileLogger
import com.watermelon.mediatools.job.MediaJob
import com.watermelon.mediatools.job.MediaJobManager
import com.watermelon.mediatools.job.MediaJobState
import com.watermelon.mediatools.job.TerminalTransitionTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

private const val TAG = "MediaJobService"
private const val CHANNEL_ID = "media_tools_jobs"
private const val RESULT_CHANNEL_ID = "media_tools_job_results"
private const val NOTIFICATION_ID = 4200 // arbitrary, app-unique; distinct from playback's own id space
private const val ACTION_CANCEL_JOB = "com.watermelon.mediatools.action.CANCEL_JOB"
private const val EXTRA_JOB_ID = "job_id"

/**
 * Foreground service mirroring WatermelonPlaybackService's role for playback: started when a
 * job begins, holds a percentage + cancel notification, stops itself when the job queue is
 * empty. Uses the "dataSync" foreground service type (confirmed via Android docs this
 * session) rather than "mediaPlayback", since this isn't audio/video playback.
 *
 * [jobManagerProvider] is set once by [com.watermelon.app.WatermelonApplication.onCreate]
 * (this app has no DI framework, confirmed by audit — a static provider is the pragmatic
 * stopgap rather than introducing one for this alone). Still a stopgap, not a proper DI
 * setup: fine for this app's single-Application-instance reality, but worth replacing if a
 * real DI framework is ever adopted.
 *
 * On Android 15+ (VANILLA_ICE_CREAM), dataSync foreground services have a 6-hour time limit
 * enforced by the system (onTimeout() callback) -- confirmed via docs. Not handled here;
 * long-running compress jobs on very large files could theoretically hit this, worth
 * revisiting if that becomes a real issue.
 *
 * NOT run on-device.
 *
 * @OptIn(UnstableApi::class) is required on this class because it uses [MediaJobManager] throughout
 * (the field, the provider, and every call site), and MediaJobManager itself is
 * @OptIn(UnstableApi::class)-annotated (it touches Media3 Transformer internals). Lint caught 5 separate
 * UnsafeOptInUsageError instances here before this annotation was added -- each usage needs
 * the opt-in propagated, and annotating the whole class is simpler than annotating every
 * individual member/call site separately.
 */
@OptIn(UnstableApi::class)
class MediaJobService : Service() {

    // Explicitly annotated (not just relying on the class-level @OptIn(UnstableApi::class) above) since
    // a companion object is technically a separate declaration and lint's opt-in
    // propagation into nested objects isn't guaranteed -- being explicit rather than
    // assuming inheritance covers jobManagerProvider's MediaJobManager reference.
    @OptIn(UnstableApi::class)
    companion object {
        /** Must be set once by the app before this service is started. See class doc. */
        var jobManagerProvider: (() -> MediaJobManager)? = null

        fun cancelIntent(context: Context, jobId: String): PendingIntent {
            val intent = Intent(context, MediaJobService::class.java).apply {
                action = ACTION_CANCEL_JOB
                putExtra(EXTRA_JOB_ID, jobId)
            }
            return PendingIntent.getService(
                context, jobId.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        /**
         * Activity intent that lands on the application's Jobs destination (WM-403).
         *
         * Deliberately implicit (action + setPackage) instead of naming MainActivity directly:
         * media-tools is a library module and must not depend on the app module. The app
         * declares the matching intent-filter in its manifest. CLEAR_TOP + SINGLE_TOP make a
         * delivered notification reach the already-running instance through onNewIntent
         * instead of stacking a second copy of the Activity.
         *
         * [focusJobId] is set only for a finished job's result notification, so the user lands
         * on that exact outcome rather than on a generic or empty surface; the ongoing
         * progress notification passes null and opens Jobs itself.
         */
        fun openJobsIntent(context: Context, focusJobId: String?): Intent {
            return Intent(JobsDeepLink.ACTION_OPEN_JOBS).apply {
                setPackage(context.packageName)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                focusJobId?.let { putExtra(JobsDeepLink.EXTRA_FOCUS_JOB_ID, it) }
            }
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob())
    private var observerJob: Job? = null
    private var jobManager: MediaJobManager? = null

    /** Detects each job's single active-to-terminal transition across jobs-flow repaints. */
    private val terminalTracker = TerminalTransitionTracker()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val manager = jobManager ?: jobManagerProvider?.invoke()?.also { jobManager = it }
        if (manager == null) {
            FileLogger.e(TAG, "onStartCommand but jobManagerProvider isn't set -- stopping self")
            stopSelf()
            return START_NOT_STICKY
        }

        if (intent?.action == ACTION_CANCEL_JOB) {
            intent.getStringExtra(EXTRA_JOB_ID)?.let { manager.cancel(it) }
            return START_NOT_STICKY
        }

        if (observerJob == null) {
            observerJob = manager.jobs
                .onEach { jobs -> onJobsChanged(jobs) }
                .launchIn(serviceScope)
        }

        return START_NOT_STICKY
    }

    private fun onJobsChanged(jobs: List<MediaJob>) {
        // Announce terminal outcomes exactly once, before any early return below: this is the
        // only place the service observes job state, and the empty-active branch stops the
        // service (the notifications themselves are posted through NotificationManager, so
        // they outlive this service instance).
        terminalTracker.update(jobs).forEach { job -> notifyResult(job) }

        val active = jobs.filter { it.state is MediaJobState.Queued || it.state is MediaJobState.Running }
        if (active.isEmpty()) {
            FileLogger.i(TAG, "no active jobs -- stopping foreground")
            // stopForeground(int) (STOP_FOREGROUND_REMOVE) requires API 24, but this
            // module's minSdk is 23 (confirmed by a real lint failure: "Call requires API
            // level 24 (current min is 23): android.app.Service#stopForeground [NewApi]").
            // The boolean overload is deprecated but available since API 1 and does the
            // same thing (true = remove the notification), so it's the correct minSdk-safe
            // choice here, not a workaround to revisit later.
            @Suppress("DEPRECATION")
            stopForeground(true)
            stopSelf()
            return
        }
        startForeground(NOTIFICATION_ID, buildNotification(active))
    }

    private fun buildNotification(activeJobs: List<MediaJob>): Notification {
        val primary = activeJobs.first()
        val title = if (activeJobs.size == 1) {
            jobTitle(primary)
        } else {
            "${activeJobs.size} media jobs running"
        }
        val averageProgress = activeJobs.map { it.progressPercent }.average().toInt()
        val content = if (activeJobs.size == 1) {
            "${jobSource(primary)} · ${primary.progressPercent}%"
        } else {
            "${activeJobs.count { it.state is MediaJobState.Running }} running · " +
                "${activeJobs.count { it.state is MediaJobState.Queued }} queued"
        }
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setProgress(100, averageProgress, false)
            .setSmallIcon(com.watermelon.mediatools.R.drawable.ic_notification_watermelon)
            .setOngoing(true)
            // Tapping the live progress notification opens Jobs (the "active outcome" deep
            // link) rather than a generic launcher relaunch.
            .setContentIntent(
                PendingIntent.getActivity(
                    this, NOTIFICATION_ID, openJobsIntent(this, focusJobId = null),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )

        if (activeJobs.size == 1) {
            builder.addAction(0, "Cancel", cancelIntent(this, primary.id))
        } else {
            val inbox = NotificationCompat.InboxStyle()
            activeJobs.take(5).forEach { job ->
                inbox.addLine("${jobTitle(job)} · ${jobSource(job)} · ${job.progressPercent}%")
            }
            builder.setStyle(inbox)
        }
        return builder.build()
    }

    private fun jobTitle(job: MediaJob): String = when (job.type) {
        com.watermelon.mediatools.job.MediaJobType.EXTRACT_AUDIO -> "Extracting audio"
        com.watermelon.mediatools.job.MediaJobType.TRIM -> "Trimming video"
        com.watermelon.mediatools.job.MediaJobType.COMPRESS -> "Compressing video"
    }

    /**
     * Posts one result notification per job that just finished (WM-403: "completion
     * notifications open the relevant item in Jobs"). Its content intent carries the job id,
     * so tapping it deep-links to that exact record in the Jobs destination instead of a
     * generic surface.
     *
     * Posted through NotificationManager rather than as a foreground notification: the service
     * stops itself as soon as no job is active, while a result must stay visible (and tappable)
     * after that. Cancelled jobs never reach here — [TerminalTransitionTracker] filters them.
     */
    private fun notifyResult(job: MediaJob) {
        val state = job.state
        val title = when {
            state is MediaJobState.Failed -> when (job.type) {
                com.watermelon.mediatools.job.MediaJobType.EXTRACT_AUDIO -> "Audio extraction failed"
                com.watermelon.mediatools.job.MediaJobType.TRIM -> "Trim failed"
                com.watermelon.mediatools.job.MediaJobType.COMPRESS -> "Compression failed"
            }
            state is MediaJobState.Completed -> when (job.type) {
                com.watermelon.mediatools.job.MediaJobType.EXTRACT_AUDIO -> "Audio extraction complete"
                com.watermelon.mediatools.job.MediaJobType.TRIM -> "Trim complete"
                com.watermelon.mediatools.job.MediaJobType.COMPRESS -> "Compression complete"
            }
            else -> "Media job finished"
        }
        val source = jobSource(job)
        val contentIntent = PendingIntent.getActivity(
            this, job.id.hashCode(), openJobsIntent(this, focusJobId = job.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(this, RESULT_CHANNEL_ID)
            .setSmallIcon(com.watermelon.mediatools.R.drawable.ic_notification_watermelon)
            .setContentTitle(title)
            .setContentText(source)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setWhen(System.currentTimeMillis())
        if (state is MediaJobState.Failed) {
            // The failure reason is the actionable text; the row in Jobs shows it too.
            builder.setStyle(
                NotificationCompat.BigTextStyle().bigText("$source — ${state.reason}")
            )
        }
        // Notification ids share the app's notification space but are keyed per job so two
        // results never overwrite each other, and a re-post replaces only its own entry.
        getSystemService(NotificationManager::class.java)
            .notify(job.id.hashCode(), builder.build())
        FileLogger.i(TAG, "result notification posted id=${job.id} title=$title")
    }

    private fun jobSource(job: MediaJob): String =
        android.net.Uri.decode(job.inputUri).substringAfterLast('/').ifBlank { "Media file" }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Media processing", NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
            // Results use their own DEFAULT-importance channel: a finished job should be able
            // to alert, while ongoing progress must stay silent (matching the existing
            // progress channel's contract).
            val results = NotificationChannel(
                RESULT_CHANNEL_ID, "Media processing results", NotificationManager.IMPORTANCE_DEFAULT
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(results)
        }
    }

    override fun onDestroy() {
        observerJob?.cancel()
        serviceScope.launch { /* no-op, keeps SupervisorJob API shape consistent if extended later */ }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
