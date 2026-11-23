package com.watermelon.subtitle.golden

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Structural self-check for the nine Kotlin-generated golden-vector files. */
class SubtitleGoldenVectorSelfCheckTest {

    @Test
    fun allGeneratedFilesArePresentNonEmptyAndWellFormed() {
        // Skipped in ordinary CI; only the migration-golden-vectors workflow supplies the dir.
        assumeTrue(System.getProperty(GoldenJson.DIR_PROPERTY) != null)
        val dir = GoldenJson.dir()
        COMPONENTS.forEach { (name, hasFloats) ->
            val file = File(dir, "$name.json")
            assertTrue("Missing $name.json", file.isFile)
            val text = file.readText(Charsets.UTF_8)
            assertTrue("$name.json is empty", text.isNotBlank())
            assertTrue("$name.json must be a JSON object", text.trimStart().startsWith("{"))
            assertTrue("$name.json must end with a JSON object", text.trimEnd().endsWith("}"))
            assertTrue("$name.json must declare its component", text.contains("\"component\":\"$name\""))
            assertTrue("$name.json must declare cases", text.contains("\"cases\":["))
            assertTrue("$name.json has no cases", text.contains("\"id\":\""))
            if (hasFloats) {
                assertTrue("$name.json must record float decimals", text.contains("\"decimal\":\""))
                assertTrue("$name.json must record raw float bits", text.contains("\"bits\":\""))
            }
        }
    }

    private companion object {
        val COMPONENTS = listOf(
            "srt_parse" to false,
            "bidi_format" to false,
            "hash_opensubtitles" to false,
            "time_mapper" to false,
            "drift_corrector" to false,
            "correlator" to true,
            "consensus" to true,
            "probe_select" to true,
            "activity_builder" to true,
        )
    }
}
