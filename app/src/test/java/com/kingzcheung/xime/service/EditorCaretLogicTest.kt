package com.kingzcheung.xime.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorCaretLogicTest {

    @Test
    fun `向左折叠选区落在选区头`() {
        assertEquals(3, EditorCaretLogic.collapseTarget(3, 12, -1))
    }

    @Test
    fun `向右折叠选区落在选区尾`() {
        assertEquals(12, EditorCaretLogic.collapseTarget(3, 12, 1))
    }

    @Test
    fun `端点越界时钳制到文本范围`() {
        assertEquals(10, EditorCaretLogic.clampCaret(99, 10))
        assertEquals(0, EditorCaretLogic.clampCaret(-5, 10))
        assertEquals(0, EditorCaretLogic.clampCaret(3, 0))
    }

    @Test
    fun `到文本开头后向左不再移动且不产生负值`() {
        assertEquals(0, EditorCaretLogic.stepCaret(0, -1, 10))
    }

    @Test
    fun `到文本结尾后向右保持在结尾`() {
        assertEquals(10, EditorCaretLogic.stepCaret(10, 1, 10))
        assertEquals(10, EditorCaretLogic.stepCaret(99, 1, 10))
    }

    @Test
    fun `端点停在越界值时仍能正常回退`() {
        // 编辑器把端点钳制到末尾但内部记录越界：先钳制再步进，向左必须能走
        assertEquals(9, EditorCaretLogic.stepCaret(99, -1, 10))
    }

    @Test
    fun `只有整段文本首尾才算边界`() {
        // 中间位置任何方向都不是边界：多行文本里的段落/行首尾不能当边界，
        // 否则会把本来能移动的方向键吞掉
        for (pos in 1..9) {
            assertFalse("pos=$pos 向上被误判为边界", EditorCaretLogic.isAtBoundary(pos, -1, 10))
            assertFalse("pos=$pos 向下被误判为边界", EditorCaretLogic.isAtBoundary(pos, 1, 10))
        }
        assertTrue(EditorCaretLogic.isAtBoundary(0, -1, 10))
        assertTrue(EditorCaretLogic.isAtBoundary(10, 1, 10))
        // 反方向在端点仍然可移动
        assertFalse(EditorCaretLogic.isAtBoundary(0, 1, 10))
        assertFalse(EditorCaretLogic.isAtBoundary(10, -1, 10))
    }

    @Test
    fun `空文本与越界端点不会误判`() {
        assertTrue(EditorCaretLogic.isAtBoundary(0, -1, 0))
        assertTrue(EditorCaretLogic.isAtBoundary(0, 1, 0))
        // 端点越界时先钳制再判定
        assertTrue(EditorCaretLogic.isAtBoundary(99, 1, 10))
        assertTrue(EditorCaretLogic.isAtBoundary(-3, -1, 10))
    }
}
