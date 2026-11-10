package com.kingzcheung.xime.glide

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.rime.RimeEngine
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** 使用设备已部署方案，验证 native algebra 与 Kotlin 搜索间的编码一致性。 */
@RunWith(AndroidJUnit4::class)
class GlideSchemaProjectionDeviceTest {
    private fun engine(): RimeEngine {
        val end = SystemClock.uptimeMillis() + 30000
        while (!RimeEngine.isInitialized() && SystemClock.uptimeMillis() < end) SystemClock.sleep(100)
        assertTrue("Rime 未就绪", RimeEngine.isInitialized())
        val engine = RimeEngine.getInstance()
        assertTrue("Rime 会话未就绪", engine.ensureSession())
        return engine
    }

    @Test fun 微软双拼投影产生真实编码并排除简拼() {
        val e = engine()
        assertEquals(listOf("hk"), e.getGlideSpellings("double_pinyin_mspy", listOf("hao")))
        assertEquals(listOf("x;"), e.getGlideSpellings("double_pinyin_mspy", listOf("xing")))
        val codes = e.getGlideSpellings("double_pinyin_mspy", GlideSyllableTrie.syllables())
        assertTrue(codes.containsAll(listOf("ni", "hk", "x;", "b;")))
        assertFalse("不能把简拼当作完整音节", codes.contains("n"))
        assertFalse("不能把全拼编码混入双拼", codes.contains("hao"))
    }

    @Test fun 真实双拼编码树处理连续轨迹并查到你好() {
        val e = engine()
        val original = e.getCurrentSchema()
        val rows = listOf("qwertyuiop", "asdfghjkl", ";zxcvbnm")
        val keys = rows.flatMapIndexed { r, row -> row.mapIndexed { c, ch ->
            GlideKey(ch.toString(), ch.toString(), GlideKeyRect(c * 100f, r * 100f, c * 100f + 100f, r * 100f + 100f))
        } }
        val geometry = GlideGeometrySnapshot("device", keys, 1000f, 300f)
        try {
            for (schema in listOf("double_pinyin_mspy", "double_pinyin_sogou")) {
                val code = e.getGlideSpellings(schema, listOf("ni")).first() +
                    e.getGlideSpellings(schema, listOf("hao")).first()
                val spellings = e.getGlideSpellings(schema, GlideSyllableTrie.syllables())
                var time = 0L
                val points = mutableListOf<GlidePoint>()
                for (ch in code) {
                    val key = keys.first { it.code == ch.toString() }
                    points.lastOrNull()?.let { prev ->
                        for (step in 1..8) {
                            val f = step / 9f
                            points += GlidePoint(prev.x + (key.rect.centerX - prev.x) * f,
                                prev.y + (key.rect.centerY - prev.y) * f, time, 1)
                            time += 16
                        }
                    }
                    points += GlidePoint(key.rect.centerX, key.rect.centerY, time, 1)
                    time += 150
                }
                val hypotheses = GlideGeometryDecoder.decode(points, geometry, 16, spellings)
                assertTrue(schema + " 正确编码未进前16: " + hypotheses,
                    hypotheses.any { it.letters == code })
                assertTrue(e.switchSchema(schema))
                assertTrue(e.bypassLookup(code, 16).any { it.text == "你好" })
            }
        } finally {
            e.clearComposition()
            if (original.isNotEmpty()) e.switchSchema(original)
        }
    }

    @Test fun 滑动输入的双u局部折返能保留整句编码() {
        val e = engine()
        val schema = "double_pinyin_flypy"
        val spellings = e.getGlideSpellings(schema, GlideSyllableTrie.syllables())
        val rows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
        val keys = rows.flatMapIndexed { r, row -> row.mapIndexed { c, ch ->
            val x = c * 100f + listOf(0f, 50f, 150f)[r]
            GlideKey(ch.toString(), ch.toString(), GlideKeyRect(x, r * 140f, x + 100f, r * 140f + 140f))
        } }
        val geometry = GlideGeometrySnapshot("device", keys, 1000f, 420f)
        val code = "hxdsuuru"
        assertTrue("设备方案必须支持用户给出的编码", spellings.containsAll(listOf("hx", "ds", "uu", "ru")))
        val points = mutableListOf<GlidePoint>()
        var time = 0L
        var previous: Char? = null
        for (ch in code) {
            val key = keys.first { it.code == ch.toString() }
            if (previous == ch) {
                for (dy in listOf(-21f, -42f, -21f, 0f)) {
                    time += 24
                    points += GlidePoint(key.rect.centerX, key.rect.centerY + dy, time, 1)
                }
            } else {
                points.lastOrNull()?.let { prev -> for (step in 1..8) {
                    time += 16
                    val f = step / 9f
                    points += GlidePoint(prev.x + (key.rect.centerX - prev.x) * f,
                        prev.y + (key.rect.centerY - prev.y) * f, time, 1)
                } }
                time += 16
                points += GlidePoint(key.rect.centerX, key.rect.centerY, time, 1)
            }
            previous = ch
        }
        val result = GlideGeometryDecoder.decode(points, geometry, 16, spellings)
        assertTrue("完整编码未进入候选: " + result, result.any { it.letters == code })
        val original = e.getCurrentSchema()
        try {
            assertTrue(e.switchSchema(schema))
            val candidates = e.bypassLookup(code, 16)
            assertTrue("未查到滑动输入: " + candidates.map { it.text }, candidates.any { it.text == "滑动输入" })
        } finally {
            e.clearComposition()
            if (original.isNotEmpty()) e.switchSchema(original)
        }
    }

    @Test fun 小鹤滑动中先预览你再组词你好而不提交主组合() {
        val e = engine()
        val original = e.getCurrentSchema()
        val registry = GlideGeometryRegistry()
        registry.beginFrame(1000f, 420f)
        listOf("qwertyuiop", "asdfghjkl", "zxcvbnm").forEachIndexed { r, row -> row.forEachIndexed { c, ch ->
            val x = c * 100f + listOf(0f, 50f, 150f)[r]
            registry.register(GlideKey(ch.toString(), ch.toString(), GlideKeyRect(x, r * 140f, x + 100f, r * 140f + 140f)))
        } }
        try {
            assertTrue(e.switchSchema("double_pinyin_flypy"))
            e.clearComposition()
            val controller = GlideKeyboardController(registry, GlideCoordinator(
                RimeGlideSentenceLookup(), object : GlideCommitter {
                    override fun installRawInput(rawInput: String): Boolean = error("预览不应提交")
                    override fun selectCandidate(rawInput: String, text: String): Boolean = error("预览不应提交")
                }), glideEnabled = { true }, schemaSpellings = {
                    e.getGlideSpellings(it, GlideSyllableTrie.syllables())
                })
            var time = 1000L
            var prev: Pair<Float, Float>? = null
            fun move(code: String) {
                for (ch in code) {
                    val k = registry.snapshot().keys.first { it.code == ch.toString() }
                    val xy = k.rect.centerX to k.rect.centerY
                    if (prev == null) controller.onDown(1, xy.first, xy.second, "host", "double_pinyin_flypy", timeMs = time)
                    else {
                        val p = prev!!
                        for (step in 1..8) {
                            time += 16
                            val f = step / 9f
                            controller.onMove(1, p.first + (xy.first - p.first) * f, p.second + (xy.second - p.second) * f, false, time)
                        }
                        time += 16
                        controller.onMove(1, xy.first, xy.second, false, time)
                    }
                    time += 150
                    prev = xy
                }
            }
            move("ni")
            val first = controller.decode(controller.previewRequest()!!)
            assertTrue("预览缺少你: " + first.candidates.map { it.text }, first.candidates.any { it.text == "你" })
            assertTrue(controller.accept(first))
            assertEquals("预览不应安装 Rime 主组合", "", e.getInput())
            assertTrue(controller.isGliding)
            move("hc")
            val phrase = controller.decode(controller.previewRequest()!!)
            assertTrue("预览缺少你好: " + phrase.candidates.map { it.text }, phrase.candidates.any { it.text == "你好" })
            assertTrue(controller.accept(phrase))
            assertEquals("组词预览不应安装 Rime 主组合", "", e.getInput())
            val request = controller.finishRequest(1, "host")!!
            assertFalse(controller.accept(first))
            assertTrue(controller.accept(controller.decode(request)))
            assertTrue(controller.candidateTexts().contains("你好"))
        } finally {
            e.clearComposition()
            if (original.isNotEmpty()) e.switchSchema(original)
        }
    }

    @Test fun 微软搜狗已部署方案都能查询你好() {
        val e = engine()
        val original = e.getCurrentSchema()
        try {
            for (schema in listOf("double_pinyin_mspy", "double_pinyin_sogou")) {
                val ni = e.getGlideSpellings(schema, listOf("ni"))
                val hao = e.getGlideSpellings(schema, listOf("hao"))
                assertTrue(schema + " 缺少 ni 编码", ni.isNotEmpty())
                assertTrue(schema + " 缺少 hao 编码", hao.isNotEmpty())
                assertTrue(e.switchSchema(schema))
                val candidates = e.bypassLookup(ni.first() + hao.first(), 16)
                assertTrue(schema + " 未查到你好", candidates.any { it.text == "你好" })
            }
        } finally {
            e.clearComposition()
            if (original.isNotEmpty()) e.switchSchema(original)
        }
    }
}
