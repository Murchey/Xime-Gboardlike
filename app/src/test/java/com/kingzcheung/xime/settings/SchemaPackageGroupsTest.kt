package com.kingzcheung.xime.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 方案按来源包分组。
 *
 * 典型场景：装了内置 + 雾凇 + 万象之后，屏幕上会出现 3 个「微软双拼」，
 * 平铺列表让用户以为"被顶替"，实际是三套不同包提供的同名方案。
 */
class SchemaPackageGroupsTest {

    private val mapping = mapOf(
        "rime_ice" to "builtin_rime_ice",
        "double_pinyin_mspy" to "builtin_rime_ice",
        "rime_frost" to "frost-schemas",
        "rime_frost_double_pinyin_mspy" to "frost-schemas",
        "wanxiang" to "wanxiang-base",
        "handmade" to SchemaPackageGroups.UNGROUPED,
    )
    private val names = mapOf(
        "builtin_rime_ice" to "雾凇拼音",
        "frost-schemas" to "白霜拼音",
        "wanxiang-base" to "万象拼音",
    )
    private val all = listOf(
        "rime_ice", "double_pinyin_mspy", "rime_frost",
        "rime_frost_double_pinyin_mspy", "wanxiang", "handmade",
    )

    @Test
    fun `按来源包分组`() {
        val groups = SchemaPackageGroups.group(all, mapping, names, listOf("rime_ice"), "未归类")
        assertEquals(4, groups.size)
        val byId = groups.associateBy { it.packageId }
        assertEquals(listOf("rime_ice", "double_pinyin_mspy"), byId["builtin_rime_ice"]!!.schemaIds)
        assertEquals(1, byId["builtin_rime_ice"]!!.enabledCount)
        assertEquals(0, byId["frost-schemas"]!!.enabledCount)
    }

    @Test
    fun `未归类固定排在最后`() {
        val groups = SchemaPackageGroups.group(all, mapping, names, emptyList(), "未归类方案")
        assertEquals(SchemaPackageGroups.UNGROUPED, groups.last().packageId)
        assertEquals("未归类方案", groups.last().displayName)
    }

    @Test
    fun `包没有清单名时回退包 id`() {
        val groups = SchemaPackageGroups.group(
            listOf("x1"), mapOf("x1" to "mystery"), emptyMap(), emptyList(), "未归类",
        )
        assertEquals("mystery", groups.single().displayName)
    }

    @Test
    fun `筛选某个包只返回该包的方案`() {
        val filtered = SchemaPackageGroups.filter(all, mapping, "frost-schemas")
        assertEquals(listOf("rime_frost", "rime_frost_double_pinyin_mspy"), filtered)
    }

    @Test
    fun `筛选为空表示全部`() {
        assertEquals(all, SchemaPackageGroups.filter(all, mapping, null))
        assertEquals(all, SchemaPackageGroups.filter(all, mapping, ""))
    }

    @Test
    fun `筛选未归类`() {
        assertEquals(listOf("handmade"), SchemaPackageGroups.filter(all, mapping, SchemaPackageGroups.UNGROUPED))
    }

    @Test
    fun `同名方案被识别出来`() {
        val namesById = mapOf(
            "double_pinyin_mspy" to "微软双拼",
            "rime_frost_double_pinyin_mspy" to "微软双拼",
            "rime_ice" to "雾凇拼音",
        )
        val duplicates = SchemaPackageGroups.duplicateNames(namesById)
        assertEquals(setOf("double_pinyin_mspy", "rime_frost_double_pinyin_mspy"), duplicates)
        assertTrue("rime_ice" !in duplicates)
    }

    @Test
    fun `没有重名时返回空集`() {
        val namesById = mapOf("a" to "方案A", "b" to "方案B")
        assertTrue(SchemaPackageGroups.duplicateNames(namesById).isEmpty())
    }

    @Test
    fun `首尾空白不影响重名判定`() {
        val duplicates = SchemaPackageGroups.duplicateNames(
            mapOf("a" to " 微软双拼", "b" to "微软双拼 "),
        )
        assertEquals(setOf("a", "b"), duplicates)
    }
}
