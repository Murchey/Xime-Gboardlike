package com.kingzcheung.xime.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** kaml 解析失败时的方案名兜底。 */
class SchemaDisplayNameFallbackTest {

    @Test
    fun `抠出 schema 块里的 name`() {
        val text = """
            # Rime schema
            schema:
              schema_id: pinyin_14jian
              name: 拼音14键
              version: "1.0"
            switches:
              - name: ascii_mode
        """.trimIndent()
        assertEquals("拼音14键", SchemaManager.nameFromText(text))
    }

    @Test
    fun `带引号的名字`() {
        val text = "schema:\n  schema_id: x\n  name: \"测试 · 14 键\"\n"
        assertEquals("测试 · 14 键", SchemaManager.nameFromText(text))
    }

    @Test
    fun `没有 schema 块或没有 name 时返回 null`() {
        assertNull(SchemaManager.nameFromText("switches:\n  - name: ascii_mode\n"))
        assertNull(SchemaManager.nameFromText("schema:\n  schema_id: x\n"))
        assertNull(SchemaManager.nameFromText(""))
    }
}