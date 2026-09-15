package com.watermelon.app

import androidx.activity.runCatching

/**
 * Extension function for safe stop and release of MediaCodec resources.
 * 
 * Duplicate pattern found in 2 files:
 *   VhsReverseSound.kt:78-79
 *   SparseSpeechProbeSource.kt:185-187
 * 
 * Original code pattern:
 *   runCatching { resource?.stop() }
 *   runCatching { resource?.release() }
 */
fun MediaCodec?.safeStopAndRelease() {
    runCatching { this?.stop() }
    runCatching { this?.release() }
}

/**
 * Convenience function for running multiple resource cleanup operations.
 * 
 * Usage:
 *   runCatching {
 *       codec.safeStopAndRelease()
 *       // other cleanup...
 *   }
 */
fun <T> runCatchingResource(
    operation: () -> T,
    onSuccess: (T) -> Unit = {},
    onFailure: (Throwable) -> Unit = {}
): T {
    return runCatching(operation)
        .onSuccess { onSuccess(it) }
        .onFailure { onFailure(it) }
        .getOrElse {
            throw IllegalStateException("Resource operation failed without message")
        }
}