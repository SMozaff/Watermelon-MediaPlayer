package com.watermelon.app.migration

import com.watermelon.app.BuildConfig

/**
 * Compile-time migration flags for the Kotlin-to-Rust cutover.
 *
 * All flags default to false in CI and release builds. They can only be enabled for a local
 * debug build with a Gradle property, which keeps the migration reversible without adding a
 * user-facing setting or a network-controlled switch.
 */
internal object MigrationFlags {
    val useRustCore: Boolean get() = BuildConfig.USE_RUST_CORE
    val shadowMode: Boolean get() = BuildConfig.RUST_SHADOW_MODE
    val debugLogging: Boolean get() = BuildConfig.DEBUG && BuildConfig.RUST_DEBUG_LOGGING

    val environment: String
        get() = if (BuildConfig.DEBUG) "debug" else "release"
}
