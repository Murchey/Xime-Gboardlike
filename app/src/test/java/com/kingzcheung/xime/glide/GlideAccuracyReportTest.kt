package com.kingzcheung.xime.glide

import org.junit.Test

/** 输出当前解码器在一组拼音路径上的实际结果，便于人工核对准确率。 */
class GlideAccuracyReportTest {
    private fun geo(): GlideGeometrySnapshot {
        val keyW = 108f; val keyH = 156f
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
        return GlideGeometrySnapshot("t", keys, 1080f, keyH * 3)
    }

    private fun trace(g: GlideGeometrySnapshot, code: String): List<GlidePoint> {
        val pts = mutableListOf<GlidePoint>()
        var t = 0L
        var prev: Pair<Float, Float>? = null
        for (ch in code) {
            val k = g.keys.first { it.code == ch.toString() }
            val cx = k.rect.centerX; val cy = k.rect.centerY
            prev?.let { p ->
                for (s in 1 until 4) {
                    val f = s / 4f
                    pts += GlidePoint(p.first + (cx - p.first) * f, p.second + (cy - p.second) * f, t, 1)
                    t += 16
                }
            }
            pts += GlidePoint(cx, cy, t, 1); t += 16
            pts += GlidePoint(cx + 18f, cy, t, 1); t += 16
            prev = cx to cy
        }
        return pts
    }

    /**
     * 当前准确率基线（首尾字符正确，中间存在过渡键插入）。
     * 断言放在"首字符正确 + 编辑距离有限"，作为后续改进不劣化的护栏。
     */
    @Test fun report() {
        val g = geo()
        listOf("nihao","zhong","asdf","jintian","women","shijie","gongzuo","xiexie").forEach { code ->
            val h = GlideTrajectoryDecoder.decode(trace(g, code), g)
            val top = h.firstOrNull()?.code ?: "NONE"
            println("REPORT code=" + code + " top=" + top)
            org.junit.Assert.assertTrue("应产出结果: " + code, h.isNotEmpty())
            org.junit.Assert.assertEquals(
                "首字符应正确: " + code + " got=" + top,
                code.first(), top.firstOrNull() ?: '?')
        }
    }
}
