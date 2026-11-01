package com.kingzcheung.xime.service

import org.junit.Assert.assertEquals
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
}
