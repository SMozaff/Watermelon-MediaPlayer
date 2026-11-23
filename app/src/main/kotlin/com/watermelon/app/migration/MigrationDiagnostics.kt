package com.watermelon.app.migration

/**
 * D-4 shadow-mode seam. The Kotlin result is always returned to the caller, so enabling these
 * flags cannot change user-visible behavior. A Rust result is requested only for local debug
 * builds with [MigrationFlags.shadowMode] enabled, and only compared; it is never substituted
 * here. Cutover is a separate Stage 5 decision.
 *
 * No telemetry, upload or non-debug logging is performed. Artifacts stay on the device logcat.
 */
internal class MigrationDiagnostics(private val bridge: RustBridge) {
    fun <T> compare(component: String, kotlinValue: T, rustCall: (RustBridge) -> T): T {
        if (MigrationFlags.shadowMode && MigrationFlags.debugLogging) {
            runCatching { rustCall(bridge) }
                .onSuccess { rust -> RustMigrationLog.compare(component, kotlinValue, rust) }
                .onFailure { error ->
                    RustMigrationLog.unavailable(component, error.message ?: error::class.java.simpleName)
                }
        }
        return kotlinValue
    }

    val isNativeComparisonAvailable: Boolean
        get() = MigrationFlags.shadowMode && MigrationFlags.debugLogging && bridge !== UnavailableRustBridge
}
