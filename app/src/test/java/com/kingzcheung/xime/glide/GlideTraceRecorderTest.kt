package com.kingzcheung.xime.glide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 轨迹采集器的样本契约（纯 JVM，不落盘）：
 * 取消的滑动不得被后续确认认领，编码不匹配的上屏不得写成样本。
 */
class GlideTraceRecorderTest {

    private val geometry = GlideGeometrySnapshot(
        "v1",
        listOf(GlideKey("a", "a", GlideKeyRect(0f, 0f, 100f, 100f))),
        100f, 100f,
    )

    private fun points() = listOf(
        GlidePoint(10f, 10f, 0L, 1),
        GlidePoint(50f, 50f, 20L, 1),
    )

    private fun pendingCode(): String? {
        val f = GlideTraceRecorder::class.java.getDeclaredField("pending")
        f.isAccessible = true
        val p = f.get(GlideTraceRecorder) ?: return null
        val code = p.javaClass.getDeclaredField("code")
        code.isAccessible = true
        return code.get(p) as String
    }

    @Test
    fun `关闭时不保留待写样本`() {
        GlideTraceRecorder.enabled = false
        GlideTraceRecorder.discard()
        GlideTraceRecorder.onTraceFinished(points(), geometry, "s", "", listOf("na"))
        assertNull(pendingCode())
    }

    @Test
    fun `开启时按安装编码记录样本`() {
        GlideTraceRecorder.enabled = true
        GlideTraceRecorder.discard()
        GlideTraceRecorder.onTraceFinished(points(), geometry, "s", "ni", listOf("hao", "hoa"))
        // 缺省按 前缀 + 几何首选假设 作为安装编码
        assertEquals("nihao", pendingCode())
        GlideTraceRecorder.onCandidatesInstalled("nihaoo")
        assertEquals("nihaoo", pendingCode())
        GlideTraceRecorder.discard()
        GlideTraceRecorder.enabled = false
    }

    @Test
    fun `取消后确认不再认领旧轨迹`() {
        GlideTraceRecorder.enabled = true
        GlideTraceRecorder.onTraceFinished(points(), geometry, "s", "", listOf("nihao"))
        assertNotNull(pendingCode())
        GlideTraceRecorder.discard()
        assertNull(pendingCode())
        GlideTraceRecorder.enabled = false
    }

    @Test
    fun `单点轨迹不产生样本`() {
        GlideTraceRecorder.enabled = true
        GlideTraceRecorder.discard()
        GlideTraceRecorder.onTraceFinished(listOf(GlidePoint(1f, 1f, 0L, 1)), geometry, "s", "", listOf("a"))
        assertNull(pendingCode())
        GlideTraceRecorder.enabled = false
    }

    @Test
    fun `编码不匹配时丢弃样本`() {
        GlideTraceRecorder.enabled = true
        GlideTraceRecorder.discard()
        GlideTraceRecorder.onTraceFinished(points(), geometry, "s", "", listOf("nihao"))
        GlideTraceRecorder.attachExpected("你好", "buPipei")
        assertNull(pendingCode())
        GlideTraceRecorder.enabled = false
    }
}
