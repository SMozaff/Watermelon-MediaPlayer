package com.watermelon.mediatools.output

import android.content.ContentResolver
import android.net.Uri
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import android.provider.MediaStore
import com.watermelon.common.util.FileLogger

private const val TAG = "OriginalFileDeleter"

/**
 * Handles deleting a pre-existing library video that this app didn't itself insert into
 * MediaStore -- the case left unhandled in MediaJobManager.resolveOriginalFileDecision
 * (see that method's doc for why a plain ContentResolver.delete() isn't enough on API 29+).
 *
 * Must be constructed in an Activity's onCreate (registerForActivityResult requires this,
 * per Android's ActivityResultRegistry lifecycle rules -- confirmed via general Android API
 * docs, not Media3-specific). MainActivity should own one instance and call [requestDelete];
 * the result callback then re-invokes MediaJobManager.resolveOriginalFileDecision once the
 * user actually confirms via the system dialog.
 *
 * NOT run on-device. API shape (MediaStore.createDeleteRequest -> IntentSenderRequest ->
 * ActivityResultContracts.StartIntentSenderForResult) confirmed via web search this session,
 * not Context7 (this is platform MediaStore API, not Media3).
 */
class OriginalFileDeleter(
    activity: ComponentActivity,
    private val onResult: (jobId: String, deleted: Boolean) -> Unit,
) {
    private var pendingJobId: String? = null

    private val launcher: ActivityResultLauncher<IntentSenderRequest> =
        activity.registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            val jobId = pendingJobId
            pendingJobId = null
            if (jobId == null) {
                FileLogger.e(TAG, "delete result arrived with no pending jobId")
                return@registerForActivityResult
            }
            val deleted = result.resultCode == android.app.Activity.RESULT_OK
            FileLogger.i(TAG, "delete request result jobId=$jobId deleted=$deleted")
            onResult(jobId, deleted)
        }

    /**
     * Requests deletion of [originalUri] via the system consent dialog. Requires API 30
     * (R) -- MediaStore.createDeleteRequest was introduced in API 30, not 29 as an earlier
     * version of this comment incorrectly stated (a real bug: the SDK_INT guard below used
     * to check Build.VERSION_CODES.Q (29) instead of R (30), which would have thrown
     * NoSuchMethodError on a real API 29 device -- caught via lint's NewApi check reporting
     * "Call requires API level 30 (current min is 23)").
     *
     * Below API 30, ContentResolver.delete() generally works directly for files the app
     * can see without a consent dialog gate -- so this path isn't wired for < R; call
     * MediaJobManager.resolveOriginalFileDecision directly on those OS versions instead.
     */
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.R)
    fun requestDelete(jobId: String, originalUri: Uri, contentResolver: ContentResolver) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            FileLogger.e(TAG, "requestDelete called on API < 30 -- use direct ContentResolver.delete instead")
            return
        }
        pendingJobId = jobId
        val pendingIntent = MediaStore.createDeleteRequest(contentResolver, listOf(originalUri))
        val request = IntentSenderRequest.Builder(pendingIntent.intentSender).build()
        launcher.launch(request)
    }
}
