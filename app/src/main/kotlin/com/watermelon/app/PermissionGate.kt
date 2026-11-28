package com.watermelon.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

private const val KEY_NOTIFICATION_PERMISSION_ASKED = "notification_permission_asked"

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
    private val activity: ComponentActivity,
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

    private val notificationPrefs = activity.getSharedPreferences(
        "watermelon_prefs", Context.MODE_PRIVATE
    )
    private val notificationLauncher = activity.registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* Non-blocking: denial only hides job notifications, never locks the library. */ }

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
        // Ask for notifications only once the media dialog has resolved: two simultaneous
        // permission launchers on one Activity would race for the same dialog slot.
        requestNotificationPermissionIfNeeded()
    }

    /**
     * API 33+ notification permission for MediaJobService's progress/result notifications
     * (WM-403). Deliberately separate from [requiredPermissions] and gated by a one-shot flag:
     * denying notifications must not lock the user out of the library, and must not be nagged
     * again on every start. A user who declines can still enable them in system settings.
     */
    fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (notificationPrefs.getBoolean(KEY_NOTIFICATION_PERMISSION_ASKED, false)) return
        if (ContextCompat.checkSelfPermission(
                activity, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            notificationPrefs.edit().putBoolean(KEY_NOTIFICATION_PERMISSION_ASKED, true).apply()
            return
        }
        notificationPrefs.edit().putBoolean(KEY_NOTIFICATION_PERMISSION_ASKED, true).apply()
        notificationLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
    }

    /** Startup check: refresh the library when access already exists, otherwise ask. */
    fun checkOrRequest() {
        granted = requiredPermissions.all { perm ->
            ContextCompat.checkSelfPermission(activity, perm) == PackageManager.PERMISSION_GRANTED
        }
        if (granted) {
            onGranted()
            requestNotificationPermissionIfNeeded()
        } else {
            launcher.launch(requiredPermissions)
        }
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
