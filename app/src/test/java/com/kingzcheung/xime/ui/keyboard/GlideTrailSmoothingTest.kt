package com.kingzcheung.xime.ui.keyboard

import com.kingzcheung.xime.glide.GlidePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 轨迹平滑：抽稀 + 三点加权平均，目的是去掉手指抖动造成的颗粒感。 */
class GlideTrailSmoothingTest {

    private fun p(x: Float, y: Float, t: Long = 0L) = GlidePoint(x, y, t, 0)

    @Test
    fun shortTrailsPassThrough() {
        assertEquals(0, smoothTrailPoints(emptyList()).size)
        val single = listOf(p(1f, 2f))
        assertEquals(1, smoothTrailPoints(single).size)
        val two = listOf(p(0f, 0f), p(10f, 0f))
        assertEquals(2, smoothTrailPoints(two).size)
    }

    @Test
    fun duplicatePointsAreThinnedOut() {
        // 手指没动时连续上报同一个坐标：不该保留一堆重合点
        val points = (0 until 10).map { p(5f, 5f) }
        val smoothed = smoothTrailPoints(points)
        assertTrue("重合点应被抽稀", smoothed.size <= 2)
    }

    @Test
    fun collinearLineStaysStraight() {
        val points = (0 until 20).map { p(it * 4f, 0f) }
        val smoothed = smoothTrailPoints(points, passes = 2)
        // 直线经加权平均仍是直线
        smoothed.forEach { assertEquals(0f, it.y, 0.001f) }
        // 端点不移动
        assertEquals(0f, smoothed.first().x, 0.001f)
        assertEquals(points.last().x, smoothed.last().x, 0.001f)
    }

    @Test
    fun zigZagJitterIsDamped() {
        // 锯齿：y 在 ±6 之间抖。平滑后振幅应明显变小
        val points = (0 until 30).map { i -> p(i * 4f, if (i % 2 == 0) -6f else 6f) }
        val smoothed = smoothTrailPoints(points, passes = 2)
        val maxDeviation = smoothed.drop(1).dropLast(1).maxOf { kotlin.math.abs(it.y) }
        assertTrue("锯齿振幅应被压低", maxDeviation < 3f)
        // 端点保持
        assertEquals(-6f, smoothed.first().y, 0.001f)
        assertEquals(6f, smoothed.last().y, 0.001f)
    }

    @Test
    fun trailingPointIsPreserved() {
        // 抽稀不该把最后一截轨迹吃掉
        val points = listOf(p(0f, 0f), p(10f, 0f), p(10.5f, 0f))
        val smoothed = smoothTrailPoints(points)
        assertEquals(points.last().x, smoothed.last().x, 0.001f)
    }
}
