package com.kingzcheung.xime.glide

import org.junit.Assert.*
import org.junit.Test

class GlideSchemaSpellingTest {
    private fun accepts(trie: GlideSpellingConstraint, code: String): Boolean {
        var states: List<Int> = listOf(0)
        for (ch in code) {
            val next = mutableListOf<Int>()
            trie.extend(states, if (ch == ';') 26 else ch - 'a', next)
            states = next
        }
        return 0 in states
    }

    @Test fun 微软双拼合法编码不套用全拼音节树() {
        val trie = GlideSpellingTrie(listOf("ni", "hk", "x;", "b;"))
        assertTrue(accepts(trie, "nihk"))
        assertTrue(accepts(trie, "x;b;"))
        assertFalse(accepts(trie, "nihao"))
        assertFalse(accepts(trie, "nih"))
    }

    @Test fun 方案切换不会混用两套韵母映射() {
        assertTrue(accepts(GlideSpellingTrie(listOf("hk")), "hk"))
        assertFalse(accepts(GlideSpellingTrie(listOf("hz")), "hk"))
        assertTrue(accepts(GlideSpellingTrie(listOf("hz")), "hz"))
    }

    private fun geometry(): GlideGeometrySnapshot {
        val rows = listOf("qwertyuiop", "asdfghjkl", ";zxcvbnm")
        val keys = rows.flatMapIndexed { r, letters -> letters.mapIndexed { c, ch ->
            GlideKey("$" + ch, ch.toString(), GlideKeyRect(c * 100f, r * 100f, c * 100f + 100f, r * 100f + 100f))
        } }
        return GlideGeometrySnapshot("t", keys, 1000f, 300f)
    }

    private fun trace(g: GlideGeometrySnapshot, code: String): List<GlidePoint> {
        val out = mutableListOf<GlidePoint>()
        var time = 0L
        for (ch in code) {
            val key = g.keys.first { it.code == ch.toString() }
            val x = key.rect.centerX
            val y = key.rect.centerY
            out.lastOrNull()?.let { p ->
                for (step in 1..8) {
                    val f = step / 9f
                    out += GlidePoint(p.x + (x - p.x) * f, p.y + (y - p.y) * f, time, 1)
                    time += 16
                }
            }
            out += GlidePoint(x, y, time, 1)
            time += 150
        }
        return out
    }

    @Test fun 滑动保留真实双拼编码而非邻近全拼组合() {
        val g = geometry()
        val result = GlideGeometryDecoder.decode(trace(g, "nihk"), g, 16, listOf("ni", "hk", "x;", "b;"))
        assertTrue("实际=" + result, result.take(5).any { it.letters == "nihk" })
        val trie = GlideSpellingTrie(listOf("ni", "hk", "x;", "b;"))
        assertTrue(result.all { accepts(trie, it.letters) })
    }

    @Test fun 分号直接输出方案编码不展开为全拼() {
        val g = geometry()
        val result = GlideGeometryDecoder.decode(trace(g, "x;"), g, 16, listOf("x;", "b;"))
        assertEquals("x;", result.firstOrNull()?.letters)
        assertTrue(result.none { "ing" in it.letters })
    }

    @Test fun 控制器查询当前方案编码而非全拼或途经键串() {
        val g = geometry()
        val registry = GlideGeometryRegistry()
        registry.beginFrame(g.widthPx, g.heightPx)
        g.keys.forEach { registry.register(it) }
        val queried = mutableListOf<String>()
        val spells = listOf("ni", "hk", "x;", "b;")
        var loads = 0
        val controller = GlideKeyboardController(registry, GlideCoordinator(
            object : GlideSentenceLookup {
                override fun lookup(rawInput: String, maxCount: Int): GlideLookupResult {
                    queried += rawInput
                    return GlideLookupResult.NoCandidate
                }
            }, object : GlideCommitter {
                override fun installRawInput(rawInput: String) = false
                override fun selectCandidate(rawInput: String, text: String) = false
            }), glideEnabled = { true }, schemaSpellings = { schema ->
                assertEquals("mspy", schema)
                loads++
                spells
            })
        val pts = trace(g, "nihk")
        controller.onDown(1, pts.first().x, pts.first().y, "host", "mspy")
        pts.drop(1).forEach { controller.onMove(1, it.x, it.y, false) }
        controller.onUp(1, "host")
        assertEquals("每次轨迹只读取一次编码集合", 1, loads)
        assertTrue("实际查询=" + queried, queried.contains("nihk"))
        assertTrue(queried.all { accepts(GlideSpellingTrie(spells), it) })
    }

    @Test fun 未取得方案编码时不静默回退为全拼() {
        val g = geometry()
        assertTrue(GlideGeometryDecoder.decode(trace(g, "nihao"), g, 16, emptyList()).isEmpty())
    }
}
