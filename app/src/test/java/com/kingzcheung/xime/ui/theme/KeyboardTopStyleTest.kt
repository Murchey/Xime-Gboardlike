package com.kingzcheung.xime.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/** 键盘顶部样式与裁剪形状的纯逻辑测试。 */
class KeyboardTopStyleTest {

    @Test
    fun `id 往返与非法回退直角`() {
        KeyboardTopStyle.entries.forEach { assertEquals(it, KeyboardTopStyle.of(it.id)) }
        assertEquals(KeyboardTopStyle.SQUARE, KeyboardTopStyle.of(null))
        assertEquals(KeyboardTopStyle.SQUARE, KeyboardTopStyle.of("bogus"))
    }

    @Test
    fun `圆角档只裁上两角`() {
        assertEquals(
            RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp),
            KeyboardTopStyle.ROUNDED.clipShape(),
        )
        assertEquals(RoundedCornerShape(0.dp), KeyboardTopStyle.SQUARE.clipShape())
        assertEquals(RoundedCornerShape(0.dp), KeyboardTopStyle.SOFT.clipShape())
    }
}
