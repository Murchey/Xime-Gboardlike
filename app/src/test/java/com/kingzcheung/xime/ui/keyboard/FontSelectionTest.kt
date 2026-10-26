package com.kingzcheung.xime.ui.keyboard

import org.junit.Assert.assertEquals
import org.junit.Test

class FontSelectionTest {

    @Test
    fun `空值回退系统字体`() {
        assertEquals(AppFonts.SYSTEM_SENTINEL, AppFonts.normalizeSelection(""))
    }

    @Test
    fun `旧内置字体回退系统字体`() {
        assertEquals(AppFonts.SYSTEM_SENTINEL, AppFonts.normalizeSelection(AppFonts.BUILTIN_SENTINEL))
    }

    @Test
    fun `导入字体路径保持不变`() {
        assertEquals("fonts/custom.ttf", AppFonts.normalizeSelection("fonts/custom.ttf"))
    }
}
