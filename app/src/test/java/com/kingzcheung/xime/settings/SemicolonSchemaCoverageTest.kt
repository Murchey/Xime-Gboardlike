package com.kingzcheung.xime.settings

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 分号韵母键的探测范围。
 *
 * 这里锁的是"哪些方案需要分号键"，而不是渲染时机（渲染在 KeyboardLayout 里，
 * 由 LocalSemicolonFinal 决定，已改为不依赖组合态）。
 *
 * 用真实方案文件跑，避免只覆盖到内置三套而漏掉方案包里的同族方案。
 */
class SemicolonSchemaCoverageTest {

    private fun readSchema(name: String): String? =
        listOf("src/main/assets/rime/$name.schema.yaml", "app/src/main/assets/rime/$name.schema.yaml")
            .map { File(it) }
            .firstOrNull { it.isFile }
            ?.readText()

    @Test
    fun `内置三套分号双拼都被识别`() {
        for (name in listOf("double_pinyin_mspy", "double_pinyin_sogou", "double_pinyin_ziguang")) {
            val text = readSchema(name)
            assertTrue("缺少方案文件 $name", text != null)
            assertTrue("$name 应判定为分号韵母方案", SchemaSemicolonKey.parse(text!!))
        }
    }

    @Test
    fun `这三套方案的 alphabet 确实包含分号`() {
        // 交叉验证：检测结果要与方案自身的 speller/alphabet 对得上
        for (name in listOf("double_pinyin_mspy", "double_pinyin_sogou", "double_pinyin_ziguang")) {
            val text = readSchema(name) ?: continue
            val alphabetLine = text.lineSequence().firstOrNull { it.trimStart().startsWith("alphabet:") }
            assertTrue("$name 的 alphabet 应含分号", alphabetLine?.contains(';') == true)
        }
    }

    @Test
    fun `不用分号的方案不会被误判`() {
        for (name in listOf(
            "double_pinyin_flypy",
            "double_pinyin_abc",
            "double_pinyin",
            "double_pinyin_jiajia",
            "rime_ice",
            "pinyin_simp",
            "wubi86",
        )) {
            val text = readSchema(name) ?: continue
            assertFalse("$name 不应判定为分号韵母方案", SchemaSemicolonKey.parse(text))
        }
    }

    @Test
    fun `部件拆字的 ing 分号同样被识别`() {
        // radical_pinyin 是反查方案，同样用 ing -> ; 的双拼规则
        val text = readSchema("radical_pinyin") ?: return
        assertTrue(SchemaSemicolonKey.parse(text))
    }
}
