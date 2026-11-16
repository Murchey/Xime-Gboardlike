package com.kingzcheung.xime.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardLayoutSchemaMappingTest {

    @Test
    fun `26 键不切方案`() {
        assertNull(KeyboardLayoutPreset.schemaIdFor("26", "qwerty"))
        assertNull(KeyboardLayoutPreset.schemaIdFor("26", "abcde"))
    }

    @Test
    fun `14 键两套排列对应两个方案`() {
        assertEquals("pinyin_14jian", KeyboardLayoutPreset.schemaIdFor("14", "qwerty"))
        assertEquals("pinyin_14jian_abcde", KeyboardLayoutPreset.schemaIdFor("14", "abcde"))
    }

    @Test
    fun `17 与 18 键同样映射`() {
        assertEquals("pinyin_17jian", KeyboardLayoutPreset.schemaIdFor("17", "qwerty"))
        assertEquals("pinyin_18jian", KeyboardLayoutPreset.schemaIdFor("18", "qwerty"))
        assertEquals("pinyin_17jian_abcde", KeyboardLayoutPreset.schemaIdFor("17", "abcde"))
        assertEquals("pinyin_18jian_abcde", KeyboardLayoutPreset.schemaIdFor("18", "abcde"))
    }

    @Test
    fun `未知取值按默认处理`() {
        assertEquals("pinyin_14jian", KeyboardLayoutPreset.schemaIdFor("14", "zzz"))
        assertNull(KeyboardLayoutPreset.schemaIdFor("99", "qwerty"))
    }

    @Test
    fun `能识别合并键方案`() {
        assertTrue(KeyboardLayoutPreset.isGridSchema("pinyin_14jian"))
        assertTrue(KeyboardLayoutPreset.isGridSchema("pinyin_18jian_abcde"))
        assertFalse(KeyboardLayoutPreset.isGridSchema("rime_ice"))
        assertFalse(KeyboardLayoutPreset.isGridSchema("t9_pinyin"))
        assertFalse(KeyboardLayoutPreset.isGridSchema("pinyin_simp"))
    }
}
