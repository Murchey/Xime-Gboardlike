package com.kingzcheung.xime.glide

import org.junit.Assert.*
import org.junit.Test

/** 滑动开始语义：新建而非追加——每次滑动代表一次独立输入意图。 */
class GlideStartSemanticsTest {
    private val host = "h"
    private val schema = "s"

    private class L : GlideSentenceLookup {
        override fun lookup(rawInput: String, maxCount: Int) =
            GlideLookupResult.Candidates(listOf(GlideCandidate("c0", "去", rawInput)))
    }
    private class C : GlideCommitter {
        override fun installRawInput(rawInput: String) = true
        override fun selectCandidate(rawInput: String, text: String) = true
    }
    private fun build(glideOn: Boolean = true): Pair<GlideKeyboardController, MutableList<Int>> {
        val r = GlideGeometryRegistry()
        r.beginFrame(300f, 100f)
        listOf("q","w","e").forEachIndexed { i, c ->
            r.register(GlideKey("k" + i, c, GlideKeyRect(i * 100f, 0f, i * 100f + 100f, 100f)))
        }
        val starts = mutableListOf<Int>()
        val ctl = GlideKeyboardController(r, GlideCoordinator(L(), C()), glideEnabled = { glideOn })
        ctl.onGlideStart = { starts += 1; "" }
        return ctl to starts
    }

    @Test fun `点按不触发滑动开始`() {
        val (ctl, starts) = build()
        ctl.onDown(1, 50f, 50f, host, schema)
        ctl.onUp(1, host)
        assertEquals("普通点按不应清组合", 0, starts.size)
    }

    @Test fun `升级为滑动时只触发一次开始`() {
        val (ctl, starts) = build()
        ctl.onDown(1, 50f, 50f, host, schema)
        ctl.onMove(1, 150f, 50f, false)
        ctl.onMove(1, 250f, 50f, false)
        ctl.onMove(1, 260f, 50f, false)
        assertEquals("同一次滑动只清一次组合", 1, starts.size)
    }

    @Test fun `两次独立滑动各触发一次开始`() {
        val (ctl, starts) = build()
        ctl.onDown(1, 50f, 50f, host, schema)
        ctl.onMove(1, 150f, 50f, false)
        ctl.onUp(1, host)
        ctl.onDown(2, 50f, 50f, host, schema)
        ctl.onMove(2, 150f, 50f, false)
        ctl.onUp(2, host)
        assertEquals("每次滑动都应重建组合", 2, starts.size)
    }

    @Test fun `glide关闭时不触发滑动开始`() {
        val (ctl, starts) = build(glideOn = false)
        ctl.onDown(1, 50f, 50f, host, schema)
        ctl.onMove(1, 250f, 50f, false)
        ctl.onUp(1, host)
        assertEquals(0, starts.size)
    }
}
