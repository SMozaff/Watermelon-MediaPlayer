package com.watermelon.subtitle.bidi

import org.junit.Assert.assertEquals
import org.junit.Test

class BidiFormatterTest {

    @Test
    fun trailingNeutralWhitespaceRemainsInsideOppositeDirectionIsolate() {
        // Pin the current behavior. The formatter's inline comment says trailing neutral
        // characters are trimmed out of the isolate, but the current loop consumes them.
        // This test intentionally records the observed output without changing production code.
        assertEquals(
            "שלום \u2068abc \u2069",
            BidiFormatter.format("שלום abc ", baseRtl = true),
        )
    }
}
