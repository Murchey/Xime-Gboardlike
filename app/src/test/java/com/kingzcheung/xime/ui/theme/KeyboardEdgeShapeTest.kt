package com.kingzcheung.xime.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/** 键盘整体边缘形状（上沿样式 + 底部圆角）。 */
class KeyboardEdgeShapeTest {

    @Test
    fun `顶部直角底部直角`() {
        assertEquals(
            RoundedCornerShape(0.dp, 0.dp, 0.dp, 0.dp),
            keyboardEdgeShape(KeyboardTopStyle.SQUARE, roundedBottom = false),
        )
    }

    @Test
    fun `顶部圆角只在上面`() {
        assertEquals(
            RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp, bottomEnd = 0.dp, bottomStart = 0.dp),
            keyboardEdgeShape(KeyboardTopStyle.ROUNDED, roundedBottom = false),
        )
    }

    @Test
    fun `底部圆角四角都圆`() {
        assertEquals(
            RoundedCornerShape(14.dp, 14.dp, 14.dp, 14.dp),
            keyboardEdgeShape(KeyboardTopStyle.ROUNDED, roundedBottom = true),
        )
    }
}
