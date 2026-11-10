package com.kingzcheung.xime.glide

import com.kingzcheung.xime.correction.SpatialTapModel
import org.junit.Assert.*
import org.junit.Test

class GlideGaussianCorrectionTest {
    @Test fun 高斯中心代价为零并按二维方差惩罚偏移() {
        val key = SpatialTapModel.KeyGeometry(50f, 70f, 100f, 140f)
        assertEquals(0f, SpatialTapModel.alignmentCost(50f, 70f, key, 440f)!!, 0.0001f)
        val x = SpatialTapModel.alignmentCost(70f, 70f, key, 440f)!!
        val y = SpatialTapModel.alignmentCost(50f, 90f, key, 440f)!!
        assertTrue(x > 0f && y > 0f)
        assertNotEquals("横纵热区应按各自物理尺寸建模", x, y)
        assertEquals(x * 4f, SpatialTapModel.alignmentCost(90f, 70f, key, 440f)!!, 0.0001f)
    }

    @Test fun 相同物理键位在不同屏幕密度下空间代价一致() {
        val a = SpatialTapModel.KeyGeometry(50f, 70f, 100f, 140f)
        val b = SpatialTapModel.KeyGeometry(100f, 140f, 200f, 280f)
        assertEquals(SpatialTapModel.alignmentCost(70f, 90f, a, 220f)!!,
            SpatialTapModel.alignmentCost(140f, 180f, b, 440f)!!, 0.0001f)
    }

    @Test fun 无效几何和密度不产生NaN() {
        val key = SpatialTapModel.KeyGeometry(50f, 70f, 100f, 140f)
        assertNull(SpatialTapModel.alignmentCost(50f, 70f, key, 0f))
        assertNull(SpatialTapModel.alignmentCost(Float.NaN, 70f, key, 440f))
        assertNull(SpatialTapModel.alignmentCost(50f, 70f, key.copy(widthPx = 0f), 440f))
    }

    private fun geometry(): GlideGeometrySnapshot {
        val rows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
        val keys = rows.flatMapIndexed { r, row -> row.mapIndexed { c, ch ->
            val x = c * 100f + listOf(0f, 50f, 150f)[r]
            GlideKey(ch.toString(), ch.toString(), GlideKeyRect(x, r * 140f, x + 100f, r * 140f + 140f))
        } }
        return GlideGeometrySnapshot("test", keys, 1000f, 420f)
    }

    @Test fun 滑动默认确实使用高斯评分且保留邻键歧义供词库判断() {
        val g = geometry()
        val points = mutableListOf<GlidePoint>()
        var time = 1000L
        for (ch in "nihao") {
            val key = g.keys.first { it.code == ch.toString() }
            val x = key.rect.centerX + 18f
            val y = key.rect.centerY + 8f
            points.lastOrNull()?.let { prev -> for (step in 1..8) {
                time += 16
                val f = step / 9f
                points += GlidePoint(prev.x + (x - prev.x) * f, prev.y + (y - prev.y) * f, time, 1)
            } }
            time += 16
            points += GlidePoint(x, y, time, 1)
            time += 150
        }
        val spells = listOf("ni", "hao", "mi", "gao", "na", "huo")
        val default = GlideGeometryDecoder.decode(points, g, 16, spells, densityDpi = 440f)
        val enabled = GlideGeometryDecoder.decode(points, g, 16, spells, densityDpi = 440f, gaussianCorrection = true)
        val legacy = GlideGeometryDecoder.decode(points, g, 16, spells, densityDpi = 440f, gaussianCorrection = false)
        assertEquals(enabled, default)
        assertTrue("偏移轨迹仍应保留 nihao: " + default, default.take(5).any { it.letters == "nihao" })
        assertNotEquals("高斯开关必须实际改变评分", legacy, enabled)
    }
}
