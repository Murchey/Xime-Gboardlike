package com.kingzcheung.xime.ui.theme

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/** 按键形状对圆角的钳制规则（纯逻辑）。 */
class KeyShapeRadiusTest {

    @Test
    fun `圆角保持配置值`() {
        assertEquals(6.dp, KeyShape.ROUNDED.radius(6.dp))
        assertEquals(18.dp, KeyShape.ROUNDED.radius(18.dp))
    }

    @Test
    fun `大圆角只抬下限`() {
        assertEquals(12.dp, KeyShape.ROUNDER.radius(6.dp))
        assertEquals(20.dp, KeyShape.ROUNDER.radius(20.dp))
    }

    @Test
    fun `方形只压上限`() {
        assertEquals(5.dp, KeyShape.SQUARE.radius(12.dp))
        assertEquals(2.dp, KeyShape.SQUARE.radius(2.dp))
    }

    @Test
    fun `直角恒为零`() {
        assertEquals(0.dp, KeyShape.SHARP.radius(24.dp))
        assertEquals(0.dp, KeyShape.SHARP.radius(0.dp))
    }

    @Test
    fun `id 往返与非法回退`() {
        KeyShape.entries.forEach { assertEquals(it, KeyShape.fromId(it.id)) }
        assertEquals(KeyShape.ROUNDED, KeyShape.fromId(null))
        assertEquals(KeyShape.ROUNDED, KeyShape.fromId("bogus"))
    }
}
