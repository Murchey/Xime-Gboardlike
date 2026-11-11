package com.kingzcheung.xime.glide

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 轨迹采集器的样本契约（纯 JVM，不落盘；未 init 时 write 直接返回）：
 * 抬手就记、标注独立追加、手动标记优先于自动确认。
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

    private fun record(code: String = "nihao", candidates: List<String> = listOf("nihao", "nihoa")) =
        GlideTraceRecorder.record(
            points = points(),
            geometry = geometry,
            schemaFingerprint = "rime_ice",
            prefix = "",
            code = code,
            candidates = candidates,
        )

    @After
    fun tearDown() {
        GlideTraceRecorder.clearSamples()
        GlideTraceRecorder.enabled = false
    }

    @Test
    fun `关闭时不记录`() {
        GlideTraceRecorder.enabled = false
        assertTrue(record().isEmpty())
    }

    @Test
    fun `开启时抬手即记录并返回轨迹id`() {
        GlideTraceRecorder.enabled = true
        val id = record()
        assertTrue("应返回轨迹 id", id.isNotEmpty())
        assertEquals(1, GlideTraceRecorder.recentEntries().size)
        assertEquals("nihao", GlideTraceRecorder.recentEntries().first().code)
        assertEquals("nihao", GlideTraceRecorder.recentEntries().first().topCandidate)
    }

    @Test
    fun `单点轨迹不记录`() {
        GlideTraceRecorder.enabled = true
        val id = GlideTraceRecorder.record(
            points = listOf(GlidePoint(1f, 1f, 0L, 1)),
            geometry = geometry,
            schemaFingerprint = "s",
            prefix = "",
            code = "a",
            candidates = listOf("a"),
        )
        assertTrue(id.isEmpty())
        assertTrue(GlideTraceRecorder.recentEntries().isEmpty())
    }

    @Test
    fun `手动标记写入并覆盖自动标注`() {
        GlideTraceRecorder.enabled = true
        val id = record()
        GlideTraceRecorder.labelConfirmed(id, "你好", "nihao")
        assertEquals("你好", GlideTraceRecorder.recentEntries().first().expected)
        GlideTraceRecorder.label(id, "你号", "mark")
        assertEquals("你号", GlideTraceRecorder.recentEntries().first().expected)
        assertEquals(0, GlideTraceRecorder.unlabeledCount())
    }

    @Test
    fun `编码对不上时自动标注跳过`() {
        GlideTraceRecorder.enabled = true
        val id = record(code = "nihao")
        GlideTraceRecorder.labelConfirmed(id, "你好", "buPipei")
        assertTrue(GlideTraceRecorder.recentEntries().first().expected.isEmpty())
    }

    @Test
    fun `冷启动回填最近记录并接续id序号`() {
        val dir = java.nio.file.Files.createTempDirectory("glide").toFile()
        try {
            val file = java.io.File(dir, GlideTraceRecorder.FILE_NAME)
            file.writeText(
                listOf(
                    "{\"v\":2,\"e\":\"trace\",\"id\":\"g1-1\",\"t\":1000,\"code\":\"a\",\"expected\":\"\",\"candidates\":[\"a\"]}",
                    "{\"v\":2,\"e\":\"label\",\"id\":\"g1-1\",\"expected\":\"啊\",\"src\":\"mark\"}",
                    "{\"v\":2,\"e\":\"trace\",\"id\":\"g1-2\",\"t\":2000,\"code\":\"b\",\"expected\":\"\",\"candidates\":[\"b\"]}",
                ).joinToString("\n") + "\n",
                Charsets.UTF_8,
            )
            val entries = GlideTraceRecorder.readRecentForTest(file)
            assertEquals(2, entries.size)
            // 时间新的在前
            assertEquals("g1-2", entries.first().id)
            // label 行回填到对应轨迹
            assertEquals("啊", entries.last().expected)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `轨迹id互不相同`() {
        GlideTraceRecorder.enabled = true
        val a = record()
        val b = record()
        assertTrue(a.isNotEmpty() && b.isNotEmpty())
        assertTrue("多次记录 id 不能重复", a != b)
    }

    @Test
    fun `最近列表有上限且新记录在前`() {
        GlideTraceRecorder.enabled = true
        repeat(25) { record() }
        val entries = GlideTraceRecorder.recentEntries()
        assertEquals(20, entries.size)
        // 最新一条的 id 序号最大（字符串后缀按数字递增）
        val last = entries.first().id.substringAfterLast('-').toInt()
        assertTrue(last >= 20)
    }
}
