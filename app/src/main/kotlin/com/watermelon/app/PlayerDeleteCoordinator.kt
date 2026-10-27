package com.watermelon.app

import android.content.ContentUris
import android.content.IntentSenderRequest
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.watermelon.common.util.FileLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * One media item queued for the "Delete from device?" confirmation flow, plus the
 * sealed outcome the flow resolves to. Extracted from MainActivity in Phase 2b.
 */
internal data class PlayerDeleteTarget(val uri: String, val displayName: String)

internal sealed interface PlayerDeleteOutcome {
    data class Deleted(val target: PlayerDeleteTarget) : PlayerDeleteOutcome
    data class Cancelled(val target: PlayerDeleteTarget) : PlayerDeleteOutcome
    data class Failed(val target: PlayerDeleteTarget, val reason: String) : PlayerDeleteOutcome
}

/**
 * Drives the system delete flow: the in-app confirmation dialog state, the
 * MediaStore delete request (Android R+), the legacy direct delete with
 * RecoverableSecurityException recovery (Q), and the outcome surfaced to the UI
 * as Compose state.
 *
 * The launcher must be registered during Activity initialization, so this class is
 * constructed in MainActivity.onCreate (matching the timing the code had before
 * extraction) and registers in its own init block.
 */
internal class PlayerDeleteCoordinator(private val activity: ComponentActivity) {

    var pendingTarget by mutableStateOf<PlayerDeleteTarget?>(null)
    var showDialog by mutableStateOf(false)
    var outcome by mutableStateOf<PlayerDeleteOutcome?>(null)

    private val launcher: ActivityResultLauncher<IntentSenderRequest> =
        activity.registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult()
        ) { result ->
            val target = pendingTarget
            pendingTarget = null
            if (target == null) {
                FileLogger.e("Delete", "player delete result arrived with no pending target")
                return@registerForActivityResult
            }
            outcome = if (result.resultCode == android.app.Activity.RESULT_OK) {
                PlayerDeleteOutcome.Deleted(target)
            } else {
                PlayerDeleteOutcome.Cancelled(target)
            }
        }

    /**
     * Starts the deletion of [target]. On Android R+ this goes through
     * MediaStore.createDeleteRequest (system consent dialog); older versions delete
     * directly and escalate to the recoverable-security dialog when the system says so.
     * Every failure path resolves to [PlayerDeleteOutcome.Failed] with a localized
     * reason, never a raw exception message.
     */
    fun request(target: PlayerDeleteTarget) {
        val mediaUri = runCatching {
            val id = ContentUris.parseId(Uri.parse(target.uri))
            ContentUris.withAppendedId(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                id
            )
        }.getOrElse {
            outcome = PlayerDeleteOutcome.Failed(
                target,
                activity.getString(R.string.delete_error_unavailable)
            )
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                val request = MediaStore.createDeleteRequest(
                    activity.contentResolver,
                    listOf(mediaUri)
                )
                launcher.launch(
                    IntentSenderRequest.Builder(request.intentSender).build()
                )
            }.onFailure { error ->
                pendingTarget = null
                outcome = PlayerDeleteOutcome.Failed(
                    target,
                    error.message ?: activity.getString(R.string.delete_error_start_request)
                )
            }
            return
        }

        activity.lifecycleScope.launch {
            try {
                val rowsDeleted = withContext(Dispatchers.IO) {
                    activity.contentResolver.delete(mediaUri, null, null)
                }
                pendingTarget = null
                outcome = if (rowsDeleted > 0) {
                    PlayerDeleteOutcome.Deleted(target)
                } else {
                    PlayerDeleteOutcome.Failed(
                        target,
                        activity.getString(R.string.delete_error_failed)
                    )
                }
            } catch (error: SecurityException) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                    error is android.app.RecoverableSecurityException
                ) {
                    runCatching {
                        launcher.launch(
                            IntentSenderRequest.Builder(
                                error.userAction.actionIntent.intentSender
                            ).build()
                        )
                    }.onFailure { launchError ->
                        pendingTarget = null
                        outcome = PlayerDeleteOutcome.Failed(
                            target,
                            launchError.message ?: activity.getString(R.string.delete_error_start_request)
                        )
                    }
                    return@launch
                }
                pendingTarget = null
                outcome = PlayerDeleteOutcome.Failed(
                    target,
                    error.message ?: activity.getString(R.string.delete_error_permission)
                )
            } catch (error: Throwable) {
                pendingTarget = null
                outcome = PlayerDeleteOutcome.Failed(
                    target,
                    error.message ?: activity.getString(R.string.delete_error_failed)
                )
            }
        }
    }
}
