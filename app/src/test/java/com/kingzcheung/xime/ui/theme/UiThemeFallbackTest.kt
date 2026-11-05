package com.kingzcheung.xime.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class UiThemeFallbackTest {

    @Test
    fun `显式指定主题时直接使用`() {
        assertEquals(
            "wechat",
            resolveUiThemeId(
                darkTheme = true,
                themeId = "wechat",
                lightThemeId = "gboard_gray",
                darkThemeId = "gboard_black",
                defaultLightId = "default_light",
                defaultDarkId = "default_dark",
            )
        )
    }

    @Test
    fun `浅深一致时跟随已选主题`() {
        assertEquals(
            "wechat",
            resolveUiThemeId(
                darkTheme = false,
                themeId = null,
                lightThemeId = "wechat",
                darkThemeId = "wechat",
                defaultLightId = "default_light",
                defaultDarkId = "default_dark",
            )
        )
        assertEquals(
            "wechat",
            resolveUiThemeId(
                darkTheme = true,
                themeId = null,
                lightThemeId = "wechat",
                darkThemeId = "wechat",
                defaultLightId = "default_light",
                defaultDarkId = "default_dark",
            )
        )
    }

    @Test
    fun `混搭时设置界面主题色回归默认值`() {
        assertEquals(
            "default_light",
            resolveUiThemeId(
                darkTheme = false,
                themeId = null,
                lightThemeId = "gboard_gray",
                darkThemeId = "claude",
                defaultLightId = "default_light",
                defaultDarkId = "default_dark",
            )
        )
        assertEquals(
            "default_dark",
            resolveUiThemeId(
                darkTheme = true,
                themeId = null,
                lightThemeId = "gboard_gray",
                darkThemeId = "claude",
                defaultLightId = "default_light",
                defaultDarkId = "default_dark",
            )
        )
    }
}
