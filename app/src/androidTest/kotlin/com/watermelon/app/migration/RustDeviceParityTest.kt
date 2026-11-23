package com.watermelon.app.migration

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.watermelon.common.model.SubtitleCue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import uniffi.watermelon_ffi.bidiFormat
import uniffi.watermelon_ffi.bidiIsRtl
import uniffi.watermelon_ffi.srtParseJson
import uniffi.watermelon_ffi.uniffiEnsureInitialized

/**
 * G1 device evidence after the Rust-only cutover. The expected strings below are copied from
 * the frozen Kotlin-generated oracle in `testdata/golden/*.json`; the Kotlin implementations
 * that produced them were deleted under owner decisions D-7/D-8. No migration flags exist and
 * there is no Kotlin fallback to compare against.
 */
@RunWith(AndroidJUnit4::class)
class RustDeviceParityTest {

    @Test
    fun native_library_loads_on_art() {
        uniffiEnsureInitialized()
        assertTrue(srtParseJson("no cues here", null).contains("cues"))
    }

    @Test
    fun bidi_matches_frozen_oracle() {
        val cases = listOf(
            Triple("Watermelon 123", false, false to "Watermelon 123"),
            Triple("سلام دنیا", true, true to "سلام دنیا"),
            Triple("سلام hello", true, false to "سلام ⁨hello⁩"),
            Triple("hello سلام", false, false to "hello ⁨سلام⁩"),
            Triple("שלום abc ", true, true to "שלום ⁨abc⁩ "),
            Triple("سلام! 123", false, true to "⁨سلام⁩! 123"),
            Triple("", true, false to ""),
        )
        uniffiEnsureInitialized()
        cases.forEach { (text, baseRtl, expected) ->
            assertEquals("isRtl: $text", expected.first, bidiIsRtl(text))
            assertEquals("format: $text", expected.second, bidiFormat(text, baseRtl))
        }
    }

    @Test
    fun srt_parse_matches_frozen_oracle() {
        // Exact `rtl_mixed` case from the frozen Kotlin-generated testdata/golden/srt_parse.json.
        val parsed = RustSrtParser.parse(
            "00:00:01,000 --> 00:00:02,000\nسلام hello\n", "fa",
        )
        assertEquals("fa", parsed.language)
        assertEquals(
            listOf(SubtitleCue(0, 1_000L, 2_000L, "سلام hello", false, "⁨سلام⁩ hello")),
            parsed.cues,
        )
    }

    @Test
    fun production_adapters_are_rust_backed() {
        uniffiEnsureInitialized()
        val subtitle = RustSrtParser.parse(
            "00:00:00,000 --> 00:00:01,000\nhello\n", null,
        )
        val signature = RustActivityBuilder.build(subtitle, 0L, 2_000L, 40)
        assertEquals(0L, signature.startMs)
        assertEquals(40, signature.bucketMs)
        assertEquals(50, signature.occupancy.size)

        // Exact `empty` case from testdata/golden/probe_select.json.
        val empty = RustSrtParser.parse("no cues here", null)
        assertTrue(RustProbeSelector.select(empty, 60_000L, 3).isEmpty())
    }
}
