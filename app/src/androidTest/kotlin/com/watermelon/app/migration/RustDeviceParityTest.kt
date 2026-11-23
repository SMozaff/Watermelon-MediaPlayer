package com.watermelon.app.migration

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.watermelon.subtitle.bidi.BidiFormatter
import com.watermelon.subtitle.parser.SrtParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import uniffi.watermelon_ffi.uniffiEnsureInitialized

/**
 * G1 device evidence: the Rust core runs on Android ART and is compared to the Kotlin oracle
 * with the D-3 rule (exact strings for these components). This test is built with
 * `-Pwatermelon.useRustCore=true -Pwatermelon.rustShadowMode=true
 * -Pwatermelon.rustDebugLogging=true` by migration-device-parity.yml.
 */
@RunWith(AndroidJUnit4::class)
class RustDeviceParityTest {

    @Test
    fun native_library_loads_on_art() {
        uniffiEnsureInitialized()
        assertTrue(MigrationFlags.useRustCore)
        assertTrue(MigrationFlags.shadowMode)
    }

    @Test
    fun bidi_functions_match_kotlin() {
        val samples = listOf(
            "hello world",
            "سلام دنیا",
            "mixed english و فارسی end",
            "abc (xyz) 123",
        )
        samples.forEach { text ->
            val rtl = BidiFormatter.isRtl(text)
            assertEquals("isRtl: $text", rtl, RustCutover.isRtl(text))
            assertEquals("format: $text", BidiFormatter.format(text, rtl), RustCutover.format(text, rtl))
        }
    }

    @Test
    fun srt_parse_matches_kotlin() {
        val srt = """
            1
            00:00:01,000 --> 00:00:02,500
            hello world

            2
            00:00:03,250 --> 00:00:05,000
            سلام دنیا
            second line (end)
        """.trimIndent()
        val expected = SrtParser.parseKotlin(srt, "fa")
        val actual = RustCutover.parse(srt, "fa")
        assertEquals(expected, actual)
        assertEquals(2, actual.cues.size)
    }
}
