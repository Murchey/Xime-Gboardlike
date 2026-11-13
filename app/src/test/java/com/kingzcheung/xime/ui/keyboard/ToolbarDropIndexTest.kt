package com.kingzcheung.xime.ui.keyboard

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 卡片拖到顶部栏目时的插入位置：
 * 落在某槽位中心左侧就插到它前面，落在最右槽位右边则追加到末尾。
 */
class ToolbarDropIndexTest {

    private val centers = mapOf(
        0 to Offset(100f, 0f),
        1 to Offset(300f, 0f),
        2 to Offset(500f, 0f),
    )

    @Test
    fun emptyStripInsertsAtStart() {
        assertEquals(0, dropIndexFor(123f, emptyMap()))
    }

    @Test
    fun leftOfFirstSlotInsertsFirst() {
        assertEquals(0, dropIndexFor(60f, centers))
    }

    @Test
    fun betweenSlotsInsertsAtThatIndex() {
        // 落在第 0 与第 1 槽之间、靠近第 1 槽中心左侧 → 插到 1 号位
        assertEquals(1, dropIndexFor(250f, centers))
        assertEquals(2, dropIndexFor(450f, centers))
    }

    @Test
    fun rightOfLastSlotAppendsAtEnd() {
        assertEquals(3, dropIndexFor(900f, centers))
    }
}
