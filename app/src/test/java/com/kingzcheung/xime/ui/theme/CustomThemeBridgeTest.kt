package com.kingzcheung.xime.ui.theme

import androidx.compose.ui.graphics.Color
import com.kingzcheung.xime.settings.CustomThemeStore
import org.junit.Assert.assertEquals
import org.junit.Test

class CustomThemeBridgeTest {

    @Test
    fun `自定义键盘背景色转换后不被默认色覆盖`() {
        val base = CustomThemeStore.defaultScheme(id = "custom_demo", name = "Demo")
        val custom = base.copy(
            light = base.light.copy(keyboardBackground = "#123456"),
            dark = base.dark.copy(keyboardBackground = "#0A0B0C"),
        )
        val scheme = custom.toKeyboardColorScheme()
        assertEquals(Color(0xFF123456), scheme.keyboardBgLight)
        assertEquals(Color(0xFF0A0B0C), scheme.keyboardBgDark)
        assertEquals(Color(0xFF123456), scheme.surfaceLight)
    }

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
