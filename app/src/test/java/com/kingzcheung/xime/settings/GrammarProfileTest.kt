package com.kingzcheung.xime.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GrammarProfileTest {

    @Test
    fun `JSON 往返保持全部参数`() {
        val original = GrammarProfile(
            collocationMaxLength = 7,
            collocationMinLength = 3,
            collocationPenalty = -6.0,
            nonCollocationPenalty = -4.0,
            weakCollocationPenalty = -10.0,
            rearPenalty = -8.0,
            contextualSuggestions = false,
        )
        val restored = GrammarProfile.fromJson(original.toJson())
        assertEquals(original, restored)
    }

    @Test
    fun `非法 JSON 返回 null`() {
        assertEquals(null, GrammarProfile.fromJson(null))
        assertEquals(null, GrammarProfile.fromJson(""))
        assertEquals(null, GrammarProfile.fromJson("not json"))
    }

    @Test
    fun `coerce 收敛越界值`() {
        val p = GrammarProfile(
            collocationMaxLength = 99,
            collocationMinLength = 9,
            collocationPenalty = -100.0,
            nonCollocationPenalty = 5.0,
            weakCollocationPenalty = -999.0,
            rearPenalty = 1.0,
            contextualSuggestions = true,
        ).coerce()
        assertEquals(8, p.collocationMaxLength)
        assertEquals(8, p.collocationMinLength)
        assertEquals(-40.0, p.collocationPenalty, 0.0)
        assertEquals(0.0, p.nonCollocationPenalty, 0.0)
        assertEquals(-40.0, p.weakCollocationPenalty, 0.0)
        assertEquals(0.0, p.rearPenalty, 0.0)
    }

    @Test
    fun `推荐值随模型不同`() {
        assertEquals(GrammarProfile.BUNDLED, GrammarProfile.recommended(""))
        assertEquals(GrammarProfile.BUNDLED, GrammarProfile.recommended("XimeGboard"))
        // rime-ice 配套模型按方案自带刻度：非组合惩罚 -4
        assertEquals(-4.0, GrammarProfile.recommended("zh-moqi").nonCollocationPenalty, 0.0)
    }

    @Test
    fun `自定义参数写入 patch`() {
        val p = GrammarProfile(
            collocationMaxLength = 7,
            collocationMinLength = 3,
            collocationPenalty = -6.0,
            nonCollocationPenalty = -4.0,
            weakCollocationPenalty = -10.0,
            rearPenalty = -8.0,
            contextualSuggestions = false,
        )
        val out = GramPatch.apply(null, "xime", p)
        assertNotNull(out)
        assertTrue(out!!.contains("'grammar/collocation_max_length': 7"))
        assertTrue(out.contains("'grammar/collocation_min_length': 3"))
        assertTrue(out.contains("'grammar/collocation_penalty': -6"))
        assertTrue(out.contains("'grammar/non_collocation_penalty': -4"))
        assertTrue(out.contains("'grammar/weak_collocation_penalty': -10"))
        assertTrue(out.contains("'grammar/rear_penalty': -8"))
        assertFalse(out.contains("contextual_suggestions': true"))
    }
}
