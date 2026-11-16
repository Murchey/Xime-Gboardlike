package com.kingzcheung.xime.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardLayoutPresetTest {

    @Test
    fun `未知或空白布局回落到 26 键`() {
        assertEquals("26", KeyboardLayoutPreset.normalizeLayout(null))
        assertEquals("26", KeyboardLayoutPreset.normalizeLayout(""))
        assertEquals("26", KeyboardLayoutPreset.normalizeLayout("12"))
        assertEquals("17", KeyboardLayoutPreset.normalizeLayout("17"))
    }

    @Test
    fun `字母排列只认 qwerty 与 abcde`() {
        assertEquals("qwerty", KeyboardLayoutPreset.normalizeOrder(null))
        assertEquals("qwerty", KeyboardLayoutPreset.normalizeOrder("QWERTY"))
        assertEquals("abcde", KeyboardLayoutPreset.normalizeOrder("abcde"))
    }

    @Test
    fun `三套合并键都已有对应方案`() {
        assertTrue(KeyboardLayoutPreset.isKeyboardSupported("26"))
        assertTrue(KeyboardLayoutPreset.isKeyboardSupported("14"))
        assertTrue(KeyboardLayoutPreset.isKeyboardSupported("17"))
        assertTrue(KeyboardLayoutPreset.isKeyboardSupported("18"))
    }

    @Test
    fun `ABCDE 三行共 26 个字母且不重复`() {
        val flat = KeyboardLayoutPreset.ABCDE_ROWS.flatten()
        assertEquals(26, flat.size)
        assertEquals(26, flat.toSet().size)
        assertTrue(flat.contains("a"))
        assertTrue(flat.contains("z"))
    }
}