package com.kingzcheung.xime.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class CustomThemeBridgeTest {

    @Test
    fun `内置主题模板完整带入六个颜色`() {
        val scheme = KeyboardColorScheme(
            id = "demo",
            name = "Demo",
            specialKeyLight = Color(0xFF112233),
            specialKeyDark = Color(0xFF445566),
            accentLight = Color(0xFF778899),
            accentDark = Color(0xFFAABBCC),
        )
        val custom = scheme.toCustomColorScheme("Demo 副本")
        assertEquals("Demo 副本", custom.name)
        assertEquals("#112233", custom.light.functionKeyBackground)
        assertEquals("#445566", custom.dark.functionKeyBackground)
        assertEquals("#778899", custom.light.accent)
        assertEquals("#AABBCC", custom.dark.accent)
    }
}
