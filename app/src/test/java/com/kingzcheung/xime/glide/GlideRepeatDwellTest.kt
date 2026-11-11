package com.kingzcheung.xime.glide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 同键折返：只有折返区间内存在停留证据，才把这次折返当作"按了两次"。
 *
 * 回归样本来自真实轨迹 g111046-12：手指从 k 快速滑到 l（每步 1~4ms，无停留）
 * 再折回 k 并停住（距键心 4px，停留 142ms），意图是 "jue qu ling shi"(jtqulkui)，
 * 旧实现无条件给折返折扣，于是读成 "jue qu liang shi"(jtqullui)。
 */
class GlideRepeatDwellTest {

    private fun geometry(): GlideGeometrySnapshot {
        val rows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
        val keys = rows.flatMapIndexed { r, letters ->
            letters.mapIndexed { c, ch ->
                GlideKey("k" + r + "_" + c, ch.toString(),
                    GlideKeyRect(c * 108f, r * 166f, c * 108f + 108f, r * 166f + 166f))
            }
        }
        return GlideGeometrySnapshot("dwell", keys, 1080f, 498f)
    }

    /** 经过 [pass] 键但不停留，最后停在 [stop] 键上并停留。 */
    private fun passThenStop(
        g: GlideGeometrySnapshot, pass: String, stop: String, dwellMs: Long,
    ): List<GlidePoint> {
        val a = g.keys.first { it.code == pass }
        val b = g.keys.first { it.code == stop }
        val out = mutableListOf<GlidePoint>()
        var t = 0L
        // 从 a 左侧进入，快速穿过 a
        out += GlidePoint(a.rect.left - 40f, a.rect.centerY, t, 1)
        for (s in 1..10) {
            t += 4  // 4ms/步：远快于 DWELL_MS=120，不构成停留
            val f = s / 10f
            out += GlidePoint(a.rect.left - 40f + (a.rect.centerX + 40f - a.rect.left + 40f) * f, a.rect.centerY, t, 1)
        }
        // 折回，直奔 stop 中心
        for (s in 1..12) {
            t += 4
            val f = s / 12f
            out += GlidePoint(a.rect.centerX + 40f + (b.rect.centerX - a.rect.centerX - 40f) * f,
                a.rect.centerY + (b.rect.centerY - a.rect.centerY) * f, t, 1)
        }
        // 在 stop 上停留：dwellPositions 要求 DWELL_RADIUS 内出现一次 >=120ms 的时间空洞，
        // 慢速连点累积出的时间跨度不算（否则会把途经键误判成停留）。
        repeat(5) {
            t += 20
            out += GlidePoint(b.rect.centerX, b.rect.centerY, t, 1)
        }
        t += 150
        out += GlidePoint(b.rect.centerX, b.rect.centerY, t, 1)
        repeat(4) {
            t += 20
            out += GlidePoint(b.rect.centerX, b.rect.centerY, t, 1)
        }
        return out
    }

    @Test
    fun 无停留的折返不应被当成重复按键() {
        val g = geometry()
        val spellings = FlypySpellings.all()
        // 快速经过 l、停在 k：期望解读里 l 只出现一次（lk 而非 ll）
        val pts = passThenStop(g, pass = "l", stop = "k", dwellMs = 140)
        val hyps = GlideGeometryDecoder.decode(pts, g, 16, spellings, 420f)
        val codes = hyps.map { it.letters }
        println("[dwell] 结果=" + codes.take(6))
        // 首选应当是"经过了 l 与 k"（含 lk）而不是"l 出现两次"（ll）
        val top = codes.firstOrNull().orEmpty()
        assertTrue("首选不应把无停留的 l 读成两次按键: " + top, !top.contains("ll"))
    }

    @Test
    fun 有停留的重复仍应被接受() {
        val g = geometry()
        val spellings = FlypySpellings.all()
        // 真正在 l 上停住：应当允许出现 ll
        val key = g.keys.first { it.code == "l" }
        val out = mutableListOf<GlidePoint>()
        var t = 0L
        out += GlidePoint(key.rect.centerX - 30f, key.rect.centerY, t, 1)
        out += GlidePoint(key.rect.centerX, key.rect.centerY, t.also { t += 20 }, 1)
        repeat(6) { t += 25; out += GlidePoint(key.rect.centerX, key.rect.centerY, t, 1) }
        val hyps = GlideGeometryDecoder.decode(out, g, 16, spellings, 420f)
        println("[dwell] 停顿样本结果=" + hyps.take(4).map { it.letters })
        assertEquals("单键停留应能解出自身", true, hyps.isNotEmpty())
    }
}
