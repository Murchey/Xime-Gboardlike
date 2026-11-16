package com.kingzcheung.xime.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SchemaCategoryTest {

    @Test
    fun `合并键方案单独一类`() {
        listOf(
            "pinyin_14jian", "pinyin_17jian", "pinyin_18jian",
            "pinyin_14jian_abcde", "pinyin_18jian_abcde",
            "double_pinyin_14jian", "double_pinyin_flypy_18jian",
        ).forEach { assertEquals("$it 应归合并键", SchemaCategory.MERGED, SchemaCategories.of(it)) }
    }

    @Test
    fun `全拼与双拼`() {
        assertEquals(SchemaCategory.FULL, SchemaCategories.of("rime_ice"))
        assertEquals(SchemaCategory.FULL, SchemaCategories.of("pinyin_simp"))
        assertEquals(SchemaCategory.SHUANGPIN, SchemaCategories.of("double_pinyin"))
        assertEquals(SchemaCategory.SHUANGPIN, SchemaCategories.of("double_pinyin_flypy"))
        assertEquals(SchemaCategory.SHUANGPIN, SchemaCategories.of("double_pinyin_mspy"))
    }

    @Test
    fun `其他方案不误判`() {
        listOf("t9_pinyin", "t9", "wubi86", "stroke", "handwriting", "").forEach {
            val c = SchemaCategories.of(it)
            assertFalse("$it 不应是合并键", c == SchemaCategory.MERGED)
            assertFalse("$it 不应是双拼", c == SchemaCategory.SHUANGPIN)
        }
        assertEquals(SchemaCategory.OTHER, SchemaCategories.of(""))
    }

    @Test
    fun `全部筛选恒真`() {
        assertTrue(SchemaCategories.matches("pinyin_14jian", SchemaCategory.ALL))
        assertTrue(SchemaCategories.matches("rime_ice", SchemaCategory.MERGED).not())
        assertTrue(SchemaCategories.matches("rime_ice", SchemaCategory.FULL))
    }
}
