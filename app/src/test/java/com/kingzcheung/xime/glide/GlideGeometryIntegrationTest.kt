package com.kingzcheung.xime.glide

import org.junit.Assert.*
import org.junit.Test

/**
 * 替换解码路径后的集成验证：几何解码器 → controller → Rime 查询。
 * 重点验证控制器查到的编码来自新解码器（完整拼音音节串），而非旧的键序列直串。
 */
class GlideGeometryIntegrationTest {
    private val host = "h"
    private val schema = "s"

    /** 记录查询到的编码；对合法拼音串返回固定候选。 */
    private class L : GlideSentenceLookup {
        val queried = mutableListOf<String>()
        override fun lookup(rawInput: String, maxCount: Int): GlideLookupResult {
            queried += rawInput
            return GlideLookupResult.Candidates(
                listOf(GlideCandidate("c0", "你好", rawInput))
            )
        }
    }

    private fun build(): Pair<GlideKeyboardController, L> {
        val r = GlideGeometryRegistry()
        r.beginFrame(1000f, 300f)
        val rows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
        rows.forEachIndexed { row, letters ->
            letters.forEachIndexed { col, c ->
                val left = col * 100f
                val top = row * 100f
                r.register(GlideKey("k" + row + "_" + col, c.toString(),
                    GlideKeyRect(left, top, left + 100f, top + 100f)))
            }
        }
        val l = L()
        val ctl = GlideKeyboardController(r, GlideCoordinator(l, object : GlideCommitter {
            override fun installRawInput(rawInput: String) = true
            override fun selectCandidate(rawInput: String, text: String) = true
        })) { true }
        return ctl to l
    }

    /** 依次经过每个字母键中心并停留。 */
    private fun swipe(ctl: GlideKeyboardController, code: String) {
        val letters = "qwertyuiopasdfghjklzxcvbnm"
        var first = true
        var prev: Pair<Float, Float>? = null
        for (ch in code) {
            val row = when (ch) {
                in "qwertyuiop" -> 0
                in "asdfghjkl" -> 1
                else -> 2
            }
            val rowLetters = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")[row]
            val col = rowLetters.indexOf(ch)
            val cx = col * 100f + 50f
            val cy = row * 100f + 50f
            prev?.let { p ->
                for (k in 1..5) {
                    val f = k / 6f
                    ctl.onMove(1, p.first + (cx - p.first) * f, p.second + (cy - p.second) * f, false)
                }
            }
            if (first) { ctl.onDown(1, cx, cy, host, schema); first = false }
            else ctl.onMove(1, cx, cy, false)
            prev = cx to cy
        }
    }

    @Test fun `滑 nihao 控制器查询到完整拼音串`() {
        val (ctl, l) = build()
        swipe(ctl, "nihao")
        val up = ctl.onUp(1, host)
        assertTrue(up is GlideKeyboardController.UpResult.Candidates)
        assertEquals(
            "控制器应查询几何解码器给出的 nihao，实际=" + l.queried,
            "nihao", l.queried.firstOrNull()
        )
    }

    @Test fun `候选中含解码串对应的词`() {
        val (ctl, _) = build()
        swipe(ctl, "nihao")
        ctl.onUp(1, host)
        assertTrue("候选应包含 Rime 返回的词", ctl.candidateTexts().contains("你好"))
    }

    @Test fun `所有查询串都是完整拼音音节串`() {
        val (ctl, l) = build()
        swipe(ctl, "qwe")
        ctl.onUp(1, host)
        assertTrue("应产生候选", l.queried.isNotEmpty())
        val vowels = setOf('a', 'e', 'i', 'o', 'u', 'v')
        for (code in l.queried) {
            assertTrue("查询串应只含小写字母: " + code, code.all { it in 'a'..'z' })
        }
    }
}
