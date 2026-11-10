package com.kingzcheung.xime.glide

import org.junit.Assert.*
import org.junit.Test

class GlideLivePreviewTest {
    private class Fixture {
        val registry = GlideGeometryRegistry()
        val queried = mutableListOf<String>()
        var commits = 0
        val coordinator = GlideCoordinator(object : GlideSentenceLookup {
            override fun lookup(rawInput: String, maxCount: Int): GlideLookupResult {
                queried += rawInput
                val text = when (rawInput) { "ni" -> "你"; "nihao" -> "你好"; else -> return GlideLookupResult.NoCandidate }
                return GlideLookupResult.Candidates(listOf(GlideCandidate(rawInput, text, rawInput)))
            }
        }, object : GlideCommitter {
            override fun installRawInput(rawInput: String): Boolean { commits++; return true }
            override fun selectCandidate(rawInput: String, text: String): Boolean { commits++; return true }
        })
        val controller = GlideKeyboardController(registry, coordinator, glideEnabled = { true },
            schemaSpellings = { listOf("ni", "hao") })
        var time = 1000L
        var last: Pair<Float, Float>? = null
        init {
            registry.beginFrame(1000f, 300f)
            listOf("qwertyuiop", "asdfghjkl", "zxcvbnm").forEachIndexed { r, row -> row.forEachIndexed { c, ch ->
                registry.register(GlideKey(ch.toString(), ch.toString(), GlideKeyRect(c * 100f, r * 100f, c * 100f + 100f, r * 100f + 100f)))
            } }
        }
        fun move(code: String) {
            for (ch in code) {
                val key = registry.snapshot().keys.first { it.code == ch.toString() }
                val xy = key.rect.centerX to key.rect.centerY
                if (last == null) controller.onDown(1, xy.first, xy.second, "host", "schema", timeMs = time)
                else {
                    val prev = last!!
                    for (step in 1..8) {
                        time += 16
                        val f = step / 9f
                        controller.onMove(1, prev.first + (xy.first - prev.first) * f, prev.second + (xy.second - prev.second) * f, false, time)
                    }
                    time += 16
                    controller.onMove(1, xy.first, xy.second, false, time)
                }
                time += 150
                last = xy
            }
        }
    }

    @Test fun 抬手前随完整音节开始组词但不提交() {
        val f = Fixture()
        f.move("ni")
        val first = f.controller.decode(f.controller.previewRequest()!!)
        assertTrue(first.candidates.any { it.text == "你" })
        assertTrue(f.controller.accept(first))
        assertEquals(GlideCompositionSession.Phase.COLLECTING, f.coordinator.session.state.phase)
        assertTrue(f.coordinator.session.state.candidates.isEmpty())
        f.move("hao")
        val phrase = f.controller.decode(f.controller.previewRequest()!!)
        assertTrue("实际=" + phrase, phrase.candidates.any { it.text == "你好" })
        assertEquals(0, f.commits)
    }

    @Test fun 抬手后旧预览不能覆盖正式候选() {
        val f = Fixture()
        f.move("ni")
        val preview = f.controller.decode(f.controller.previewRequest()!!)
        f.move("hao")
        val finalRequest = f.controller.finishRequest(1, "host")!!
        assertFalse(f.controller.accept(preview))
        val final = f.controller.decode(finalRequest)
        assertTrue(f.controller.accept(final))
        assertEquals("nihao", f.controller.rawInputForDisplay())
        assertTrue(f.controller.candidateTexts().contains("你好"))
        assertEquals(GlideCompositionSession.Phase.PENDING_CONFIRM, f.coordinator.session.state.phase)
        assertEquals(0, f.commits)
    }

    @Test fun 新一笔拒绝上一笔后台结果() {
        val f = Fixture()
        f.move("ni")
        val request = f.controller.finishRequest(1, "host")!!
        val result = f.controller.decode(request)
        f.controller.onDown(2, 50f, 50f, "host", "schema", timeMs = 2000)
        assertFalse(f.controller.accept(result))
    }

    @Test fun 取消后不显示迟到预览或最终结果() {
        val f = Fixture()
        f.move("ni")
        val result = f.controller.decode(f.controller.previewRequest()!!)
        f.controller.onCancel()
        assertFalse(f.controller.accept(result))
        assertNull(f.controller.previewRequest())
        assertEquals(0, f.commits)
    }

    @Test fun 普通点按不启动候选预览() {
        val f = Fixture()
        f.move("n")
        assertNull(f.controller.previewRequest())
        assertNull(f.controller.finishRequest(1, "host"))
        assertTrue(f.queried.isEmpty())
    }
}
