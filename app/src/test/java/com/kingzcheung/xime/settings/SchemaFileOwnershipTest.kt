package com.kingzcheung.xime.settings

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 方案包共享文件的归属判定。
 *
 * 这组用例直接用真实方案包的形态造数据：万象的 7 个辅助包都携带
 * wanxiang_abbrev.schema.yaml 与内容不同的 chaifen.txt；雾凇包与内置
 * 共享 melt_eng / radical_pinyin。历史实现这些都会被判成阻断冲突，
 * 或者覆盖后把旧包的认领整条删掉。
 */
class SchemaFileOwnershipTest {

    private fun registryOf(vararg entries: Triple<String, String, List<String>>): JSONObject =
        JSONObject().apply {
            for ((name, sha, owners) in entries) {
                put(name, JSONObject().apply {
                    put("sha256", sha)
                    put("size", 1)
                    put("claimedBy", JSONArray(owners))
                })
            }
        }

    @Test
    fun `内容相同视为共享不阻断`() {
        val registry = registryOf(
            Triple("wanxiang_abbrev.schema.yaml", "aaa", listOf("wanxiang-base"))
        )
        val analysis = SchemaFileOwnership.analyze(
            targetFiles = listOf("wanxiang_abbrev.schema.yaml"),
            newHashes = mapOf("wanxiang_abbrev.schema.yaml" to "aaa"),
            registryFiles = registry,
            packageId = "wanxiang-flypy",
        )
        assertEquals(listOf("wanxiang_abbrev.schema.yaml"), analysis.identical)
        assertFalse(analysis.hasBlocking)
        assertTrue(analysis.canInstallDirectly)
    }

    @Test
    fun `内容不同的公共配置不算阻断`() {
        val registry = registryOf(
            Triple("chaifen.txt", "old", listOf("wanxiang-base")),
            Triple("default.yaml", "old2", listOf("wanxiang-base")),
        )
        val analysis = SchemaFileOwnership.analyze(
            targetFiles = listOf("chaifen.txt", "default.yaml"),
            newHashes = mapOf("chaifen.txt" to "new", "default.yaml" to "new2"),
            registryFiles = registry,
            packageId = "wanxiang-flypy",
        )
        assertTrue(analysis.blocking.isEmpty())
        assertTrue(analysis.overridable.contains("chaifen.txt"))
        assertTrue(analysis.overridable.contains("default.yaml"))
        assertTrue(analysis.canInstallDirectly)
    }

    @Test
    fun `方案定义与词库内容不同要用户确认`() {
        val registry = registryOf(
            Triple("rime_ice.schema.yaml", "old", listOf("builtin")),
            Triple("base.dict.yaml", "old2", listOf("builtin")),
        )
        val analysis = SchemaFileOwnership.analyze(
            targetFiles = listOf("rime_ice.schema.yaml", "base.dict.yaml"),
            newHashes = mapOf("rime_ice.schema.yaml" to "new", "base.dict.yaml" to "new2"),
            registryFiles = registry,
            packageId = "frost-schemas",
        )
        assertEquals(2, analysis.blocking.size)
        assertTrue(analysis.hasBlocking)
        assertFalse(analysis.canInstallDirectly)
    }

    @Test
    fun `同包重装不算冲突`() {
        val registry = registryOf(
            Triple("chaifen.txt", "old", listOf("wanxiang-base")),
        )
        val analysis = SchemaFileOwnership.analyze(
            targetFiles = listOf("chaifen.txt"),
            newHashes = mapOf("chaifen.txt" to "new"),
            registryFiles = registry,
            packageId = "wanxiang-base",
        )
        assertTrue(analysis.isEmpty)
    }

    @Test
    fun `内置共享资源认领的文件不算冲突`() {
        val registry = registryOf(
            Triple("symbols.yaml", "old", listOf(BuiltinPackages.SHARED)),
        )
        val analysis = SchemaFileOwnership.analyze(
            targetFiles = listOf("symbols.yaml"),
            newHashes = mapOf("symbols.yaml" to "new"),
            registryFiles = registry,
            packageId = "frost-schemas",
        )
        assertTrue(analysis.isEmpty)
    }

    @Test
    fun `合并归属时保留旧属主`() {
        val registry = registryOf(
            Triple("chaifen.txt", "old", listOf("wanxiang-base")),
        )
        val fileEntries = JSONObject().put(
            "chaifen.txt",
            JSONObject().put("sha256", "new").put("size", 2),
        )
        val merged = SchemaFileOwnership.mergeClaims(
            registryFiles = registry,
            packageId = "wanxiang-flypy",
            fileEntries = fileEntries,
            isShared = { false },
        )
        val owners = merged.getJSONObject("chaifen.txt").getJSONArray("claimedBy").let { arr ->
            (0 until arr.length()).map { arr.getString(it) }
        }
        // 关键：base 仍在归属里，卸载 flypy 不会把 base 的文件删掉
        assertTrue(owners.contains("wanxiang-base"))
        assertTrue(owners.contains("wanxiang-flypy"))
    }

    @Test
    fun `新文件首次登记归属本包`() {
        val merged = SchemaFileOwnership.mergeClaims(
            registryFiles = JSONObject(),
            packageId = "newpkg",
            fileEntries = JSONObject().put(
                "a.schema.yaml",
                JSONObject().put("sha256", "s").put("size", 1),
            ),
            isShared = { false },
        )
        val owners = merged.getJSONObject("a.schema.yaml").getJSONArray("claimedBy")
        assertEquals(1, owners.length())
        assertEquals("newpkg", owners.getString(0))
    }

    @Test
    fun `共享文件不改写注册表的 sha`() {
        val registry = registryOf(
            Triple("symbols.yaml", "baseline", listOf(BuiltinPackages.SHARED)),
        )
        val merged = SchemaFileOwnership.mergeClaims(
            registryFiles = registry,
            packageId = "frost-schemas",
            fileEntries = JSONObject().put(
                "symbols.yaml",
                JSONObject().put("sha256", "package-version").put("size", 9),
            ),
            isShared = { true },
        )
        // 共享文件的基线内容由 SharedPresetOverrides 单独留档，注册表保留基线 sha
        assertEquals("baseline", merged.getJSONObject("symbols.yaml").getString("sha256"))
    }

    @Test
    fun `方案定义与词库之外的 yaml 都允许覆盖`() {
        assertTrue(SchemaFileOwnership.isOverridableType("chaifen.txt"))
        assertTrue(SchemaFileOwnership.isOverridableType("wanxiang_algebra.yaml"))
        assertTrue(SchemaFileOwnership.isOverridableType("default.yaml"))
        assertTrue(SchemaFileOwnership.isOverridableType("wanxiang.custom.yaml"))
        assertTrue(SchemaFileOwnership.isOverridableType("lua/t9.lua"))
        assertFalse(SchemaFileOwnership.isOverridableType("rime_ice.schema.yaml"))
        assertFalse(SchemaFileOwnership.isOverridableType("cn_dicts/base.dict.yaml"))
    }
}
