package com.kingzcheung.xime.glide

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 解码器"理想输入"自检：把每条期望编码渲染成精确经过各键中心的合成轨迹，
 * 再交给真实解码器。用来区分两类失败：
 *
 *  - 理想轨迹也解不出 → 搜索/代价结构有缺陷（换真实数据前就该修）
 *  - 理想轨迹能解出、真实轨迹解不出 → 是真实手指轨迹的噪声问题（要改特征/模型）
 */
class GlideIdealTraceTest {

    private fun flypySpellings(): List<String> {
        val out = LinkedHashSet<String>()
        val initials = listOf("", "b","p","m","f","d","t","n","l","g","k","h","j","q","x","zh","ch","sh","r","z","c","s","y","w")
        val finals = linkedMapOf(
            "a" to 'a', "o" to 'o', "e" to 'e', "i" to 'i', "u" to 'u', "v" to 'v', "er" to 'r',
            "ai" to 'd', "ei" to 'w', "ui" to 'v', "ao" to 'c', "ou" to 'z',
            "iu" to 'q', "ie" to 'p', "ve" to 't',
            "an" to 'j', "en" to 'f', "in" to 'b', "un" to 'y', "vn" to 'y',
            "ang" to 'h', "eng" to 'g', "ing" to 'k', "ong" to 's',
            "ia" to 'x', "ua" to 'x', "uo" to 'o', "uai" to 'k', "uan" to 'r', "uang" to 'l',
            "ian" to 'm', "iang" to 'l', "iao" to 'n', "iong" to 's',
        )
        for (i in initials) for ((f, k) in finals) {
            val head = when (i) {
                "" -> {
                    if (f == "a" || f == "o" || f == "e") { out += "" + k + k; continue }
                    if (f == "er") { out += "or"; continue }
                    'o'
                }
                "zh" -> 'v'
                "ch" -> 'i'
                "sh" -> 'u'
                else -> i.first()
            }
            out += "" + head + k
        }
        return out.toList()
    }

    private fun geometry(): GlideGeometrySnapshot {
        val rows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
        val keys = rows.flatMapIndexed { r, letters ->
            letters.mapIndexed { c, ch ->
                GlideKey(
                    id = "k" + r + "_" + c,
                    code = ch.toString(),
                    rect = GlideKeyRect(c * 108f, r * 166f, c * 108f + 108f, r * 166f + 166f),
                )
            }
        }
        return GlideGeometrySnapshot("ideal", keys, 1080f, 498f)
    }

    /** 精确经过每个键中心，并在每键停留 150ms（模拟"点一下"）。 */
    private fun idealTrace(g: GlideGeometrySnapshot, code: String): List<GlidePoint> {
        val out = mutableListOf<GlidePoint>()
        var t = 0L
        var prev: GlidePoint? = null
        for (ch in code) {
            val key = g.keys.first { it.code == ch.toString() }
            val x = key.rect.centerX
            val y = key.rect.centerY
            prev?.let { p ->
                for (step in 1..8) {
                    val f = step / 9f
                    out += GlidePoint(p.x + (x - p.x) * f, p.y + (y - p.y) * f, t, 1)
                    t += 16
                }
            }
            out += GlidePoint(x, y, t, 1)
            t += 150
            // 停留点：同一个位置多记几帧，模拟手指按住在键中心
            repeat(4) { out += GlidePoint(x, y, t, 1); t += 40 }
            prev = GlidePoint(x, y, t, 1)
        }
        return out
    }

    @Test
    fun 理想轨迹应能解出自身编码() {
        val g = geometry()
        val spellings = flypySpellings()
        assertTrue("音节表不应为空", spellings.size > 400)

        // 覆盖短/中/长三档
        val cases = listOf("jqxlui", "vhgr", "yipwwogu", "nidobmdebnqk", "wovebaqldjdcpmzo")
        var pass = 0
        var top1 = 0
        for (code in cases) {
            assertTrue("样本本身应是合法双拼编码: " + code, isLegal(code, spellings))
            val pts = idealTrace(g, code)
            val hyps = GlideGeometryDecoder.decode(pts, g, 16, spellings, 420f)
            val codes = hyps.map { it.letters }
            val hit = code in codes
            val top = codes.firstOrNull()
            if (hit) pass++
            if (top == code) top1++
            println("[ideal] " + code + " 解出=" + hit + " top=" + top + " 全部=" + codes.take(5))
        }
        println("[ideal] 理想轨迹 top1=" + top1 + "/" + cases.size + " 命中=" + pass + "/" + cases.size)
        assertTrue("理想轨迹应至少命中一半（否则是搜索结构问题，不是噪声问题）", pass * 2 >= cases.size)
    }

    /** 两键一音节地检查是否全在编制内。 */
    private fun isLegal(code: String, spellings: List<String>): Boolean {
        if (code.length % 2 != 0) return false
        val set = spellings.toHashSet()
        var i = 0
        while (i < code.length) {
            if (!set.contains(code.substring(i, i + 2))) return false
            i += 2
        }
        return true
    }
}
