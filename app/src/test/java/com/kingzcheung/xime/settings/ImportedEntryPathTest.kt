package com.kingzcheung.xime.settings

import com.kingzcheung.xime.rime.RimeConfigHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 归档导入路径归一化：万象把补丁放在 custom/ 子目录，Rime 只认根目录。 */
class ImportedEntryPathTest {

    @Test
    fun customPatchIsLiftedToRimeRoot() {
        assertEquals("wanxiang.custom.yaml", SchemaManager.normalizeImportedEntry("custom/wanxiang.custom.yaml"))
        assertEquals("wanxiang_t9.custom.yaml", SchemaManager.normalizeImportedEntry("custom/wanxiang_t9.custom.yaml"))
    }

    @Test
    fun nestedOrNonPatchFilesStayPut() {
        assertEquals("custom/sub/x.custom.yaml", SchemaManager.normalizeImportedEntry("custom/sub/x.custom.yaml"))
        assertEquals("custom/readme.txt", SchemaManager.normalizeImportedEntry("custom/readme.txt"))
        assertEquals("wanxiang.schema.yaml", SchemaManager.normalizeImportedEntry("wanxiang.schema.yaml"))
    }

    @Test
    fun leadingDotSlashAndBackslashesAreNormalized() {
        // 去掉 ./ 之后仍是 custom/ 根级补丁 → 照常提升到根目录
        assertEquals("a.custom.yaml", SchemaManager.normalizeImportedEntry("./custom/a.custom.yaml"))
        assertEquals("dicts/base.dict.yaml", SchemaManager.normalizeImportedEntry("dicts\\base.dict.yaml"))
    }
}

/** 清理"下架内置方案残留"时，绝不能再误删用户装的第三方方案。 */
class ObsoleteBuiltinCleanupTest {

    private val assets = setOf(
        "rime_ice.schema.yaml",
        "rime_ice.dict.yaml",
        "t9.schema.yaml",
        "t9_pinyin.schema.yaml",
        "wubi86.schema.yaml",
    )
    private val noDisabled = emptySet<String>()

    @Test
    fun thirdPartySchemaIsNeverRemoved() {
        // 万象、雾凇这类文件在 packageFor 里落到 SHARED 兜底，早期版本因此被当成内置残留删掉
        assertFalse(RimeConfigHelper.shouldRemoveObsoleteBuiltinFile("wanxiang.schema.yaml", assets, noDisabled))
        assertFalse(RimeConfigHelper.shouldRemoveObsoleteBuiltinFile("wanxiang.dict.yaml", assets, noDisabled))
        assertFalse(RimeConfigHelper.shouldRemoveObsoleteBuiltinFile("rime_frost.schema.yaml", assets, noDisabled))
    }

    @Test
    fun obsoleteFileOfShippedPackageIsRemoved() {
        // 内置包还在（t9 相关文件仍随 app 发布），但 pinyin_simp 已下架 → 可以清理
        assertTrue(RimeConfigHelper.shouldRemoveObsoleteBuiltinFile("pinyin_simp.schema.yaml", assets, noDisabled))
        assertTrue(RimeConfigHelper.shouldRemoveObsoleteBuiltinFile("pinyin_simp.dict.yaml", assets, noDisabled))
    }

    @Test
    fun shippedFilesAreKept() {
        assertFalse(RimeConfigHelper.shouldRemoveObsoleteBuiltinFile("rime_ice.schema.yaml", assets, noDisabled))
        assertFalse(RimeConfigHelper.shouldRemoveObsoleteBuiltinFile("t9.schema.yaml", assets, noDisabled))
    }

    @Test
    fun disabledPackagesAreKept() {
        assertFalse(
            RimeConfigHelper.shouldRemoveObsoleteBuiltinFile(
                "pinyin_simp.schema.yaml",
                assets,
                setOf(BuiltinPackages.PINYIN_SIMP),
            )
        )
    }

    @Test
    fun emptyAssetListIsNoOp() {
        assertFalse(RimeConfigHelper.shouldRemoveObsoleteBuiltinFile("pinyin_simp.schema.yaml", emptySet(), noDisabled))
    }

    /** schema_list 全被剪空（比如用户方案文件都没了）时要补回内置方案，否则键盘退化成英文。 */
    @Test
    fun schemaListIsRefilledWhenPrunedEmpty() {
        val kept = listOf("patch:", "  schema_list:")
        val out = RimeConfigHelper.refillSchemaListLines(kept, "\n", listOf("rime_ice", "t9_pinyin"))
        assertEquals(3, out.size)
        assertEquals("patch:", out[0])
        assertTrue(out.last().contains("- schema: rime_ice"))
        assertTrue(out.last().contains("- schema: t9_pinyin"))
    }

    @Test
    fun schemaListWithEntriesIsLeftAlone() {
        val kept = listOf("  schema_list:", "    - schema: wanxiang", "  menu:", "    page_size: 50")
        assertEquals(kept, RimeConfigHelper.refillSchemaListLines(kept, "\n", listOf("rime_ice")))
    }

    @Test
    fun noFallbackMeansNoChange() {
        val kept = listOf("  schema_list:")
        assertEquals(kept, RimeConfigHelper.refillSchemaListLines(kept, "\n", emptyList()))
    }

    /** 导入多方案包时要把新方案全部并入启用列表，而不是只留第一个。 */
    @Test
    fun importedSchemasAreMergedIntoEnabledList() {
        val merged = SchemaManager.mergeEnabledSchemas(
            listOf("rime_ice", "wubi86"),
            listOf("wanxiang", "wanxiang_t9", "rime_ice"),
        )
        assertEquals(listOf("rime_ice", "wubi86", "wanxiang", "wanxiang_t9"), merged)
    }

    @Test
    fun mergeIsANoOpWithoutImports() {
        val current = listOf("rime_ice")
        assertEquals(current, SchemaManager.mergeEnabledSchemas(current, emptyList()))
    }
}
