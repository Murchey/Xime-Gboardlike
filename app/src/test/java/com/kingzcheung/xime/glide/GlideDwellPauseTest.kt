package com.kingzcheung.xime.glide

import org.junit.Assert.*
import org.junit.Test

class GlideDwellPauseTest {
    private fun geometry(): GlideGeometrySnapshot {
        val w = 100f; val h = 100f
        val keys = "qwertyuiop".mapIndexed { c, ch ->
            GlideKey(ch.toString(), ch.toString(), GlideKeyRect(c * w, 0f, c * w + w, h))
        } + "asdfghjkl".mapIndexed { c, ch ->
            val x = 50f + c * w
            GlideKey(ch.toString(), ch.toString(), GlideKeyRect(x, h, x + w, 2 * h))
        } + "zxcvbnm".mapIndexed { c, ch ->
            val x = 150f + c * w
            GlideKey(ch.toString(), ch.toString(), GlideKeyRect(x, 2 * h, x + w, 3 * h))
        }
        return GlideGeometrySnapshot("pause", keys, w * 11, h * 3)
    }

    private fun line(interval: Long, stationaryGap: Long? = null): List<GlidePoint> {
        val g = geometry()
        val ax = g.keys.first { it.code == "q" }.rect.centerX
        val bx = g.keys.first { it.code == "o" }.rect.centerX
        val y = g.keys.first { it.code == "q" }.rect.centerY
        val out = mutableListOf<GlidePoint>()
        var t = 0L
        for (step in 0..40) {
            out += GlidePoint(ax + (bx - ax) * step / 40f, y, t, 1)
            t += interval
        }
        if (stationaryGap != null) {
            // q 键中心停一下：两个相同坐标点之间留下明确的时间空洞。
            out += GlidePoint(ax, y, out.first().timeMs - 200, 1)
            t += stationaryGap
            out += GlidePoint(ax, y, t, 1)
        }
        return out.sortedBy { it.timeMs }
    }

    @Test fun 多个短间隔累积不算停留() {
        val g = geometry()
        val slow = GlideGeometryDecoder.decode(line(60), g, 16)
        val fast = GlideGeometryDecoder.decode(line(16), g, 16)
        assertEquals("慢滑与快滑首选应一致", fast.first().letters, slow.first().letters)
        assertTrue("慢滑不应因假停留显著增费: " + slow.first().cost + " vs " + fast.first().cost,
            slow.first().cost <= fast.first().cost + 2f)
    }

    @Test fun 静止时间空洞仍算停留() {
        val g = geometry()
        val paused = GlideGeometryDecoder.decode(line(16, 180), g, 16)
        assertTrue("有静止停留时仍应出候选", paused.isNotEmpty())
        assertTrue("停留不能让编码退化为途经长串: " + paused.map { it.letters },
            paused.none { it.letters.length >= 8 })
    }
}
