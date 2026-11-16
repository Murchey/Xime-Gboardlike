package com.kingzcheung.xime.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 派生合并键方案：拿仓库里真实的基础方案文本跑一遍，检查四个必要改动与"原配置不丢"。
 */
class GridSchemaGeneratorTest {

    private fun repoFile(rel: String): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            File(dir, rel).takeIf { it.exists() }?.let { return it }
            File(dir, "app/$rel").takeIf { it.exists() }?.let { return it }
            dir = dir.parentFile
        }
        error("file not found: $rel")
    }

    private fun baseText(id: String): String =
        repoFile("src/main/assets/rime/$id.schema.yaml").readText()

    private fun generate(base: String, layout: String = "14", order: String = "qwerty"): String {
        val target = GridSchemaGenerator.derivedId(base, layout, order)
        val groups = GridSchemaGenerator.groupsOf(layout, order)
        val out = GridSchemaGenerator.generate(baseText(base), base, target, "测试方案", groups)
        assertNotNull("$base → $target 生成失败", out)
        return out!!
    }

    /** 随包的 pinyin_14jian 等已删除，老用户要迁移到派生版。 */
    @Test
    fun `老 id 迁移到派生 id`() {
        assertEquals("rime_ice_14jian", GridSchemaGenerator.legacyToDerived("pinyin_14jian"))
        assertEquals("rime_ice_18jian_abcde", GridSchemaGenerator.legacyToDerived("pinyin_18jian_abcde"))
        assertNull(GridSchemaGenerator.legacyToDerived("rime_ice_14jian"))
        assertNull(GridSchemaGenerator.legacyToDerived("double_pinyin_14jian"))
        assertNull(GridSchemaGenerator.legacyToDerived("rime_ice"))
    }

    @Test
    fun `派生 id 的识别与拆解`() {
        // 8 个内置基础方案 + 6 个随包合并键方案，都能被正确识别/拆解
        assertTrue(GridSchemaGenerator.isDerived("double_pinyin_flypy_18jian_abcde"))
        assertEquals("double_pinyin_flypy", GridSchemaGenerator.baseOf("double_pinyin_flypy_18jian_abcde"))
        assertEquals("qwerty_18_abcde", GridSchemaGenerator.sectionOf("double_pinyin_flypy_18jian_abcde"))
        assertEquals("qwerty_14", GridSchemaGenerator.sectionOf("rime_ice_14jian"))
        assertEquals("qwerty_17", GridSchemaGenerator.sectionOf("double_pinyin_17jian"))

        assertFalse(GridSchemaGenerator.isDerived("double_pinyin_flypy"))
        assertFalse(GridSchemaGenerator.isDerived("rime_ice"))
        assertEquals("double_pinyin", GridSchemaGenerator.baseOf("double_pinyin"))
        assertNull(GridSchemaGenerator.sectionOf("double_pinyin"))
    }

    @Test
    fun `组码映射与布局一致`() {
        // 14 键 QWERTY：q,w→q、e,r→e …… 与 msime 的 GROUP_CODES 同构
        assertEquals(
            "abcdedggujjlmbooqeatucqztz",
            GridSchemaGenerator.groupMapOf(GridSchemaGenerator.groupsOf("14", "qwerty")),
        )
        // ABCDE：ab→a、cd→c、ef→e ……
        assertEquals(
            "aacceeggijjllnnpprssuuwwyy",
            GridSchemaGenerator.groupMapOf(GridSchemaGenerator.groupsOf("14", "abcde")),
        )
        // 18 键 QWERTY：q、we→w、rt→r、y、u、io→i、p ……
        assertEquals(
            "abxswffhijjlmbipqrsruvwxyz",
            GridSchemaGenerator.groupMapOf(GridSchemaGenerator.groupsOf("18", "qwerty")),
        )
    }

    @Test
    fun `派生 id 规则`() {
        assertEquals("rime_ice_14jian", GridSchemaGenerator.derivedId("rime_ice", "14", "qwerty"))
        assertEquals("double_pinyin_flypy_18jian_abcde", GridSchemaGenerator.derivedId("double_pinyin_flypy", "18", "abcde"))
        assertEquals("double_pinyin_17jian", GridSchemaGenerator.derivedId("double_pinyin", "17", "qwerty"))
    }

    @Test
    fun `全拼派生改掉 schema_id prism 与 alphabet`() {
        val out = generate("rime_ice")
        assertTrue(out.contains("  schema_id: rime_ice_14jian"))
        assertTrue(out.contains("  prism: rime_ice_14jian"))
        assertTrue("prism 不能和基础方案撞名", out.contains("  prism: rime_ice_14jian"))
        assertTrue(out.contains("  alphabet: abcdegjlmoqtuz"))
        assertTrue(out.contains("  initials: abcdegjlmoqtuz"))
        // 滤镜要读候选里的组码，所以拼写提示必须留着（由滤镜负责替换成拼音）
        // 注释只留 lua 滤镜算出来的拼音（rime 自己的拼写提示对组码没意义）
        assertTrue("always_show_comments 应关闭", out.contains("  always_show_comments: false"))
        assertTrue("应挂拼音提示滤镜", out.contains("    - lua_filter@*grid_hint"))
        assertTrue("应写入组码映射", out.contains("  group_map: \"" + GridSchemaGenerator.groupMapOf(
            GridSchemaGenerator.groupsOf("14", "qwerty"),
        ) + "\""))
    }

    @Test
    fun `双拼派生保留双拼规则并追加组码`() {
        val out = generate("double_pinyin")
        assertTrue(out.contains("  schema_id: double_pinyin_14jian"))
        assertTrue(out.contains("  prism: double_pinyin_14jian"))
        assertFalse("不能残留基础方案 prism", Regex("(?m)^  prism: double_pinyin$").containsMatchIn(out))
        // 双拼自己的 xlit 规则还在
        assertTrue(out.contains("xlit/ⓆⓌⓇⓉⓎⓊⒾⓄⓅⓈⒹⒻⒼⒽⓂⒿⒸⓀⓁⓏⓍⓋⒷⓃ/"))
        // 组码规则追加在 algebra 末尾
        assertTrue(out.contains("    # 合并键：组内字母 → 组码"))
        assertTrue(out.contains("    - derive/[qw]/q/"))
        assertTrue(out.contains("    - derive/[bn]/b/"))
    }

    @Test
    fun `组码覆盖全部 26 个字母`() {
        listOf("14", "17", "18").forEach { layout ->
            listOf("qwerty", "abcde").forEach { order ->
                val groups = GridSchemaGenerator.groupsOf(layout, order)
                val covered = groups.joinToString("")
                assertEquals("$layout/$order 组键字母数", 26, covered.length)
                assertEquals("$layout/$order 不重复", ('a'..'z').toList(), covered.toList().sorted())
                val codes = GridSchemaGenerator.codesOf(groups)
                assertEquals("$layout/$order 组码数", layout.toInt(), codes.length)
                assertTrue("$layout/$order 组码都来自组内首字母", codes.all { it in covered })
            }
        }
    }

    @Test
    fun `八种基础方案都能派生`() {
        for (base in GridSchemaGenerator.BASE_SCHEMA_IDS) {
            val out = generate(base)
            assertTrue("$base 的 schema_id 应为派生 id", out.contains("  schema_id: " + base + "_14jian"))
            assertTrue("$base 的 prism 应为派生 id", out.contains("  prism: " + base + "_14jian"))
        }
    }
}
