package com.kingzcheung.xime.glide

import org.junit.Assert.*
import org.junit.Test

/** 阶段A：提交契约与旧结果保护（纯 JVM）。 */
class GlideCompositionSessionTest {

    private val host = "editor-1"
    private val schema = "rime_ice@v1"
    private val layout = "portrait@v1"
    private fun cand(id: String, text: String, code: String) =
        GlideCandidate(id = id, text = text, rawInput = code)

    @Test fun `抬手进入待确认但绝不自动上屏`() {
        val s = GlideCompositionSession()
        val trace = s.beginGlide(host, schema, layout)
        s.appendCode("nihao")
        s.markDecoding(trace, s.state.revision)
        val accepted = s.deliverCandidates(trace, s.state.revision, host, "nihao",
            listOf(cand("c1", "你好", "nihao")))
        assertTrue(accepted)
        assertEquals(GlideCompositionSession.Phase.PENDING_CONFIRM, s.state.phase)
        assertNull(s.state.confirmedCandidateId)
        assertTrue(s.state.isComposing)
    }

    @Test fun `确认只成功一次且重复确认不再次上屏`() {
        val s = GlideCompositionSession()
        val t = s.beginGlide(host, schema, layout)
        s.appendCode("nihao")
        s.deliverCandidates(t, s.state.revision, host, "nihao", listOf(cand("c1", "你好", "nihao")))
        val first = s.confirm("c1", host)
        assertTrue(first is GlideCompositionSession.ConfirmResult.Confirmed)
        val second = s.confirm("c1", host)
        assertTrue(second is GlideCompositionSession.ConfirmResult.AlreadyConfirmed)
        assertEquals(GlideCompositionSession.Phase.CONFIRMED, s.state.phase)
    }

    @Test fun `错误host或错误候选拒绝确认`() {
        val s = GlideCompositionSession()
        val t = s.beginGlide(host, schema, layout)
        s.appendCode("nihao")
        s.deliverCandidates(t, s.state.revision, host, "nihao", listOf(cand("c1", "你好", "nihao")))
        assertTrue(s.confirm("c1", "other-editor") is GlideCompositionSession.ConfirmResult.Rejected)
        assertTrue(s.confirm("missing", host) is GlideCompositionSession.ConfirmResult.Rejected)
        assertEquals(GlideCompositionSession.Phase.PENDING_CONFIRM, s.state.phase)
    }

    @Test fun `编辑递增revision后旧解码结果被丢弃`() {
        val s = GlideCompositionSession()
        val t = s.beginGlide(host, schema, layout)
        s.appendCode("nihao")
        val oldRev = s.state.revision
        s.appendCode("shijie") // 用户继续滑，revision 增加
        val accepted = s.deliverCandidates(t, oldRev, host, "nihaoshijie", listOf(cand("c1", "你好世界", "nihaoshijie")))
        assertFalse(accepted)
        assertEquals(emptyList<GlideCandidate>(), s.state.candidates)
    }

    @Test fun `退格编辑同样使旧解码失效`() {
        val s = GlideCompositionSession()
        val t = s.beginGlide(host, schema, layout)
        s.appendCode("nihao")
        val oldRev = s.state.revision
        assertTrue(s.backspace())
        assertEquals("niha", s.state.rawInput)
        assertFalse(s.deliverCandidates(t, oldRev, host, "nihao", listOf(cand("c1", "你好", "nihao"))))
    }

    @Test fun `候选必须文本和编码均有效`() {
        val s = GlideCompositionSession()
        val t = s.beginGlide(host, schema, layout)
        s.appendCode("x")
        s.deliverCandidates(t, s.state.revision, host, "x", listOf(
            GlideCandidate(id = "bad1", text = "", rawInput = "x"),
            GlideCandidate(id = "bad2", text = "字", rawInput = ""),
        ))
        assertEquals(GlideCompositionSession.Phase.IDLE, s.state.phase)
        assertTrue(s.state.candidates.isEmpty())
    }

    @Test fun `取消不产生确认且回到空闲`() {
        val s = GlideCompositionSession()
        val t = s.beginGlide(host, schema, layout)
        s.appendCode("nihao")
        s.deliverCandidates(t, s.state.revision, host, "nihao", listOf(cand("c1", "你好", "nihao")))
        s.cancel()
        assertEquals(GlideCompositionSession.Phase.IDLE, s.state.phase)
        assertTrue(s.confirm("c1", host) is GlideCompositionSession.ConfirmResult.Rejected)
    }

    @Test fun `宿主切换与方案切换丢弃未确认结果`() {
        val s = GlideCompositionSession()
        val t = s.beginGlide(host, schema, layout)
        s.appendCode("nihao")
        s.deliverCandidates(t, s.state.revision, host, "nihao", listOf(cand("c1", "你好", "nihao")))
        s.onHostChanged()
        assertEquals(GlideCompositionSession.Phase.IDLE, s.state.phase)
        assertEquals("", s.state.rawInput)

        val s2 = GlideCompositionSession()
        val t2 = s2.beginGlide(host, schema, layout)
        s2.appendCode("nihao")
        s2.deliverCandidates(t2, s2.state.revision, host, "nihao", listOf(cand("c1", "你好", "nihao")))
        s2.onEnvironmentChanged()
        assertEquals(GlideCompositionSession.Phase.IDLE, s2.state.phase)
    }

    @Test fun `再滑追加编码而不提交上一段`() {
        val s = GlideCompositionSession()
        val t = s.beginGlide(host, schema, layout)
        s.appendCode("nihao")
        s.deliverCandidates(t, s.state.revision, host, "nihao", listOf(cand("c1", "你好", "nihao")))
        assertEquals(GlideCompositionSession.Phase.PENDING_CONFIRM, s.state.phase)
        assertTrue(s.appendCode("shijie"))
        assertEquals("nihaoshijie", s.state.rawInput)
        assertEquals(GlideCompositionSession.Phase.COLLECTING, s.state.phase)
        assertNull(s.state.confirmedCandidateId)
    }

    @Test fun `确认态不受取消影响`() {
        val s = GlideCompositionSession()
        val t = s.beginGlide(host, schema, layout)
        s.appendCode("nihao")
        s.deliverCandidates(t, s.state.revision, host, "nihao", listOf(cand("c1", "你好", "nihao")))
        s.confirm("c1", host)
        s.cancel()
        assertEquals(GlideCompositionSession.Phase.CONFIRMED, s.state.phase)
    }
}
