package com.kingzcheung.xime.glide

import org.junit.Assert.*
import org.junit.Test

class GlideSlowTraceTest {
    private fun geometry(): GlideGeometrySnapshot {
        val w = 100f
        val h = 100f
        val rows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
        val offsets = listOf(0f, w * 0.5f, w * 1.5f)
        val keys = rows.flatMapIndexed { r, row -> row.mapIndexed { c, ch ->
            val x = offsets[r] + c * w
            GlideKey(ch.toString(), ch.toString(), GlideKeyRect(x, r * h, x + w, r * h + h))
        } }
        return GlideGeometrySnapshot("slow", keys, w * 11, h * 3)
    }

    /** 连续慢滑：键间 40 个等距点、每点 30ms，没有任何静止重复点。 */
    private fun slowTrace(g: GlideGeometrySnapshot, code: String): List<GlidePoint> {
        val out = mutableListOf<GlidePoint>()
        var time = 0L
        var prev: Pair<Float, Float>? = null
        for (ch in code) {
            val k = g.keys.first { it.code == ch.toString() }
            val x = k.rect.centerX
            val y = k.rect.centerY
            val p = prev
            if (p != null) {
                for (step in 1..40) {
                    time += 30
                    val f = step / 41f
                    out += GlidePoint(p.first + (x - p.first) * f, p.second + (y - p.second) * f, time, 1)
                }
            } else {
                out += GlidePoint(x, y, time, 1)
                time += 30
            }
            prev = x to y
        }
        return out
    }

    @Test fun 慢滑不把沿途键计成停留() {
        val g = geometry()
        val h = GlideGeometryDecoder.decode(slowTrace(g, "nihao"), g, 16)
        assertTrue("实际=" + h.map { it.letters }, h.isNotEmpty())
        assertEquals("慢滑首选应仍是 nihao，实际=" + h.map { it.letters }, "nihao", h.first().letters)
        assertTrue("不应出现长串途经字母: " + h.first().letters, h.first().letters.length <= 6)
    }

    @Test fun 真实静止停留仍被识别() {
        val g = geometry()
        val fast = slowTrace(g, "nihao").toMutableList()
        val k = g.keys.first { it.code == "o" }
        val end = fast.last().timeMs
        fast += GlidePoint(k.rect.centerX, k.rect.centerY, end + 150, 1)
        val h = GlideGeometryDecoder.decode(fast, g, 16)
        assertTrue("实际=" + h.map { it.letters }, h.any { it.letters == "nihao" })
    }
}
