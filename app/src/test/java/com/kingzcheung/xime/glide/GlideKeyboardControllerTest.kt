package com.kingzcheung.xime.glide

import org.junit.Assert.*
import org.junit.Test

/** 阶段B：编排层端到端（几何→仲裁→轨迹→会话→查询），注入假 Rime。 */
class GlideKeyboardControllerTest {
    private val host = "host-1"
    private val schema = "rime_ice@v1"

    private class L(var result: GlideLookupResult = GlideLookupResult.NoCandidate) : GlideSentenceLookup {
        var input: String? = null
        override fun lookup(rawInput: String, maxCount: Int): GlideLookupResult { input = rawInput; return result }
    }
    private class C : GlideCommitter {
        var installs = 0
        override fun installRawInput(rawInput: String): Boolean { installs++; return true }
        override fun selectCandidate(rawInput: String, text: String): Boolean = true
    }
    private fun registry(): GlideGeometryRegistry {
        val r = GlideGeometryRegistry()
        r.beginFrame(300f, 100f)
        listOf("q","w","e").forEachIndexed { i, c ->
            r.register(GlideKey("k"+i, c, GlideKeyRect(i*100f, 0f, i*100f+100f, 100f)))
        }
        return r
    }
    private fun setup(l: L): GlideKeyboardController {
        val c = C()
        return GlideKeyboardController(registry(), GlideCoordinator(l, c)) { true }
    }

    @Test fun `短按当点按 不产生glide与查询`() {
        val l = L()
        val ctl = setup(l)
        ctl.onDown(1, 50f, 50f, host, schema)
        ctl.onMove(1, 52f, 51f, false)
        assertEquals(GlideKeyboardController.UpResult.Tap, ctl.onUp(1, host))
        assertNull(l.input)
        assertFalse(ctl.isGliding)
    }

    @Test fun `跨键滑动收集轨迹 抬手触发整句查询但不提交`() {
        val l = L(GlideLookupResult.Candidates(listOf(
            GlideCandidate("c0", "去我额", "qwe"))))
        val ctl = setup(l)
        ctl.onDown(1, 50f, 50f, host, schema)  // q
        ctl.onMove(1, 150f, 50f, false)          // w -> upgrade glide
        ctl.onMove(1, 250f, 50f, false)          // e
        assertTrue(ctl.isGliding)
        val up = ctl.onUp(1, host)
        assertTrue(up is GlideKeyboardController.UpResult.Candidates)
        assertTrue((up as GlideKeyboardController.UpResult.Candidates).outcome
            is GlideCoordinator.Outcome.ShowCandidates)
        assertEquals("qwe", l.input)
        assertTrue(ctl.coordinator().session.state.isComposing)
    }

    // 便捷：通过反射不可取，测试直接重建同 coordinator 验证未提交
    private fun GlideKeyboardController.coordinator(): GlideCoordinator {
        val f = GlideKeyboardController::class.java.getDeclaredField("coordinator")
        f.isAccessible = true
        return f.get(this) as GlideCoordinator
    }

    @Test fun `第二指取消不补字不查询`() {
        val l = L(GlideLookupResult.Candidates(listOf(GlideCandidate("c0", "去", "q"))))
        val ctl = setup(l)
        ctl.onDown(1, 50f, 50f, host, schema)
        ctl.onMove(1, 150f, 50f, false)
        ctl.onMove(1, 160f, 50f, secondPointer = true)
        assertEquals(GlideKeyboardController.UpResult.Discarded, ctl.onUp(1, host))
        assertNull(l.input)
    }

    @Test fun `系统取消清空轨迹并作废会话`() {
        val l = L()
        val ctl = setup(l)
        ctl.onDown(1, 50f, 50f, host, schema)
        ctl.onMove(1, 150f, 50f, false)
        ctl.onCancel()
        assertTrue(ctl.trail.isEmpty())
        assertFalse(ctl.allowFallbackClick())
        assertNull(l.input)
    }

    @Test fun `glide关闭时点按语义保持不变`() {
        val l = L()
        val ctl = GlideKeyboardController(registry(), GlideCoordinator(l, C())) { false }
        ctl.onDown(1, 50f, 50f, host, schema)
        ctl.onMove(1, 250f, 50f, false)
        // 功能关闭时 glide 不接管：返回 Idle，由原有点击链路处理（allowFallbackClick=true）
        assertEquals(GlideKeyboardController.UpResult.Idle, ctl.onUp(1, host))
        assertTrue(ctl.allowFallbackClick())
        assertNull(l.input)
    }
}
