package com.kingzcheung.xime.glide

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** 真机验证采集落盘、标注追加与导出副本可读（导出通道的前提）。 */
@RunWith(AndroidJUnit4::class)
class GlideTraceExportDeviceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun geometry() = GlideGeometrySnapshot(
        "v-device",
        listOf(GlideKey("a", "a", GlideKeyRect(0f, 0f, 100f, 100f))),
        100f, 100f,
    )

    @Before
    fun setup() {
        GlideTraceRecorder.init(context)
        GlideTraceRecorder.clearSamples()
        GlideTraceRecorder.enabled = true
    }

    @After
    fun tearDown() {
        GlideTraceRecorder.clearSamples()
        GlideTraceRecorder.enabled = false
    }

    private fun record(code: String = "nihao"): String = GlideTraceRecorder.record(
        points = listOf(GlidePoint(10f, 10f, 0L, 1), GlidePoint(60f, 60f, 30L, 1)),
        geometry = geometry(),
        schemaFingerprint = "rime_ice",
        prefix = "",
        code = code,
        candidates = listOf("nihao", "nhai"),
    )

    private fun lines(): List<String> = GlideTraceRecorder.file()!!.readLines().filter { it.isNotBlank() }

    @Test
    fun 抬手即落盘且含轨迹与几何() {
        val id = record()
        assertTrue(id.isNotEmpty())
        assertEquals(1, lines().size)
        val obj = JSONObject(lines().first())
        assertEquals("trace", obj.getString("e"))
        assertEquals(id, obj.getString("id"))
        assertEquals("", obj.getString("expected"))
        assertTrue(obj.has("points"))
        assertTrue(obj.has("keys"))
        assertTrue(obj.has("candidates"))
    }

    @Test
    fun 标注以独立行追加并可手动覆盖() {
        val id = record()
        GlideTraceRecorder.labelConfirmed(id, "你好", "nihao")
        GlideTraceRecorder.label(id, "你号", "mark")
        val ls = lines()
        assertEquals("轨迹 1 行 + 标注 2 行", 3, ls.size)
        val last = JSONObject(ls.last())
        assertEquals("label", last.getString("e"))
        assertEquals("mark", last.getString("src"))
        assertEquals("你号", last.getString("expected"))
        assertEquals("你号", GlideTraceRecorder.recentEntries().first().expected)
    }

    @Test
    fun 导出副本存在且内容与原文件一致() {
        record()
        val copy = GlideTraceRecorder.exportCopy()
        assertNotNull("有样本时应产出导出副本", copy)
        try {
            assertEquals(GlideTraceRecorder.file()!!.readText(), copy!!.readText())
        } finally {
            copy?.delete()
        }
    }

    @Test
    fun 清空后不再导出() {
        record()
        GlideTraceRecorder.clearSamples()
        assertEquals(0, GlideTraceRecorder.recordCount())
        assertTrue(GlideTraceRecorder.recentEntries().isEmpty())
        assertNull(GlideTraceRecorder.exportCopy())
    }
}
