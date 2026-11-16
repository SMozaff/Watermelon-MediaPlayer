package com.watermelon.subtitle.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SrtParserCharacterizationTest {

    @Test
    fun parsesBomAndAllSupportedLineEndings() {
        val cases = listOf(
            "\uFEFF1\r\n00:00:01,000 --> 00:00:02,000\r\nfirst" to "first",
            "00:00:01.000 --> 00:00:02.000\nsecond" to "second",
            "00:00:01,000 --> 00:00:02,000\rthird" to "third",
        )
        cases.forEach { (source, expected) ->
            assertEquals(expected, SrtParser.parse(source).cues.single().rawText)
        }
    }

    @Test
    fun acceptsMissingIndicesBlankLineVariationsAndTimingLineSuffixes() {
        val source = """
            00:00:01,1 --> 00:00:02,12 align:start
            one


            00:00:03.123 --> 00:00:04.000
            two
        """.trimIndent()
        val parsed = SrtParser.parse(source)
        assertEquals(listOf("one", "two"), parsed.cues.map { it.rawText })
        assertEquals(listOf(0, 1), parsed.cues.map { it.index })
        assertEquals(1_100L, parsed.cues[0].startMs)
        assertEquals(2_120L, parsed.cues[0].endMs)
        assertEquals(3_123L, parsed.cues[1].startMs)
    }

    @Test
    fun sortsByStartTimeButKeepsOriginalParseIndices() {
        val source = """
            00:00:05,000 --> 00:00:06,000
            later

            00:00:01,000 --> 00:00:02,000
            earlier
        """.trimIndent()
        val cues = SrtParser.parse(source).cues
        assertEquals(listOf("earlier", "later"), cues.map { it.rawText })
        assertEquals(listOf(1, 0), cues.map { it.index })
    }

    @Test
    fun stripsHtmlLikeTagsAndPreservesTheirText() {
        val parsed = SrtParser.parse(
            "00:00:01,000 --> 00:00:02,000\n<i>hello</i> <b>world</b> <font color=\"red\">!</font>"
        )
        assertEquals("hello world !", parsed.cues.single().rawText)
    }

    @Test
    fun skipsEmptyWhitespaceAndMalformedBlocks() {
        val source = """
            00:00:01,000 --> 00:00:02,000


            block without timing

            00:00:03,000 --> 00:00:04,000
              

            00:00:05,000 --> not-a-time
            invalid
        """.trimIndent()
        assertTrue(SrtParser.parse(source).cues.isEmpty())
    }

    @Test
    fun parsesPersianArabicAndMixedDirectionText() {
        val parsed = SrtParser.parse(
            "00:00:01,000 --> 00:00:02,000\nسلام دنیا\n\n" +
                "00:00:03,000 --> 00:00:04,000\nمرحبا بالعالم\n\n" +
                "00:00:05,000 --> 00:00:06,000\nسلام hello"
        )
        assertEquals(3, parsed.cues.size)
        assertTrue(parsed.cues[0].baseRtl)
        assertTrue(parsed.cues[1].baseRtl)
        assertTrue(parsed.cues[2].baseRtl)
        assertEquals("سلام hello", parsed.cues[2].rawText)
        assertTrue(parsed.cues[2].displayText.contains("\u2068hello\u2069"))
    }

    @Test
    fun languageIsPassedThroughAndNullRemainsNull() {
        val source = "00:00:01,000 --> 00:00:02,000\nhello"
        assertEquals("fa", SrtParser.parse(source, "fa").language)
        assertEquals(null, SrtParser.parse(source).language)
    }

    @Test
    fun acceptsOneAndTwoDigitHourFieldsAndLongBodyLines() {
        val longLine = "x".repeat(20_000)
        val source = "1:02:03,4 --> 1:02:04,5\none\n\n" +
            "12:00:00.999 --> 12:00:01.999\n$longLine"
        val cues = SrtParser.parse(source).cues
        assertEquals(3_723_400L, cues[0].startMs)
        assertEquals(3_724_500L, cues[0].endMs)
        assertEquals(43_200_999L, cues[1].startMs)
        assertEquals(20_000, cues[1].rawText.length)
        assertFalse(cues[1].rawText.isEmpty())
    }
}
