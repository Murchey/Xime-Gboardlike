package com.kingzcheung.xime.glide

import org.junit.Assert.*
import org.junit.Test

class GlideGeometryTest {
    private fun key(id: String, code: String, l: Float, t: Float, r: Float, b: Float, zone: String = "") =
        GlideKey(id, code, GlideKeyRect(l, t, r, b), zone = zone)

    private fun snapshot() = GlideGeometrySnapshot(
        version = "portrait@v1", widthPx = 1080f, heightPx = 600f,
        keys = listOf(
            key("q0", "q", 0f, 0f, 100f, 100f),
            key("w0", "w", 100f, 0f, 200f, 100f),
            // 分体键盘：同一字母 w 两个物理实例
            key("w1", "w", 800f, 0f, 900f, 100f, zone = "right"),
            key("semi", ";", 900f, 0f, 1000f, 100f, zone = "right"),
        ))

    @Test fun `矩形属性与命中`() {
        val r = GlideKeyRect(10f, 20f, 60f, 90f)
        assertEquals(50f, r.width, 0.01f)
        assertEquals(70f, r.height, 0.01f)
        assertEquals(35f, r.centerX, 0.01f)
        assertTrue(r.contains(35f, 55f))
        assertFalse(r.contains(5f, 55f))
    }

    @Test fun `同一字母允许多个物理实例`() {
        val s = snapshot()
        assertEquals(4, s.keys.size)
        assertEquals(2, s.eligibleKeys.count { it.code == "w" })
        assertTrue(s.isValid)
    }

    @Test fun `keyAt 取落在矩形内且中心最近的键`() {
        val s = snapshot()
        assertEquals("q0", s.keyAt(50f, 50f)?.id)
        assertEquals("w0", s.keyAt(150f, 50f)?.id)
        assertEquals("w1", s.keyAt(850f, 50f)?.id)
        assertNull(s.keyAt(500f, 50f))
    }

    @Test fun `nearestKey 受最大距离约束`() {
        val s = snapshot()
        assertEquals("q0", s.nearestKey(50f, 50f)?.id)
        assertNull(s.nearestKey(500f, 50f, maxDistance = 10f))
        assertNotNull(s.nearestKey(500f, 50f, maxDistance = 1000f))
    }

    @Test fun `分号韵母键可参与几何`() {
        val s = snapshot()
        assertEquals(";", s.keyAt(950f, 50f)?.code)
        assertEquals("right", s.keyAt(950f, 50f)?.zone)
    }

    @Test fun `非法快照不参与解码`() {
        assertFalse(GlideGeometrySnapshot("", emptyList(), 1080f, 600f).isValid)
        assertFalse(GlideGeometrySnapshot("v", listOf(
            GlideKey("bad", "", GlideKeyRect(0f,0f,10f,10f))), 1080f, 600f).isValid)
    }

    @Test fun `distanceToEdge 在键内为0 键外为正`() {
        val r = GlideKeyRect(0f, 0f, 100f, 100f)
        assertEquals(0f, r.distanceToEdge(50f, 50f), 0.01f)
        assertEquals(20f, r.distanceToEdge(120f, 50f), 0.01f)
    }
}
