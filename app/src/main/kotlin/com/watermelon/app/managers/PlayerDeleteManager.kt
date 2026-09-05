package com.watermelon.app.managers

import android.app.Activity
import android.content.ContentResolver
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Handles player-initiated video deletion with system consent.
 */
class PlayerDeleteManager(
    private val activity: Activity,
    private val contentResolver: ContentResolver,
    private val scope: CoroutineScope,
) {

    private data class PlayerDeleteTarget(val uri: String, val displayName: String)

    private sealed interface PlayerDeleteOutcome {
        data class Deleted(val target: PlayerDeleteTarget) : PlayerDeleteOutcome
        data class Cancelled(val target: PlayerDeleteTarget) : PlayerDeleteOutcome
        data class Failed(val target: PlayerDeleteTarget, val reason: String) : PlayerDeleteOutcome
    }

    private var pendingPlayerDelete: PlayerDeleteTarget? = null
    private var playerDeleteOutcome: PlayerDeleteOutcome? = null

    private val playerDeleteLauncher = activity.registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val target = pendingPlayerDelete
        pendingPlayerDelete = null
        if (target == null) return@registerForActivityResult
        playerDeleteOutcome = if (result.resultCode == Activity.RESULT_OK) {
            PlayerDeleteOutcome.Deleted(target)
        } else {
            PlayerDeleteOutcome.Cancelled(target)
        }
    }

    fun requestPlayerDelete(targetUri: String, targetDisplayName: String) {
        val target = PlayerDeleteTarget(targetUri, targetDisplayName)
        val mediaUri = runCatching {
            val id = android.content.ContentUris.parseId(Uri.parse(target.uri))
            android.content.ContentUris.withAppendedId(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                id
            )
        }.getOrElse { error ->
            playerDeleteOutcome = PlayerDeleteOutcome.Failed(
                target,
                "This video is no longer available in the media library."
            )
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                val request = MediaStore.createDeleteRequest(contentResolver, listOf(mediaUri))
                pendingPlayerDelete = target
                playerDeleteLauncher.launch(
                    androidx.activity.result.IntentSenderRequest.Builder(request.intentSender).build()
                )
            }.onFailure { error ->
                pendingPlayerDelete = null
                playerDeleteOutcome = PlayerDeleteOutcome.Failed(
                    target,
                    error.message ?: "Android could not start the delete request."
                )
            }
            return
        }

        scope.launch {
            try {
                val rowsDeleted = withContext(Dispatchers.IO) {
                    contentResolver.delete(mediaUri, null, null)
                }
                pendingPlayerDelete = null
                playerDeleteOutcome = if (rowsDeleted > 0) {
                    PlayerDeleteOutcome.Deleted(target)
                } else {
                    PlayerDeleteOutcome.Failed(target, "Watermelon could not delete this video.")
                }
            } catch (error: SecurityException) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                    error is android.app.RecoverableSecurityException
                ) {
                    runCatching {
                        pendingPlayerDelete = target
                        playerDeleteLauncher.launch(
                            androidx.activity.result.IntentSenderRequest.Builder(
                                error.userAction.actionIntent.intentSender
                            ).build()
                        )
                    }.onFailure { launchError ->
                        pendingPlayerDelete = null
                        playerDeleteOutcome = PlayerDeleteOutcome.Failed(
                            target,
                            launchError.message ?: "Android could not start the delete request."
                        )
                    }
                    return@launch
                }
                pendingPlayerDelete = null
                playerDeleteOutcome = PlayerDeleteOutcome.Failed(
                    target,
                    error.message ?: "Watermelon does not have permission to delete this video."
                )
            } catch (error: Throwable) {
                pendingPlayerDelete = null
                playerDeleteOutcome = PlayerDeleteOutcome.Failed(
                    target,
                    error.message ?: "Watermelon could not delete this video."
                )
            }
        }
    }

    fun getPendingDeleteTarget(): PlayerDeleteTarget? = pendingPlayerDelete
    fun getDeleteOutcome(): PlayerDeleteOutcome? = playerDeleteOutcome
    fun clearDeleteOutcome() { playerDeleteOutcome = null }
}