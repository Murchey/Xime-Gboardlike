package com.kingzcheung.xime.glide

import org.junit.Assert.*
import org.junit.Test

class GlidePhraseRankingTest {
    private class Lookup : GlideSentenceLookup {
        override fun lookup(rawInput: String, maxCount: Int) = when (rawInput) {
            "niuogfme" -> GlideLookupResult.Candidates(listOf(
                GlideCandidate("a", "你说跟么", rawInput),
                GlideCandidate("b", "你说", rawInput),
                GlideCandidate("c", "你", rawInput),
            ))
            "niuoufme" -> GlideLookupResult.Candidates(listOf(
                GlideCandidate("d", "你说什么", rawInput),
                GlideCandidate("e", "你说什么呢", rawInput),
                GlideCandidate("f", "你说什么呀", rawInput),
                GlideCandidate("g", "你说什么啊", rawInput),
            ))
            else -> GlideLookupResult.NoCandidate
        }
    }

    @Test fun 整句短语簇排在碎词假设之前() {
        val registry = GlideGeometryRegistry()
        registry.beginFrame(1000f, 300f)
        listOf("qwertyuiop", "asdfghjkl", "zxcvbnm").forEachIndexed { r, row -> row.forEachIndexed { c, ch ->
            registry.register(GlideKey(ch.toString(), ch.toString(),
                GlideKeyRect(c * 100f, r * 100f, c * 100f + 100f, r * 100f + 100f)))
        } }
        val c = GlideKeyboardController(registry, GlideCoordinator(Lookup(), object : GlideCommitter {
            override fun installRawInput(rawInput: String) = true
            override fun selectCandidate(rawInput: String, text: String) = true
        }), glideEnabled = { true })
        // 直接构造两个假设，错误假设几何代价更低但只产出碎词。
        val method = GlideKeyboardController::class.java.getDeclaredMethod(
            "lookupHypotheses", java.util.List::class.java, String::class.java)
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val result = method.invoke(c, listOf(
            GlideGeometryDecoder.Hypothesis("niuogfme", 1f),
            GlideGeometryDecoder.Hypothesis("niuoufme", 2f)), "") as List<GlideCandidate>
        assertEquals("你说什么", result.first().text)
    }
}
