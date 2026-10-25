package com.kingzcheung.xime.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DoubleSpacePunctuationTest {

    @Test
    fun `second space within window replaces the first space`() {
        val first = 1_000L
        assertTrue(
            DoubleSpacePunctuation.shouldReplace(
                enabled = true,
                previousChar = " ",
                lastSpaceAt = first,
                now = first + 200L,
            )
        )
    }

    @Test
    fun `window boundary is inclusive`() {
        val first = 1_000L
        assertTrue(
            DoubleSpacePunctuation.shouldReplace(
                true, " ", first, first + DoubleSpacePunctuation.TIMEOUT_MS
            )
        )
        assertFalse(
            DoubleSpacePunctuation.shouldReplace(
                true, " ", first, first + DoubleSpacePunctuation.TIMEOUT_MS + 1L
            )
        )
    }

    @Test
    fun `rejects disabled, no previous space, or no first space`() {
        assertFalse(DoubleSpacePunctuation.shouldReplace(false, " ", 1_000L, 1_100L))
        assertFalse(DoubleSpacePunctuation.shouldReplace(true, null, 1_000L, 1_100L))
        assertFalse(DoubleSpacePunctuation.shouldReplace(true, "a", 1_000L, 1_100L))
        assertFalse(DoubleSpacePunctuation.shouldReplace(true, " ", 0L, 1_100L))
        assertFalse(DoubleSpacePunctuation.shouldReplace(true, " ", 2_000L, 1_100L))
    }
}
