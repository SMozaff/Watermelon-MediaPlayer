package com.watermelon.app.migration

import android.util.Log

/**
 * Local-only comparison logging for shadow mode (D-4). No telemetry, upload or non-debug
 * logging is performed. Calls are no-ops unless the local opt-in BuildConfig flag is set.
 */
internal object RustMigrationLog {
    private const val TAG = "RustMigration"

    fun compare(component: String, kotlinValue: Any?, rustValue: Any?) {
        if (!MigrationFlags.debugLogging) return
        Log.d(TAG, "$component kotlin=$kotlinValue rust=$rustValue")
    }

    fun unavailable(component: String, reason: String) {
        if (!MigrationFlags.debugLogging) return
        Log.d(TAG, "$component rust unavailable: $reason")
    }
}
