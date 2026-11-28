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
    fun `面板打开后字母直接进搜索词`() {
        EmojiSearchInput.open()
        assertTrue(EmojiSearchInput.handleKey("s", false))
        assertTrue(EmojiSearchInput.handleKey("m", false))
        assertEquals("sm", EmojiSearchInput.query)
    }

    @Test
    fun `shift 状态输入大写`() {
        EmojiSearchInput.open()
        EmojiSearchInput.handleKey("a", true)
        assertEquals("A", EmojiSearchInput.query)
    }

    @Test
    fun `退格删除末字符且空串安全`() {
        EmojiSearchInput.open()
        EmojiSearchInput.handleKey("a", false)
        EmojiSearchInput.handleKey("delete", false)
        assertEquals("", EmojiSearchInput.query)
        EmojiSearchInput.handleKey("delete", false)
        assertEquals("", EmojiSearchInput.query)
    }

    @Test
    fun `回车被吞掉不上屏也不退出搜索态`() {
        EmojiSearchInput.open()
        EmojiSearchInput.handleKey("a", false)
        assertTrue("回车应由搜索框消费", EmojiSearchInput.handleKey("enter", false))
        assertTrue("面板打开期间搜索框始终是按键目标", EmojiSearchInput.panelOpen)
        assertEquals("a", EmojiSearchInput.query)
    }

    @Test
    fun `功能键不拦截交给布局处理`() {
        EmojiSearchInput.open()
        assertFalse(EmojiSearchInput.handleKey("symbol", false))
        assertFalse(EmojiSearchInput.handleKey("number", false))
        assertFalse(EmojiSearchInput.handleKey("ime_switch", false))
        assertEquals("", EmojiSearchInput.query)
    }

    @Test
    fun `reset 之后按键回到宿主输入框`() {
        EmojiSearchInput.open()
        EmojiSearchInput.handleKey("x", false)
        EmojiSearchInput.reset()
        assertEquals("", EmojiSearchInput.query)
        assertFalse(EmojiSearchInput.panelOpen)
        assertFalse(EmojiSearchInput.handleKey("y", false))
    }

    @Test
    fun `重新打开清空上次关键词`() {
        EmojiSearchInput.open()
        EmojiSearchInput.handleKey("x", false)
        EmojiSearchInput.open()
        assertEquals("", EmojiSearchInput.query)
        assertTrue(EmojiSearchInput.panelOpen)
    }
}
