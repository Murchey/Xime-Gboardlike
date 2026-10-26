package com.kingzcheung.xime.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DoublePinyinFinalsTest {

    @Test
    fun `小鹤韵母映射`() {
        val map = DoublePinyinFinals.parse(
            """
            - xform/iu$/Ⓠ/
            - xform/(.)ei$/$1Ⓦ/
            - xform/uan$/Ⓡ/
            - xform/[iu]ang$/Ⓛ/
            - xform/ing$|uai$/Ⓚ/
            - xform/^sh/Ⓤ/
            - xform/^[aoe]/Ⓞ/
            """.trimIndent()
        )
        assertEquals("iu", map['q'])
        assertEquals("ei", map['w'])
        assertEquals("uan", map['r'])
        assertEquals("iang/uang", map['l'])
        assertEquals("ing/uai", map['k'])
        assertFalse(map.containsKey('u'))
        assertEquals("a", map['a'])
    }

    @Test
    fun `自然码可选字母与多韵母`() {
        val map = DoublePinyinFinals.parse(
            """
            - xform/[iu]a$/Ⓦ/
            - xform/[uv]an$/Ⓡ/
            - xform/(.)i?ong$/$1Ⓢ/
            """.trimIndent()
        )
        assertEquals("ia/ua", map['w'])
        assertEquals("uan/van", map['r'])
        assertEquals("ong/iong", map['s'])
        assertTrue(DoublePinyinFinals.isDoublePinyin("double_pinyin_flypy"))
        assertFalse(DoublePinyinFinals.isDoublePinyin("rime_ice"))
    }
}
