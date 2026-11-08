package com.kingzcheung.xime.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.kingzcheung.xime.settings.CustomThemeStore
import org.junit.Assert.assertEquals
import org.junit.Test

class CustomThemeBridgeTest {

    /** 颜色 → #RRGGBB（与主题桥接同一格式，便于直接比对字符串）。 */
    private fun toHex(color: Color): String =
        CustomThemeStore.formatHexColor(color.toArgb().toLong() and 0xFFFFFFFFL)

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
    fun `内置主题模板带入实际渲染用的功能键与回车键颜色`() {
        // 功能键组与回车键在键盘上是推导出来的，模板必须带"推导结果"而不是裸 token，
        // 否则从模板新建的配色和原主题长得不一样。
        val scheme = KeyboardColorScheme(
            id = "demo",
            name = "Demo",
            specialKeyLight = Color(0xFF112233),
            specialKeyDark = Color(0xFF445566),
            functionKeyBackgroundLight = Color(0xFF112233),
            functionKeyBackgroundDark = Color(0xFF445566),
            accentLight = Color(0xFF778899),
            accentDark = Color(0xFFAABBCC),
            accentKeyBackgroundLight = Color(0xFF010203),
            accentKeyBackgroundDark = Color(0xFF040506),
        )
        val custom = scheme.toCustomColorScheme("Demo 副本")
        assertEquals("Demo 副本", custom.name)
        // 有 functionKey token 时直接用 token
        assertEquals("#112233", custom.light.functionKeyBackground)
        assertEquals("#445566", custom.dark.functionKeyBackground)
        // 回车键取键盘真实渲染色（这里有显式 token，直接用 token）
        assertEquals("#010203", custom.light.enterKeyBackground)
        assertEquals("#040506", custom.dark.enterKeyBackground)
        assertEquals("#778899", custom.light.accent)
        assertEquals("#AABBCC", custom.dark.accent)
    }

    @Test
    fun `未配置功能键色时模板与键盘走同一条推导`() {
        // 没有 functionKey token 的主题：模板值应等于键盘实际取值（由键帽色推导）
        val scheme = KeyboardColorScheme(
            id = "demo2",
            name = "Demo2",
            specialKeyLight = Color(0xFF112233),
            specialKeyDark = Color(0xFF445566),
            accentLight = Color(0xFF778899),
            accentDark = Color(0xFFAABBCC),
        )
        val custom = scheme.toCustomColorScheme("Demo2 副本")
        // 未配置 token 的主题：模板值 == 键盘推导值（同一函数，按方案对象算）
        assertEquals(
            toHex(KeyboardThemes.resolveFunctionKeyColor(scheme, false)),
            custom.light.functionKeyBackground,
        )
        assertEquals(
            toHex(KeyboardThemes.resolveFunctionKeyColor(scheme, true)),
            custom.dark.functionKeyBackground,
        )
        // 回车键在没有显式 token 时等于强调色
        assertEquals("#778899", custom.light.enterKeyBackground)
        assertEquals("#AABBCC", custom.dark.enterKeyBackground)
    }
}
