package com.kingzcheung.xime.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GramPatchTest {

    @Test
    fun `空文件写入生成 patch 块`() {
        val out = GramPatch.apply(null, "xime")
        assertEquals(
            "patch:\n  'grammar/language': 'xime'\n  'translator/contextual_suggestions': true\n",
            out,
        )
    }

    @Test
    fun `已有 patch 时保留用户其他键并追加我们的键`() {
        val existing = "patch:\n  'speller/algebra':\n    - 'derive/^x/y/'\n"
        val out = GramPatch.apply(existing, "xime")!!
        assertTrue(out.contains("'speller/algebra':"))
        assertTrue(out.contains("'grammar/language': 'xime'"))
        assertTrue(out.contains("'translator/contextual_suggestions': true"))
    }

    @Test
    fun `重复 apply 幂等覆盖不重复追加`() {
        val once = GramPatch.apply(null, "xime")!!
        val twice = GramPatch.apply(once, "xime")!!
        assertEquals(once, twice)
    }

    @Test
    fun `切换语言只更新值不新增行`() {
        val first = GramPatch.apply(null, "xime")!!
        val second = GramPatch.apply(first, "baguawen")!!
        assertTrue(second.contains("'grammar/language': 'baguawen'"))
        assertEquals(1, Regex("grammar/language").findAll(second).count())
        assertEquals(1, Regex("contextual_suggestions").findAll(second).count())
    }

    @Test
    fun `清除时保留用户其他 patch`() {
        val existing = "patch:\n  'speller/algebra':\n    - 'derive/^x/y/'\n" +
            "  'grammar/language': 'xime'\n  'translator/contextual_suggestions': true\n"
        val out = GramPatch.apply(existing, null)!!
        assertTrue(out.contains("'speller/algebra':"))
        assertTrue(!out.contains("grammar/language"))
        assertTrue(!out.contains("contextual_suggestions"))
    }

    @Test
    fun `清除后没有其他内容时返回 null`() {
        val ours = GramPatch.apply(null, "xime")!!
        assertNull(GramPatch.apply(ours, null))
    }

    @Test
    fun `用户自己的 grammar 配置被我们接管后可还原为空`() {
        // 用户原本手写过 grammar/language，我们覆盖后清除即回到无该键
        val existing = "patch:\n  'grammar/language': 'user_own'\n"
        val applied = GramPatch.apply(existing, "xime")!!
        assertEquals(1, Regex("grammar/language").findAll(applied).count())
        val cleared = GramPatch.apply(applied, null)
        assertNull(cleared)
    }

    @Test
    fun `无 patch 头但有正文时保留正文并追加 patch`() {
        val existing = "customization:\n  greeting: hi\n"
        val out = GramPatch.apply(existing, "xime")!!
        assertTrue(out.contains("customization:"))
        assertTrue(out.contains("patch:"))
    }

    @Test
    fun `patch 区之后还有其他顶层块时插在 patch 区内`() {
        val existing = "patch:\n  'foo/bar': 1\ncustomization:\n  greeting: hi\n"
        val out = GramPatch.apply(existing, "xime")!!
        val patchIdx = out.indexOf("patch:")
        val customizationIdx = out.indexOf("customization:")
        val ourIdx = out.indexOf("grammar/language")
        assertTrue(patchIdx in 0 until ourIdx && ourIdx < customizationIdx)
        // 之后重复 apply 依然幂等
        assertEquals(out, GramPatch.apply(out, "xime"))
    }
}
