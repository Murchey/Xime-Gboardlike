package com.kingzcheung.xime.ui.keyboard

import org.junit.Assert.assertEquals
import org.junit.Test

/** 方案按钮作为可拖动槽位：位置由 menu_schema_slot 决定，默认末尾。 */
class SchemaSlotTest {

    private val draft = listOf("a", "b", "c")

    @Test
    fun negativeSlotMeansAppend() {
        assertEquals(listOf("a", "b", "c", "sys_schema_slot"), withSchemaSlot(draft, -1))
    }

    @Test
    fun slotIsClampedIntoRange() {
        assertEquals(listOf("sys_schema_slot", "a", "b", "c"), withSchemaSlot(draft, 0))
        assertEquals(listOf("a", "sys_schema_slot", "b", "c"), withSchemaSlot(draft, 1))
        // 越界值收敛到末尾，不会崩
        assertEquals(listOf("a", "b", "c", "sys_schema_slot"), withSchemaSlot(draft, 99))
    }

    @Test
    fun emptyDraftStillHasSchemaSlot() {
        assertEquals(listOf("sys_schema_slot"), withSchemaSlot(emptyList(), 0))
    }
}
