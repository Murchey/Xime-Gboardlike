package com.kingzcheung.xime.glide

import com.kingzcheung.xime.glide.GlideGestureArbiter.Decision as D
import com.kingzcheung.xime.glide.GlideGestureArbiter.Owner as O
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 走路打字场景：手机与手指一起晃，按键时的微小位移不能被当成滑动。
 *
 * 这里用默认阈值构造仲裁器（生产路径同样用默认值），
 * 断言十几像素的抖动仍然按普通点按处理。
 */
class GlideWalkingJitterTest {

    private fun defaultArbiter() = GlideGestureArbiter({ true }, { true })

    @Test
    fun smallJitterStaysTap() {
        val a = defaultArbiter()
        a.onDown(1, 100f, 200f, "q")
        // 走路时接触点来回晃 15px，甚至扫过邻键边缘
        a.onMove(1, 108f, 206f, "q", false)
        a.onMove(1, 112f, 209f, "w", false)
        a.onMove(1, 105f, 203f, "q", false)
        assertEquals("抖动不该升级为滑动", O.PENDING, a.state.owner)
        assertEquals(D.EMIT_TAP, a.onUp(1))
    }

    @Test
    fun jitterJustUnderThresholdStaysTap() {
        val a = defaultArbiter()
        a.onDown(1, 100f, 200f, "e")
        // 25px：仍在阈值内（阈值 28px）
        a.onMove(1, 118f, 220f, "d", false)
        assertEquals(O.PENDING, a.state.owner)
        assertEquals(D.EMIT_TAP, a.onUp(1))
    }

    @Test
    fun realSwipeStillUpgrades() {
        val a = defaultArbiter()
        a.onDown(1, 100f, 200f, "q")
        // 真正滑动会跨过整块键位
        a.onMove(1, 160f, 200f, "w", false)
        a.onMove(1, 220f, 200f, "e", false)
        assertEquals(O.GLIDE, a.state.owner)
        assertEquals(D.EMIT_GLIDE, a.onUp(1))
    }
}
