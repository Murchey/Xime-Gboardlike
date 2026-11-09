package com.kingzcheung.xime.glide

import org.junit.Assert.*
import org.junit.Test

class GlideGeometryRegistryTest {
    private fun k(id: String, code: String, l: Float) =
        GlideKey(id, code, GlideKeyRect(l, 0f, l + 100f, 100f))

    @Test fun `注册与快照`() {
        val r = GlideGeometryRegistry()
        r.beginFrame(1080f, 600f)
        r.register(k("row0-col0", "q", 0f))
        r.register(k("row0-col1", "w", 100f))
        val s = r.snapshot()
        assertEquals(2, s.keys.size)
        assertTrue(s.isValid)
        assertEquals("q", s.keyAt(50f, 50f)?.code)
    }

    @Test fun `同一编码多个物理实例共存`() {
        val r = GlideGeometryRegistry()
        r.beginFrame(1080f, 600f)
        r.register(k("left-w", "w", 100f))
        r.register(k("right-w", "w", 800f))
        assertEquals(2, r.snapshot().keys.count { it.code == "w" })
    }

    @Test fun `新帧清空且改变version使旧轨迹失效`() {
        val r = GlideGeometryRegistry()
        r.beginFrame(1080f, 600f)
        r.register(k("a", "a", 0f))
        val old = r.snapshot()
        r.beginFrame(1080f, 650f)
        assertTrue(r.snapshot().version != old.version)
        assertFalse(r.snapshot().isValid)
    }

    @Test fun `非法键被忽略 注销生效`() {
        val r = GlideGeometryRegistry()
        r.beginFrame(1080f, 600f)
        r.register(GlideKey("bad", "", GlideKeyRect(0f, 0f, 10f, 10f)))
        assertEquals(0, r.size)
        r.register(k("a", "a", 0f))
        r.unregister("a")
        assertEquals(0, r.size)
    }

    @Test fun `重复注册同一id覆盖而不叠加`() {
        val r = GlideGeometryRegistry()
        r.beginFrame(1080f, 600f)
        r.register(k("a", "a", 0f))
        r.register(k("a", "b", 200f))
        assertEquals(1, r.size)
        assertEquals("b", r.snapshot().keyAt(250f, 50f)?.code)
    }
}
