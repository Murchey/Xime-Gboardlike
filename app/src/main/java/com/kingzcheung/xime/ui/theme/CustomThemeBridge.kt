package com.kingzcheung.xime.ui.theme

import androidx.compose.ui.graphics.Color
import com.kingzcheung.xime.settings.BackgroundConfig
import com.kingzcheung.xime.settings.ColorSchemeEntry
import com.kingzcheung.xime.settings.CustomColorScheme
import com.kingzcheung.xime.settings.CustomThemeStore

/** 自定义配色 → 运行时主题（预览与键盘共用，避免两套取色逻辑）。 */
fun CustomColorScheme.toKeyboardColorScheme(): KeyboardColorScheme {
    val lightAccent = light.accent.toComposeColor()
    val darkAccent = dark.accent.toComposeColor()
    return KeyboardColorScheme(
        id = id,
        name = name,
        specialKeyLight = light.functionKeyBackground.toComposeColor(),
        specialKeyDark = dark.functionKeyBackground.toComposeColor(),
        accentLight = lightAccent,
        accentDark = darkAccent,
        primaryLight = lightAccent,
        primaryDark = darkAccent,
        primaryContainerLight = light.functionKeyBackground.toComposeColor(),
        primaryContainerDark = dark.functionKeyBackground.toComposeColor(),
        surfaceLight = light.keyboardBackground.toComposeColor(),
        surfaceDark = dark.keyboardBackground.toComposeColor(),
        keyboardBgLight = light.keyboardBackground.toComposeColor(),
        keyboardBgDark = dark.keyboardBackground.toComposeColor(),
        keyBgLight = light.keyBackground.toComposeColor(),
        keyBgDark = dark.keyBackground.toComposeColor(),
        candidateBarBgLight = light.keyboardBackground.toComposeColor(),
        candidateBarBgDark = dark.keyboardBackground.toComposeColor(),
        keyTextColorLight = light.keyText.toComposeColor(),
        keyTextColorDark = dark.keyText.toComposeColor(),
        candidateTextColorLight = light.candidateText.toComposeColor(),
        candidateTextColorDark = dark.candidateText.toComposeColor(),
        candidateSelectedTextColorLight = lightAccent,
        candidateSelectedTextColorDark = darkAccent,
    )
}

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
        keyboardBackground = BackgroundConfig(
            type = "solid",
            color = lightBg,
            colorDark = darkBg,
        ),
    )
}

private fun String.toHexLong(): Long =
    CustomThemeStore.parseHexColor(this) ?: 0xFF000000L

private fun String.toComposeColor(): Color = Color(toHexLong())
