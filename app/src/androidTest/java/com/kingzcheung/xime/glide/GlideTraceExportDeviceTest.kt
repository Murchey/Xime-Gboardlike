package com.kingzcheung.xime.glide

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** 真机验证采集落盘与导出副本可读（导出通道的前提）。 */
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
        GlideTraceRecorder.discard()
        GlideTraceRecorder.clearSamples()
        GlideTraceRecorder.enabled = false
    }

    private fun writeOne(expected: String = "你好"): Boolean {
        GlideTraceRecorder.onTraceFinished(
            points = listOf(GlidePoint(10f, 10f, 0L, 1), GlidePoint(60f, 60f, 30L, 1)),
            geometry = geometry(),
            schemaFingerprint = "rime_ice",
            prefix = "",
            candidates = listOf("nihao", "nhai"),
        )
        GlideTraceRecorder.onCandidatesInstalled("nihao")
        GlideTraceRecorder.attachExpected(expected, "nihao")
        return GlideTraceRecorder.recordCount() == 1
    }

    @Test
    fun 采样落盘并可按jsonl导出行读取() {
        assertTrue("应写入一条样本", writeOne())
        val line = GlideTraceRecorder.file()!!.readLines().first()
        assertTrue("应含期望文本", line.contains("\"expected\":\"你好\""))
        assertTrue("应含键位几何", line.contains("\"keys\""))
        assertTrue("应含轨迹点", line.contains("\"points\""))
        assertTrue("应含几何候选", line.contains("\"candidates\""))
    }

    @Test
    fun 导出副本存在且内容与原文件一致() {
        assertTrue(writeOne("测试"))
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
        assertTrue(writeOne())
        GlideTraceRecorder.clearSamples()
        assertEquals(0, GlideTraceRecorder.recordCount())
        assertNull(GlideTraceRecorder.exportCopy())
    }
}
