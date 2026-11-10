package com.kingzcheung.xime.glide

import org.junit.Assert.*
import org.junit.Test

/**
 * 坐标系一致性：键用 root 坐标上报，快照输出必须转成键盘局部坐标，
 * 才能与父层 pointerInput 的 down.position 对齐。
 */
class GlideOriginTest {

    private fun key(id: String, code: String, l: Float, t: Float) =
        GlideKey(id, code, GlideKeyRect(l, t, l + 100f, t + 120f))

    @Test fun `快照减去键盘原点得到局部坐标`() {
        val r = GlideGeometryRegistry()
        r.beginFrame(1080f, 957f, originX = 0f, originY = 1674f)
        r.register(key("a", "a", 0f, 1674f))
        val snap = r.snapshot()
        val k = snap.keys.first()
        assertEquals("顶部应回到键盘局部 0", 0f, k.rect.top, 0.01f)
        assertEquals(0f, k.rect.left, 0.01f)
    }

    @Test fun `不同高度的键盘原点都能正确归一`() {
        val r = GlideGeometryRegistry()
        r.beginFrame(1080f, 800f, originX = 12f, originY = 1500f)
        r.register(key("q", "q", 12f, 1500f))
        r.register(key("w", "w", 112f, 1500f))
        val snap = r.snapshot()
        with(snap) {
            assertTrue(keys.all { it.rect.top == 0f })
            assertEquals(0f, keys.first { it.code == "q" }.rect.left, 0.01f)
            assertEquals(100f, keys.first { it.code == "w" }.rect.left, 0.01f)
        }
    }

    @Test fun `原点为零时行为不变`() {
        val r = GlideGeometryRegistry()
        r.beginFrame(1080f, 700f)
        r.register(key("a", "a", 0f, 0f))
        assertEquals(0f, r.snapshot().keys.first().rect.top, 0.01f)
    }

    @Test fun `局部坐标下同行聚类仍成立`() {
        val r = GlideGeometryRegistry()
        r.beginFrame(1080f, 700f, originX = 0f, originY = 1000f)
        (0..3).forEach { i -> r.register(key("k" + i, "" + ('a' + i), i * 100f, 1000f)) }
        assertEquals("同排必须同一 row", 1, r.snapshot().keys.map { it.row }.distinct().size)
    }
}
