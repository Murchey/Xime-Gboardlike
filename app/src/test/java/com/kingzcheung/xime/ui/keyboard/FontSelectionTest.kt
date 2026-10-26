package com.kingzcheung.xime.ui.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test
    fun `系统字体文件标记可往返`() {
        val path = "/system/fonts/NotoSansCJK-Regular.ttc"
        val selection = AppFonts.systemFileSelection(path)
        assertEquals(selection, AppFonts.normalizeSelection(selection))
        assertEquals(path, AppFonts.systemFilePath(selection))
    }

    @Test
    fun `普通字体选择不识别为系统文件`() {
        assertNull(AppFonts.systemFilePath(AppFonts.SYSTEM_SENTINEL))
        assertNull(AppFonts.systemFilePath("fonts/custom.ttf"))
        assertNull(AppFonts.systemFilePath("@sysfile:"))
    }
}
