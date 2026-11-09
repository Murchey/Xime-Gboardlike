package com.kingzcheung.xime.glide

import org.junit.Assert.*
import org.junit.Test
import com.kingzcheung.xime.glide.GlideGestureArbiter.Decision as D
import com.kingzcheung.xime.glide.GlideGestureArbiter.Owner as O

class GlideGestureArbiterTest {
    private fun arb(enabled: Boolean = true, eligible: Boolean = true) =
        GlideGestureArbiter({ eligible }, { enabled }, slopPx = 10f, minDistinctKeys = 2)

    @Test fun `功能关闭时DOWN不进入PENDING 保持普通点按语义`() {
        val a = arb(enabled = false)
        a.onDown(1, 10f, 10f, "q")
        assertEquals(O.NONE, a.state.owner)
        assertEquals(D.COLLECT, a.onUp(1))
    }

    @Test fun `起点不是字母键不进入滑动`() {
        val a = arb()
        a.onDown(1, 10f, 10f, null)
        assertEquals(O.NONE, a.state.owner)
    }

    @Test fun `轻微抖动仍按点按 不升级Glide`() {
        val a = arb()
        a.onDown(1, 10f, 10f, "q")
        a.onMove(1, 12f, 11f, "q", false)
        assertEquals(O.PENDING, a.state.owner)
        assertEquals(D.EMIT_TAP, a.onUp(1))
    }

    @Test fun `跨键滑过阈值升级Glide 抬手交给解码`() {
        val a = arb()
        a.onDown(1, 10f, 10f, "q")
        a.onMove(1, 60f, 12f, "w", false)
        assertEquals(O.GLIDE, a.state.owner)
        assertEquals(D.EMIT_GLIDE, a.onUp(1))
    }

    @Test fun `Glide接管后长按timeout不生效`() {
        val a = arb()
        a.onDown(1, 10f, 10f, "q")
        a.onMove(1, 60f, 12f, "w", false)
        assertEquals(D.COLLECT, a.onLongPressTimeout())
        assertTrue(a.state.isGlide)
    }

    @Test fun `未升级时长按可生效`() {
        val a = arb()
        a.onDown(1, 10f, 10f, "q")
        assertEquals(D.EMIT_LONG_PRESS, a.onLongPressTimeout())
        assertEquals(O.LONG_PRESS, a.state.owner)
    }

    @Test fun `Glide或取消态禁止兜底补点击`() {
        val a = arb()
        a.onDown(1, 10f, 10f, "q")
        a.onMove(1, 60f, 12f, "w", false)
        assertFalse(a.allowFallbackClick())
        val b = arb()
        b.onDown(1, 10f, 10f, "q")
        assertEquals(D.DISCARD, b.onCancel())
        assertEquals(O.CANCELLED, b.state.owner)
        assertFalse(b.allowFallbackClick())
    }

    @Test fun `第二根手指取消且不补字`() {
        val a = arb()
        a.onDown(1, 10f, 10f, "q")
        assertEquals(D.DISCARD, a.onMove(1, 30f, 12f, "q", secondPointer = true))
        assertEquals(O.CANCELLED, a.state.owner)
        assertEquals(D.DISCARD, a.onUp(1))
    }

    @Test fun `系统取消丢弃不产生点按`() {
        val a = arb()
        a.onDown(1, 10f, 10f, "q")
        assertEquals(D.DISCARD, a.onCancel())
        assertEquals(O.CANCELLED, a.state.owner)
    }

    @Test fun `其他pointer的MOVE被忽略`() {
        val a = arb()
        a.onDown(1, 10f, 10f, "q")
        a.onMove(2, 500f, 500f, "p", false)
        assertEquals(O.PENDING, a.state.owner)
        assertEquals(D.EMIT_TAP, a.onUp(1))
    }
}
