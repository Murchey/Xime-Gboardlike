package com.kingzcheung.xime.glide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 候选去重的行为约束。
 *
 * lookupHypotheses 用 `seen` 按**文本**去重：同一文本只保留最先出现的那个，
 * 且是在遍历顺序（假设位次 × 候选位次）下"先到先得"，不是按最终分数取最优。
 * 这决定了一条文本能否进入候选池，值得用测试锁住。
 */
class GlideCandidateDedupTest {

    private class Lookup(private val byCode: Map<String, List<String>>) : GlideSentenceLookup {
        override fun lookup(rawInput: String, maxCount: Int): GlideLookupResult {
            val texts = byCode[rawInput] ?: return GlideLookupResult.NoCandidate
            return GlideLookupResult.Candidates(texts.mapIndexed { i, t ->
                GlideCandidate("c" + i + "_" + rawInput, t, rawInput)
            })
        }
    }

    private fun controller(lookup: GlideSentenceLookup): GlideKeyboardController {
        val registry = GlideGeometryRegistry()
        registry.beginFrame(1000f, 300f)
        listOf("qwertyuiop", "asdfghjkl", "zxcvbnm").forEachIndexed { r, row ->
            row.forEachIndexed { c, ch ->
                registry.register(GlideKey(ch.toString(), ch.toString(),
                    GlideKeyRect(c * 100f, r * 100f, c * 100f + 100f, r * 100f + 100f)))
            }
        }
        return GlideKeyboardController(registry, GlideCoordinator(lookup,
            object : GlideCommitter {
                override fun installRawInput(rawInput: String) = true
                override fun selectCandidate(rawInput: String, text: String) = true
            }), glideEnabled = { true })
    }

    private fun rank(ctl: GlideKeyboardController, hyps: List<GlideGeometryDecoder.Hypothesis>): List<String> {
        val m = GlideKeyboardController::class.java.getDeclaredMethod(
            "lookupHypotheses", java.util.List::class.java, String::class.java)
        m.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return (m.invoke(ctl, hyps, "") as List<GlideCandidate>).map { it.text }
    }

    @Test
    fun 相同文本应只出现一次() {
        // 两个不同编码假设都产出「你好」
        val ctl = controller(Lookup(mapOf("aa" to listOf("你好", "你"), "bb" to listOf("你好", "妮"))))
        ctl.gramScorer = { _, _ -> com.kingzcheung.xime.rime.RimeEngine.GRAM_UNAVAILABLE }
        val out = rank(ctl, listOf(
            GlideGeometryDecoder.Hypothesis("aa", 1f),
            GlideGeometryDecoder.Hypothesis("bb", 2f)))
        assertEquals("「你好」应只保留一条", 1, out.count { it == "你好" })
    }

    @Test
    fun 去重应保留分数更高的那条() {
        // 同一文本「甲」由两个假设产出：aa 代价 5（差），bb 代价 1（好）。
        // 旧实现"先到先得"会留下 aa 那条（分数更低）；正确做法是保留 bb 那条。
        val ctl = controller(Lookup(mapOf("aa" to listOf("甲"), "bb" to listOf("甲"))))
        ctl.gramScorer = { _, _ -> com.kingzcheung.xime.rime.RimeEngine.GRAM_UNAVAILABLE }
        val m = GlideKeyboardController::class.java.getDeclaredMethod(
            "lookupHypotheses", java.util.List::class.java, String::class.java)
        m.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val all = m.invoke(ctl, listOf(
            GlideGeometryDecoder.Hypothesis("aa", 5f),
            GlideGeometryDecoder.Hypothesis("bb", 1f)), "") as List<GlideCandidate>
        assertEquals("去重后只应剩一条", 1, all.count { it.text == "甲" })
        // id 里带 hIdx：应为 1（来自第二个假设 bb），而不是 0
        assertTrue("应保留分数更高的那条（hIdx=1），实际=" + all.first().id,
            all.first().id.startsWith("glide-1-"))
    }

    @Test
    fun 不同文本应全部保留() {
        val ctl = controller(Lookup(mapOf("aa" to listOf("甲", "乙"), "bb" to listOf("丙"))))
        ctl.gramScorer = { _, _ -> com.kingzcheung.xime.rime.RimeEngine.GRAM_UNAVAILABLE }
        val out = rank(ctl, listOf(
            GlideGeometryDecoder.Hypothesis("aa", 1f),
            GlideGeometryDecoder.Hypothesis("bb", 2f)))
        assertTrue("三条不同文本都应保留", out.containsAll(listOf("甲", "乙", "丙")))
    }

    @Test
    fun 前缀应拼接到查询串() {
        var lastQuery = ""
        val lookup = object : GlideSentenceLookup {
            override fun lookup(rawInput: String, maxCount: Int): GlideLookupResult {
                lastQuery = rawInput
                return GlideLookupResult.Candidates(listOf(GlideCandidate("c", "结果", rawInput)))
            }
        }
        val ctl = controller(lookup)
        ctl.gramScorer = { _, _ -> com.kingzcheung.xime.rime.RimeEngine.GRAM_UNAVAILABLE }
        val m = GlideKeyboardController::class.java.getDeclaredMethod(
            "lookupHypotheses", java.util.List::class.java, String::class.java)
        m.isAccessible = true
        m.invoke(ctl, listOf(GlideGeometryDecoder.Hypothesis("bb", 1f)), "aa")
        assertEquals("前缀应拼在编码前", "aabb", lastQuery)
    }
}
