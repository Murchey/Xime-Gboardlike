package com.kingzcheung.xime.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 切回 26 键时该恢复哪个方案。 */
class KeyboardLayoutRestoreTest {

    @Test
    fun `恢复进入合并键布局前的方案`() {
        assertEquals("double_pinyin_flypy", KeyboardLayoutPreset.restoreTarget("double_pinyin_flypy"))
        assertEquals("rime_ice", KeyboardLayoutPreset.restoreTarget("rime_ice"))
        assertEquals("wubi86", KeyboardLayoutPreset.restoreTarget("  wubi86  "))
    }

    @Test
    fun `没有保存过就不动`() {
        assertNull(KeyboardLayoutPreset.restoreTarget(""))
        assertNull(KeyboardLayoutPreset.restoreTarget("   "))
    }

    @Test
    fun `保存的本身就是合并键方案时不恢复`() {
        assertNull(KeyboardLayoutPreset.restoreTarget("rime_ice_14jian"))
        assertNull(KeyboardLayoutPreset.restoreTarget("double_pinyin_flypy_18jian_abcde"))
    }
}
