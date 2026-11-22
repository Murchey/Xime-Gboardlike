package com.kingzcheung.xime.ui.theme

import com.kingzcheung.xime.settings.CustomColorScheme
import com.kingzcheung.xime.settings.CustomThemeStore
import com.kingzcheung.xime.settings.ThemeBackgroundSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 自定义主题「键盘底图 + 玻璃效果」的数据与映射测试（纯 JVM）。
 *
 * 覆盖：越界数值钳制 / 路径穿越清理 / JSON 往返 / 旧文件兼容 / 图片平移计算 /
 * 底图与按键透明度映射到运行时主题。
 */
class ThemeBackgroundFeatureTest {

    private fun withImage(vararg patch: Pair<String, Any?>): ThemeBackgroundSettings {
        var settings = ThemeBackgroundSettings(imagePath = "/themes/backgrounds/bg.jpg")
        patch.forEach { (key, value) ->
            settings = when (key) {
                "offsetX" -> settings.copy(offsetX = value as Float)
                "offsetY" -> settings.copy(offsetY = value as Float)
                "overlayAlpha" -> settings.copy(overlayAlpha = value as Float)
                "maskBlurEnabled" -> settings.copy(maskBlurEnabled = value as Boolean)
                "maskBlurRadius" -> settings.copy(maskBlurRadius = value as Int)
                "keyOpacity" -> settings.copy(keyOpacity = value as Float)
                "keyBlurEnabled" -> settings.copy(keyBlurEnabled = value as Boolean)
                "keyBlurRadius" -> settings.copy(keyBlurRadius = value as Int)
                else -> settings
            }
        }
        return settings
    }

    @Test
    fun `越界数值被钳到合法区间`() {
        val raw = ThemeBackgroundSettings(
            imagePath = "/themes/backgrounds/bg.jpg",
            offsetX = -1f,
            offsetY = 2f,
            overlayAlpha = 1.5f,
            maskBlurRadius = 999,
            keyOpacity = -0.2f,
            keyBlurRadius = -5,
            fit = "diagonal",
        ).normalized()
        assertEquals(0f, raw.offsetX)
        assertEquals(1f, raw.offsetY)
        assertEquals(1f, raw.overlayAlpha)
        assertEquals(ThemeBackgroundSettings.MAX_BLUR_RADIUS, raw.maskBlurRadius)
        assertEquals(0f, raw.keyOpacity)
        assertEquals(0, raw.keyBlurRadius)
        assertEquals(ThemeBackgroundSettings.FIT_COVER, raw.fit)
    }

    @Test
    fun `带路径穿越的图片路径被清空`() {
        assertEquals("", ThemeBackgroundSettings(imagePath = "/themes/../secret.png").normalized().imagePath)
        assertTrue(ThemeBackgroundSettings(imagePath = "  ").normalized().imagePath.isEmpty())
    }

    @Test
    fun `JSON 往返保留底图与玻璃参数`() {
        val scheme = CustomThemeStore.defaultScheme(id = "custom_test", name = "测试").copy(
            background = withImage(
                "offsetX" to 0.2f,
                "offsetY" to 0.8f,
                "overlayAlpha" to 0.4f,
                "maskBlurEnabled" to true,
                "maskBlurRadius" to 12,
                "keyOpacity" to 0.7f,
                "keyBlurEnabled" to true,
                "keyBlurRadius" to 20,
            ),
        )
        val json = CustomThemeStore.exportJson(listOf(scheme))
        val restored = CustomThemeStore.importJson(json).single()
        assertEquals(scheme.background, restored.background)
    }

    @Test
    fun `没有底图字段的旧 JSON 仍可导入`() {
        val legacy = """
            {"format":"xime-color-scheme","version":1,"schemes":[
              {"id":"custom_old","name":"旧方案",
               "light":{"keyboard_background":"#F1F3F4","key_background":"#FFFFFF",
                        "function_key_background":"#E8EAED","key_text":"#202124",
                        "candidate_text":"#5F6368","accent":"#1A73E8"},
               "dark":{"keyboard_background":"#1E1F21","key_background":"#38393B",
                       "function_key_background":"#38393B","key_text":"#E7E8EA",
                       "candidate_text":"#8D8E90","accent":"#A8C8FB"}}
            ]}
        """.trimIndent()
        val scheme = CustomThemeStore.importJson(legacy).single()
        assertEquals(ThemeBackgroundSettings(), scheme.background)
    }

    @Test
    fun `cover 填充下平移只作用于有多余空间的方向`() {
        // 100x100 的图放进 200x100：cover 放大到 200x200，横向无余量、纵向余量 100
        val top = imagePlacement(100f, 100f, 200f, 100f, "cover", 0f, 0f)
        assertEquals(0f, top.left)
        assertEquals(200f, top.width)
        assertEquals(0f, top.top)

        val bottom = imagePlacement(100f, 100f, 200f, 100f, "cover", 1f, 1f)
        assertEquals(0f, bottom.left)
        assertEquals(-100f, bottom.top)

        val centered = imagePlacement(100f, 100f, 200f, 100f, "cover", 0.5f, 0.5f)
        assertEquals(-50f, centered.top)
    }

    @Test
    fun `contain 填充在留白里平移`() {
        // 100x100 放进 200x100：contain 只放大 1 倍，横向留白 100、纵向贴边
        val centered = imagePlacement(100f, 100f, 200f, 100f, "contain", 0.5f, 0.5f)
        assertEquals(50f, centered.left)
        assertEquals(0f, centered.top)
        val left = imagePlacement(100f, 100f, 200f, 100f, "contain", 0f, 0f)
        assertEquals(0f, left.left)
    }

    @Test
    fun `底图与按键透明度映射到运行时主题`() {
        val plain = CustomThemeStore.defaultScheme()
        val plainTheme = plain.toKeyboardColorScheme()
        assertNull(plainTheme.keyboardBackground)
        assertEquals(1f, plainTheme.keyBgLight.alpha)

        val glass = plain.copy(
            background = withImage(
                "keyOpacity" to 0.6f,
                "keyBlurEnabled" to true,
                "keyBlurRadius" to 18,
                "maskBlurEnabled" to true,
                "maskBlurRadius" to 24,
            ),
        )
        val theme = glass.toKeyboardColorScheme()
        assertEquals("image", theme.keyboardBackground?.type)
        assertEquals("/themes/backgrounds/bg.jpg", theme.keyboardBackground?.src)
        assertEquals(24, theme.keyboardBackground?.maskBlurRadius)
        assertEquals(0.6f, theme.keyBgLight.alpha)
        assertEquals(0.6f, theme.keyBgDark.alpha)
        assertTrue(theme.keyBlurEnabled)
        assertEquals(18, theme.keyBlurRadius)
        assertEquals(0.6f, theme.keyOpacity)
    }

    @Test
    fun `未开启模糊时不带模糊半径`() {
        val scheme = CustomThemeStore.defaultScheme().copy(
            background = withImage("maskBlurEnabled" to false, "maskBlurRadius" to 30),
        )
        val theme = scheme.toKeyboardColorScheme()
        assertEquals(0, theme.keyboardBackground?.maskBlurRadius)
    }

    @Test
    fun `内置主题转自定义模板不丢颜色且默认无底图`() {
        val template: CustomColorScheme = KeyboardThemes.themes.first().toCustomColorScheme("模板")
        assertEquals(ThemeBackgroundSettings(), template.background)
    }
}
