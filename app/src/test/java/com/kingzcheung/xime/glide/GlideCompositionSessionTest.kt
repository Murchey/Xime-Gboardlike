package com.kingzcheung.xime.glide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 组合会话的状态边界。
 *
 * 核心约束：一次滑动只上屏一次；旧异步解码结果不得覆盖新编辑；
 * 取消/宿主切换不产生提交。这些错了会重复上屏或吞掉用户输入。
 */
class GlideCompositionSessionTest {

    private fun candidate(id: String = "c1") =
        GlideCandidate(id, "你好", "nihc")

    private fun session(): GlideCompositionSession {
        val s = GlideCompositionSession()
        val t = s.beginGlide("host", "schema", "layout")
        s.deliverCandidates(t, s.state.revision, "host", "nihc", listOf(candidate()))
        return s
    }

    @Test
    fun 正常确认应成功() {
        val s = session()
        val r = s.confirm("c1", "host")
        assertTrue(r is GlideCompositionSession.ConfirmResult.Confirmed)
    }

    @Test
    fun 重复确认同一候选应返回已确认() {
        val s = session()
        s.confirm("c1", "host")
        val again = s.confirm("c1", "host")
        assertTrue("重复确认必须幂等", again is GlideCompositionSession.ConfirmResult.AlreadyConfirmed)
    }

    @Test
    fun hostToken不匹配应拒绝() {
        val s = session()
        val r = s.confirm("c1", "other-host")
        assertTrue(r is GlideCompositionSession.ConfirmResult.Rejected)
    }

    @Test
    fun 旧异步解码结果不得覆盖新编辑() {
        val s = GlideCompositionSession()
        val t = s.beginGlide("host", "schema", "layout")
        val oldRev = s.state.revision
        // 用户继续编辑
        s.appendCode("a")
        // 旧结果迟到
        val accepted = s.deliverCandidates(t, oldRev, "host", "nihc", listOf(candidate()))
        assertFalse("旧 revision 的结果必须被丢弃", accepted)
    }

    @Test
    fun 取消后不应能确认() {
        val s = session()
        s.cancel()
        val r = s.confirm("c1", "host")
        assertTrue(r is GlideCompositionSession.ConfirmResult.Rejected)
    }

    @Test
    fun 已确认后取消不应回退状态() {
        val s = session()
        s.confirm("c1", "host")
        s.cancel()
        assertEquals("已确认态不应被 cancel 改回",
            GlideCompositionSession.Phase.CONFIRMED, s.state.phase)
    }

    @Test
    fun 确认失败回滚后应可重试() {
        val s = session()
        s.confirm("c1", "host")
        s.rollbackFailedConfirm("c1")
        assertEquals(GlideCompositionSession.Phase.PENDING_CONFIRM, s.state.phase)
        val retry = s.confirm("c1", "host")
        assertTrue("回滚后应允许重试", retry is GlideCompositionSession.ConfirmResult.Confirmed)
    }

    @Test
    fun 空候选列表应回到空闲态() {
        val s = GlideCompositionSession()
        val t = s.beginGlide("host", "schema", "layout")
        s.deliverCandidates(t, s.state.revision, "host", "x", emptyList())
        assertEquals(GlideCompositionSession.Phase.IDLE, s.state.phase)
    }

    @Test
    fun 退格清空编码后不应产生负状态() {
        val s = GlideCompositionSession()
        val t = s.beginGlide("host", "schema", "layout")
        s.deliverCandidates(t, s.state.revision, "host", "ab", listOf(candidate()))
        assertTrue(s.backspace())
        assertTrue(s.backspace())
        assertFalse("编码已空，退格应返回 false", s.backspace())
        assertEquals("", s.state.rawInput)
    }

    @Test
    fun 宿主切换应清空全部状态() {
        val s = session()
        s.onHostChanged()
        assertEquals(GlideCompositionSession.Phase.IDLE, s.state.phase)
        assertEquals("", s.state.rawInput)
        assertTrue(s.state.candidates.isEmpty())
    }
}
