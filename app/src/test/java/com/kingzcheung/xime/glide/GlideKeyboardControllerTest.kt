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
    /** 新解码器要求 26 键齐全（音节前缀树按 a..z 索引）。 */
    private fun registry(): GlideGeometryRegistry {
        val r = GlideGeometryRegistry()
        r.beginFrame(1000f, 300f)
        val rows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
        rows.forEachIndexed { row, letters ->
            letters.forEachIndexed { col, c ->
                val left = col * 100f
                val top = row * 100f
                r.register(GlideKey("k" + row + "_" + col, c.toString(),
                    GlideKeyRect(left, top, left + 100f, top + 100f)))
            }
        }
        return r
    }

    /** 依次经过每个字母键中心，键上停留（新解码器靠停留判定确认按键）。 */
    private fun swipe(ctl: GlideKeyboardController, code: String) {
        var t = 0L
        var prev: Pair<Float, Float>? = null
        var first = true
        for (ch in code) {
            val idx = "qwertyuiopasdfghjklzxcvbnm".indexOf(ch)
            val col = idx % 10
            val row = idx / 10
            val cx = col * 100f + 50f
            val cy = row * 100f + 50f
            prev?.let { p ->
                for (k in 1..5) {
                    val f = k / 6f
                    if (first) { first = false }
                    ctl.onMove(1, p.first + (cx - p.first) * f, p.second + (cy - p.second) * f, false)
                    t += 16
                }
            }
            if (first) { ctl.onDown(1, cx, cy, host, schema); first = false }
            else ctl.onMove(1, cx, cy, false)
            t += 150
            prev = cx to cy
        }
    }
    private fun setup(l: L): GlideKeyboardController {
        val c = C()
        return GlideKeyboardController(registry(), GlideCoordinator(l, c), glideEnabled = { true })
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
            GlideCandidate("c0", "你好", "nihao"))))
        val ctl = setup(l)
        swipe(ctl, "nihao")
        assertTrue(ctl.isGliding)
        val up = ctl.onUp(1, host)
        assertTrue(up is GlideKeyboardController.UpResult.Candidates)
        assertTrue((up as GlideKeyboardController.UpResult.Candidates).outcome
            is GlideCoordinator.Outcome.ShowCandidates)
        assertNotNull("应至少查询一次", l.input)
        assertTrue(l.input!!.isNotEmpty())
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
        val ctl = GlideKeyboardController(registry(), GlideCoordinator(l, C()), glideEnabled = { false })
        ctl.onDown(1, 50f, 50f, host, schema)
        ctl.onMove(1, 250f, 50f, false)
        // 功能关闭时 glide 不接管：返回 Idle，由原有点击链路处理（allowFallbackClick=true）
        assertEquals(GlideKeyboardController.UpResult.Idle, ctl.onUp(1, host))
        assertTrue(ctl.allowFallbackClick())
        assertNull(l.input)
    }
}
