package com.kingzcheung.xime.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 五笔连打的累积缓冲：纯状态，不涉及 InputConnection。 */
class ChainedCommitBufferTest {

    @Test
    fun `追加多段后拼成一句`() {
        val buf = ChainedCommitBuffer()
        buf.append("你")
        buf.append("好")
        buf.append("世")
        buf.append("界")
        assertEquals("你好世界", buf.text)
    }

    @Test
    fun `空追加不影响内容`() {
        val buf = ChainedCommitBuffer()
        buf.append("")
        assertTrue(buf.isEmpty)
        buf.append("字")
        buf.append("")
        assertEquals("字", buf.text)
    }

    @Test
    fun `退格删掉末尾一个汉字`() {
        val buf = ChainedCommitBuffer()
        buf.append("你好世")
        buf.deleteLast()
        assertEquals("你好", buf.text)
    }

    @Test
    fun `退格到空之后继续删不崩`() {
        val buf = ChainedCommitBuffer()
        buf.append("字")
        buf.deleteLast()
        assertTrue(buf.isEmpty)
        buf.deleteLast()
        assertEquals("", buf.text)
    }

    @Test
    fun `emoji 整体删除不劈成半个`() {
        val buf = ChainedCommitBuffer()
        buf.append("好😀")
        buf.deleteLast()
        assertEquals("好", buf.text)
    }

    @Test
    fun `取走内容后缓冲清空`() {
        val buf = ChainedCommitBuffer()
        buf.append("上屏")
        assertEquals("上屏", buf.drain())
        assertTrue(buf.isEmpty)
    }

    @Test
    fun `显式清空`() {
        val buf = ChainedCommitBuffer()
        buf.append("abc")
        buf.clear()
        assertTrue(buf.isEmpty)
    }

    @Test
    fun `连续追加与删除交替`() {
        val buf = ChainedCommitBuffer()
        buf.append("一二")
        buf.append("三")
        buf.deleteLast()
        buf.append("四")
        assertEquals("一二四", buf.text)
    }

    @Test
    fun `英文与空白按原样保留`() {
        val buf = ChainedCommitBuffer()
        buf.append("ab ")
        buf.append("cd")
        assertEquals("ab cd", buf.text)
    }

    @Test
    fun `单字符反复删到空不残留`() {
        val buf = ChainedCommitBuffer()
        repeat(5) { buf.append("字") }
        repeat(5) { buf.deleteLast() }
        assertTrue(buf.isEmpty)
        assertFalse(buf.text.isNotEmpty())
    }
}
