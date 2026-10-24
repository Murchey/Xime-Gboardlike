package com.kingzcheung.xime.ui.keyboard

import org.junit.Assert.assertEquals
import org.junit.Test

/** 长按气泡换行与二维选中：句号键 15/16 个符号排成 8+7 / 8+8 两行。 */
class LongPressGridTest {

    private val gap = 10f
    private val margin = 4f
    private val keyboardWidth = 1080f

    @Test
    fun `不超过阈值时保持单行`() {
        assertEquals(8, longPressColumns(8))
        assertEquals(1, longPressRows(8))
        assertEquals(9, longPressColumns(9))
        assertEquals(1, longPressRows(9))
    }

    @Test
    fun `超过阈值时均分两行`() {
        assertEquals(8, longPressColumns(15))
        assertEquals(2, longPressRows(15))
        assertEquals(8, longPressColumns(16))
        assertEquals(2, longPressRows(16))
    }

    @Test
    fun `横向按手指所在的格子选列`() {
        assertEquals(0, longPressIndexAt(0f, 0f, 0f, 40f, 40f, 15, keyboardWidth, margin, gap))
        assertEquals(2, longPressIndexAt(120f, 0f, 0f, 40f, 40f, 15, keyboardWidth, margin, gap))
    }

    @Test
    fun `右侧按键初始高亮在手指附近而不是最左格`() {
        // 8 列 120dp 的大气泡：锚点 900 时主体左缘被钳到 116，手指落在第 6 格。
        assertEquals(6, longPressIndexAt(900f, 900f, 0f, 120f, 40f, 15, keyboardWidth, margin, gap))
    }

    @Test
    fun `向上拖动先选第二行再回第一行`() {
        // 第一行画在上方；手指进入气泡先命中最近的第二行（index 8 起）
        assertEquals(8, longPressIndexAt(0f, 0f, -20f, 40f, 40f, 15, keyboardWidth, margin, gap))
        assertEquals(10, longPressIndexAt(120f, 0f, -20f, 40f, 40f, 15, keyboardWidth, margin, gap))
        // 继续向上越过第二行高度后回到第一行
        assertEquals(2, longPressIndexAt(120f, 0f, -60f, 40f, 40f, 15, keyboardWidth, margin, gap))
    }

    @Test
    fun `越界时钳制到最后一个符号`() {
        assertEquals(14, longPressIndexAt(999f, 0f, -20f, 40f, 40f, 15, keyboardWidth, margin, gap))
    }

    @Test
    fun `单行气泡纵向拖动仍选当前行`() {
        assertEquals(8, longPressIndexAt(999f, 0f, -999f, 40f, 40f, 9, keyboardWidth, margin, gap))
    }
}
