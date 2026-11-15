package com.kingzcheung.xime.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 单手模式的几何换算：留白只加在一侧，键盘宽度不变。 */
class OneHandedModeTest {

    @Test
    fun offKeepsUserMargins() {
        assertEquals(4 to 6, OneHandedMode.margins(OneHandedMode.OFF, 400, 4, 6))
    }

    @Test
    fun leftPutsGapOnRight() {
        val gap = OneHandedMode.gapDp(400)
        assertTrue("留白应大于 0", gap > 0)
        assertEquals(0 to gap, OneHandedMode.margins(OneHandedMode.LEFT, 400, 0, 0))
    }

    @Test
    fun rightPutsGapOnLeft() {
        val gap = OneHandedMode.gapDp(400)
        assertEquals(gap to 0, OneHandedMode.margins(OneHandedMode.RIGHT, 400, 0, 0))
    }

    @Test
    fun userMarginsArePreserved() {
        val gap = OneHandedMode.gapDp(400)
        assertEquals(8 to (8 + gap), OneHandedMode.margins(OneHandedMode.LEFT, 400, 8, 8))
        assertEquals((8 + gap) to 8, OneHandedMode.margins(OneHandedMode.RIGHT, 400, 8, 8))
    }

    @Test
    fun keyboardStaysAtMostFullWidth() {
        val (start, end) = OneHandedMode.margins(OneHandedMode.LEFT, 400, 0, 0)
        val keyboardWidth = 400 - start - end
        assertTrue("键盘宽度不能超过屏宽", keyboardWidth <= 400)
        assertEquals((400 * OneHandedMode.WIDTH_RATIO).toInt(), keyboardWidth)
    }

    @Test
    fun zeroWidthScreenIsSafe() {
        assertEquals(0, OneHandedMode.gapDp(0))
        assertEquals(0 to 0, OneHandedMode.margins(OneHandedMode.LEFT, 0, 0, 0))
    }

    @Test
    fun customWidthIsClampedToRange() {
        // 400dp 屏：合法宽度 280..392；超界值都被收进范围
        assertEquals(400 - 280, OneHandedMode.gapDpForWidth(400, 100))
        assertEquals(400 - 392, OneHandedMode.gapDpForWidth(400, 400))
        assertEquals(400 - 350, OneHandedMode.gapDpForWidth(400, 350))
    }

    @Test
    fun widthGapSurvivesZeroScreen() {
        assertEquals(0, OneHandedMode.gapDpForWidth(0, 300))
    }

    @Test
    fun sideToggles() {
        assertEquals(OneHandedMode.RIGHT, OneHandedMode.nextSide(OneHandedMode.LEFT))
        assertEquals(OneHandedMode.LEFT, OneHandedMode.nextSide(OneHandedMode.RIGHT))
        assertTrue(OneHandedMode.isKeyboardOnLeft(OneHandedMode.LEFT))
        assertFalse(OneHandedMode.isKeyboardOnLeft(OneHandedMode.RIGHT))
    }
}
