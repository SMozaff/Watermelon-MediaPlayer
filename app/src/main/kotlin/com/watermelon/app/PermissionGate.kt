package com.watermelon.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Owns the media-permission flow, extracted from MainActivity in Phase 2b.
 *
 * The launcher must be registered unconditionally during Activity initialization
 * (Android's constraint, not a design choice here), so this class is constructed
 * as a plain field of MainActivity and registers in its own init block.
 *
 * [granted] and [permanentlyDenied] are Compose state so the app shell recomposes
 * when a grant arrives from either path that can deliver one: the system dialog
 * callback (checkOrRequest) or a return from system settings (recheckAfterStart —
 * which fires no launcher callback at all).
 */
internal class PermissionGate(
    activity: ComponentActivity,
    private val onGranted: () -> Unit
) {
    var granted by mutableStateOf(false)
        private set
    var permanentlyDenied by mutableStateOf(false)
        private set

    val requiredPermissions: Array<String> = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
            arrayOf(Manifest.permission.READ_MEDIA_VIDEO)
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    private val launcher = activity.registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        granted = results.values.all { it }
        if (granted) {
            permanentlyDenied = false
            onGranted()
        } else {
            // After a declined request, the system no longer shows the permission dialog
            // for permanently-denied permissions. The only recovery path then is the app's
            // settings page, which the prompt surfaces via openPermissionSettings().
            permanentlyDenied = requiredPermissions.none { perm ->
                ActivityCompat.shouldShowRequestPermissionRationale(activity, perm)
            }
        }
    }

    /** Startup check: refresh the library when access already exists, otherwise ask. */
    fun checkOrRequest() {
        granted = requiredPermissions.all { perm ->
            ContextCompat.checkSelfPermission(activity, perm) == PackageManager.PERMISSION_GRANTED
        }
        if (granted) onGranted() else launcher.launch(requiredPermissions)
    }

    /**
     * Re-check on every start: the user may have granted access from system settings
     * after a permanent denial, a path that fires no launcher callback.
     */
    fun recheckAfterStart() {
        if (!granted && requiredPermissions.all { perm ->
                ContextCompat.checkSelfPermission(activity, perm) == PackageManager.PERMISSION_GRANTED
            }
        ) {
            granted = true
            permanentlyDenied = false
            onGranted()
        }
    }

    /** Re-opens the system permission dialog (used by the prompt's primary action). */
    fun launch() {
        launcher.launch(requiredPermissions)
    }
}
