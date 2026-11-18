package com.kingzcheung.xime.ui.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 长按气泡的行列命中：两行 panel 下，手指向下（离气泡更远）也应落在最近的一行，
 * 继续向上才逐行上移；只有完全没动时才用初始行。
 */
class LongPressIndexAtTest {

    private fun indexAt(
        deltaY: Float,
        initialRow: Int? = null,
        touchXInRoot: Float = 100f,
        itemCount: Int = 12,
    ) = longPressIndexAt(
        touchXInRoot = touchXInRoot,
        anchorX = 100f,
        deltaY = deltaY,
        cellWidthPx = 100f,
        rowHeightPx = 100f,
        itemCount = itemCount,
        keyboardWidthPx = 1080f,
        marginPx = 10f,
        bubbleGapPx = 10f,
        initialRow = initialRow,
    )

    @Test
    fun `十二项是两行六列`() {
        assertEquals(2, longPressRows(12))
        assertEquals(6, longPressColumns(12))
    }

    @Test
    fun `未移动时用初始行_默认第一行`() {
        assertEquals(0, indexAt(deltaY = 0f))
        assertEquals(0, indexAt(deltaY = 0f, initialRow = 0))
        assertTrue(indexAt(deltaY = 0f, initialRow = 1) >= 6)
    }

    @Test
    fun `手指向下滑也选中第二行`() {
        // 气泡在按键上方，向下移动离气泡更远：最近的一行是第二行（index >= 列数）
        assertTrue(indexAt(deltaY = 50f) >= 6)
        assertTrue(indexAt(deltaY = 50f, initialRow = 0) >= 6)
    }

    @Test
    fun `向上小距离同样落在最近一行_再向上才回第一行`() {
        assertTrue(indexAt(deltaY = -50f) >= 6)
        // 越过 gap + 一个行高之后才上移一行
        assertTrue(indexAt(deltaY = -115f) < 6)
    }

    @Test
    fun `横向按手指列命中`() {
        // bodyLeft = 100 - 100/2 = 50，cell=100：x=50 第 0 列，x=350 第 3 列
        assertEquals(0, indexAt(deltaY = 0f, touchXInRoot = 50f))
        assertEquals(3, indexAt(deltaY = 0f, touchXInRoot = 350f))
        assertEquals(6 + 3, indexAt(deltaY = 60f, touchXInRoot = 350f))
    }
}
