package com.kingzcheung.xime.glide

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 记录一个容易误判为 bug 的行为：gramBaseline 跨批沿用不会改变排序。
 *
 * gramDeltaFor 以「本批首个候选的 gram 分」为基准转成相对增量。
 * 一次手势里预览会多次调用 lookupHypotheses，若 baseline 沿用上批的值，
 * 只是给本批所有候选加了一个**相同的常数偏移**；
 * 而排序依据是批内分数差，常数会抵消，所以结果不变。
 *
 * 实测（真机端到端回放 16 条）：在 lookupHypotheses 入口重置 baseline 与
 * 不重置，指标都是 6/16、平均距离 1.44。
 * 因此不必做这个"修复"——它不解决任何实际问题。
 */
class GlideGramBaselineScopeTest {

    private class Lookup : GlideSentenceLookup {
        override fun lookup(rawInput: String, maxCount: Int): GlideLookupResult =
            GlideLookupResult.Candidates(listOf(
                GlideCandidate("a", "甲", rawInput),
                GlideCandidate("b", "乙", rawInput)))
    }

    private fun controller(): GlideKeyboardController {
        val registry = GlideGeometryRegistry()
        registry.beginFrame(1000f, 300f)
        listOf("qwertyuiop", "asdfghjkl", "zxcvbnm").forEachIndexed { r, row ->
            row.forEachIndexed { c, ch ->
                registry.register(GlideKey(ch.toString(), ch.toString(),
                    GlideKeyRect(c * 100f, r * 100f, c * 100f + 100f, r * 100f + 100f)))
            }
        }
        return GlideKeyboardController(registry, GlideCoordinator(Lookup(),
            object : GlideCommitter {
                override fun installRawInput(rawInput: String) = true
                override fun selectCandidate(rawInput: String, text: String) = true
            }), glideEnabled = { true })
    }

    private fun scores(ctl: GlideKeyboardController, code: String): List<Double> {
        // 必须走一次 onDown：gramDeltaFor 在 currentSchema 为空时直接返回 0，
        // 只调 lookupHypotheses 会绕过 gram，测出来的分差全来自位次惩罚。
        ctl.onDown(1, 10f, 10f, "test", "double_pinyin_flypy")
        val m = GlideKeyboardController::class.java.getDeclaredMethod(
            "lookupHypotheses", java.util.List::class.java, String::class.java)
        m.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return (m.invoke(ctl, listOf(GlideGeometryDecoder.Hypothesis(code, 1f)), "")
            as List<GlideCandidate>).map { it.score }
    }

    @Test
    fun 跨批基准偏移不改变批内分差() {
        val ctl = controller()
        val table = mapOf("甲" to -2.0, "乙" to -8.0)
        ctl.gramScorer = { _, word -> table[word] ?: -20.0 }
        val first = scores(ctl, "aa")
        val second = scores(ctl, "bb")
        // 同一 gram 分布下，两批的批内分差应完全一致
        assertEquals(first[0] - first[1], second[0] - second[1], 1e-9)
    }

    @Test
    fun gram分差确实体现在候选分上() {
        val ctl = controller()
        val table = mapOf("甲" to -2.0, "乙" to -8.0)
        ctl.gramScorer = { _, word -> table[word] ?: -20.0 }
        val s = scores(ctl, "aa")
        // 「甲」比「乙」gram 高 6.0，分数应高约 6.0（其余项相同）
        // 「甲」比「乙」gram 高 6.0，另叠加位次惩罚 0.4 → 分差约 6.4
        assertEquals(6.4, s[0] - s[1], 1e-6)
    }
}
