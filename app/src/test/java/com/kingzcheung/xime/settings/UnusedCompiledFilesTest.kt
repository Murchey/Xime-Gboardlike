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

    @Test
    fun removesDictionaryTableWhenNoEnabledSchemaMentionsIt() {
        // t9 停用后，pinyin_simp 没有 schema 文件，旧规则永远删不掉它的 table
        val doomed = SchemaManager.unusedDictionaryArtifacts(
            files = listOf("pinyin_simp.table.bin", "pinyin_simp.reverse.bin", "rime_ice.table.bin"),
            dictionaryIds = setOf("pinyin_simp", "rime_ice"),
            enabledSchemas = setOf("rime_ice"),
            enabledSchemaText = "dictionary: rime_ice",
        )
        assertEquals(listOf("pinyin_simp.table.bin", "pinyin_simp.reverse.bin"), doomed)
    }

    @Test
    fun keepsDictionaryTableMentionedByEnabledSchema() {
        val files = listOf("pinyin_simp.table.bin", "melt_eng.table.bin")
        val doomed = SchemaManager.unusedDictionaryArtifacts(
            files = files,
            dictionaryIds = setOf("pinyin_simp", "melt_eng"),
            enabledSchemas = setOf("t9_pinyin", "rime_ice"),
            enabledSchemaText = "dictionary: pinyin_simp\nmelt_eng:\n  dictionary: melt_eng",
        )
        assertEquals(emptyList<String>(), doomed)
    }
}