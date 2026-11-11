package com.kingzcheung.xime.glide

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 验证 gram 真的能改变候选顺序（不只是"分数算出来了"）。
 *
 * 构造两个几何代价完全相同、只有 gram 分不同的候选：
 * 不接 gram 时按位次/假设先验排序，接入 gram 后应由语义决定。
 * 这补上了"分数算出 → 实际影响排序"这一环。
 */
class GlideGramRankingTest {

    private class Lookup(private val byCode: Map<String, List<String>>) : GlideSentenceLookup {
        override fun lookup(rawInput: String, maxCount: Int): GlideLookupResult {
            val texts = byCode[rawInput] ?: return GlideLookupResult.NoCandidate
            return GlideLookupResult.Candidates(texts.mapIndexed { i, t ->
                GlideCandidate("c" + i, t, rawInput)
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
        val r = m.invoke(ctl, hyps, "") as List<GlideCandidate>
        return r.map { it.text }
    }

    /** 两个编码完全同价，各自只产出一个候选。 */
    private fun twoHyps() = listOf(
        GlideGeometryDecoder.Hypothesis("aa", 5f),
        GlideGeometryDecoder.Hypothesis("bb", 5f),
    )

    @Test
    fun 无gram信号时按原有规则排序() {
        val ctl = controller(Lookup(mapOf("aa" to listOf("甲"), "bb" to listOf("乙"))))
        ctl.gramScorer = { _, _ -> com.kingzcheung.xime.rime.RimeEngine.GRAM_UNAVAILABLE }
        val order = rank(ctl, twoHyps())
        println("[gramrank] 无gram: " + order)
        // 几何同价时靠假设先验：第 0 个假设优先
        assertEquals("甲", order.first())
    }

    @Test
    fun gram优势能翻转同等几何的候选() {
        val ctl = controller(Lookup(mapOf("aa" to listOf("甲"), "bb" to listOf("乙"))))
        // 让"乙"的 gram 分明显更高（模拟"起来"压过"起凉"那种情况）
        ctl.gramScorer = { _, word -> if (word == "乙") -1.0 else -8.0 }
        ctl.onDown(1, 50f, 50f, "h", "s")
        val order = rank(ctl, twoHyps())
        println("[gramrank] gram偏好乙: " + order)
        assertEquals("乙", order.first())
    }

    @Test
    fun gram无法翻转明显的几何劣势() {
        val ctl = controller(Lookup(mapOf("aa" to listOf("甲"), "bb" to listOf("乙"))))
        // 乙 的 gram 略好，但几何代价远高
        ctl.gramScorer = { _, word -> if (word == "乙") -5.0 else -6.0 }
        ctl.onDown(1, 50f, 50f, "h", "s")
        val order = rank(ctl, listOf(
            GlideGeometryDecoder.Hypothesis("aa", 1f),
            GlideGeometryDecoder.Hypothesis("bb", 8f),
        ))
        println("[gramrank] 几何主导: " + order)
        assertEquals("几何代价低者应优先（gram 差值不足以翻转）", "甲", order.first())
    }
}
