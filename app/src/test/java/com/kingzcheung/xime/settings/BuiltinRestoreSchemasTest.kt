package com.kingzcheung.xime.settings

import org.junit.Assert.assertEquals
import org.junit.Test

/** 一键恢复内置部署：只启用真实存在的内置方案，第三方方案不再自动启用。 */
class BuiltinRestoreSchemasTest {

    @Test
    fun keepsOnlyExistingBuiltinSchemasInBuiltinOrder() {
        val available = setOf("t9_pinyin", "rime_ice", "my_third_party")
        assertEquals(
            listOf("rime_ice", "t9_pinyin"),
            SchemaManager.builtinSchemasForRestore(available),
        )
    }

    @Test
    fun thirdPartyNeverEnabledByRestore() {
        assertEquals(
            emptyList<String>(),
            SchemaManager.builtinSchemasForRestore(setOf("my_third_party", "another")),
        )
    }

    @Test
    fun missingBuiltinFilesAreSkipped() {
        // 只剩五笔：不应把其它内置方案写回启用列表（文件不存在会被过滤）
        assertEquals(
            listOf("wubi86"),
            SchemaManager.builtinSchemasForRestore(setOf("wubi86")),
        )
    }
}
