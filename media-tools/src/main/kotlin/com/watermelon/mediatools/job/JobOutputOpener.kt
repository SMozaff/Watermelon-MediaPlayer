package com.watermelon.mediatools.job

/**
 * Opens a finished job's output through injected probes so every failure mode is handled —
 * and unit-testable — without an Android framework (WM-403: "every action fails safely when an
 * output is no longer present or no compatible handler exists").
 *
 * The three outcomes, all non-throwing:
 * - [Result.MISSING_OUTPUT]: no uri, a blank uri, a file/MediaStore entry that no longer
 *   resolves, or a probe that threw — reported before any activity is launched.
 * - [Result.NO_HANDLER]: the output exists but nothing on the device can open it
 *   (ActivityNotFoundException from startActivity, or an opaque content resolver failure).
 * - [Result.OPENED]: handed to the system successfully.
 *
 * The application shell maps both non-[Result.OPENED] outcomes to the same "could not open"
 * message; the distinction exists for logging/tests, not for user-facing copy.
 */
class JobOutputOpener(
    private val outputExists: (String) -> Boolean,
    private val openOutput: (String) -> Unit,
) {

    enum class Result { OPENED, MISSING_OUTPUT, NO_HANDLER }

    fun open(outputUri: String?): Result {
        if (outputUri.isNullOrBlank()) return Result.MISSING_OUTPUT
        val present = runCatching { outputExists(outputUri) }.getOrDefault(false)
        if (!present) return Result.MISSING_OUTPUT
        return try {
            openOutput(outputUri)
            Result.OPENED
        } catch (e: Exception) {
            // ActivityNotFoundException and friends: a real, expected device condition
            // (app uninstalled, no viewer for the mime type), never a crash.
            com.watermelon.common.util.FileLogger.w(
                "JobOutputOpener",
                "no handler for output $outputUri: ${e::class.java.simpleName}"
            )
            Result.NO_HANDLER
        }
    }
}
