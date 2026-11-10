package com.kingzcheung.xime.glide

import org.junit.Assert.*
import org.junit.Test

/**
 * 行列推导回归：同排键必须落在同一 row（容差聚类），
 * 否则"相邻键"约束会把所有路径判成不合理跳跃，滑动直接没有候选。
 */
class GlideGridRegressionTest {
    private fun k(id: String, code: String, l: Float, t: Float) =
        GlideKey(id, code, GlideKeyRect(l, t, l + 100f, t + 120f))

    @Test fun `同排键亚像素差异仍归为同一行`() {
        val r = GlideGeometryRegistry()
        r.beginFrame(1080f, 700f)
        r.register(k("q","q",0f,0f))
        r.register(k("w","w",100f,0.3f))     // 亚像素差
        r.register(k("e","e",200f,0.7f))
        val snap = r.snapshot()
        val rows = snap.keys.map { it.row }.distinct()
        assertEquals("同排必须同一 row", 1, rows.size)
    }

    @Test fun `不同行被分开且列也正确聚类`() {
        val r = GlideGeometryRegistry()
        r.beginFrame(1080f, 700f)
        r.register(k("q","q",0f,0f))
        r.register(k("a","a",50f,130f))
        val snap = r.snapshot()
        val q = snap.keys.first { it.code == "q" }
        val a = snap.keys.first { it.code == "a" }
        assertNotEquals("不同行必须不同 row", q.row, a.row)
    }

    @Test fun `相邻键网格距离在合理范围内`() {
        val r = GlideGeometryRegistry()
        r.beginFrame(1080f, 700f)
        (0..4).forEach { i -> r.register(k("k" + i, "" + ('a' + i), i * 100f, 0f)) }
        val snap = r.snapshot()
        val keys = snap.keys.sortedBy { it.col }
        assertTrue("相邻键应相邻", keys[0].gridDistance(keys[1]) <= 2)
    }
}
