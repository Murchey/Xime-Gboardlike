package com.kingzcheung.xime.glide

import org.junit.Assert.*
import org.junit.Test

/**
 * 双拼分号韵母：滑动经过 ; 键应解码为 ing。
 * 对应用户需求：紫光/搜狗/微软双拼下滑动也能输入 ing。
 */
class GlideSemicolonIngTest {

    /** 26 键 + 右下角分号键（双拼 ing）。 */
    private fun qwertyWithSemicolon(): GlideGeometrySnapshot {
        val keyW = 100f
        val keyH = 100f
        val rows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
        val offsets = listOf(0f, keyW * 0.5f, keyW * 1.5f)
        val keys = mutableListOf<GlideKey>()
        rows.forEachIndexed { r, row ->
            row.forEachIndexed { c, ch ->
                val left = offsets[r] + c * keyW
                keys += GlideKey("k_" + ch, ch.toString(),
                    GlideKeyRect(left, r * keyH, left + keyW, r * keyH + keyH))
            }
        }
        // 分号键在 p 右侧（双拼布局的 ; 位置）
        keys += GlideKey("k_semi", ";", GlideKeyRect(10f * keyW, 0f, 11f * keyW, keyH))
        return GlideGeometrySnapshot("t", keys, 1100f, keyH * 3)
    }

    private fun center(g: GlideGeometrySnapshot, code: String): Pair<Float, Float> {
        val k = g.keys.first { it.code == code }
        return k.rect.centerX to k.rect.centerY
    }

    /** 依次经过键中心并停留。 */
    private fun trace(g: GlideGeometrySnapshot, code: String): List<GlidePoint> {
        val pts = mutableListOf<GlidePoint>()
        var t = 0L
        var prev: Pair<Float, Float>? = null
        for (ch in code) {
            val (cx, cy) = center(g, ch.toString())
            prev?.let { p ->
                for (k in 1..5) {
                    val f = k / 6f
                    pts += GlidePoint(p.first + (cx - p.first) * f, p.second + (cy - p.second) * f, t, 1)
                    t += 16
                }
            }
            pts += GlidePoint(cx, cy, t, 1); t += 150
            prev = cx to cy
        }
        return pts
    }

    @Test fun `滑 x 到分号解出 xing`() {
        val g = qwertyWithSemicolon()
        val h = GlideGeometryDecoder.decode(trace(g, "x;"), g, 8)
        assertTrue("应有假设，实际=" + h.map { it.letters }, h.isNotEmpty())
        assertTrue("应解出 xing，实际=" + h.map { it.letters },
            h.take(3).any { it.letters == "xing" })
    }

    @Test fun `滑 b 到分号解出 bing`() {
        val g = qwertyWithSemicolon()
        val h = GlideGeometryDecoder.decode(trace(g, "b;"), g, 8)
        assertTrue(h.isNotEmpty())
        assertTrue("应解出 bing，实际=" + h.map { it.letters },
            h.take(3).any { it.letters == "bing" })
    }

    @Test fun `无分号键时解码仍正常`() {
        val g = GlideGeometrySnapshot("t", qwertyWithSemicolon().keys.filter { it.code != ";" },
            1100f, 300f)
        val h = GlideGeometryDecoder.decode(trace(g, "nihao"), g, 8)
        assertTrue("实际=" + h.map { it.letters }, h.isNotEmpty())
        assertEquals("nihao", h.first().letters)
    }
}
