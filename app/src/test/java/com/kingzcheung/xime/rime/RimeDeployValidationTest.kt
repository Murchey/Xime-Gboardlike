package com.kingzcheung.xime.rime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * 导入方案不可用的两个常见原因：
 * 1) 外部编辑器给 YAML 写了 UTF-8 BOM —— librime 不跳过 BOM，整份配置解析失败；
 * 2) 部署「成功」但方案没有编译产物 —— 只看部署 hash 会把未编译当成已部署永久跳过。
 */
class RimeDeployValidationTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun bomBytes(body: ByteArray): ByteArray =
        byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + body

    @Test
    fun `去掉 YAML 文件头的 BOM`() {
        val dir = temp.newFolder("rime")
        val withBom = File(dir, "demo.schema.yaml")
        withBom.writeBytes(bomBytes("schema:\n  schema_id: demo\n".toByteArray()))

        val fixed = RimeConfigHelper.stripBomFromUserYaml(dir)

        assertEquals(1, fixed)
        val text = withBom.readText()
        assertTrue("BOM 应已被清除", !text.startsWith("\uFEFF"))
        assertTrue("正文应完整保留", text.startsWith("schema:"))
    }

    @Test
    fun `无 BOM 的文件不被改写`() {
        val dir = temp.newFolder("rime2")
        val plain = File(dir, "plain.yaml")
        val original = "patch:\n  foo: bar\n".toByteArray()
        plain.writeBytes(original)

        assertEquals(0, RimeConfigHelper.stripBomFromUserYaml(dir))
        assertTrue(original.contentEquals(plain.readBytes()))
    }

    @Test
    fun `词典与超大文件跳过 BOM 扫描`() {
        val dir = temp.newFolder("rime3")
        val dict = File(dir, "big.dict.yaml")
        dict.writeBytes(bomBytes("name: big\n".toByteArray()))

        assertEquals(0, RimeConfigHelper.stripBomFromUserYaml(dir))
        // 词典原样保留（不会被改写）
        assertTrue(dict.readBytes()[0] == 0xEF.toByte())
    }

    @Test
    fun `缺少编译产物的方案能被识别`() {
        val buildDir = temp.newFolder("build")
        File(buildDir, "ready.prism.bin").writeText("x")
        File(buildDir, "ready2.schema.yaml").writeText("x")

        val missing = RimeConfigHelper.missingCompiledSchemas(
            enabled = listOf("ready", "ready2", "broken"),
            buildDir = buildDir,
        )

        assertEquals(listOf("broken"), missing)
    }

    @Test
    fun `产物齐全时没有缺失项`() {
        val buildDir = temp.newFolder("build2")
        File(buildDir, "a.prism.bin").writeText("x")
        File(buildDir, "b.prism.bin").writeText("x")

        assertTrue(
            RimeConfigHelper.missingCompiledSchemas(listOf("a", "b"), buildDir).isEmpty()
        )
    }
}
