package com.kingzcheung.xime.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 液态玻璃强度档位的纯逻辑测试。 */
class LiquidGlassStrengthTest {

    @Test
    fun `id 往返与非法回退标准档`() {
        LiquidGlassStrength.entries.forEach { assertEquals(it, LiquidGlassStrength.of(it.id)) }
        assertEquals(LiquidGlassStrength.STANDARD, LiquidGlassStrength.of(null))
        assertEquals(LiquidGlassStrength.STANDARD, LiquidGlassStrength.of("bogus"))
    }

    @Test
    fun `亮边粗细与厚度逐档加强`() {
        assertTrue(LiquidGlassStrength.STANDARD.strokeDp < LiquidGlassStrength.STRONG.strokeDp)
        assertTrue(LiquidGlassStrength.STRONG.strokeDp < LiquidGlassStrength.STRONGEST.strokeDp)
        assertEquals(0f, LiquidGlassStrength.STANDARD.bandDp)
        assertTrue(LiquidGlassStrength.STRONG.bandDp < LiquidGlassStrength.STRONGEST.bandDp)
    }

    @Test
    fun `标准档不画厚度和高光`() {
        assertEquals(0f, LiquidGlassStrength.STANDARD.bandAlpha(true))
        assertEquals(0f, LiquidGlassStrength.STANDARD.sheenAlpha(true))
        assertTrue(LiquidGlassStrength.STRONG.sheenAlpha(true) > 0f)
    }

    @Test
    fun `浅色下厚度和高光更亮且右下描暗边`() {
        val s = LiquidGlassStrength.STRONGEST
        assertTrue(s.bandAlpha(false) >= s.bandAlpha(true))
        assertTrue(s.sheenAlpha(false) >= s.sheenAlpha(true))
        assertEquals(0f, s.shadeAlpha(true))
        assertTrue(s.shadeAlpha(false) > 0f)
    }

    @Test
    fun `亮边四处不透明度逐档加强`() {
        val light = LiquidGlassStrength.STANDARD.rimAlphas(false)
        val strong = LiquidGlassStrength.STRONG.rimAlphas(false)
        val strongest = LiquidGlassStrength.STRONGEST.rimAlphas(false)
        for (i in light.indices) {
            assertTrue(light[i] < strong[i])
            assertTrue(strong[i] < strongest[i])
        }
        // 左上主光最亮、右下回光次之、侧面最弱
        val a = LiquidGlassStrength.STANDARD.rimAlphas(false)
        assertTrue(a[0] > a[3] && a[3] > a[1] && a[1] > a[2])
    }
}
