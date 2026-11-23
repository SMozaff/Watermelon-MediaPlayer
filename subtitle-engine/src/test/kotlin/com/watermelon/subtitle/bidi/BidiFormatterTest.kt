package com.watermelon.subtitle.bidi

import org.junit.Assert.assertEquals
import org.junit.Test

class BidiFormatterTest {

    @Test
    fun trailingNeutralWhitespaceStaysOutsideOppositeDirectionIsolate() {
        // Behavior change (2026-11-23): the inline comment now matches the implementation.
        // Trailing neutral characters are appended after PDI rather than trapped inside it.
        assertEquals(
            "שלום \u2068abc\u2069 ",
            BidiFormatter.format("שלום abc ", baseRtl = true),
        )
    }
}
