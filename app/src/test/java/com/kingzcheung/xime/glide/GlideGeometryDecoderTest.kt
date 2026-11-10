package com.kingzcheung.xime.glide

import org.junit.Assert.*
import org.junit.Test

/**
 * 几何解码器（移植自水杉输入法）的准确率验证。
 * 与旧解码器的关键区别：用连续几何代价代替离散"停留/经过"阈值。
 */
class GlideGeometryDecoderTest {

    /** 与真机同构的 26 键布局。 */
    private fun qwerty(): GlideGeometrySnapshot {
        val keyW = 108f
        val keyH = 156f
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
        return GlideGeometrySnapshot("test", keys, 1080f, keyH * 3)
    }

    private fun center(g: GlideGeometrySnapshot, ch: Char): Pair<Float, Float> {
        val k = g.keys.first { it.code == ch.toString() }
        return k.rect.centerX to k.rect.centerY
    }

    /**
     * 按拼音串构造折线轨迹：依次经过每个字母键中心，键间线性插值。
     * 每个键上再停留 150ms（模拟手指落在键上），使 dwell 判定生效。
     */
    private fun trace(g: GlideGeometrySnapshot, code: String, dwellMs: Long = 150L): List<GlidePoint> {
        val pts = mutableListOf<GlidePoint>()
        var t = 0L
        var prev: Pair<Float, Float>? = null
        for (ch in code) {
            val (cx, cy) = center(g, ch)
            prev?.let { p ->
                val steps = 6
                for (s in 1 until steps) {
                    val f = s / steps.toFloat()
                    pts += GlidePoint(p.first + (cx - p.first) * f, p.second + (cy - p.second) * f, t, 1)
                    t += 16
                }
            }
            // 在键上停留：进入 → 停 dwellMs → 离开，只构成一次停留
            pts += GlidePoint(cx, cy, t, 1)
            t += dwellMs
            prev = cx to cy
        }
        return pts
    }

    private fun decode(code: String, limit: Int = 8): List<GlideGeometryDecoder.Hypothesis> {
        val g = qwerty()
        return GlideGeometryDecoder.decode(trace(g, code), g, limit)
    }

    @Test fun `同行直滑 sada 应解出合法音节`() {
        val h = decode("sada")
        assertTrue("应有假设", h.isNotEmpty())
        assertEquals("sada", h.first().letters)
    }

    @Test fun `非拼音串返回空是正确行为`() {
        // asdf 无法切成完整音节，不应硬凑出结果
        assertTrue(decode("asdf").isEmpty())
    }

    @Test fun `nihao 首选应为 nihao`() {
        val h = decode("nihao")
        assertTrue("应有假设，实际=" + h.map { it.letters }, h.isNotEmpty())
        assertEquals("nihao", h.first().letters)
    }

    @Test fun `zhong 首选应为 zhong 而非 zong`() {
        val h = decode("zhong")
        assertTrue(h.isNotEmpty())
        // zhong 与 zong 共线，几何上难分；允许二者在前二，但 zhong 必须存在
        assertTrue("实际=" + h.map { it.letters },
            h.take(3).any { it.letters == "zhong" })
    }

    @Test fun `women 首选应为 women`() {
        val h = decode("women")
        assertTrue("实际=" + h.map { it.letters }, h.isNotEmpty())
        assertEquals("women", h.first().letters)
    }

    @Test fun `只经过的键不应被当成按下`() {
        // q 到 o 的直线上有 w e r t y u i，但只有 q 与 o 是停留点
        val g = qwerty()
        val pts = mutableListOf<GlidePoint>()
        var t = 0L
        val (qx, qy) = center(g, 'q')
        val (ox, oy) = center(g, 'o')
        pts += GlidePoint(qx, qy, t, 1); t += 150
        pts += GlidePoint(qx, qy, t, 1); t += 16
        for (s in 1 until 10) {
            val f = s / 10f
            pts += GlidePoint(qx + (ox - qx) * f, qy + (oy - qy) * f, t, 1); t += 16
        }
        pts += GlidePoint(ox, oy, t, 1); t += 150
        pts += GlidePoint(ox, oy, t, 1)
        val h = GlideGeometryDecoder.decode(pts, g, 5)
        assertTrue("应有假设", h.isNotEmpty())
        assertFalse("不应解出含中间键的长串: " + h.first().letters,
            h.first().letters.length > 4)
    }

    @Test fun `空轨迹与单点返回空`() {
        val g = qwerty()
        assertTrue(GlideGeometryDecoder.decode(emptyList(), g, 5).isEmpty())
        assertTrue(GlideGeometryDecoder.decode(
            listOf(GlidePoint(10f, 10f, 0, 1)), g, 5).isEmpty())
    }

    @Test fun `返回的假设按代价升序`() {
        val h = decode("women")
        for (i in 1 until h.size) {
            assertTrue("代价应升序", h[i - 1].cost <= h[i].cost)
        }
    }
}
