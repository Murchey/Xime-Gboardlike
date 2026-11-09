package com.kingzcheung.xime.settings

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 分号韵母键判定。
 *
 * 用仓库内真实的方案文件做断言：微软/搜狗/紫光双拼确实把 `ing` 映射到 `;`，
 * 其余方案（小鹤用 k、智能ABC/自然码用 y、加加用 q）不该被误判——否则它们的
 * Shift 键会无端变成分号，反而打不出分词符。
 */
class SchemaSemicolonKeyTest {

    private fun readSchema(name: String): String? {
        val candidates = listOf(
            File("src/main/assets/rime/$name.schema.yaml"),
            File("app/src/main/assets/rime/$name.schema.yaml"),
        )
        return candidates.firstOrNull { it.isFile }?.readText()
    }

    private fun assertSchema(name: String, expected: Boolean) {
        val text = readSchema(name)
        // 真实方案文件必须读到：静默跳过会让"判定逻辑写错"也显示为通过
        assertTrue("找不到方案文件 $name.schema.yaml（测试工作目录应在 app/ 下）", text != null)
        assertTrue(
            "$name 的分号判定应为 $expected",
            SchemaSemicolonKey.parse(text!!) == expected,
        )
    }

    @Test
    fun `微软双拼用分号表示 ing`() = assertSchema("double_pinyin_mspy", true)

    @Test
    fun `搜狗双拼用分号表示 ing`() = assertSchema("double_pinyin_sogou", true)

    @Test
    fun `紫光双拼用分号表示 ing`() = assertSchema("double_pinyin_ziguang", true)

    @Test
    fun `小鹤双拼不用分号`() = assertSchema("double_pinyin_flypy", false)

    @Test
    fun `智能ABC双拼不用分号`() = assertSchema("double_pinyin_abc", false)

    @Test
    fun `自然码双拼不用分号`() = assertSchema("double_pinyin", false)

    @Test
    fun `拼音加加不用分号`() = assertSchema("double_pinyin_jiajia", false)

    @Test
    fun `全拼方案不用分号`() = assertSchema("rime_ice", false)

    @Test
    fun `分号在源侧的反向规则不算韵母键`() {
        // 反向规则（; → ing）说明分号是输入用的键，不是韵母键的产出
        val text = "speller:\n  algebra:\n    - xform/(\\w);/\$1ing/\n"
        assertFalse(SchemaSemicolonKey.parse(text))
    }

    @Test
    fun `分号在替换侧才判定为韵母键`() {
        val text = "speller:\n  algebra:\n    - xform/ing\$/;\n"
        assertTrue(SchemaSemicolonKey.parse(text))
    }

    @Test
    fun `注释里的规则不生效`() {
        val text = "speller:\n  algebra:\n    # - xform/ing\$/;\n    - xform/ing\$/y\n"
        assertFalse(SchemaSemicolonKey.parse(text))
    }

    @Test
    fun `空方案不崩`() {
        assertFalse(SchemaSemicolonKey.parse(""))
        assertFalse(SchemaSemicolonKey.parse("speller:\n  alphabet: abc\n"))
    }
}
