package com.kingzcheung.xime.glide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 手势所有权状态机的边界：一次 pointer 序列最多产生一种输入动作。
 *
 * 这类状态机的风险在边界——未升级 Glide 时抬手必须补点击，
 * 一旦升级 Glide 就必须抑制所有原键动作，两者混淆会丢字或重复输入。
 */
class GlideGestureArbiterEdgeTest {

    private fun arbiter(enabled: Boolean = true, eligible: Boolean = true) =
        GlideGestureArbiter(eligibleStart = { eligible }, glideEnabled = { enabled })

    @Test
    fun 原地抬手应产生点击() {
        val a = arbiter()
        a.onDown(1, 100f, 100f, "k")
        assertEquals(GlideGestureArbiter.Decision.EMIT_TAP, a.onUp(1))
    }

    @Test
    fun 滑动关闭时不接管手势应返回COLLECT() {
        // owner=NONE 表示"仲裁器不接管"，普通按键由既有按键路径处理，
        // 所以这里**不应**返回 EMIT_TAP（那会导致同一按键被处理两次）。
        val a = arbiter(enabled = false)
        a.onDown(1, 100f, 100f, "k")
        a.onMove(1, 105f, 100f, "k", false)
        assertEquals(GlideGestureArbiter.Decision.COLLECT, a.onUp(1))
    }

    @Test
    fun 跨两个键且超过抖动应升级为滑动() {
        val a = arbiter()
        a.onDown(1, 100f, 100f, "k")
        a.onMove(1, 150f, 100f, "l", false)
        a.onMove(1, 200f, 100f, "l", false)
        assertTrue("应已升级为 Glide", a.state.isGlide)
        assertEquals(GlideGestureArbiter.Decision.EMIT_GLIDE, a.onUp(1))
    }

    @Test
    fun 仅超抖动但未跨键不应升级() {
        // distinctKeys 的语义是"累计经过的不同键数"，onMove 传入非空 keyId
        // 会递增计数（不论是否与起点同键），因此要模拟"停留在同一键"必须传 null。
        val a = arbiter()
        a.onDown(1, 100f, 100f, "k")
        a.onMove(1, 140f, 100f, null, false)
        assertFalse("未跨键时不应升级", a.state.isGlide)
    }

    @Test
    fun 第二根手指应取消本次手势() {
        val a = arbiter()
        a.onDown(1, 100f, 100f, "k")
        val d = a.onMove(1, 150f, 100f, "l", true)
        assertEquals(GlideGestureArbiter.Decision.DISCARD, d)
        assertEquals(GlideGestureArbiter.Decision.DISCARD, a.onUp(1))
    }

    @Test
    fun 取消后不应补点击() {
        val a = arbiter()
        a.onDown(1, 100f, 100f, "k")
        a.onCancel()
        assertEquals(GlideGestureArbiter.Decision.DISCARD, a.onUp(1))
    }

    @Test
    fun 非字母键起点不应升级为滑动() {
        val a = arbiter(eligible = false)
        a.onDown(1, 100f, 100f, "shift")
        a.onMove(1, 200f, 100f, "l", false)
        assertFalse("非字母键起点不应进入 Glide", a.state.isGlide)
    }

    @Test
    fun 升级后长按不应触发() {
        val a = arbiter()
        a.onDown(1, 100f, 100f, "k")
        a.onMove(1, 150f, 100f, "l", false)
        a.onMove(1, 200f, 100f, "l", false)
        assertEquals(GlideGestureArbiter.Decision.COLLECT, a.onLongPressTimeout())
    }
}
