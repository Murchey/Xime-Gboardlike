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
            - xform/^ch/Ⓘ/
            - xform/^zh/Ⓥ/
            - xform/ui$/Ⓥ/
            - xform/^[aoe]/Ⓞ/
            """.trimIndent()
        )
        assertEquals("iu", map['q'])
        assertEquals("ei", map['w'])
        assertEquals("uan", map['r'])
        assertEquals("iang\nuang", map['l'])
        assertEquals("ing\nuai", map['k'])
        assertEquals("sh", map['u'])
        assertEquals("ch", map['i'])
        assertEquals("zh\nui", map['v'])
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
        assertEquals("ia\nua", map['w'])
        assertEquals("uan\nvan", map['r'])
        assertEquals("ong\niong", map['s'])
        assertTrue(DoublePinyinFinals.isDoublePinyin("double_pinyin_flypy"))
        assertFalse(DoublePinyinFinals.isDoublePinyin("rime_ice"))
    }

    @Test
    fun `键位映射跟随方案自身 xlit`() {
        val map = DoublePinyinFinals.parse(
            """
            - xform/ei$/Ⓠ/
            - xform/uo$/Ⓔ/
            - xform/ian$/Ⓦ/
            - xform/er$|iu$/Ⓡ/
            - xlit/ⓆⓌⒺⓇⓉⓎⓄⓅⒶⓈⒹⒻⒼⒽⒿⓀⓁⓏⓍⒸⓋⒷⓃⓂ/qwertyopasdfghjklzxcvbnm/
            """.trimIndent()
        )
        assertEquals("ei", map['q'])
        assertEquals("uo", map['e'])
        assertEquals("ian", map['w'])
        assertEquals("er\niu", map['r'])
    }
}
