package com.kingzcheung.xime.ui.keyboard

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 顶部横排拖动排序的偏移补偿。
 *
 * 修的是「长按后一次只能往前/往后挪一格」：交换槽位后若不补偿偏移，
 * 被拖图标的视觉位置会跳到远处，下一次移动就检测不到邻近槽位。
 */
class DragSwapOffsetTest {

    private val eps = 0.001f

    @Test
    fun visualPositionStaysUnderFingerAfterSwap() {
        val fromCenter = Offset(100f, 0f)
        val toCenter = Offset(300f, 0f)
        val offsetBefore = Offset(180f, 0f) // 手指已经移到 x=280 附近

        val visualBefore = fromCenter + offsetBefore
        val newOffset = adjustDragOffsetAfterSwap(offsetBefore, fromCenter, toCenter)
        val visualAfter = toCenter + newOffset

        assertEquals(visualBefore.x, visualAfter.x, eps)
        assertEquals(visualBefore.y, visualAfter.y, eps)
    }

    @Test
    fun backwardSwapAlsoKeepsPosition() {
        val fromCenter = Offset(300f, 0f)
        val toCenter = Offset(100f, 0f)
        val offsetBefore = Offset(-160f, 12f)

        val visualBefore = fromCenter + offsetBefore
        val newOffset = adjustDragOffsetAfterSwap(offsetBefore, fromCenter, toCenter)
        val visualAfter = toCenter + newOffset

        assertEquals(visualBefore.x, visualAfter.x, eps)
        assertEquals(visualBefore.y, visualAfter.y, eps)
    }

    @Test
    fun compensationIsTheSlotDelta() {
        val fromCenter = Offset(0f, 0f)
        val toCenter = Offset(120f, 0f)
        val newOffset = adjustDragOffsetAfterSwap(Offset(50f, 0f), fromCenter, toCenter)
        // 偏移减少了一个槽位间距，图标才不会跳走
        assertEquals(-70f, newOffset.x, eps)
    }

    @Test
    fun repeatedSwapsKeepSettlingUnderFinger() {
        // 连续跨越多格：每次交换后视觉位置都要保持，才能一路拖过去
        var center = Offset(0f, 0f)
        val finger = Offset(200f, 0f)
        // 起始偏移让图标正好停在手指下（视觉位置 = 中心 + 偏移）
        var offset = finger - center

        repeat(4) {
            val next = Offset(center.x + 100f, 0f)
            offset = adjustDragOffsetAfterSwap(offset, center, next)
            center = next
            assertEquals(finger.x, center.x + offset.x, eps)
        }
    }
}
