package com.watermelon.mediatools.subtitle.sync

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Structural self-check for the PCM accumulator golden-vector file. */
class SpeechAccumulatorGoldenVectorSelfCheckTest {

    @Test
    fun speechAccumulatorFileIsPresentNonEmptyAndWellFormed() {
        // Skipped in ordinary CI; only the migration-golden-vectors workflow supplies the dir.
        assumeTrue(System.getProperty(DIR_PROPERTY) != null)
        val path = System.getProperty(DIR_PROPERTY) ?: error("Set -D$DIR_PROPERTY=<absolute directory>")
        val file = File(File(path), "speech_accumulator.json")
        assertTrue("Missing speech_accumulator.json", file.isFile)
        val text = file.readText(Charsets.UTF_8)
        assertTrue("speech_accumulator.json is empty", text.isNotBlank())
        assertTrue(text.trimStart().startsWith("{"))
        assertTrue(text.trimEnd().endsWith("}"))
        assertTrue(text.contains("\"component\":\"speech_accumulator\""))
        assertTrue(text.contains("\"cases\":["))
        assertTrue(text.contains("\"id\":\""))
        assertTrue(text.contains("\"decimal\":\""))
        assertTrue(text.contains("\"bits\":\""))
    }

    private companion object {
        const val DIR_PROPERTY = "watermelon.goldenDir"
    }
}
