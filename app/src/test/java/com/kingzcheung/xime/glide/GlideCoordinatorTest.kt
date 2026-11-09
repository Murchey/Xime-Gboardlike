package com.kingzcheung.xime.glide

import org.junit.Assert.*
import org.junit.Test

/** 阶段A：整句查询与提交闭环（纯 JVM，注入假 Rime）。 */
class GlideCoordinatorTest {

    private val host = "editor-1"
    private val schema = "rime_ice@v1"
    private val layout = "portrait@v1"
    private fun cands(code: String, vararg texts: String) = texts.mapIndexed { i, t ->
        GlideCandidate(id = "c" + i, text = t, rawInput = code)
    }

    private class FakeLookup(var result: GlideLookupResult = GlideLookupResult.NoCandidate) : GlideSentenceLookup {
        var lastInput: String? = null
        var calls = 0
        override fun lookup(rawInput: String, maxCount: Int): GlideLookupResult {
            calls++; lastInput = rawInput; return result
        }
    }
    private class FakeCommitter(var install: Boolean = true, var select: Boolean = true) : GlideCommitter {
        var installs = 0; var selects = 0
        override fun installRawInput(rawInput: String): Boolean { installs++; return install }
        override fun selectCandidate(rawInput: String, text: String): Boolean { selects++; return select }
    }

    @Test fun `抬手查询整句编码并进入待确认不提交`() {
        val l = FakeLookup(GlideLookupResult.Candidates(cands("nihaoshijie", "你好世界")))
        val cm = FakeCommitter()
        val co = GlideCoordinator(l, cm)
        co.session.beginGlide(host, schema, layout)
        co.session.appendCode("nihaoshijie")
        val out = co.onTraceFinished(host, schema, layout)
        assertTrue(out is GlideCoordinator.Outcome.ShowCandidates)
        assertEquals("nihaoshijie", l.lastInput)
        assertEquals(1, l.calls)
        assertEquals(0, cm.installs)
        assertEquals(0, cm.selects)
        assertTrue(co.session.state.isComposing)
    }

    @Test fun `确认才安装编码并选词一次`() {
        val l = FakeLookup(GlideLookupResult.Candidates(cands("nihaoshijie", "你好世界")))
        val cm = FakeCommitter()
        val co = GlideCoordinator(l, cm)
        co.session.beginGlide(host, schema, layout)
        co.session.appendCode("nihaoshijie")
        co.onTraceFinished(host, schema, layout)
        val out = co.confirm("c0", host)
        assertTrue(out is GlideCoordinator.CommitOutcome.Committed)
        assertEquals(1, cm.installs)
        assertEquals(1, cm.selects)
    }

    @Test fun `错误host拒绝确认且不调用提交`() {
        val l = FakeLookup(GlideLookupResult.Candidates(cands("nihaoshijie", "你好世界")))
        val cm = FakeCommitter()
        val co = GlideCoordinator(l, cm)
        co.session.beginGlide(host, schema, layout)
        co.session.appendCode("nihaoshijie")
        co.onTraceFinished(host, schema, layout)
        assertTrue(co.confirm("c0", "wrong") is GlideCoordinator.CommitOutcome.Rejected)
        assertEquals(0, cm.installs)
    }

    @Test fun `Busy与Error不当作无候选静默吞掉`() {
        for (res in listOf(GlideLookupResult.Busy, GlideLookupResult.Error("boom"), GlideLookupResult.Unsupported)) {
            val co = GlideCoordinator(FakeLookup(res), FakeCommitter())
            co.session.beginGlide(host, schema, layout)
            co.session.appendCode("nihaoshijie")
            val out = co.onTraceFinished(host, schema, layout)
            assertFalse(out is GlideCoordinator.Outcome.ShowCandidates)
            assertFalse(out is GlideCoordinator.Outcome.NoCandidate && res !is GlideLookupResult.NoCandidate)
        }
    }

    @Test fun `install失败不算已上屏且会话可恢复`() {
        val co = GlideCoordinator(
            FakeLookup(GlideLookupResult.Candidates(cands("nihaoshijie", "你好世界"))),
            FakeCommitter(install = false))
        co.session.beginGlide(host, schema, layout)
        co.session.appendCode("nihaoshijie")
        co.onTraceFinished(host, schema, layout)
        assertTrue(co.confirm("c0", host) is GlideCoordinator.CommitOutcome.InstallFailed)
        assertEquals(GlideCompositionSession.Phase.PENDING_CONFIRM, co.session.state.phase)
        // 修正安装失败后可重试成功
        assertTrue(co.confirm("c0", host) is GlideCoordinator.CommitOutcome.InstallFailed)
    }

    @Test fun `select失败不算已上屏`() {
        val co = GlideCoordinator(
            FakeLookup(GlideLookupResult.Candidates(cands("nihaoshijie", "你好世界"))),
            FakeCommitter(select = false))
        co.session.beginGlide(host, schema, layout)
        co.session.appendCode("nihaoshijie")
        co.onTraceFinished(host, schema, layout)
        assertTrue(co.confirm("c0", host) is GlideCoordinator.CommitOutcome.SelectFailed)
        assertEquals(GlideCompositionSession.Phase.PENDING_CONFIRM, co.session.state.phase)
    }
}
