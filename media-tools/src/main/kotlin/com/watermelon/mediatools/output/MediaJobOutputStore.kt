package com.watermelon.mediatools.output

import android.net.Uri
import com.watermelon.mediatools.job.MediaJobType

/**
 * The output-storage surface [com.watermelon.mediatools.job.MediaJobManager] actually uses:
 * stage an output path, publish a finished file into MediaStore, delete a staging file.
 *
 * It exists as an interface for one reason (WM-403): [OutputFileStore] needs a real Android
 * [android.content.Context] (MediaStore + filesDir), so it cannot be constructed in a JVM unit
 * test, which made every MediaJobManager state-transition test unrunnable. Tests supply a
 * fake implementing this interface instead; production keeps passing the one real
 * implementation, [OutputFileStore], unchanged.
 *
 * Deliberately not in common-interfaces: it is a media-tools internal contract, and this repo's
 * interface-implementation gate only tracks common-interfaces.
 */
interface MediaJobOutputStore {
    /** App-private staging path for [type]'s output before it's published to MediaStore. */
    fun stagingPathFor(type: MediaJobType, displayName: String): String

    /**
     * Publishes the finished staging file and returns its content Uri, or null when publishing
     * failed (MediaJobManager turns null into a terminal Failed state).
     */
    fun publish(type: MediaJobType, stagingPath: String, displayName: String): Uri?

    /** Deletes an orphaned staging file after a failed or cancelled job. */
    fun deleteStaging(stagingPath: String)
}
