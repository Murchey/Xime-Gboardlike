package com.kingzcheung.xime.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import com.kingzcheung.xime.service.nextSchemaId

/**
 * 方案轮换：nextSchemaId 取「已启用列表里当前方案的下一个，末尾回到首个」。
 * 与菜单栏方案列表顺序一致，避免轮换结果和菜单看到的顺序不同。
 */
class NextSchemaIdTest {

    @Test
    fun `轮换到下一个方案`() {
        assertEquals("b", nextSchemaId(listOf("a", "b", "c"), "a"))
        assertEquals("c", nextSchemaId(listOf("a", "b", "c"), "b"))
    }

    @Test
    fun `末尾回到首个`() {
        assertEquals("a", nextSchemaId(listOf("a", "b", "c"), "c"))
    }

    @Test
    fun `当前方案不在列表时取首个`() {
        assertEquals("a", nextSchemaId(listOf("a", "b"), "gone"))
    }

    @Test
    fun `列表为空返回 null`() {
        assertNull(nextSchemaId(emptyList(), "a"))
    }

    @Test
    fun `只有一个方案时原地不动`() {
        assertEquals("only", nextSchemaId(listOf("only"), "only"))
    }
}
