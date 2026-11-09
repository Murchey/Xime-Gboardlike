package com.kingzcheung.xime.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 音形变体兼容性测试。
 *
 * 用户要求：微软/搜狗/紫光双拼的所有基于 RIME 的变体（含音形/辅码）都要兼容。
 * 判定依据是 speller/algebra 里的规则（替换侧以分号开头），不看方案名。
 */
class SemicolonYinxingVariantTest {

    @Test
    fun `微软双拼音形变体被识别`() {
        // 典型音形方案：在基础双拼规则上叠加声调/字形标记
        val text = """
            speller:
              alphabet: abcdefghijklmnopqrstuvwxyz;
              algebra:
                - xform/iu$/ⓚ/
                - xform/ing$$/;$1/
                - xlit/ⓚⓜ/qw/
        """.trimIndent()
        assertTrue(SchemaSemicolonKey.parse(text))
    }

    @Test
    fun `微软双拼拆分变体被识别`() {
        // 拆分方案：规则形式可能带附加捕获组
        val text = """
            speller:
              algebra:
                - xform/(.)ing$/$1;/
        """.trimIndent()
        assertTrue(SchemaSemicolonKey.parse(text))
    }

    @Test
    fun `搜狗双拼辅码变体被识别`() {
        val text = """
            speller:
              algebra:
                - xform/ing$/;/
                - derive/^(..)(\d)$/$1/
        """.trimIndent()
        assertTrue(SchemaSemicolonKey.parse(text))
    }

    @Test
    fun `紫光双拼音形变体被识别`() {
        // 紫光的 ing 规则：xform/ing$/;/ 和 xform/(.)ing$/$1;/ 两种形态
        val text = """
            speller:
              algebra:
                - xform/ing$/;/
                - xform/(.)i?ong$/$1ⓢ/
        """.trimIndent()
        assertTrue(SchemaSemicolonKey.parse(text))
    }

    @Test
    fun `音形方案中非分号韵母不误判`() {
        // 小鹤的 ing 映射到 k，不应判为分号方案
        val text = """
            speller:
              algebra:
                - xform/(\w)k/$1ing/
                - xform/ing$/ⓐ/
        """.trimIndent()
        assertFalse(SchemaSemicolonKey.parse(text))
    }

    @Test
    fun `librime 合并后含分号规则的变体被识别`() {
        // 模拟 getSchemaList 返回的合并规则（含 patch 注入的）
        val mergedRules = listOf(
            "xform/iu$/ⓚ/",
            "xform/ing$/;/",
            "xform/(.)i?ong$/$1ⓢ/",
        )
        assertTrue(SchemaSemicolonKey.anyRuleHasSemicolonFinal(mergedRules))
    }

    @Test
    fun `自定义方案用同一规则也自动生效`() {
        // 用户自定义方案只要写了 xform/ing$/;/ 就自动获得分号键
        val text = """
            speller:
              alphabet: abcdefghijklmnopqrstuvwxyz;
              algebra:
                - xform/ing$/;/
        """.trimIndent()
        assertTrue(SchemaSemicolonKey.parse(text))
    }
}
