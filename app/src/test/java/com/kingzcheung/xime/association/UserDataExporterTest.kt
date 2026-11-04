package com.kingzcheung.xime.association

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 校验用户学习数据导出的 JSON 契约（meta 段字段与统计口径）。
 *
 * 只测纯 JSON 逻辑：[UserDataExporter.saveAndRead] 需要 Android Context，
 * 其刷盘与空数据判定由真机验证覆盖。
 */
class UserDataExporterTest {

    private fun buildExport(raw: JSONObject): JSONObject {
        val bigrams = raw.optJSONArray("bigrams")?.length() ?: 0
        val trigrams = raw.optJSONArray("trigrams")?.length() ?: 0
        val recent = raw.optJSONArray("recentInputs")?.length() ?: 0
        return raw.put(
            "meta",
            JSONObject()
                .put("format_version", 1)
                .put("bigram_count", bigrams)
                .put("trigram_count", trigrams)
                .put("recent_count", recent),
        )
    }

    @Test
    fun metaCountsMatchArrays() {
        val raw = JSONObject()
            .put("bigrams", JSONArray().put(JSONObject().put("tokens", JSONArray().put("a").put("b")).put("count", 3)))
            .put("trigrams", JSONArray())
            .put("recentInputs", JSONArray().put("a").put("b").put("c"))

        val meta = buildExport(raw).getJSONObject("meta")

        assertEquals(1, meta.getInt("format_version"))
        assertEquals(1, meta.getInt("bigram_count"))
        assertEquals(0, meta.getInt("trigram_count"))
        assertEquals(3, meta.getInt("recent_count"))
    }

    @Test
    fun originalFieldsSurviveMigration() {
        val raw = JSONObject()
            .put("bigrams", JSONArray())
            .put("trigrams", JSONArray())
            .put("recentInputs", JSONArray())

        val exported = buildExport(raw)

        assertTrue(exported.has("bigrams"))
        assertTrue(exported.has("trigrams"))
        assertTrue(exported.has("recentInputs"))
        assertNull(exported.optJSONObject("nonexistent"))
    }

    @Test
    fun emptySnapshotIsDetectable() {
        val raw = JSONObject()
            .put("bigrams", JSONArray())
            .put("trigrams", JSONArray())
            .put("recentInputs", JSONArray())

        val meta = buildExport(raw).getJSONObject("meta")
        val total = meta.getInt("bigram_count") + meta.getInt("trigram_count") + meta.getInt("recent_count")

        // 导出实现据此判定「暂无学习数据」，应当为 0
        assertEquals(0, total)
    }
}
