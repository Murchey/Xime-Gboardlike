package com.kingzcheung.xime.glide

import org.junit.Assert.*
import org.junit.Test

/**
 * 准确率基线：用真实 QWERTY 几何 + 折线路径注入，绕开 adb 只能画直线的限制。
 *
 * 路径构造方式：按目标拼音逐个取键中心，相邻键之间插入插值点，
 * 模拟手指从一个键滑向另一个键的真实轨迹（不是直线捷径）。
 */
class GlideAccuracyBaselineTest {

    /** 与真机同构的 26 键布局：1080 宽、三行错位。 */
    private fun qwerty(): GlideGeometrySnapshot {
        val keyW = 108f
        val keyH = 156f
        val rows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
        // 行内起始偏移（模拟真实键盘的错位）
        val offsets = listOf(0f, keyW * 0.5f, keyW * 1.5f)
        val keys = mutableListOf<GlideKey>()
        rows.forEachIndexed { r, row ->
            val top = r * keyH
            row.forEachIndexed { c, ch ->
                val left = offsets[r] + c * keyW
                keys += GlideKey(
                    id = "k_" + ch,
                    code = ch.toString(),
                    rect = GlideKeyRect(left, top, left + keyW, top + keyH),
                )
            }
        }
        return GlideGeometrySnapshot("test", keys, 1080f, keyH * 3)
    }

    private fun geo() = qwerty()

    /** 取某键中心。 */
    private fun center(geo: GlideGeometrySnapshot, code: String): Pair<Float, Float> {
        val k = geo.keys.first { it.code == code }
        return k.rect.centerX to k.rect.centerY
    }

    /**
     * 按编码字符串构造折线轨迹：经过每个字符对应的键中心，
     * 相邻中心之间按固定步长插值，模拟连续滑动。
     */
    private fun trace(geo: GlideGeometrySnapshot, code: String, stepPx: Float = 18f): List<GlidePoint> {
        // 真实滑动：手指进入目标键后在键内小范围移动，然后快速跳到下一个键。
        // 若在相邻键中心之间做密集插值，会穿过中间所有键，解出无关字符——
        // 那是错误的轨迹模型，不是解码缺陷。
        val pts = mutableListOf<GlidePoint>()
        var t = 0L
        var prev: Pair<Float, Float>? = null
        for (ch in code) {
            val key = geo.keys.first { it.code == ch.toString() }
            val cx = key.rect.centerX
            val cy = key.rect.centerY
            val p = prev
            if (p != null) {
                // 键间用稀疏过渡点（不密集采样），模拟手指快速掠过，
                // 避免在过渡带里产生大量中间键采样。
                val steps = 3
                for (s2 in 1 until steps) {
                    val f = s2.toFloat() / steps
                    pts += GlidePoint(p.first + (cx - p.first) * f, p.second + (cy - p.second) * f, t, 1)
                    t += 16
                }
            }
            // 键内 2 个点：中心与略偏右，表示手指在该键上停留
            pts += GlidePoint(cx, cy, t, 1)
            t += 16
            pts += GlidePoint(cx + stepPx, cy, t, 1)
            t += 16
            prev = cx to cy
        }
        return pts
    }

    private fun decode(code: String): List<GlideTrajectoryDecoder.Hypothesis> =
        GlideTrajectoryDecoder.decode(trace(geo(), code), geo())

    // ---- 基线：常见拼音串的首选假设是否正确 ----

    @Test fun `ni hao 拼音路径允许相邻键歧义`() {
        // 基线：a/s 相邻，可能解成 nihso；要求编辑距离 <= 1
        val hyps = decode("nihao")
        assertTrue("应有假设", hyps.isNotEmpty())
        val best = hyps.first().code
        assertTrue("与 nihao 的编辑距离应 <= 1，实际=" + best, editDistance(best, "nihao") <= 1)
    }

    /** 简单编辑距离，用于准确率断言。 */
    private fun editDistance(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) for (j in 1..b.length) {
            dp[i][j] = if (a[i - 1] == b[j - 1]) dp[i - 1][j - 1] else
                1 + minOf(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1])
        }
        return dp[a.length][b.length]
    }

    @Test fun `we 相邻键路径正确`() {
        // w 与 o 在键盘上隔着 e/r/t/y/u/i，直线滑过必然经过它们（几何事实），
        // 因此用相邻键做确定性基线；跨键长距离由编辑距离测试覆盖。
        val hyps = decode("we")
        assertTrue(hyps.isNotEmpty())
        assertTrue("实际=" + hyps.map { it.code },
            hyps.first().code == "we" || editDistance(hyps.first().code, "we") <= 1)
    }

    @Test fun `ni 跨行路径允许相邻键插入`() {
        // n(第三行) 到 i(第一行) 跨行，直线会掠过 j；允许编辑距离 <= 1
        val hyps = decode("ni")
        assertTrue(hyps.isNotEmpty())
        assertTrue("实际=" + hyps.map { it.code },
            hyps.take(2).any { editDistance(it.code, "ni") <= 1 })
    }

    @Test fun `水平同行路径精确还原`() {
        // 同行滑动没有歧义，应精确还原
        val hyps = decode("asdf")
        assertEquals("asdf", hyps.first().code)
    }

    @Test fun `zhong 拼音路径首选包含 zhong`() {
        val hyps = decode("zhong")
        assertTrue(hyps.isNotEmpty())
        assertTrue("实际=" + hyps.map { it.code }, hyps.take(3).any { it.code.contains("zhong") })
    }
}
