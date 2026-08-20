package com.watermelon.mediatools.service

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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

private const val TAG = "MediaJobService"
private const val CHANNEL_ID = "media_tools_jobs"
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
 * @UnstableApi is required on this class because it uses [MediaJobManager] throughout
 * (the field, the provider, and every call site), and MediaJobManager itself is
 * @UnstableApi-annotated (it touches Media3 Transformer internals). Lint caught 5 separate
 * UnsafeOptInUsageError instances here before this annotation was added -- each usage needs
 * the opt-in propagated, and annotating the whole class is simpler than annotating every
 * individual member/call site separately.
 */
@UnstableApi
class MediaJobService : Service() {

    // Explicitly annotated (not just relying on the class-level @UnstableApi above) since
    // a companion object is technically a separate declaration and lint's opt-in
    // propagation into nested objects isn't guaranteed -- being explicit rather than
    // assuming inheritance covers jobManagerProvider's MediaJobManager reference.
    @UnstableApi
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
    }

    private val serviceScope = CoroutineScope(SupervisorJob())
    private var observerJob: Job? = null
    private var jobManager: MediaJobManager? = null

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
            .setSmallIcon(android.R.drawable.stat_sys_download) // placeholder -- app should supply a real icon
            .setOngoing(true)

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

    private fun jobSource(job: MediaJob): String =
        android.net.Uri.decode(job.inputUri).substringAfterLast('/').ifBlank { "Media file" }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Media processing", NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        observerJob?.cancel()
        serviceScope.launch { /* no-op, keeps SupervisorJob API shape consistent if extended later */ }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
