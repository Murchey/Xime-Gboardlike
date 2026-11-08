package com.kingzcheung.xime.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * 方案包健康自检：解析不出 schema_id 的 .schema.yaml 会导致
 * 「导入后方案列表有名字、但打字没候选」，必须在部署前暴露出来。
 */
class BrokenSchemaDetectionTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `正常 schema 不算损坏`() {
        val dir = temp.newFolder("ok")
        File(dir, "demo.schema.yaml").writeText(
            """
            schema:
              schema_id: demo
              name: 演示方案
              version: "1.0"
            """.trimIndent()
        )
        assertTrue(SchemaManager.findBrokenSchemaFiles(dir).isEmpty())
    }

    @Test
    fun `缺少 schema 块的文件被识别为损坏`() {
        val dir = temp.newFolder("noschema")
        // 只有 speller 没有 schema 块：导入后无法注册成方案
        File(dir, "bad.schema.yaml").writeText("speller:\n  alphabet: abc\n")
        assertEquals(listOf("bad.schema.yaml"), SchemaManager.findBrokenSchemaFiles(dir))
    }

    @Test
    fun `YAML 语法错误的文件被识别为损坏`() {
        val dir = temp.newFolder("broken")
        File(dir, "boom.schema.yaml").writeText("schema:\n  schema_id: [unclosed\n")
        assertEquals(listOf("boom.schema.yaml"), SchemaManager.findBrokenSchemaFiles(dir))
    }

    @Test
    fun `带 BOM 的正常文件不算损坏`() {
        val dir = temp.newFolder("bom")
        val body = "schema:\n  schema_id: bomdemo\n  name: BOM\n"
        File(dir, "bom.schema.yaml").writeBytes(
            byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + body.toByteArray()
        )
        // 部署前会清 BOM，这里按修复后语义判断，不应报损坏
        assertTrue(SchemaManager.findBrokenSchemaFiles(dir).isEmpty())
    }

    @Test
    fun `目录不存在时返回空`() {
        assertTrue(SchemaManager.findBrokenSchemaFiles(File(temp.root, "missing")).isEmpty())
    }
}
