package com.kingzcheung.xime.glide

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class GlideTraceTest {
    private fun pts(vararg xy: Pair<Float, Float>, step: Long = 16L) =
        xy.mapIndexed { i, p -> GlidePoint(p.first, p.second, i * step, 1) }

    @Test fun `轨迹时长与弧长`() {
        val t = GlideTrace(1, "v1", "rime_ice", "host", pts(0f to 0f, 30f to 40f, 30f to 90f))
        assertEquals(32L, t.durationMs)
        assertEquals(100f, t.totalArcLength, 0.01f)
        assertTrue(t.isValid)
    }

    @Test fun `单点轨迹无效`() {
        assertFalse(GlideTrace(1, "v1", "s", "h", pts(0f to 0f)).isValid)
    }

    @Test fun `downsample保留起终点并抽稀中间`() {
        val p = pts(0f to 0f, 1f to 0f, 2f to 0f, 3f to 0f, 40f to 0f, 80f to 0f)
        val d = GlideTraceSampler.downsample(p, 10f)
        assertEquals(p.first(), d.first())
        assertEquals(p.last(), d.last())
        assertTrue(d.size < p.size)
    }

    @Test fun `resample等弧长且保留端点与时间单调`() {
        val p = pts(0f to 0f, 100f to 0f, step = 100L)
        val r = GlideTraceSampler.resampleByArc(p, 10f)
        assertEquals(0f, r.first().x, 0.01f)
        assertEquals(100f, r.last().x, 0.01f)
        assertTrue(r.size >= 10)
        for (i in 1 until r.size) {
            assertTrue(r[i].timeMs >= r[i - 1].timeMs)
            val d = abs(r[i].x - r[i - 1].x)
            assertTrue(d <= 10.01f)
        }
    }

    @Test fun `零长度轨迹resample不崩`() {
        val p = pts(5f to 5f, 5f to 5f)
        val r = GlideTraceSampler.resampleByArc(p, 10f)
        assertEquals(2, r.size)
    }

    @Test fun `特征提取保留重复键不去重`() {
        val geo = GlideGeometrySnapshot("v1", listOf(
            GlideKey("a", "a", GlideKeyRect(0f, 0f, 100f, 100f)),
            GlideKey("b", "b", GlideKeyRect(100f, 0f, 200f, 100f)),
        ), 200f, 100f)
        // a -> a -> b -> a：a 重复进入必须保留
        val p = pts(50f to 50f, 55f to 50f, 150f to 50f, 50f to 50f)
        val f = GlideTraceSampler.extractFeatures(p, geo, 1000f)
        assertEquals(4, f.size)
        assertEquals(listOf("a", "a", "b", "a"), GlideTraceSampler.visitedCodes(f))
    }

    @Test fun `特征累计弧长递增且转角可计算`() {
        val geo = GlideGeometrySnapshot("v1", listOf(
            GlideKey("a", "a", GlideKeyRect(0f, 0f, 100f, 100f))), 100f, 100f)
        val p = pts(10f to 10f, 50f to 10f, 50f to 50f)
        val f = GlideTraceSampler.extractFeatures(p, geo, 1000f)
        assertEquals(3, f.size)
        assertTrue(f[1].arcLength >= f[0].arcLength)
        assertTrue(f[2].arcLength > f[1].arcLength)
        // 第二次方向从上一段(向右)变为向下，应有非零转角
        assertTrue(f[2].turn > 0f)
    }

    @Test fun `超出最大键距离的点code为null且不中断`() {
        val geo = GlideGeometrySnapshot("v1", listOf(
            GlideKey("a", "a", GlideKeyRect(0f, 0f, 10f, 10f))), 1000f, 1000f)
        val p = pts(1f to 1f, 900f to 900f)
        val f = GlideTraceSampler.extractFeatures(p, geo, 5f)
        assertEquals(2, f.size)
        assertEquals("a", f[0].code)
        assertNull(f[1].code)
    }
}
