package com.kingzcheung.xime.glide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 解码器的结构约束守护：剪枝是 BEAM 搜索，不能让"走得远"的路径被"走得近"的便宜路径挤出候选。
 *
 * 用合成轨迹固定在测试里，避免以后调参时无意破坏这些性质。
 */
class GlideBeamStructureTest {

    private fun geometry(): GlideGeometrySnapshot {
        val rows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
        val keys = rows.flatMapIndexed { r, letters ->
            letters.mapIndexed { c, ch ->
                GlideKey("k" + r + "_" + c, ch.toString(),
                    GlideKeyRect(c * 108f, r * 166f, c * 108f + 108f, r * 166f + 166f))
            }
        }
        return GlideGeometrySnapshot("beam", keys, 1080f, 498f)
    }

    /** 精确经过每个键中心并停留，每步移动不超过 2 键（合理手势）。 */
    private fun trace(g: GlideGeometrySnapshot, code: String): List<GlidePoint> {
        val out = mutableListOf<GlidePoint>()
        var t = 0L
        var prev: GlidePoint? = null
        for (ch in code) {
            val key = g.keys.first { it.code == ch.toString() }
            val x = key.rect.centerX
            val y = key.rect.centerY
            prev?.let { p ->
                val steps = 8
                for (s in 1..steps) {
                    val f = s / (steps + 1f)
                    out += GlidePoint(p.x + (x - p.x) * f, p.y + (y - p.y) * f, t, 1)
                    t += 16
                }
            }
            out += GlidePoint(x, y, t, 1)
            t += 150
            repeat(3) { out += GlidePoint(x, y, t, 1); t += 40 }
            prev = GlidePoint(x, y, t, 1)
        }
        return out
    }

    private fun spellings() = FlypySpellings.all()

    @Test
    fun 音节编码表覆盖真实双拼且不含伪音节() {
        val all = spellings()
        assertTrue("音节编码不应过少", all.size > 350)
        assertTrue("音节编码不应过多（说明混进了笛卡尔积）", all.size < 500)
        // 每条都必须是两键
        assertTrue("编码必须全部是两键", all.all { it.length == 2 })
        // 典型合法编码在列
        for (c in listOf("ni", "hk", "jq", "xl", "ui")) {
            assertTrue("缺少合法编码 " + c, c in all)
        }
    }

    @Test
    fun 短编码应能被解出且不多吐键() {
        val g = geometry()
        val result = GlideGeometryDecoder.decode(trace(g, "vhgr"), g, 16, spellings(), 420f)
        assertTrue("应解出 vhgr，实际=" + result.take(4).map { it.letters },
            result.take(4).any { it.letters == "vhgr" })
        assertEquals("首选应为 vhgr，实际=" + result.firstOrNull()?.letters,
            "vhgr", result.firstOrNull()?.letters)
    }

    @Test
    fun 合法双拼编码不应被音节约束拒绝() {
        val codes = spellings()
        assertFalse("列表为空", codes.isEmpty())
        // 真实样本里的期望编码都应可在表中找到
        val table = codes.toHashSet()
        for (code in listOf("jqxlui", "vhgr", "nidobmdebnqk")) {
            var i = 0
            var ok = true
            while (i < code.length) {
                if (!table.contains(code.substring(i, i + 2))) { ok = false; break }
                i += 2
            }
            assertTrue(code + " 的每个音节都应在表中", ok)
        }
    }
}
