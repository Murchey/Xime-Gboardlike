package com.kingzcheung.xime.glide

import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * 记录 distinctKeys 的真实语义：它统计的是「收到带键 id 的 MOVE 次数」，
 * 不是「经过的不同键数」——同一键连续上报也会累加。
 *
 * 这个语义差异本身不影响正确性（升级条件还要叠加位移阈值 slopPx），
 * 但名字有误导性，且决定了测试该怎么写。独立成测试以免后人误判。
 */
class GlideDistinctKeysSemanticsTest {

    @Test
    fun 同一键重复上报也会递增计数() {
        val a = GlideGestureArbiter(eligibleStart = { true }, glideEnabled = { true })
        a.onDown(1, 100f, 100f, "k")
        a.onMove(1, 101f, 100f, "k", false)
        a.onMove(1, 102f, 100f, "k", false)
        // 仍在同一键内小幅移动，但计数已到 3
        org.junit.Assert.assertEquals(3, a.state.distinctKeys)
    }

    @Test
    fun 位移不足抖动时即使计数够也不升级() {
        val a = GlideGestureArbiter(eligibleStart = { true }, glideEnabled = { true })
        a.onDown(1, 100f, 100f, "k")
        // 大量重复上报但位移始终 < slopPx(12)
        repeat(10) { i -> a.onMove(1, 100f + i * 0.5f, 100f, "k", false) }
        assertFalse("位移不足时不应升级", a.state.isGlide)
    }
}
