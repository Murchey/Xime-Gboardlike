package com.kingzcheung.xime.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.kingzcheung.xime.settings.BackgroundConfig
import com.kingzcheung.xime.settings.ColorSchemeEntry
import com.kingzcheung.xime.settings.CustomColorScheme
import com.kingzcheung.xime.settings.CustomThemeStore
import com.kingzcheung.xime.settings.ThemeColorSet

/** 自定义配色 → 运行时主题（预览与键盘共用，避免两套取色逻辑）。 */
fun CustomColorScheme.toKeyboardColorScheme(): KeyboardColorScheme {
    val lightAccent = light.accent.toComposeColor()
    val darkAccent = dark.accent.toComposeColor()
    val bg = background.normalized()
    val opacity = bg.keyOpacity
    val lightBgLong = light.keyboardBackground.toHexLong()
    val darkBgLong = dark.keyboardBackground.toHexLong()
    // 有底图时把纯色降级为兜底色（图片解码失败/路径失效时仍能看）
    val imageBackground = if (bg.hasImage) {
        BackgroundConfig(
            type = "image",
            src = bg.imagePath,
            srcDark = bg.imagePath,
            fit = bg.fit,
            color = lightBgLong,
            colorDark = darkBgLong,
            overlayAlpha = bg.overlayAlpha,
            overlayAlphaDark = bg.overlayAlpha,
            offsetX = bg.offsetX,
            offsetY = bg.offsetY,
            maskBlurRadius = if (bg.maskBlurEnabled) bg.maskBlurRadius else 0,
        )
    } else {
        null
    }
    return KeyboardColorScheme(
        id = id,
        name = name,
        highlightFirstCandidate = highlightFirstCandidate,
        // 按键透明度：统一乘到键帽底色上（功能键/强调键同口径），文字与图标不受影响
        specialKeyLight = light.functionKeyBackground.toComposeColor().withOpacity(opacity),
        specialKeyDark = dark.functionKeyBackground.toComposeColor().withOpacity(opacity),
        // 自定义主题的「功能键背景」直接驱动 shift/退格/字符键分组
        functionKeyBackgroundLight = light.functionKeyBackground.toComposeColor().withOpacity(opacity),
        functionKeyBackgroundDark = dark.functionKeyBackground.toComposeColor().withOpacity(opacity),
        accentLight = lightAccent,
        accentDark = darkAccent,
        primaryLight = lightAccent,
        primaryDark = darkAccent,
        primaryContainerLight = light.functionKeyBackground.toComposeColor().withOpacity(opacity),
        primaryContainerDark = dark.functionKeyBackground.toComposeColor().withOpacity(opacity),
        surfaceLight = light.keyboardBackground.toComposeColor(),
        surfaceDark = dark.keyboardBackground.toComposeColor(),
        keyboardBgLight = light.keyboardBackground.toComposeColor(),
        keyboardBgDark = dark.keyboardBackground.toComposeColor(),
        keyBgLight = light.keyBackground.toComposeColor().withOpacity(opacity),
        keyBgDark = dark.keyBackground.toComposeColor().withOpacity(opacity),
        candidateBarBgLight = light.keyboardBackground.toComposeColor(),
        candidateBarBgDark = dark.keyboardBackground.toComposeColor(),
        keyTextColorLight = light.keyText.toComposeColor(),
        keyTextColorDark = dark.keyText.toComposeColor(),
        candidateTextColorLight = light.candidateText.toComposeColor(),
        candidateTextColorDark = dark.candidateText.toComposeColor(),
        // 关闭"强调第一个候选"时，选中色回退为普通候选色（仍有底色以外的最小标识）
        candidateSelectedTextColorLight = if (highlightFirstCandidate) lightAccent else light.candidateText.toComposeColor(),
        candidateSelectedTextColorDark = if (highlightFirstCandidate) darkAccent else dark.candidateText.toComposeColor(),
        // 回车键：面板里单独配过就用配置值，否则留空跟随强调色
        accentKeyBackgroundLight = light.enterKeyBackground.takeIf { it.isNotBlank() }
            ?.toComposeColor()?.withOpacity(opacity),
        accentKeyBackgroundDark = dark.enterKeyBackground.takeIf { it.isNotBlank() }
            ?.toComposeColor()?.withOpacity(opacity),
        accentKeyTextColorLight = light.enterKeyIconColor.takeIf { it.isNotBlank() }?.toComposeColor(),
        accentKeyTextColorDark = dark.enterKeyIconColor.takeIf { it.isNotBlank() }?.toComposeColor(),
        keyboardBackground = imageBackground,
        keyOpacity = opacity,
        keyBlurEnabled = bg.keyBlurEnabled,
        keyBlurRadius = bg.keyBlurRadius,
    )
}

/** 按键透明度：乘到颜色自身的 alpha 上（0 = 全透明，1 = 保持原样）。 */
private fun Color.withOpacity(opacity: Float): Color =
    copy(alpha = (alpha * opacity.coerceIn(0f, 1f)).coerceIn(0f, 1f))

/** 自定义配色 → xime.yaml 同构的 color_schemes 条目，供既有的取色覆盖逻辑复用。 */
fun CustomColorScheme.toColorSchemeEntry(): ColorSchemeEntry {
    val lightBg = light.keyboardBackground.toHexLong()
    val darkBg = dark.keyboardBackground.toHexLong()
    return ColorSchemeEntry(
        name = name,
        primaryColor = light.accent.toHexLong(),
        primaryColorDark = dark.accent.toHexLong(),
        keyboardBgColor = lightBg,
        keyBgColor = light.keyBackground.toHexLong(),
        keyBgColorDark = dark.keyBackground.toHexLong(),
        specialKeyBgColor = light.functionKeyBackground.toHexLong(),
        specialKeyBgColorDark = dark.functionKeyBackground.toHexLong(),
        candidateBarBgColor = lightBg,
        keyTextColor = light.keyText.toHexLong(),
        keyTextColorDark = dark.keyText.toHexLong(),
        candidateTextColor = light.candidateText.toHexLong(),
        candidateTextColorDark = dark.candidateText.toHexLong(),
        candidateSelectedTextColor = light.accent.toHexLong(),
        candidateSelectedTextColorDark = dark.accent.toHexLong(),
        keyboardBackground = if (background.hasImage) {
            BackgroundConfig(
                type = "image",
                src = background.imagePath,
                srcDark = background.imagePath,
                fit = background.fit,
                overlayAlpha = background.overlayAlpha,
                overlayAlphaDark = background.overlayAlpha,
                offsetX = background.offsetX,
                offsetY = background.offsetY,
                maskBlurRadius = if (background.maskBlurEnabled) background.maskBlurRadius else 0,
                color = lightBg,
                colorDark = darkBg,
            )
        } else {
            BackgroundConfig(type = "solid", color = lightBg, colorDark = darkBg)
        },
    )
}

/**
 * 内置主题 → 自定义配色模板：把**实际渲染用的**浅/深色板完整带入。
 *
 * 关键点：功能键组与回车键在键盘上是经 [KeyboardThemes.resolveFunctionKeyColor] /
 * [KeyboardThemes.resolveEnterKeyColor] 推导出来的，直接读 specialKey/accent token
 * 会让模板颜色和键盘实际颜色对不上（此前就是这个问题）。
 */
fun KeyboardColorScheme.toCustomColorScheme(name: String): CustomColorScheme = CustomColorScheme(
    id = CustomThemeStore.newId(),
    name = name,
    light = ThemeColorSet(
        keyboardBackground = keyboardBgLight.toHexString(),
        keyBackground = keyBgLight.toHexString(),
        functionKeyBackground = KeyboardThemes.resolveFunctionKeyColor(this, false).toHexString(),
        keyText = keyTextColorLight.toHexString(),
        candidateText = candidateTextColorLight.toHexString(),
        accent = accentLight.toHexString(),
        // 回车键取键盘实际渲染色，保证模板与真实效果一致
        enterKeyBackground = KeyboardThemes.resolveEnterKeyColor(this, false).toHexString(),
        enterKeyIconColor = accentKeyTextColorLight?.toHexString().orEmpty(),
    ),
    dark = ThemeColorSet(
        keyboardBackground = keyboardBgDark.toHexString(),
        keyBackground = keyBgDark.toHexString(),
        functionKeyBackground = KeyboardThemes.resolveFunctionKeyColor(this, true).toHexString(),
        keyText = keyTextColorDark.toHexString(),
        candidateText = candidateTextColorDark.toHexString(),
        accent = accentDark.toHexString(),
        enterKeyBackground = KeyboardThemes.resolveEnterKeyColor(this, true).toHexString(),
        enterKeyIconColor = accentKeyTextColorDark?.toHexString().orEmpty(),
    ),
)

private fun Color.toHexString(): String =
    CustomThemeStore.formatHexColor(toArgb().toLong() and 0xFFFFFFFFL)

private fun String.toHexLong(): Long =
    CustomThemeStore.parseHexColor(this) ?: 0xFF000000L

private fun String.toComposeColor(): Color = Color(toHexLong())
