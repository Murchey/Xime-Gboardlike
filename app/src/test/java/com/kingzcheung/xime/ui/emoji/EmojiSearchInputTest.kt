package com.kingzcheung.xime.ui.emoji

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class EmojiSearchInputTest {

    @Before
    fun reset() = EmojiSearchInput.reset()

    @Test
    fun `面板未打开时不拦截按键`() {
        assertFalse(EmojiSearchInput.handleKey("a", false))
        assertEquals("", EmojiSearchInput.query)
    }

    @Test
    fun `打开面板默认浏览态且按键不进搜索框`() {
        EmojiSearchInput.open()
        assertTrue(EmojiSearchInput.panelOpen)
        assertFalse("首次打开应是半屏浏览态", EmojiSearchInput.searchMode)
        assertFalse("浏览态按键应走宿主输入框", EmojiSearchInput.handleKey("a", false))
        assertEquals("", EmojiSearchInput.query)
    }

    @Test
    fun `进入搜索态后字母进搜索词`() {
        EmojiSearchInput.open()
        EmojiSearchInput.enterSearch()
        assertTrue(EmojiSearchInput.handleKey("s", false))
        assertTrue(EmojiSearchInput.handleKey("m", false))
        assertEquals("sm", EmojiSearchInput.query)
    }

    @Test
    fun `shift 状态输入大写`() {
        EmojiSearchInput.open()
        EmojiSearchInput.enterSearch()
        EmojiSearchInput.handleKey("a", true)
        assertEquals("A", EmojiSearchInput.query)
    }

    @Test
    fun `退格删除末字符且空串安全`() {
        EmojiSearchInput.open()
        EmojiSearchInput.enterSearch()
        EmojiSearchInput.handleKey("a", false)
        EmojiSearchInput.handleKey("delete", false)
        assertEquals("", EmojiSearchInput.query)
        EmojiSearchInput.handleKey("delete", false)
        assertEquals("", EmojiSearchInput.query)
    }

    @Test
    fun `回车不退出搜索态也不产生候选`() {
        EmojiSearchInput.open()
        EmojiSearchInput.enterSearch()
        EmojiSearchInput.handleKey("a", false)
        assertTrue(EmojiSearchInput.handleKey("enter", false))
        assertTrue(EmojiSearchInput.searchMode)
        assertEquals("a", EmojiSearchInput.query)
    }

    @Test
    fun `退出搜索态回到浏览态并清词`() {
        EmojiSearchInput.open()
        EmojiSearchInput.enterSearch()
        EmojiSearchInput.handleKey("s", false)
        EmojiSearchInput.exitSearch()
        assertEquals("", EmojiSearchInput.query)
        assertFalse(EmojiSearchInput.searchMode)
        assertTrue("面板仍开着", EmojiSearchInput.panelOpen)
        assertFalse("按键应回到宿主输入框", EmojiSearchInput.handleKey("b", false))
    }

    @Test
    fun `功能键不拦截交给布局处理`() {
        EmojiSearchInput.open()
        EmojiSearchInput.enterSearch()
        assertFalse(EmojiSearchInput.handleKey("symbol", false))
        assertFalse(EmojiSearchInput.handleKey("number", false))
        assertFalse(EmojiSearchInput.handleKey("ime_switch", false))
        assertEquals("", EmojiSearchInput.query)
    }

    @Test
    fun `reset 之后按键回到宿主输入框`() {
        EmojiSearchInput.open()
        EmojiSearchInput.enterSearch()
        EmojiSearchInput.handleKey("x", false)
        EmojiSearchInput.reset()
        assertEquals("", EmojiSearchInput.query)
        assertFalse(EmojiSearchInput.panelOpen)
        assertFalse(EmojiSearchInput.searchMode)
        assertFalse(EmojiSearchInput.handleKey("y", false))
    }

    @Test
    fun `重新打开回到浏览态且清空上次关键词`() {
        EmojiSearchInput.open()
        EmojiSearchInput.enterSearch()
        EmojiSearchInput.handleKey("x", false)
        EmojiSearchInput.open()
        assertEquals("", EmojiSearchInput.query)
        assertFalse(EmojiSearchInput.searchMode)
        assertTrue(EmojiSearchInput.panelOpen)
    }
}
