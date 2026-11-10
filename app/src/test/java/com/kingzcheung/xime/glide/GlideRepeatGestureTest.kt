package com.kingzcheung.xime.glide

import org.junit.Assert.*
import org.junit.Test

class GlideRepeatGestureTest {
    private fun points(vararg y: Float) = y.mapIndexed { i, value -> GlidePoint(0f, value, i * 24L, 1) }
    private val centers = listOf(0f to 0f, 1f to 0f)

    @Test fun 同键短距离上下往复计一次重复() {
        val cues = GlideRepeatGesture.detect(points(0f, -0.15f, -0.3f, -0.15f, 0f), centers)
        assertEquals(1, cues.size)
        assertEquals(0, cues.single().key)
    }
    @Test fun 静止和轻微抖动不计重复() {
        assertTrue(GlideRepeatGesture.detect(points(0f, 0f, 0f, 0f), centers).isEmpty())
        assertTrue(GlideRepeatGesture.detect(points(0f, -0.06f, 0.06f, 0f), centers).isEmpty())
    }
    @Test fun 单向经过不计重复() {
        assertTrue(GlideRepeatGesture.detect(points(0.4f, 0.2f, 0f, -0.2f, -0.4f), centers).isEmpty())
    }
    @Test fun 离开键位到其他键再回来不计局部重复() {
        val pts = listOf(0f, 0.4f, 0.8f, 1f, 0.4f, 0f).mapIndexed { i, x -> GlidePoint(x, 0f, i * 24L, 1) }
        assertTrue(GlideRepeatGesture.detect(pts, centers).isEmpty())
    }
    @Test fun 从键边缘进入键中心再原路离开不计重复() {
        assertTrue(GlideRepeatGesture.detect(points(0.4f, 0.2f, 0f, 0.2f, 0.4f), centers).isEmpty())
    }
    @Test fun 两次折返不重复消费同一段() {
        val cues = GlideRepeatGesture.detect(points(0f, -0.3f, 0f, -0.3f, 0f), centers)
        assertEquals(2, cues.size)
        assertTrue(cues[1].startArc >= cues[0].endArc)
    }

    private fun geometry(): GlideGeometrySnapshot {
        val rows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
        val keys = rows.flatMapIndexed { r, row -> row.mapIndexed { c, ch ->
            val left = c * 100f + listOf(0f, 50f, 150f)[r]
            GlideKey(ch.toString(), ch.toString(), GlideKeyRect(left, r * 140f, left + 100f, r * 140f + 140f))
        } }
        return GlideGeometrySnapshot("test", keys, 1000f, 420f)
    }

    private fun phraseTrace(g: GlideGeometrySnapshot, loop: Boolean): List<GlidePoint> {
        val out = mutableListOf<GlidePoint>()
        var time = 0L
        var prev: Char? = null
        for (ch in "hxdsuuru") {
            val k = g.keys.first { it.code == ch.toString() }
            val x = k.rect.centerX
            val y = k.rect.centerY
            if (ch == prev) {
                if (loop) for (dy in listOf(-21f, -42f, -21f, 0f)) {
                    time += 24
                    out += GlidePoint(x, y + dy, time, 1)
                }
            } else {
                out.lastOrNull()?.let { p -> for (step in 1..8) {
                    time += 16
                    val f = step / 9f
                    out += GlidePoint(p.x + (x - p.x) * f, p.y + (y - p.y) * f, time, 1)
                } }
                time += 16
                out += GlidePoint(x, y, time, 1)
            }
            prev = ch
        }
        return out
    }

    @Test fun 控制器保留触摸事件时间而非处理时刻() {
        val g = geometry()
        val registry = GlideGeometryRegistry()
        registry.beginFrame(g.widthPx, g.heightPx)
        g.keys.forEach { registry.register(it) }
        val controller = GlideKeyboardController(registry, GlideCoordinator(
            object : GlideSentenceLookup {
                override fun lookup(rawInput: String, maxCount: Int): GlideLookupResult = GlideLookupResult.NoCandidate
            }, object : GlideCommitter {
                override fun installRawInput(rawInput: String) = false
                override fun selectCandidate(rawInput: String, text: String) = false
            }), glideEnabled = { true })
        controller.onDown(1, 50f, 70f, "h", "s", timeMs = 1000L)
        controller.onMove(1, 750f, 70f, false, timeMs = 1100L)
        controller.onMove(1, 750f, 28f, false, timeMs = 1124L)
        controller.onMove(1, 750f, 70f, false, timeMs = 1148L)
        assertEquals(listOf(1000L, 1100L, 1124L, 1148L), controller.trail.map { it.timeMs })
    }

    @Test fun 滑动输入的双u折返保留完整编码并提高该假设得分() {
        val g = geometry()
        val spellings = listOf("hx", "ds", "uu", "ru", "ur", "du", "su", "hu", "xu")
        val loop = GlideGeometryDecoder.decode(phraseTrace(g, true), g, 16, spellings)
        val plain = GlideGeometryDecoder.decode(phraseTrace(g, false), g, 16, spellings)
        assertTrue("折返后假设=" + loop, loop.take(5).any { it.letters == "hxdsuuru" })
        val loopCost = loop.first { it.letters == "hxdsuuru" }.cost
        val plainCost = plain.firstOrNull { it.letters == "hxdsuuru" }?.cost ?: Float.POSITIVE_INFINITY
        assertTrue("折返必须为 uu 提供额外证据", loopCost < plainCost)
    }
}
