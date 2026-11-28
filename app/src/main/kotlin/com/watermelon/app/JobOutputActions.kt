package com.watermelon.app

import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.widget.Toast
import com.watermelon.mediatools.job.JobOutputOpener
import java.io.File

/**
 * Handles "Open result" from the Jobs destination (WM-403) for both live jobs and durable
 * history records.
 *
 * The actual safety rule lives in [JobOutputOpener] so it can be unit-tested without Android
 * APIs; this shell supplies the two device-specific probes (does the MediaStore item or file
 * still exist? is there a viewer for its mime type?) and turns either failure outcome into a
 * short message instead of an ActivityNotFoundException crash.
 */
internal fun MainActivity.openJobOutput(outputUri: String?) {
    val opener = JobOutputOpener(
        outputExists = { uri -> outputExists(uri) },
        openOutput = { uri -> startActivity(viewIntentFor(uri)) },
    )
    if (opener.open(outputUri) != JobOutputOpener.Result.OPENED) {
        Toast.makeText(
            this,
            getString(com.watermelon.ui.R.string.jobs_error_open_output),
            Toast.LENGTH_SHORT,
        ).show()
    }
}

/**
 * Best-effort existence check for a published output. Content URIs come from MediaStore (the
 * user may have deleted the item since the job finished); file URIs come from legacy paths.
 * A resolver that throws (stale permission, removed storage volume) is treated as "missing",
 * never as an exception through the UI.
 */
private fun MainActivity.outputExists(uri: String): Boolean {
    val parsed = Uri.parse(uri)
    return when (parsed.scheme) {
        "file", null -> File(parsed.path ?: return false).exists()
        else -> runCatching {
            contentResolver.query(
                parsed,
                arrayOf(MediaStore.MediaColumns._ID),
                null,
                null,
                null,
            )?.use { cursor -> cursor.moveToFirst() } ?: false
        }.getOrDefault(false)
    }
}

private fun MainActivity.viewIntentFor(uri: String): Intent {
    val parsed = Uri.parse(uri)
    return Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(parsed, contentResolver.getType(parsed))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
