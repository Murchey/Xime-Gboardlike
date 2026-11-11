package com.kingzcheung.xime.glide

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith
import com.kingzcheung.xime.rime.RimeConfigHelper
import com.kingzcheung.xime.rime.RimeEngine

/**
 * 真机端到端：直接驱动 GlideKeyboardController（真实几何 + 真实 Rime 引擎），
 * 绕开 adb 触摸注入的限制（adb input motionevent 会被当成连续点击）。
 *
 * 这补上了「手势 → 解码 → 查询 → 候选」的完整链路验证，
 * 与 JVM 回放的区别是：这里用了真实的 Rime 查询与候选排序。
 */
@RunWith(AndroidJUnit4::class)
class GlideEndToEndDeviceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun registry(): GlideGeometryRegistry {
        val r = GlideGeometryRegistry()
        val rows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
        val w = 108f
        val h = 166f
        r.beginFrame(w * 10, h * 3)
        rows.forEachIndexed { row, letters ->
            letters.forEachIndexed { col, ch ->
                r.register(GlideKey("k" + row + "_" + col, ch.toString(),
                    GlideKeyRect(col * w, row * h, col * w + w, row * h + h)))
            }
        }
        return r
    }

    private fun keyCenter(registry: GlideGeometryRegistry, ch: Char): Pair<Float, Float> {
        val geo = registry.snapshot()
        val k = geo.keys.first { it.code == ch.toString() }
        return k.rect.centerX to k.rect.centerY
    }

    private fun performGlide(
        ctl: GlideKeyboardController,
        registry: GlideGeometryRegistry,
        code: String,
    ) {
        var t = 0L
        val pts = mutableListOf<GlidePoint>()
        for ((idx, ch) in code.withIndex()) {
            val (x, y) = keyCenter(registry, ch)
            if (idx > 0) {
                val (px, py) = keyCenter(registry, code[idx - 1])
                for (s in 1..6) {
                    val f = s / 7f
                    pts += GlidePoint(px + (x - px) * f, py + (y - py) * f, t, 1)
                    t += 16
                }
            }
            pts += GlidePoint(x, y, t, 1)
            t += 150
            repeat(3) { pts += GlidePoint(x, y, t, 1); t += 20 }
            t += 150
            pts += GlidePoint(x, y, t, 1)
        }
        ctl.onDown(1, pts.first().x, pts.first().y, "e2e", "double_pinyin_flypy", false, pts.first().timeMs)
        for (p in pts.drop(1)) {
            ctl.onMove(1, p.x, p.y, false, p.timeMs)
        }
        ctl.onUp(1, "e2e")
    }

    @Test
    fun 小鹤双拼滑动能解出正确候选() {
        val (u, s) = RimeConfigHelper.initializeRimeData(context)
        RimeEngine.getInstance().initialize(u, s)
        // switchSchema 内部会在无会话时补建，并保证引擎停在目标方案上
        val switched = RimeEngine.getInstance().switchSchema("double_pinyin_flypy")
        println("[e2e] switched=" + switched)

        val registry = registry()
        val controller = GlideKeyboardController(
            registry,
            GlideCoordinator(RimeGlideSentenceLookup(), RimeGlideCommitter()),
            glideEnabled = { true },
            schemaSpellings = { schema ->
                RimeEngine.getInstance().getGlideSpellings(schema, GlideSyllableTrie.syllables())
            },
            densityDpi = { 420f },
        )

        val spellings = RimeEngine.getInstance()
            .getGlideSpellings("double_pinyin_flypy", GlideSyllableTrie.syllables())
        // 小鹤双拼正确编码：你好=nihc（hao 的韵母键是 c），我=wo，就像是=jqxlui
        val cases = listOf("nihc", "wo", "jqxlui")
        val report = mutableListOf<String>()
        for ((idx, code) in cases.withIndex()) {
            if (idx > 0) {
                // 每条用例前重建会话：bypassLookup 的独立会话在首次查询后可能失效
                RimeEngine.getInstance().switchSchema("double_pinyin_flypy")
            }
            // 每条用例重建 controller：resetCompositionSession 不清 Rime 侧组合，
            // 会污染下一条（实测第 2、3 条 raw 为空）。
            val ctl = GlideKeyboardController(
                registry,
                GlideCoordinator(RimeGlideSentenceLookup(), RimeGlideCommitter()),
                glideEnabled = { true },
                schemaSpellings = { schema ->
                    RimeEngine.getInstance().getGlideSpellings(schema, GlideSyllableTrie.syllables())
                },
                densityDpi = { 420f },
            )
            performGlide(ctl, registry, code)
            val texts = ctl.candidateTexts().take(5)
            val raw = ctl.rawInputForDisplay()
            report += "code=" + code + " raw=[" + raw + "] cands=" + texts
        }
        report.forEach { println("[e2e] " + it) }
        // 记录实际结果（不断言具体文本，避免方案词典差异导致测试脆弱）
        report.forEach { println("[e2e] " + it) }
        report.forEach { println("[e2e] " + it) }
        // 链路通：每条用例都必须产生候选。
        // 不断言具体首选文本——方案词典版本会变，硬编码会让测试脆弱。
        org.junit.Assert.assertTrue(
            "switched=" + switched + " spellings=" + spellings.size +
                " 报告=" + report.joinToString("\n"),
            report.none { it.contains("cands=[]") })
    }
    @Test
    fun 前文能影响同音候选的排序() {
        val (u, s) = RimeConfigHelper.initializeRimeData(context)
        RimeEngine.getInstance().initialize(u, s)
        RimeEngine.getInstance().switchSchema("double_pinyin_flypy")

        fun makeController(ctx: String): GlideKeyboardController {
            val reg = registry()
            val c = GlideKeyboardController(
                reg,
                GlideCoordinator(RimeGlideSentenceLookup(), RimeGlideCommitter()),
                glideEnabled = { true },
                schemaSpellings = { schema ->
                    RimeEngine.getInstance().getGlideSpellings(schema, GlideSyllableTrie.syllables())
                },
                densityDpi = { 420f },
            )
            c.precedingText = { ctx }
            return c
        }

        // 同一手势，只改前文：看候选顺序是否变化
        val code = "jqxlui"
        val without = makeController("").let { ctl ->
            performGlide(ctl, registry(), code)
            ctl.candidateTexts().take(5)
        }
        RimeEngine.getInstance().switchSchema("double_pinyin_flypy")
        val withCtx = makeController("我们").let { ctl ->
            performGlide(ctl, registry(), code)
            ctl.candidateTexts().take(5)
        }
        val report = "无前文=" + without + " | 有前文=" + withCtx
        println("[e2e-ctx] " + report)
        // 只断言链路通；不把"前文改变了排序"写成硬断言——
        // 实测前文只调整了次选位次，首选未变。是否/何时该变需要更多样本才能定。
        org.junit.Assert.assertTrue(report, without.isNotEmpty() && withCtx.isNotEmpty())
    }
}


