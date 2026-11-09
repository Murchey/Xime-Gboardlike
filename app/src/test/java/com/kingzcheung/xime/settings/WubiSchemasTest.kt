package com.kingzcheung.xime.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 五笔方案识别：连打只对五笔族有意义，别把拼音方案也带进去。 */
class WubiSchemasTest {

    @Test
    fun `内置五笔方案被识别`() {
        assertTrue(WubiSchemas.isWubi("wubi86"))
        assertTrue(WubiSchemas.isWubi("wubi98"))
        assertTrue(WubiSchemas.isWubi("wubi86_pinyin"))
    }

    @Test
    fun `第三方方案包里的五笔也能识别`() {
        assertTrue(WubiSchemas.isWubi("rime_frost_wubi86"))
        assertTrue(WubiSchemas.isWubi("wanxiang_wubi"))
    }

    @Test
    fun `拼音与其它方案不受影响`() {
        assertFalse(WubiSchemas.isWubi("rime_ice"))
        assertFalse(WubiSchemas.isWubi("double_pinyin_mspy"))
        assertFalse(WubiSchemas.isWubi("pinyin_simp"))
        assertFalse(WubiSchemas.isWubi("t9_pinyin"))
    }

    @Test
    fun `空方案 id 不崩`() {
        assertFalse(WubiSchemas.isWubi(""))
    }

    @Test
    fun `大小写不影响判定`() {
        assertTrue(WubiSchemas.isWubi("Wubi86"))
    }
}
