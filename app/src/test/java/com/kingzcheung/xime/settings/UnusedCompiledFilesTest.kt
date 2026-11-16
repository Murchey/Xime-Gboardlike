package com.kingzcheung.xime.settings

import org.junit.Assert.assertEquals
import org.junit.Test

/** 清除未使用的编译产物：只删停用方案的 prism/table，保留在用方案与共享词典。 */
class UnusedCompiledFilesTest {

    private val files = listOf(
        "rime_ice.prism.bin",
        "rime_ice.reverse.bin",
        "pinyin_simp.table.bin",
        "old_schema.prism.bin",
        "old_schema.table.bin",
        "default.yaml",
        "old_schema.schema.yaml",
    )

    @Test
    fun keepsEnabledAndSharedDictionaries() {
        val doomed = SchemaManager.unusedCompiledFiles(
            files = files,
            enabledSchemas = setOf("rime_ice", "t9_pinyin"),
            schemaIds = setOf("rime_ice", "old_schema", "t9_pinyin"),
            protectedDictionaries = setOf("pinyin_simp"),
        )
        assertEquals(listOf("old_schema.prism.bin", "old_schema.table.bin"), doomed)
    }

    @Test
    fun keepsCompiledOutputsOfUnknownNames() {
        // default.yaml 不是方案 id 命名的产物，不能删
        val doomed = SchemaManager.unusedCompiledFiles(
            files = listOf("default.yaml", "shared.prism.bin"),
            enabledSchemas = emptySet(),
            schemaIds = setOf("rime_ice"),
            protectedDictionaries = emptySet(),
        )
        assertEquals(emptyList<String>(), doomed)
    }
}