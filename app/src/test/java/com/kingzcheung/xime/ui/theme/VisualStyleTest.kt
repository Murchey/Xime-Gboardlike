package com.kingzcheung.xime.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 视觉样式与材质配方的纯逻辑测试。 */
class VisualStyleTest {

    @Test
    fun `id 往返与非法回退`() {
        VisualStyle.entries.forEach { assertEquals(it, VisualStyle.fromId(it.id)) }
        assertEquals(VisualStyle.ORIGINAL, VisualStyle.fromId(null))
        assertEquals(VisualStyle.ORIGINAL, VisualStyle.fromId("bogus"))
    }

    @Test
    fun `设置只提供三种且旧玻璃样式回落到原有外观`() {
        assertEquals(listOf(VisualStyle.ORIGINAL, VisualStyle.NEON, VisualStyle.FACET), VisualStyle.offered)
        assertEquals(VisualStyle.ORIGINAL, VisualStyle.forKeyboard(VisualStyle.GLASS))
        assertEquals(VisualStyle.ORIGINAL, VisualStyle.forKeyboard(VisualStyle.FROST))
        assertEquals(VisualStyle.NEON, VisualStyle.forKeyboard(VisualStyle.NEON))
    }

    @Test
    fun `原有外观不画任何材质`() {
        val optics = MaterialRecipes.resolve(VisualStyle.ORIGINAL, MaterialLevel.FLOATING)
        assertEquals(0f, optics.shade)
        assertEquals(0f, optics.tint)
        assertEquals(0f, optics.border)
        assertEquals(0f, optics.glow)
    }

    @Test
    fun `浮起层级按比例加强`() {
        val base = MaterialRecipes.resolve(VisualStyle.FACET, MaterialLevel.BASE)
        val raised = MaterialRecipes.resolve(VisualStyle.FACET, MaterialLevel.RAISED)
        assertEquals(base.tint * 1.15f, raised.tint, 1e-6f)
        assertEquals(base.facet * 1.15f, raised.facet, 1e-6f)
    }

    @Test
    fun `只有霓光加悬浮层才有发光`() {
        assertTrue(MaterialRecipes.resolve(VisualStyle.NEON, MaterialLevel.FLOATING).glow > 0f)
        assertEquals(0f, MaterialRecipes.resolve(VisualStyle.NEON, MaterialLevel.BASE).glow)
        assertEquals(0f, MaterialRecipes.resolve(VisualStyle.FACET, MaterialLevel.FLOATING).glow)
    }
}
