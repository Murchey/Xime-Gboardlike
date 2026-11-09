package com.kingzcheung.xime.settings

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * 分号韵母键判定。
 *
 * 关键回归点：**万象拼音这类方案包**把双拼规则放在独立的 algebra 文件里，
 * 通过 custom.yaml 的 __patch 注入，原 .schema.yaml 里查不到——
 * 只读 schema 文件会漏判，这些方案包里「微软双拼」的 ing 依旧打不出来。
 */
class SchemaSemicolonKeyTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun readSchema(name: String): String? {
        val candidates = listOf(
            File("src/main/assets/rime/$name.schema.yaml"),
            File("app/src/main/assets/rime/$name.schema.yaml"),
        )
        return candidates.firstOrNull { it.isFile }?.readText()
    }

    private fun assertSchemaFile(name: String, expected: Boolean) {
        val text = readSchema(name)
        assertTrue("找不到方案文件 $name.schema.yaml（测试工作目录应在 app/ 下）", text != null)
        assertTrue(
            "$name 的分号判定应为 $expected",
            SchemaSemicolonKey.parse(text!!) == expected,
        )
    }

    // ── 仓库内置方案（雾凇系写法 xform/ing$/;/ ）──────────

    @Test
    fun `double_pinyin_mspy 的分号判定为 true`() = assertSchemaFile("double_pinyin_mspy", true)

    @Test
    fun `double_pinyin_sogou 的分号判定为 true`() = assertSchemaFile("double_pinyin_sogou", true)

    @Test
    fun `double_pinyin_ziguang 的分号判定为 true`() = assertSchemaFile("double_pinyin_ziguang", true)

    @Test
    fun `double_pinyin_flypy 的分号判定为 false`() = assertSchemaFile("double_pinyin_flypy", false)

    @Test
    fun `double_pinyin_abc 的分号判定为 false`() = assertSchemaFile("double_pinyin_abc", false)

    @Test
    fun `double_pinyin 的分号判定为 false`() = assertSchemaFile("double_pinyin", false)

    @Test
    fun `double_pinyin_jiajia 的分号判定为 false`() = assertSchemaFile("double_pinyin_jiajia", false)

    @Test
    fun `rime_ice 的分号判定为 false`() = assertSchemaFile("rime_ice", false)

    // ── 万象系写法：xform/ing(\d)$/;$1 ─────────────────

    @Test
    fun `带韵律标记的分号规则也能识别`() {
        assertTrue(SchemaSemicolonKey.parse(SAMPLE_WANXIANG_ALGEBRA))
    }

    @Test
    fun `雾凇系裸分号规则也能识别`() {
        assertTrue(SchemaSemicolonKey.parse(SAMPLE_FROST_ALGEBRA))
    }

    @Test
    fun `librime 合并后的规则列表同样判定`() {
        val merged = SAMPLE_WANXIANG_ALGEBRA.lines()
            .map { it.trim().removePrefix("- ").trim() }
            .filter { it.startsWith("xform/") }
        assertTrue(SchemaSemicolonKey.anyRuleHasSemicolonFinal(merged))
        assertFalse(
            SchemaSemicolonKey.anyRuleHasSemicolonFinal(
                listOf("xform/ing(\\d)\$/Ⓨ\$1/", "xform/(\\w);/\$1ing/")
            )
        )
    }

    // ── 边界 ────────────────────────────────────────────

    @Test
    fun `分号在源侧的反向规则不算韵母键`() {
        assertFalse(SchemaSemicolonKey.parse(SAMPLE_FLYPY_ALGEBRA))
    }

    @Test
    fun `注释里的规则不生效`() {
        assertFalse(SchemaSemicolonKey.parse("speller:\n  algebra:\n    # - xform/ing\$/;\n    - xform/ing\$/y\n"))
    }

    @Test
    fun `没有前缀横线的规则也算`() {
        // rime 的规则总是以 / 收尾：xform/<源>/<替换>/
        assertTrue(SchemaSemicolonKey.parse("xform/ing\$/;/\n"))
    }

    @Test
    fun `空方案不崩`() {
        assertFalse(SchemaSemicolonKey.parse(""))
        assertFalse(SchemaSemicolonKey.parse("speller:\n  alphabet: abc\n"))
        assertFalse(SchemaSemicolonKey.anyRuleHasSemicolonFinal(emptyList()))
    }

    // ── 跨文件：模拟万象拼音的注入方式 ─────────────────

    @Test
    fun `规则在被 patch 引入的独立 algebra 文件里也能发现`() {
        val rimeDir = tempFolder.newFolder("rime")
        // 主方案自身没有双拼规则，真正的规则在 custom 的 __patch 指向的文件里
        File(rimeDir, "wanxiang.schema.yaml").writeText(
            "schema:\n  schema_id: wanxiang\nspeller:\n  alphabet: abc;/\n"
        )
        File(rimeDir, "wanxiang.custom.yaml").writeText(
            "patch:\n  speller/algebra:\n    __patch:\n      - wanxiang_algebra:/base/微软双拼\n"
        )
        File(rimeDir, "wanxiang_algebra.yaml").writeText(SAMPLE_WANXIANG_ALGEBRA)

        assertTrue(SchemaSemicolonKey.forSchemaDir(rimeDir, "wanxiang"))
    }

    @Test
    fun `未被引用的文件不参与判定`() {
        val rimeDir = tempFolder.newFolder("rime2")
        File(rimeDir, "wanxiang.schema.yaml").writeText(
            "schema:\n  schema_id: wanxiang\nspeller:\n  alphabet: abc\n"
        )
        // 目录里存在含分号规则的文件，但主方案没引用它
        File(rimeDir, "unused_algebra.yaml").writeText(SAMPLE_WANXIANG_ALGEBRA)

        assertFalse(SchemaSemicolonKey.forSchemaDir(rimeDir, "wanxiang"))
    }

    private companion object {
        /** 取自万象拼音 wanxiang_algebra.yaml 的「微软双拼」段（节选）。 */
        val SAMPLE_WANXIANG_ALGEBRA = """
          微软双拼:
            __append:
              - xform/ian(\\d)$/Ⓜ$1/
              - xform/in(\\d)$/Ⓝ$1/
              - xform/ing(\\d)$/;$1/
              - xlit/ⓆⓌⓇ/qwr/
        """.trimIndent()

        /** 雾凇系双拼：替换侧就是裸分号。 */
        val SAMPLE_FROST_ALGEBRA = """
        speller:
          algebra:
            - xform/in$/Ⓝ/
            - xform/ing$/;/
        """.trimIndent()

        /** 小鹤双拼：ing 映射到 k，不应命中。 */
        val SAMPLE_FLYPY_ALGEBRA = """
        speller:
          algebra:
            - xform/(\\w)k/$1ing/
        """.trimIndent()
    }
}
