package com.kingzcheung.xime.glide

import org.junit.Assert.*
import org.junit.Test

class GlideAppendCompositionTest {
    private class Fixture {
        val registry = GlideGeometryRegistry()
        val queries = mutableListOf<String>()
        val coordinator = GlideCoordinator(object : GlideSentenceLookup {
            override fun lookup(rawInput: String, maxCount: Int): GlideLookupResult {
                queries += rawInput
                val text = when (rawInput) {
                    "ni" -> "你"
                    "nihao" -> "你好"
                    "nihaoma" -> "你好吗"
                    else -> return GlideLookupResult.NoCandidate
                }
                return GlideLookupResult.Candidates(listOf(GlideCandidate(rawInput, text, rawInput)))
            }
        }, object : GlideCommitter {
            override fun installRawInput(rawInput: String) = true
            override fun selectCandidate(rawInput: String, text: String) = true
        })
        var prefix = ""
        val controller = GlideKeyboardController(registry, coordinator, glideEnabled = { true },
            schemaSpellings = { listOf("ni", "hao", "ma") }).apply {
            onGlideStart = { prefix }
        }
        var time = 1000L
        var last: Pair<Float, Float>? = null
        init {
            registry.beginFrame(1000f, 300f)
            listOf("qwertyuiop", "asdfghjkl", "zxcvbnm").forEachIndexed { r, row -> row.forEachIndexed { c, ch ->
                registry.register(GlideKey(ch.toString(), ch.toString(),
                    GlideKeyRect(c * 100f, r * 100f, c * 100f + 100f, r * 100f + 100f)))
            } }
        }
        fun swipe(code: String): GlideKeyboardController.DecodeResult {
            var id = time / 1000 + 1
            for (ch in code) {
                val k = registry.snapshot().keys.first { it.code == ch.toString() }
                val xy = k.rect.centerX to k.rect.centerY
                if (last == null) controller.onDown(id, xy.first, xy.second, "host", "schema", timeMs = time)
                else {
                    val p = last!!
                    for (s in 1..8) {
                        time += 16
                        val f = s / 9f
                        controller.onMove(id, p.first + (xy.first - p.first) * f, p.second + (xy.second - p.second) * f, false, time)
                    }
                    time += 16
                    controller.onMove(id, xy.first, xy.second, false, time)
                }
                time += 150
                last = xy
            }
            last = null
            val request = controller.finishRequest(id, "host")!!
            return controller.decode(request).also { controller.accept(it) }
        }
    }

    @Test fun 有候选时继续滑动用前缀加新编码整句查词() {
        val f = Fixture()
        val first = f.swipe("ni")
        assertEquals("你", first.candidates.single().text)
        assertEquals("ni", first.rawInput)
        f.prefix = "ni"
        val second = f.swipe("hao")
        assertTrue("查询=" + f.queries, f.queries.contains("nihao"))
        assertEquals("你好", second.candidates.single().text)
        assertEquals("nihao", second.rawInput)
        assertEquals("nihao", f.controller.rawInputForDisplay())
    }

    @Test fun 多次滑动持续累积编码与候选() {
        val f = Fixture()
        f.swipe("ni")
        f.prefix = "ni"
        f.swipe("hao")
        f.prefix = "nihao"
        val third = f.swipe("ma")
        assertTrue("查询=" + f.queries, f.queries.contains("nihaoma"))
        assertEquals("你好吗", third.candidates.single().text)
        assertEquals("nihaoma", f.controller.rawInputForDisplay())
    }

    @Test fun 追加失败保留已有候选不锁死() {
        val f = Fixture()
        f.swipe("ni")
        f.prefix = "ni"
        val bad = f.swipe("qw")
        assertTrue(bad.candidates.isEmpty())
        assertEquals("ni", bad.rawInput)
        // 有前缀但没解码结果时不取消会话，后续普通选词仍可用。
        assertEquals("ni", f.controller.rawInputForDisplay())
        assertTrue(f.controller.candidateTexts().contains("你"))
    }

    @Test fun 确认候选后重置会话不再追加旧编码() {
        val f = Fixture()
        f.swipe("ni")
        f.prefix = "ni"
        f.swipe("hao")
        f.controller.resetCompositionSession()
        f.prefix = ""
        val fresh = f.swipe("ma")
        assertEquals("ma", fresh.rawInput)
        assertFalse("查询=" + f.queries, f.queries.last().startsWith("nihao"))
    }

    @Test fun 英文滑动不追加中文候选前缀() {
        val f = Fixture()
        f.swipe("ni")
        f.prefix = "ni"
        f.controller.onDown(99, 50f, 50f, "host", "schema", ascii = true, timeMs = 5000)
        val request = f.controller.previewRequest()
        assertTrue(request == null || request!!.prefix.isEmpty())
    }
}
