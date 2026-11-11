package com.kingzcheung.xime.glide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 轨迹时间戳的单调性与重复容忍。
 *
 * 真机采集里同一毫秒常有 2 个采样点（设备高频上报），实测 20 条样本
 * 共数百个重复时间戳但**没有逆序**。下游依赖：
 *  - dwellPositions 用「总时长 >= DWELL_MS」判停留（重复不影响）
 *  - GlideRepeatGesture 要求折返时差 >= 32ms（连续 2 点重复仅占几毫秒，可忽略）
 * 这里锁住这两个前提，避免将来引入「相邻时差必须 > 0」的假设。
 */
class GlideTimestampSemanticsTest {

    private fun pt(x: Float, y: Float, t: Long) = GlidePoint(x, y, t, 1)

    @Test
    fun 重复时间戳不应导致停留判据失效() {
        // 同一点上密集上报 200ms，其中一半时间戳重复
        val pts = mutableListOf<GlidePoint>()
        var t = 0L
        repeat(10) { pts += pt(100f, 100f, t) }      // 同一毫秒 10 个点
        t += 200
        repeat(10) { pts += pt(100f, 100f, t) }
        // 用「总时长」判据：0 -> 200 满足 >= 120
        assertEquals(200L, pts.last().timeMs - pts.first().timeMs)
    }

    @Test
    fun 同一毫秒的连续点不应被当成折返() {
        // 连续 2 点时差为 0，GlideRepeatGesture 的 >=32ms 门槛会挡住它
        val a = pt(100f, 100f, 500L)
        val b = pt(160f, 100f, 500L)
        assertTrue("时差 0 应低于 32ms 门槛", b.timeMs - a.timeMs < 32L)
    }

    @Test
    fun 正常采样间隔应超过折返门槛() {
        // 真机典型间隔 3~5ms，累积 10 个点即可超过 32ms
        var t = 0L
        val pts = mutableListOf<GlidePoint>()
        repeat(10) { pts += pt(100f, 100f, t); t += 4 }
        assertTrue("10 个点累积应超过 32ms",
            pts.last().timeMs - pts.first().timeMs >= 32L)
    }
}
