package com.watermelon.mediatools.job

import org.junit.Assert.assertEquals
import org.junit.Test

/** Missing-output recovery coverage (WM-403) without Android Intents or ContentResolvers. */
class JobOutputOpenerTest {

    @Test
    fun `an existing output with a compatible handler is opened`() {
        var opened: String? = null
        val opener = JobOutputOpener(
            outputExists = { true },
            openOutput = { opened = it },
        )

        assertEquals(JobOutputOpener.Result.OPENED, opener.open("content://media/1.mp3"))
        assertEquals("content://media/1.mp3", opened)
    }

    @Test
    fun `a deleted output is reported missing without launching anything`() {
        var opened = false
        val opener = JobOutputOpener(
            outputExists = { false },
            openOutput = { opened = true },
        )

        assertEquals(JobOutputOpener.Result.MISSING_OUTPUT, opener.open("content://media/gone.mp4"))
        assertEquals(false, opened)
    }

    @Test
    fun `blank and absent output references are safe`() {
        val opener = JobOutputOpener(outputExists = { true }, openOutput = {})

        assertEquals(JobOutputOpener.Result.MISSING_OUTPUT, opener.open(null))
        assertEquals(JobOutputOpener.Result.MISSING_OUTPUT, opener.open("  "))
    }

    @Test
    fun `a throwing existence probe is treated as a missing output`() {
        val opener = JobOutputOpener(
            outputExists = { throw SecurityException("permission revoked") },
            openOutput = {},
        )

        assertEquals(JobOutputOpener.Result.MISSING_OUTPUT, opener.open("content://media/old.mp4"))
    }

    @Test
    fun `a missing viewer is reported instead of throwing`() {
        // A plain exception stands in for ActivityNotFoundException: android.* classes are
        // stubs in JVM unit tests and cannot be constructed there.
        val opener = JobOutputOpener(
            outputExists = { true },
            openOutput = { throw IllegalStateException("no viewer for this mime type") },
        )

        assertEquals(JobOutputOpener.Result.NO_HANDLER, opener.open("content://media/odd.bin"))
    }
}
