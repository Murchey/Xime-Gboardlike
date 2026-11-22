package com.kingzcheung.xime.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/** 键盘顶部样式与裁剪形状的纯逻辑测试。 */
class KeyboardTopStyleTest {

    @Test
    fun `id 往返与非法回退圆角`() {
        KeyboardTopStyle.entries.forEach { assertEquals(it, KeyboardTopStyle.of(it.id)) }
        assertEquals(KeyboardTopStyle.ROUNDED, KeyboardTopStyle.of(null))
        assertEquals(KeyboardTopStyle.ROUNDED, KeyboardTopStyle.of("bogus"))
    }

    @Test
    fun `圆角档只裁上两角`() {
        assertEquals(
            RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            KeyboardTopStyle.ROUNDED.clipShape(),
        )
        assertEquals(RoundedCornerShape(0.dp), KeyboardTopStyle.SQUARE.clipShape())
        assertEquals(RoundedCornerShape(0.dp), KeyboardTopStyle.SOFT.clipShape())
    }
}
