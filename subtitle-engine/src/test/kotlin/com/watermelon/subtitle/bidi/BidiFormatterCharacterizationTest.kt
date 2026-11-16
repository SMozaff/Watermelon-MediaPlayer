package com.watermelon.subtitle.bidi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BidiFormatterCharacterizationTest {

    @Test
    fun detectsPureLeftToRightAndRightToLeftText() {
        assertFalse(BidiFormatter.isRtl("Watermelon 123"))
        assertTrue(BidiFormatter.isRtl("سلام دنیا"))
        assertFalse(BidiFormatter.isRtl(""))
    }

    @Test
    fun leavesTextUnchangedWhenNoOppositeDirectionRunExists() {
        assertEquals("Watermelon 123!", BidiFormatter.format("Watermelon 123!", baseRtl = false))
        assertEquals("سلام دنیا", BidiFormatter.format("سلام دنیا", baseRtl = true))
        assertEquals("", BidiFormatter.format("", baseRtl = true))
    }

    @Test
    fun isolatesEmbeddedOppositeDirectionRuns() {
        assertEquals("سلام \u2068hello\u2069", BidiFormatter.format("سلام hello", baseRtl = true))
        assertEquals("hello \u2068سلام\u2069", BidiFormatter.format("hello سلام", baseRtl = false))
    }

    @Test
    fun neutralPunctuationAndDigitsAttachAccordingToTheCurrentRunLoop() {
        assertEquals("سلام \u2068123!\u2069", BidiFormatter.format("سلام 123!", baseRtl = true))
        assertEquals("\u2068سلام!\u2069 123", BidiFormatter.format("سلام! 123", baseRtl = false))
    }

    @Test
    fun trailingNeutralWhitespaceRemainsInsideOppositeDirectionIsolate() {
        assertEquals("שלום \u2068abc \u2069", BidiFormatter.format("שלום abc ", baseRtl = true))
    }
}
