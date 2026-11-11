package com.kingzcheung.xime.glide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 键位注册表的坐标处理与行列聚类。
 *
 * 风险点：行列由坐标聚类推导（为了支持分体键盘/自定义布局），
 * 聚类算法的容差和贪心策略若不合理，会把同排键判成多排，
 * 或把相邻排合并——直接破坏"相邻键"判断和连线代价。
 */
class GlideGeometryRegistryTest {

    private fun key(code: String, l: Float, t: Float, r: Float, b: Float, id: String = code) =
        GlideKey(id, code, GlideKeyRect(l, t, r, b))

    @Test
    fun 同一排键应聚为一行() {
        val reg = GlideGeometryRegistry()
        reg.beginFrame(1080f, 500f)
        // 一行 3 个键，top 完全相同
        reg.register(key("q", 0f, 0f, 100f, 100f))
        reg.register(key("w", 100f, 0f, 200f, 100f))
        reg.register(key("e", 200f, 0f, 300f, 100f))
        val snap = reg.snapshot()
        assertEquals("所有键应在同一行", 1, snap.keys.map { it.row }.distinct().size)
        assertEquals("应有 3 列", 3, snap.keys.map { it.col }.distinct().size)
    }

    @Test
    fun 亚像素差异不应拆成多行() {
        val reg = GlideGeometryRegistry()
        reg.beginFrame(1080f, 500f)
        // 同一排有 0.3px 的 top 抖动（真机上常见）
        reg.register(key("q", 0f, 0f, 100f, 100f))
        reg.register(key("w", 100f, 0.3f, 200f, 100.3f))
        reg.register(key("e", 200f, 0.6f, 300f, 100.6f))
        val snap = reg.snapshot()
        assertEquals("亚像素差异不应拆行", 1, snap.keys.map { it.row }.distinct().size)
    }

    @Test
    fun 不同行应分开() {
        val reg = GlideGeometryRegistry()
        reg.beginFrame(1080f, 500f)
        reg.register(key("q", 0f, 0f, 100f, 100f))
        reg.register(key("a", 0f, 100f, 100f, 200f))
        reg.register(key("z", 0f, 200f, 100f, 300f))
        val snap = reg.snapshot()
        assertEquals("三行应分开", 3, snap.keys.map { it.row }.distinct().size)
    }

    @Test
    fun 原点偏移应转换到键盘局部坐标() {
        val reg = GlideGeometryRegistry()
        // 键盘根容器在屏幕 (0, 800)
        reg.beginFrame(1080f, 500f, originX = 0f, originY = 800f)
        reg.register(key("q", 0f, 800f, 100f, 900f))
        val snap = reg.snapshot()
        val k = snap.keys.first()
        assertEquals("top 应减去原点", 0f, k.rect.top, 0.01f)
        assertEquals("bottom 应减去原点", 100f, k.rect.bottom, 0.01f)
    }

    @Test
    fun 非法键不应注册() {
        val reg = GlideGeometryRegistry()
        reg.beginFrame(1080f, 500f)
        reg.register(key("q", 0f, 0f, 0f, 0f))  // 宽高为 0
        assertEquals("非法键不应进入快照", 0, reg.size)
    }

    @Test
    fun 同id重复注册应覆盖() {
        val reg = GlideGeometryRegistry()
        reg.beginFrame(1080f, 500f)
        reg.register(key("q", 0f, 0f, 100f, 100f, id = "same"))
        reg.register(key("q", 50f, 50f, 150f, 150f, id = "same"))
        assertEquals("同 id 应覆盖而非累加", 1, reg.size)
    }

    @Test
    fun beginFrame应清空并递增代际() {
        val reg = GlideGeometryRegistry()
        reg.beginFrame(1080f, 500f)
        val gen1 = reg.currentGeneration
        reg.register(key("q", 0f, 0f, 100f, 100f))
        reg.beginFrame(1080f, 500f)
        assertTrue("代际应递增", reg.currentGeneration > gen1)
        assertEquals("应清空旧键", 0, reg.size)
    }

    /**
     * cluster 是贪心实现：与「上一个中心」的差 <= 容差就并入，会链式累积。
     * 例如键高 100（容差 40）时，top = 0/39/78/117 会被并成 1 个中心，
     * 尽管 117-0 = 117 远超容差。
     *
     * 真机 26 键布局下行距 166、容差 66.4，间距远大于容差，**不会触发**。
     * 这里用测试记录该行为及其适用边界：若将来支持行距 < 0.4*键高的
     * 紧凑布局，需要把 cluster 换成真正的 1D 聚类（如按间隙分割）。
     */
    @Test
    fun 链式相邻的top会被贪心合并_记录已知边界() {
        val reg = GlideGeometryRegistry()
        reg.beginFrame(1080f, 800f)
        reg.register(key("q", 0f, 0f, 100f, 100f))
        reg.register(key("a", 0f, 39f, 100f, 139f))
        reg.register(key("z", 0f, 78f, 100f, 178f))
        reg.register(key("x", 0f, 117f, 100f, 217f))
        val rows = reg.snapshot().keys.map { it.row }.distinct().size
        // 实测：0/39/78/117 被贪心聚成 2 个中心（0 与 78），而非 4 个。
        // 断言具体数值以锁住当前行为，改动 cluster 时能被发现。
        assertEquals("已知边界：4 个不同 top 被贪心链式并成 2 行（真机布局不会触发）", 2, rows)
    }

    @Test
    fun 真实行距下不应误合并() {
        // 真机 26 键布局：键高 166、行距 166，容差 66.4
        val reg = GlideGeometryRegistry()
        reg.beginFrame(1080f, 498f)
        reg.register(key("q", 0f, 0f, 108f, 166f))
        reg.register(key("a", 0f, 166f, 108f, 332f))
        reg.register(key("z", 0f, 332f, 108f, 498f))
        val rows = reg.snapshot().keys.map { it.row }.distinct().size
        assertEquals("三行必须分开", 3, rows)
    }

    @Test
    fun 真实键盘的亚像素left差异应聚成正确列数() {
        // 实测样本里的 left 值含亚像素抖动（155/162、266/270、376/378…），
        // 容差 66.4 仍应准确聚出 10 列。
        val reg = GlideGeometryRegistry()
        reg.beginFrame(1080f, 498f)
        val lefts = listOf(0f, 54f, 108f, 155f, 162f, 216f, 266f, 270f, 324f, 376f,
            378f, 432f, 486f, 540f, 594f, 596f, 648f, 702f, 706f, 756f, 810f, 816f, 864f, 918f, 972f)
        lefts.forEachIndexed { i, l ->
            reg.register(key("k$i", l, 0f, l + 100f, 166f))
        }
        val cols = reg.snapshot().keys.map { it.col }.distinct().size
        assertEquals("26 键 qwerty 应聚成 10 列", 10, cols)
    }

    @Test
    fun 快照version应含代际() {
        val reg = GlideGeometryRegistry()
        reg.beginFrame(1080f, 500f)
        val v1 = reg.snapshot().version
        reg.beginFrame(1080f, 500f)
        val v2 = reg.snapshot().version
        assertTrue("布局变化后 version 应不同", v1 != v2)
    }
}
