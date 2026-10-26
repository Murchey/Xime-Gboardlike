package com.kingzcheung.xime.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import com.charleskorn.kaml.YamlList
import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.YamlNode
import com.charleskorn.kaml.YamlScalar

class FuzzyPinyinPatchTest {

    private val rules = listOf("derive/^l/n/", "derive/^n/l/")

    @Test
    fun `空文件生成 patch 并把规则插到列表首位`() {
        val out = FuzzyPinyinPatch.apply(null, rules)!!
        assertTrue(out.contains("patch:"))
        assertTrue(out.contains("'speller/algebra/0+':"))
        assertTrue(out.contains("derive/^l/n/"))
        assertTrue(out.contains("derive/^n/l/"))
    }

    @Test
    fun `保留已有其他 patch`() {
        val existing = "patch:\n  menu:\n    page_size: 20\n"
        val out = FuzzyPinyinPatch.apply(existing, rules)!!
        assertTrue(out.contains("menu:"))
        assertTrue(out.contains("page_size: 20"))
        assertTrue(out.contains("'speller/algebra/0+':"))
    }

    @Test
    fun `替换旧规则不重复 key`() {
        val existing = "patch:\n  'speller/algebra/0+':\n    - 'old'\n  menu:\n    page_size: 20\n"
        val out = FuzzyPinyinPatch.apply(existing, rules)!!
        assertFalse(out.contains("old"))
        assertEquals(1, Regex("speller/algebra/0\\+").findAll(out).count())
        assertTrue(out.contains("page_size: 20"))
    }

    @Test
    fun `取消全部规则时删除自己的 key`() {
        val existing = "patch:\n  'speller/algebra/0+':\n    - 'derive/^l/n/'\n  menu:\n    page_size: 20\n"
        val out = FuzzyPinyinPatch.apply(existing, emptyList())!!
        assertFalse(out.contains("speller/algebra"))
        assertTrue(out.contains("page_size: 20"))
    }

    @Test
    fun `只有自己的 key 时删除文件`() {
        val existing = "patch:\n  'speller/algebra/0+':\n    - 'derive/^l/n/'\n"
        assertNull(FuzzyPinyinPatch.apply(existing, emptyList()))
    }

    @Test
    fun `生成的 YAML 可解析为列表补丁`() {
        val out = FuzzyPinyinPatch.apply(null, rules)!!
        val root = Yaml(configuration = YamlConfiguration(strictMode = false))
            .parseToYamlNode(out) as YamlMap
        val patch = root.get<YamlNode>("patch") as YamlMap
        val list = patch.get<YamlNode>(FuzzyPinyinPatch.KEY) as YamlList
        assertEquals(2, list.items.size)
        assertEquals(rules[0], (list.items[0] as YamlScalar).content)
        assertEquals(rules[1], (list.items[1] as YamlScalar).content)
    }

    @Test
    fun `规则按配置顺序展开`() {
        assertEquals(
            listOf("derive/^([zcs])h/\$1/", "derive/^([zcs])([^h])/\$1h\$2/"),
            FuzzyPinyinRules.rulesFor(setOf("zh_z")),
        )
        assertEquals(listOf("derive/^l/n/", "derive/^n/l/"), FuzzyPinyinRules.rulesFor(setOf("n_l")))
    }
}
