package com.watermelon.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Deep-link focus coverage (WM-403): "open the relevant item in Jobs" must scroll to that
 * item's lazy-list index, including the section headers that precede it. Kept as a pure
 * function so the index contract is testable without a Compose test rule.
 */
class JobsFocusIndexTest {

    @Test
    fun `no focus id means no scroll`() {
        assertNull(jobsFocusIndex(listOf("a"), listOf("b"), null))
    }

    @Test
    fun `an active row sits after the Active section header`() {
        assertEquals(2, jobsFocusIndex(listOf("a", "b"), listOf("c"), "b"))
        assertEquals(1, jobsFocusIndex(listOf("a", "b"), listOf("c"), "a"))
    }

    @Test
    fun `a recent row sits after both section headers when work is active`() {
        assertEquals(5, jobsFocusIndex(listOf("a", "b"), listOf("c", "d"), "d"))
    }

    @Test
    fun `a recent row is at index one when there is no active section`() {
        assertEquals(1, jobsFocusIndex(emptyList(), listOf("c", "d"), "c"))
        assertEquals(2, jobsFocusIndex(emptyList(), listOf("c", "d"), "d"))
    }

    @Test
    fun `an unknown focus id has nothing to scroll to`() {
        assertNull(jobsFocusIndex(listOf("a"), listOf("b"), "missing"))
    }
}
