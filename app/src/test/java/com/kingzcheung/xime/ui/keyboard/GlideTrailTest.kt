package com.kingzcheung.xime.ui.keyboard

import com.kingzcheung.xime.glide.GlidePoint
import org.junit.Assert.*
import org.junit.Test

class GlideTrailTest {
    private fun point(x: Float, time: Long) = GlidePoint(x, 20f, time, 1)

    @Test fun 尾迹随时间渐淡并完全消失() {
        assertEquals(1f, glideTrailAlpha(1000, 1000), 0.0001f)
        assertEquals(0.25f, glideTrailAlpha(1000, 1140), 0.0001f)
        assertEquals(0f, glideTrailAlpha(1000, 1280), 0.0001f)
        assertEquals(0f, glideTrailAlpha(1000, 2000), 0.0001f)
    }
    @Test fun 抬手不立即清除最后一段轨迹() {
        val trail = GlideTrailState()
        trail.update(listOf(point(10f, 1000)), 5000)
        trail.update(listOf(point(10f, 1000), point(50f, 1020)), 5020)
        trail.update(emptyList(), 5025)
        assertEquals(2, trail.points.size)
        trail.advance(5160)
        assertEquals(2, trail.points.size)
        trail.advance(5300)
        assertTrue(trail.points.isEmpty())
    }
    @Test fun 长轨迹只保留可见尾段但不修改解码数据() {
        val source = (0..1000).map { point(it.toFloat(), 1000L + it * 10) }
        val trail = GlideTrailState()
        trail.update(source, 15000)
        assertTrue(trail.points.size <= 29)
        assertEquals(1001, source.size)
        assertEquals(1000f, trail.points.last().x)
    }
    @Test fun 新手势不连接上一条残影() {
        val trail = GlideTrailState()
        trail.update(listOf(point(10f, 1000), point(50f, 1020)), 5000)
        trail.update(listOf(point(200f, 1040)), 5020)
        assertEquals(1, trail.points.size)
        assertEquals(200f, trail.points.single().x)
    }
    @Test fun 清除轨迹可即时关闭显示() {
        val trail = GlideTrailState()
        trail.update(listOf(point(10f, 1000), point(50f, 1020)), 5000)
        trail.clear()
        assertTrue(trail.points.isEmpty())
    }
}
