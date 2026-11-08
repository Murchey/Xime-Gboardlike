package com.kingzcheung.xime.ui.theme

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.core.graphics.ColorUtils
import com.kingzcheung.xime.settings.BackgroundConfig
import com.kingzcheung.xime.settings.ColorSchemeEntry
import com.kingzcheung.xime.settings.CustomThemeStore
import com.kingzcheung.xime.settings.KeysConfigHelper
import com.kingzcheung.xime.settings.SettingsPreferences
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import kotlin.math.max

/**
 * 解析主题图片路径为 File 对象。
 * 支持四种格式（与字体配置一致）：
 * - 绝对路径（以 / 开头）：相对于应用数据根目录，如 /themes/bg.png → files/themes/bg.png
 * - rime/ 开头：相对于应用数据根目录，如 rime/themes/bg.png → files/rime/themes/bg.png
 * - 其他相对路径（包含 / 但不以 / 开头）：相对于 rime/ 目录，如 themes/bg.png → files/rime/themes/bg.png
 * - 仅文件名（不包含 /）：相对于 rime/ 目录（配置文件同目录）查找
 */
private fun resolveThemeImagePath(context: Context, src: String): File {
    val filesBase = context.filesDir.canonicalFile
    val rimeBase = File(filesBase, "rime").canonicalFile
    return when {
        src.startsWith("/") -> File(filesBase, src.removePrefix("/")).canonicalFile
        src.startsWith("rime/") -> File(filesBase, src).canonicalFile
        else -> File(rimeBase, src).canonicalFile
    }
}

/**
 * 打开主题背景图片流。
 * 优先读取用户数据目录（规则见 [resolveThemeImagePath]，用户可自行放入或通过分享导入），
 * 找不到再回退到内置 assets/<src>。
 */
fun openThemeImageStream(context: Context, src: String): InputStream? {
    val userFile = resolveThemeImagePath(context, src)
    val rimeBase = File(context.filesDir, "rime").canonicalFile
    val filesBase = context.filesDir.canonicalFile
    val withinBase = userFile.path.startsWith(rimeBase.path + File.separator) ||
        userFile.path.startsWith(filesBase.path + File.separator)
    if (userFile.isFile && withinBase) {
        return FileInputStream(userFile)
    }
    return try {
        context.assets.open(src)
    } catch (e: Exception) {
        null
    }
}

data class KeyboardColorScheme(
    val id: String,
    val name: String,
    val specialKeyLight: Color,
    val specialKeyDark: Color,
    val accentLight: Color,
    val accentDark: Color,
    val primaryLight: Color = accentLight,
    val primaryDark: Color = accentDark,
    val primaryContainerLight: Color = specialKeyLight,
    val primaryContainerDark: Color = specialKeyDark,
    val surfaceLight: Color = Color.White,
    val surfaceDark: Color = Color(0xFF1C1B1F),
    // 键盘背景色
    val keyboardBgLight: Color = surfaceLight,
    val keyboardBgDark: Color = surfaceDark,
    // 按键颜色
    val keyBgLight: Color = Color.White,
    val keyBgDark: Color = Color(0xFF4A4A4A),
    // 候选栏颜色
    val candidateBarBgLight: Color = surfaceLight,
    val candidateBarBgDark: Color = surfaceDark,
    // 按键文字颜色
    val keyTextColorLight: Color = Color(0xFF202124),
    val keyTextColorDark: Color = Color(0xFFE8EAED),
    // 候选文字颜色
    val candidateTextColorLight: Color = Color(0xFF1A73E8),
    val candidateTextColorDark: Color = Color(0xFF8AB4F8),
    // 候选选中文字颜色（未配置时回退到主题强调色）
    val candidateSelectedTextColorLight: Color = Color(0xFF8F73E2),
    val candidateSelectedTextColorDark: Color = Color(0xFFD0BCFF),
    // 分隔线颜色
    val dividerColorLight: Color = Color(0xFFDADCE0),
    val dividerColorDark: Color = Color(0xFF3C4043),

    // ── 可选主题 token（null = 沿用既有推导，未配置的主题零影响）──
    /** 按压高亮色；null 时按键帽亮度自动推导。 */
    val keyPressHighlightColorLight: Color? = null,
    val keyPressHighlightColorDark: Color? = null,
    /** 强调键（回车/确定）底色与文字；null 时沿用功能键配色。 */
    /** 功能键组（退格/字符/shift）底色；null 时由渲染层按普通键推导（暗色略亮、浅色略暗）。 */
    val functionKeyBackgroundLight: Color? = null,
    val functionKeyBackgroundDark: Color? = null,
    val accentKeyBackgroundLight: Color? = null,
    val accentKeyBackgroundDark: Color? = null,
    val accentKeyTextColorLight: Color? = null,
    val accentKeyTextColorDark: Color? = null,
    /** 长按气泡/弹窗底色与文字；null 时沿用键帽底色/键帽文字。 */
    val popupBackgroundLight: Color? = null,
    val popupBackgroundDark: Color? = null,
    val popupTextColorLight: Color? = null,
    val popupTextColorDark: Color? = null,
    /** 候选注释（拼音/字根）颜色；null 时与候选文字同色。 */
    val candidateCommentColorLight: Color? = null,
    val candidateCommentColorDark: Color? = null,

    // 背景配置（纯色/渐变/图片），比上方 flat color 字段优先级更高
    val keyboardBackground: BackgroundConfig? = null,
    val keyBackground: BackgroundConfig? = null,
    val candidateBarBackground: BackgroundConfig? = null,
    /** Material You 动态配色：颜色来自壁纸调色板，随壁纸变化。 */
    val isDynamic: Boolean = false,
    /**
     * 收录但不在主题列表展示：用于合并高度相似的历史主题。
     * 已选中的用户仍能正常使用（getThemeById 可解析），只是选择器里不再重复出现。
     */
    val hidden: Boolean = false,
    /** 是否显著标注第一个候选词；自定义配色可关闭。 */
    val highlightFirstCandidate: Boolean = true,
)

object KeyboardThemes {
    /** 从 xime.yaml 加载的配置覆盖项。 */
    private var configOverrides: Map<String, ColorSchemeEntry> = emptyMap()

    /** 已下线的旧内置主题：旧版 xime.yaml/用户部署目录里可能还残留，不再显示。 */
    private val obsoleteThemeIds = setOf(
        "gboard", "lavender_purple", "ocean_blue", "sunset_orange", "slate_gray",
        "rose_pink", "teal_cyan", "sakura_pink", "sky_blue", "mint_green",
        "lilac_purple", "peach_orange", "cream_beige", "aurora_gradient",
        "sunset_gradient", "ocean_gradient", "purple_gradient", "rose_gradient",
        "forest_gradient", "kitosonita_landscape", "mrfawdi_nature", "olivcelso_desert",
        "telegram",
    )

    /** 单套（浅色或深色）主题色。 */
    private data class ThemePalette(
        val background: Color,
        val key: Color,
        val specialKey: Color,
        val keyText: Color,
        val candidateText: Color,
        val accent: Color,
        val selectedText: Color? = null,
        val container: Color? = null,
        // 可选 token：不填则沿用推导值
        val functionKey: Color? = null,
        val pressHighlight: Color? = null,
        val accentKeyBg: Color? = null,
        val accentKeyText: Color? = null,
        val popupBg: Color? = null,
        val popupText: Color? = null,
        val commentText: Color? = null,
    )

    private fun buildTheme(
        id: String,
        name: String,
        light: ThemePalette,
        dark: ThemePalette,
        hidden: Boolean = false,
    ): KeyboardColorScheme {
        // 功能键取主题的 tinted container：既有品牌色相，又不会像高饱和 accent 那样刺眼。
        val lightSpecial = light.container ?: light.specialKey
        val darkSpecial = dark.container ?: dark.specialKey
        return KeyboardColorScheme(
            id = id,
            name = name,
            specialKeyLight = lightSpecial,
            specialKeyDark = darkSpecial,
            accentLight = light.accent,
            accentDark = dark.accent,
            primaryLight = light.accent,
            primaryDark = dark.accent,
            primaryContainerLight = lightSpecial,
            primaryContainerDark = darkSpecial,
            surfaceLight = light.background,
            surfaceDark = dark.background,
            keyboardBgLight = light.background,
            keyboardBgDark = dark.background,
            keyBgLight = light.key,
            keyBgDark = dark.key,
            candidateBarBgLight = light.background,
            candidateBarBgDark = dark.background,
            keyTextColorLight = light.keyText,
            keyTextColorDark = dark.keyText,
            // 非选中候选使用主题自己的中性灰；终末地按用户精调色单独指定。
            candidateTextColorLight = ensureContrast(light.candidateText, light.background),
            candidateTextColorDark = ensureContrast(dark.candidateText, dark.background),
            candidateSelectedTextColorLight = ensureContrast(
                light.selectedText ?: light.accent, light.background
            ),
            candidateSelectedTextColorDark = ensureContrast(
                dark.selectedText ?: dark.accent, dark.background
            ),
            functionKeyBackgroundLight = light.functionKey,
            functionKeyBackgroundDark = dark.functionKey,
            keyPressHighlightColorLight = light.pressHighlight,
            keyPressHighlightColorDark = dark.pressHighlight,
            accentKeyBackgroundLight = light.accentKeyBg,
            accentKeyBackgroundDark = dark.accentKeyBg,
            accentKeyTextColorLight = light.accentKeyText,
            accentKeyTextColorDark = dark.accentKeyText,
            popupBackgroundLight = light.popupBg,
            popupBackgroundDark = dark.popupBg,
            popupTextColorLight = light.popupText,
            popupTextColorDark = dark.popupText,
            candidateCommentColorLight = light.commentText,
            candidateCommentColorDark = dark.commentText,
            hidden = hidden,
        )
    }

    /**
     * 现代内置主题：Gboard 灰/黑、微信、QQ、终末地、X、GitHub、ChatGPT、Claude。
     *
     * 配色基于联网搜索到的品牌官方色值校准：
     * - 背景与键面使用中性灰（RGB 接近相等），消除偏色；终末地用设计语言的纸/墨色系
     * - 键面与键盘底色对比 ≥1.28:1（浅色）/ ≥1.30:1（深色），键位边界清晰
     * - 品牌强调色按官方色值（微信 #07C160、QQ #0099FF、X #1D9BF0、GitHub #0969DA、
     *   OpenAI #10A37F、Claude #D97757、终末地信号黄 #FFFA00），只用于强调/功能键
     * - 键面文字 ≥9:1（浅色普遍 12+），候选/选中 ≥4.5:1，强调色 ≥3:1
     */
    private val defaultThemes = listOf(
        // 参照 Gboard 实机配色（浅：白键 + 蓝灰功能键；深：深灰键 + 蓝灰功能键）
        buildTheme(
            id = "gboard_gray", name = "Gboard 灰",
            light = ThemePalette(
                background = Color(0xFFEDEEF2), key = Color(0xFFFFFFFF),
                keyText = Color(0xFF1F1F23), candidateText = Color(0xFF5F6368),
                accent = Color(0xFF1A73E8), selectedText = Color(0xFF0B57D0), container = Color(0xFFD7E2F4),
                specialKey = Color(0xFFD7E2F4),
                functionKey = Color(0xFFD7E2F4),
                accentKeyBg = Color(0xFFD7E2F4), accentKeyText = Color(0xFF46506B),
            ),
            dark = ThemePalette(
                background = Color(0xFF1E1F24), key = Color(0xFF38393E),
                keyText = Color(0xFFE7E8EA), candidateText = Color(0xFF9AA0A6),
                accent = Color(0xFFA8C7FA), selectedText = Color(0xFFC9DDFF), container = Color(0xFF3B4956),
                specialKey = Color(0xFF3B4956),
                functionKey = Color(0xFF3B4956),
                accentKeyBg = Color(0xFF3B4956), accentKeyText = Color(0xFFDCE6F5),
            ),
        ),
        // 参照 Gboard 黑（浅：白键 + 灰功能键 + Google 蓝回车；深：纯黑底 + 浅灰键）
        buildTheme(
            id = "gboard_black", name = "Gboard 黑",
            light = ThemePalette(
                background = Color(0xFFF1F3F7), key = Color(0xFFFFFFFF),
                keyText = Color(0xFF202124), candidateText = Color(0xFF5F6368),
                accent = Color(0xFF1A73E8), selectedText = Color(0xFF0B57D0), container = Color(0xFFE1E3E2),
                specialKey = Color(0xFFE1E3E2),
                functionKey = Color(0xFFE1E3E2),
                accentKeyBg = Color(0xFF1A73E8), accentKeyText = Color(0xFFFFFFFF),
            ),
            dark = ThemePalette(
                background = Color(0xFF161616), key = Color(0xFF4E4E50),
                keyText = Color(0xFFF2F2F2), candidateText = Color(0xFFA6A6A8),
                accent = Color(0xFF8AB4F8), selectedText = Color(0xFFA8C7FA), container = Color(0xFF282828),
                specialKey = Color(0xFF282828),
                functionKey = Color(0xFF282828),
                accentKeyBg = Color(0xFF282828), accentKeyText = Color(0xFFF2F2F2),
            ),
        ),
        buildTheme(
            id = "wechat", name = "微信",
            light = ThemePalette(
                background = Color(0xFFDCDCDC), key = Color(0xFFFFFFFF),
                keyText = Color(0xFF17191A), candidateText = Color(0xFF5A6167),
                accent = Color(0xFF059048), selectedText = Color(0xFF06703E), container = Color(0xFFD4F2E1),
                specialKey = Color(0xFFD4F2E1),
            ),
            dark = ThemePalette(
                background = Color(0xFF0A0A0A), key = Color(0xFF2E2E2E),
                keyText = Color(0xFFE7EAE8), candidateText = Color(0xFF9AA39D),
                accent = Color(0xFF2BD96B), selectedText = Color(0xFF8BF0B6), container = Color(0xFF1A3B29),
                specialKey = Color(0xFF1A3B29),
            ),
        ),
        // 与 X / GitHub 高度重合（浅色 Δ≈11，深色 Δ≈16），合并进蓝色系不再展示
        buildTheme(
            id = "qq", name = "QQ",
            hidden = true,
            light = ThemePalette(
                background = Color(0xFFDCE4EC), key = Color(0xFFFFFFFF),
                keyText = Color(0xFF14181C), candidateText = Color(0xFF57636E),
                accent = Color(0xFF0085DF), selectedText = Color(0xFF0068B3), container = Color(0xFFD6F2FD),
                specialKey = Color(0xFFD6F2FD),
            ),
            dark = ThemePalette(
                background = Color(0xFF080C10), key = Color(0xFF242C34),
                keyText = Color(0xFFE3EAF2), candidateText = Color(0xFF94A2B0),
                accent = Color(0xFF4CC9F0), selectedText = Color(0xFF9ADCF8), container = Color(0xFF16354B),
                specialKey = Color(0xFF16354B),
            ),
        ),
        buildTheme(
            id = "endfield", name = "终末地",
            light = ThemePalette(
                background = Color(0xFFFFFFFF), key = Color(0xFFE7E8EA),
                keyText = Color(0xFF1F1B12), candidateText = Color(0xFF1E1F21),
                accent = Color(0xFF202124), selectedText = Color(0xFF947600), container = Color(0xFFEED001),
                specialKey = Color(0xFFEED001),
            ),
            dark = ThemePalette(
                background = Color(0xFF000000), key = Color(0xFF38393B),
                keyText = Color(0xFFFFFCEC), candidateText = Color(0xFFF1F3F4),
                accent = Color(0xFFFFCC00), selectedText = Color(0xFFE8C000), container = Color(0xFFBF9C00),
                specialKey = Color(0xFFBF9C00),
            ),
        ),
        buildTheme(
            id = "x", name = "X",
            light = ThemePalette(
                background = Color(0xFFFFFFFF), key = Color(0xFFEFF3F4),
                keyText = Color(0xFF0F1419), candidateText = Color(0xFF536471),
                accent = Color(0xFF0F1419), selectedText = Color(0xFF536471), container = Color(0xFFEFF3F4),
                specialKey = Color(0xFFEFF3F4),
            ),
            dark = ThemePalette(
                background = Color(0xFF000000), key = Color(0xFF38383D),
                keyText = Color(0xFFE7E9EA), candidateText = Color(0xFFB0B8C0),
                accent = Color(0xFFE7E9EA), selectedText = Color(0xFF8B98A5), container = Color(0xFF16181C),
                specialKey = Color(0xFF16181C),
            ),
        ),
        buildTheme(
            id = "github", name = "GitHub",
            light = ThemePalette(
                background = Color(0xFFDCE2E8), key = Color(0xFFFFFFFF),
                keyText = Color(0xFF1F2328), candidateText = Color(0xFF525C66),
                accent = Color(0xFF1A7F37), selectedText = Color(0xFF116329), container = Color(0xFFDCFCE7),
                specialKey = Color(0xFFDCFCE7),
            ),
            dark = ThemePalette(
                background = Color(0xFF0A0E12), key = Color(0xFF242A31),
                keyText = Color(0xFFE6EDF3), candidateText = Color(0xFF98A1AB),
                accent = Color(0xFF3FB950), selectedText = Color(0xFF56D364), container = Color(0xFF1B3A26),
                specialKey = Color(0xFF1B3A26),
            ),
        ),
        // 与 GitHub 浅色 Δ≈12.5，合并进蓝色系不再展示
        buildTheme(
            id = "chatgpt", name = "ChatGPT",
            hidden = true,
            light = ThemePalette(
                background = Color(0xFFDCE2E8), key = Color(0xFFFFFFFF),
                keyText = Color(0xFF0B1F3A), candidateText = Color(0xFF566270),
                accent = Color(0xFF006EFE), selectedText = Color(0xFF0058CC), container = Color(0xFFD0E0F8),
                specialKey = Color(0xFFD0E0F8),
            ),
            dark = ThemePalette(
                background = Color(0xFF080A0C), key = Color(0xFF222628),
                keyText = Color(0xFFF2F5F8), candidateText = Color(0xFFA5ADB8),
                accent = Color(0xFF006EFE), selectedText = Color(0xFF7AB8FF), container = Color(0xFF1C2E46),
                specialKey = Color(0xFF1C2E46),
            ),
        ),
        // Pixel 中性：与 Gboard 灰 浅色 Δ≈13、强调色一致，合并隐藏
        buildTheme(
            id = "pixel", name = "Pixel",
            hidden = true,
            light = ThemePalette(
                background = Color(0xFFF1F3F4), key = Color(0xFFFFFFFF),
                keyText = Color(0xFF202124), candidateText = Color(0xFF5F6368),
                accent = Color(0xFF1A73E8), selectedText = Color(0xFF0B57D0), container = Color(0xFFE8F0FE),
                specialKey = Color(0xFFE8F0FE),
                pressHighlight = Color(0x1F000000),
                accentKeyBg = Color(0xFF1A73E8), accentKeyText = Color(0xFFFFFFFF),
                popupBg = Color(0xFFFFFFFF), popupText = Color(0xFF202124),
                commentText = Color(0xFF5F6368),
            ),
            dark = ThemePalette(
                background = Color(0xFF1E1F21), key = Color(0xFF38393B),
                keyText = Color(0xFFE8EAED), candidateText = Color(0xFF9AA0A6),
                accent = Color(0xFF8AB4F8), selectedText = Color(0xFFA8C7FA), container = Color(0xFF2D3B52),
                specialKey = Color(0xFF2D3B52),
                pressHighlight = Color(0x33FFFFFF),
                accentKeyBg = Color(0xFF8AB4F8), accentKeyText = Color(0xFF202124),
                popupBg = Color(0xFF2E3033), popupText = Color(0xFFE8EAED),
                commentText = Color(0xFF9AA0A6),
            ),
        ),
        // AMOLED：纯黑底 + 低亮度键面，深色省电、边界清楚
        buildTheme(
            id = "amoled", name = "AMOLED",
            light = ThemePalette(
                background = Color(0xFFFFFFFF), key = Color(0xFFF2F2F2),
                keyText = Color(0xFF111111), candidateText = Color(0xFF444444),
                accent = Color(0xFF1A73E8), selectedText = Color(0xFF0B57D0), container = Color(0xFFE8EEF6),
                specialKey = Color(0xFFE8EEF6),
                pressHighlight = Color(0x14000000),
                accentKeyBg = Color(0xFF1A73E8), accentKeyText = Color(0xFFFFFFFF),
                popupBg = Color(0xFFFFFFFF), popupText = Color(0xFF111111),
                commentText = Color(0xFF444444),
            ),
            dark = ThemePalette(
                background = Color(0xFF000000), key = Color(0xFF1A1A1A),
                keyText = Color(0xFFF5F5F5), candidateText = Color(0xFFD0D0D0),
                accent = Color(0xFF8AB4F8), selectedText = Color(0xFF8AB4F8), container = Color(0xFF202020),
                specialKey = Color(0xFF202020),
                pressHighlight = Color(0x26FFFFFF),
                accentKeyBg = Color(0xFF8AB4F8), accentKeyText = Color(0xFF111111),
                popupBg = Color(0xFF141414), popupText = Color(0xFFF5F5F5),
                commentText = Color(0xFFB0B0B0),
            ),
        ),
        buildTheme(
            id = "claude", name = "Claude",
            light = ThemePalette(
                background = Color(0xFFE0DDD8), key = Color(0xFFFFFFFF),
                keyText = Color(0xFF222019), candidateText = Color(0xFF5F5A4C),
                accent = Color(0xFFD05831), selectedText = Color(0xFF9E492B), container = Color(0xFFF1E6DD),
                specialKey = Color(0xFFF1E6DD),
            ),
            dark = ThemePalette(
                background = Color(0xFF0E0E0D), key = Color(0xFF2C2A28),
                keyText = Color(0xFFF2EFE7), candidateText = Color(0xFFADA89C),
                accent = Color(0xFFE58A6C), selectedText = Color(0xFFF2B49B), container = Color(0xFF402D26),
                specialKey = Color(0xFF402D26),
            ),
        ),
        // Material You（M3 基线紫）：色相与全部中性色都和现有蓝/绿/黄主题拉开
        buildTheme(
            id = "material_you", name = "Material You",
            light = ThemePalette(
                background = Color(0xFFF3EDF7), key = Color(0xFFFFFFFF),
                keyText = Color(0xFF1C1B1F), candidateText = Color(0xFF49454F),
                accent = Color(0xFF6750A4), selectedText = Color(0xFF4F378B), container = Color(0xFFEADDFF),
                specialKey = Color(0xFFEADDFF),
                pressHighlight = Color(0x1F6750A4),
                accentKeyBg = Color(0xFF6750A4), accentKeyText = Color(0xFFFFFFFF),
                popupBg = Color(0xFFFFFFFF), popupText = Color(0xFF1C1B1F),
                commentText = Color(0xFF79747E),
            ),
            dark = ThemePalette(
                background = Color(0xFF1C1B1F), key = Color(0xFF2B2930),
                keyText = Color(0xFFE6E1E5), candidateText = Color(0xFFCAC4D0),
                accent = Color(0xFFD0BCFF), selectedText = Color(0xFFEADDFF), container = Color(0xFF4F378B),
                specialKey = Color(0xFF4F378B),
                pressHighlight = Color(0x33D0BCFF),
                accentKeyBg = Color(0xFFD0BCFF), accentKeyText = Color(0xFF381E72),
                popupBg = Color(0xFF2B2930), popupText = Color(0xFFE6E1E5),
                commentText = Color(0xFF938F99),
            ),
        ),
        // Nord（fcitx5 社区经典）：蓝灰冷色调，键面与底色靠色相区分
        buildTheme(
            id = "nord", name = "Nord 北境",
            light = ThemePalette(
                background = Color(0xFFECEFF4), key = Color(0xFFFFFFFF),
                keyText = Color(0xFF2E3440), candidateText = Color(0xFF4C566A),
                accent = Color(0xFF5E81AC), selectedText = Color(0xFF4C6A92), container = Color(0xFFD8DEE9),
                specialKey = Color(0xFFD8DEE9),
                pressHighlight = Color(0x1F2E3440),
                accentKeyBg = Color(0xFF5E81AC), accentKeyText = Color(0xFFECEFF4),
                popupBg = Color(0xFFFFFFFF), popupText = Color(0xFF2E3440),
                commentText = Color(0xFF4C566A),
            ),
            dark = ThemePalette(
                background = Color(0xFF2E3440), key = Color(0xFF343B4A),
                keyText = Color(0xFFECEFF4), candidateText = Color(0xFFA3ABB8),
                accent = Color(0xFF88C0D0), selectedText = Color(0xFF8FBCBB), container = Color(0xFF434C5E),
                specialKey = Color(0xFF434C5E),
                pressHighlight = Color(0x3388C0D0),
                accentKeyBg = Color(0xFF88C0D0), accentKeyText = Color(0xFF2E3440),
                popupBg = Color(0xFF3B4252), popupText = Color(0xFFECEFF4),
                commentText = Color(0xFF8A93A5),
            ),
        ),
        // fcitx5 经典：面板式深灰 + 中性蓝强调，比 Gboard 黑更"桌面"
        buildTheme(
            id = "fcitx5_classic", name = "fcitx5 经典",
            light = ThemePalette(
                background = Color(0xFFF2F3F5), key = Color(0xFFFFFFFF),
                keyText = Color(0xFF1F2328), candidateText = Color(0xFF5B6570),
                accent = Color(0xFF3C7DD9), selectedText = Color(0xFF2E63AD), container = Color(0xFFDCE6F2),
                specialKey = Color(0xFFDCE6F2),
                pressHighlight = Color(0x1F000000),
                accentKeyBg = Color(0xFF3C7DD9), accentKeyText = Color(0xFFFFFFFF),
                popupBg = Color(0xFFFFFFFF), popupText = Color(0xFF1F2328),
                commentText = Color(0xFF5B6570),
            ),
            dark = ThemePalette(
                background = Color(0xFF1F2226), key = Color(0xFF2B2F36),
                keyText = Color(0xFFE6E9ED), candidateText = Color(0xFF9BA6B2),
                accent = Color(0xFF5B9BE8), selectedText = Color(0xFF8FC0F0), container = Color(0xFF34404E),
                specialKey = Color(0xFF34404E),
                pressHighlight = Color(0x33FFFFFF),
                accentKeyBg = Color(0xFF5B9BE8), accentKeyText = Color(0xFF101418),
                popupBg = Color(0xFF2B2F36), popupText = Color(0xFFE6E9ED),
                commentText = Color(0xFF9BA6B2),
            ),
        ),
    )


    /**
     * 预计算后的主题缓存（避免每次访问都重新计算颜色）。
     * 用 Compose 状态包装：UI getter 在组合中读取时会建立订阅，缓存重建后
     * 所有已取色的 Composable 自动重组——动态配色随壁纸变化实时刷新依赖此机制。
     */
    private var themesCache by mutableStateOf(defaultThemes)
    private var themesMapCache: Map<String, KeyboardColorScheme> = defaultThemes.associateBy { it.id }

    /** 从配置文件加载主题覆盖项。应在 Application.onCreate 中调用。 */
    fun initFromConfig(context: Context) {
        reload(context)
    }

    /** 把已不存在的旧主题 ID（如早期内置的 lavender_purple）回退到当前默认主题。 */
    fun normalizeThemeSelection(context: Context) {
        val available = themesMapCache.keys
        if (available.isEmpty()) return
        val fallbackLight = SettingsPreferences.defaultKeyboardTheme
            .takeIf { it in available }
            ?: "gboard_gray".takeIf { it in available }
            ?: available.first()
        val fallbackDark = SettingsPreferences.defaultDarkKeyboardTheme
            .takeIf { it in available }
            ?: "gboard_black".takeIf { it in available }
            ?: available.last()
        SettingsPreferences.defaultKeyboardTheme = fallbackLight
        SettingsPreferences.defaultDarkKeyboardTheme = fallbackDark
        val light = SettingsPreferences.getKeyboardThemeForMode(context, isDark = false)
        if (light !in available) {
            SettingsPreferences.setKeyboardThemeForMode(context, isDark = false, themeId = fallbackLight)
        }
        val dark = SettingsPreferences.getKeyboardThemeForMode(context, isDark = true)
        if (dark !in available) {
            SettingsPreferences.setKeyboardThemeForMode(context, isDark = true, themeId = fallbackDark)
        }
    }

    /** 重新加载 xime.yaml/xime.custom.yaml 与用户自定义配色，并更新缓存。 */
    fun reload(context: Context) {
        val customSchemes = CustomThemeStore.load(context)
        val customIds = customSchemes.map { it.id }.toSet()
        configOverrides = KeysConfigHelper.loadColorSchemes(context)
            .filterKeys { it !in obsoleteThemeIds } +
            customSchemes.associate { it.id to it.toColorSchemeEntry() }
        // 1) 对硬编码主题应用配置覆盖
        val overridden = defaultThemes.map { applyConfigOverrides(context, it) }
        // 2) 把配置中有但硬编码列表中没有的新主题也加入缓存
        //    （dynamic_color: true 的条目由 DynamicThemes 运行时从壁纸调色板构建）
        val existingIds = overridden.map { it.id }.toSet()
        val newThemes = configOverrides
            .filterKeys { it !in existingIds && it !in customIds }
            .map { (id, entry) -> buildSchemeFromConfig(context, id, entry) }
            .filterNotNull()
        // 3) 用户自定义配色直接用原始色值构建，避免再次派生覆盖深色强调色
        val customThemes = customSchemes.map { it.toKeyboardColorScheme() }
        themesCache = overridden + newThemes + customThemes
        themesMapCache = themesCache.associateBy { it.id }
    }

    /**
     * 刷新动态配色方案：对比缓存中动态主题的强调色与当前系统取色，
     * 发生变化才重建（壁纸更换后下次打开键盘调用，实现生效）。
     * 无动态主题或取色未变时直接返回 false，不触发任何重组。
     *
     * @return 是否发生了重建
     */
    fun refreshDynamicSchemes(context: Context): Boolean {
        val dynamicScheme = themesCache.firstOrNull { it.isDynamic } ?: return false
        val current = DynamicThemes.currentAccentColors(context) ?: return false
        if (dynamicScheme.accentLight == current.first && dynamicScheme.accentDark == current.second) {
            return false
        }
        var rebuilt = false
        val refreshed = themesCache.map { scheme ->
            if (scheme.isDynamic) {
                DynamicThemes.create(context, scheme.id, scheme.name)?.also { rebuilt = true } ?: scheme
            } else {
                scheme
            }
        }
        if (rebuilt) {
            themesCache = refreshed
            themesMapCache = refreshed.associateBy { it.id }
        }
        return rebuilt
    }



    /** 根据配置项创建全新的 KeyboardColorScheme；动态配色条目构建失败（不支持）返回 null。 */
    private fun buildSchemeFromConfig(context: Context, id: String, entry: ColorSchemeEntry): KeyboardColorScheme? {
        if (entry.dynamicColor) {
            return DynamicThemes.create(context, id, entry.name.ifEmpty { id })
        }
        val primary = if (entry.primaryColor != 0L) entry.primaryColor
        else extractImageSeedColor(context, entry)
        val cfgColor = longToColor(primary)
        val cfgColorDark = if (entry.primaryColorDark != 0L) longToColor(entry.primaryColorDark)
        else lightenColor(cfgColor)
        val veryLight = lightenColor(cfgColor, 0.8f)
        val global = KeysConfigHelper.getKeyboardColors()

        val kbdBg = resolveBgColor(entry, isDark = false) ?: Color.White
        val kbdBgDark = resolveBgColor(entry, isDark = true) ?: Color(0xFF1C1B1F)
        val keyBg = resolveKeyBgColor(entry, isDark = false) ?: longToColor(global.keyBgColor)
        val keyBgDark = resolveKeyBgColor(entry, isDark = true) ?: longToColor(global.keyBgColorDark)
        val txtColor = entry.keyTextColor?.let { longToColor(it) } ?: longToColor(global.keyTextColor)
        val txtColorDark = entry.keyTextColorDark?.let { longToColor(it) }
            ?: longToColor(global.keyTextColorDark)
        val candColorLight = entry.candidateTextColor?.let { longToColor(it) }
            ?: longToColor(global.candidateTextColor)
        val candColorDark = entry.candidateTextColorDark?.let { longToColor(it) }
            ?: longToColor(global.candidateTextColorDark)
        val candSelectedLight = entry.candidateSelectedTextColor?.let { longToColor(it) } ?: cfgColor
        val candSelectedDark = entry.candidateSelectedTextColorDark?.let { longToColor(it) } ?: cfgColorDark

        return KeyboardColorScheme(
            id = id,
            name = entry.name.ifEmpty { id },
            specialKeyLight = entry.specialKeyBgColor?.let { longToColor(it) } ?: veryLight,
            specialKeyDark = entry.specialKeyBgColorDark?.let { longToColor(it) } ?: cfgColor,
            accentLight = cfgColor,
            accentDark = cfgColorDark,
            primaryLight = cfgColor,
            primaryDark = cfgColorDark,
            primaryContainerLight = veryLight,
            primaryContainerDark = cfgColor,
            surfaceLight = Color.White,
            surfaceDark = Color(0xFF1C1B1F),
            keyboardBgLight = kbdBg,
            keyboardBgDark = kbdBgDark,
            keyBgLight = keyBg,
            keyBgDark = keyBgDark,
            candidateBarBgLight = kbdBg,
            candidateBarBgDark = kbdBgDark,
            keyTextColorLight = txtColor,
            keyTextColorDark = txtColorDark,
            candidateTextColorLight = candColorLight,
            candidateTextColorDark = candColorDark,
            candidateSelectedTextColorLight = candSelectedLight,
            candidateSelectedTextColorDark = candSelectedDark,
            keyboardBackground = entry.keyboardBackground,
            keyBackground = entry.keyBackground,
            candidateBarBackground = entry.candidateBarBackground,
        )
    }

    /** 从 BackgroundConfig（solid/gradient fallback）或旧 keyboardBgColor 字段解析键盘背景色。 */
    private fun resolveBgColor(entry: ColorSchemeEntry, isDark: Boolean): Color? {
        val bg = entry.keyboardBackground
        if (bg != null) {
            when (bg.type) {
                "solid" -> {
                    val hex = if (isDark) bg.colorDark ?: bg.color else bg.color
                    if (hex != null) return longToColor(hex)
                }
                "gradient" -> {
                    val hexes = if (isDark) bg.colorsDark ?: bg.colors else bg.colors
                    if (!hexes.isNullOrEmpty()) return longToColor(hexes[0])
                }
            }
        }
        return entry.keyboardBgColor?.let {
            val c = longToColor(it)
            if (isDark) darkenColor(c) else c
        }
    }

    /** 从 BackgroundConfig 或旧 keyBgColor 字段解析按键背景色。 */
    private fun resolveKeyBgColor(entry: ColorSchemeEntry, isDark: Boolean): Color? {
        val bg = entry.keyBackground
        if (bg != null) {
            when (bg.type) {
                "solid" -> {
                    val hex = if (isDark) bg.colorDark ?: bg.color else bg.color
                    if (hex != null) return longToColor(hex)
                }
                "gradient" -> {
                    val hexes = if (isDark) bg.colorsDark ?: bg.colors else bg.colors
                    if (!hexes.isNullOrEmpty()) return longToColor(hexes[0])
                }
            }
        }
        if (isDark) {
            return entry.keyBgColorDark?.let { longToColor(it) }
        }
        return entry.keyBgColor?.let { longToColor(it) }
    }

    /** 将 hex long 转为 Color。0xRRGGBB 补上 FF alpha，0xAARRGGBB 保留 alpha。 */
    /**
     * 从图片背景提取主色作为种子色。参考 Material 3 动态配色思路：
     * 解码小图 → 转 HSL → 排除接近黑白灰的像素 → 分桶统计 → 取权重最高的桶平均色。
     * 取色结果按 src+文件大小/修改时间缓存到 SharedPreferences，
     * 避免每次冷启动在主线程重复解码（图片主题没有 primary_color 时）。
     * 取色失败返回默认薰衣草紫。
     */
    private fun extractImageSeedColor(context: Context, entry: ColorSchemeEntry): Long {
        val src = entry.keyboardBackground?.takeIf { it.type == "image" }?.src
        if (src.isNullOrBlank()) return 0xFF8F73E2
        val cacheKey = themeSeedCacheKey(context, src)
        val cached = context.getSharedPreferences("theme_seed_cache", Context.MODE_PRIVATE)
            .getLong(cacheKey, 0L)
        if (cached != 0L) return cached
        val result = doExtractImageSeedColor(context, src)
        if (result != 0xFF8F73E2) {
            context.getSharedPreferences("theme_seed_cache", Context.MODE_PRIVATE)
                .edit().putLong(cacheKey, result).apply()
        }
        return result
    }

    /** 生成种子色缓存键：src + 文件大小/修改时间，文件变化后自动失效。 */
    private fun themeSeedCacheKey(context: Context, src: String): String {
        val userFile = resolveThemeImagePath(context, src)
        val file = if (userFile.isFile) userFile else null
        val size = file?.length() ?: -1L
        val mtime = file?.lastModified() ?: -1L
        return "seed_$src|$size|$mtime"
    }

    private fun doExtractImageSeedColor(context: Context, src: String): Long {
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            openThemeImageStream(context, src)?.use { BitmapFactory.decodeStream(it, null, options) }
            var sampleSize = 1
            var maxDim = max(options.outWidth, options.outHeight)
            while (maxDim / sampleSize > 64) sampleSize *= 2
            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val bitmap = openThemeImageStream(context, src)?.use { BitmapFactory.decodeStream(it, null, decodeOptions) }
                ?: return 0xFF8F73E2
            try {
                val width = bitmap.width
                val height = bitmap.height
                val pixels = IntArray(width * height)
                bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
                val hueBuckets = 16
                val satBuckets = 4
                val count = LongArray(hueBuckets * satBuckets)
                val sumR = DoubleArray(hueBuckets * satBuckets)
                val sumG = DoubleArray(hueBuckets * satBuckets)
                val sumB = DoubleArray(hueBuckets * satBuckets)
                val hsl = FloatArray(3)
                for (pixel in pixels) {
                    val r = (pixel shr 16) and 0xFF
                    val g = (pixel shr 8) and 0xFF
                    val b = pixel and 0xFF
                    ColorUtils.RGBToHSL(r, g, b, hsl)
                    val hue = hsl[0]
                    val sat = hsl[1]
                    val light = hsl[2]
                    if (sat < 0.12f || light < 0.08f || light > 0.92f) continue
                    val hi = (hue / 360f * hueBuckets).toInt().coerceIn(0, hueBuckets - 1)
                    val si = (sat * satBuckets).toInt().coerceIn(0, satBuckets - 1)
                    val idx = hi * satBuckets + si
                    count[idx]++
                    sumR[idx] += r
                    sumG[idx] += g
                    sumB[idx] += b
                }
                var bestIdx = -1
                var bestScore = 0L
                for (i in count.indices) {
                    if (count[i] == 0L) continue
                    val sat = (i % satBuckets + 1) / satBuckets.toFloat()
                    val score = count[i] * (1L + (sat * 4).toLong())
                    if (score > bestScore) {
                        bestScore = score
                        bestIdx = i
                    }
                }
                if (bestIdx < 0) return 0xFF8F73E2
                val r = (sumR[bestIdx] / count[bestIdx]).toInt().toLong()
                val g = (sumG[bestIdx] / count[bestIdx]).toInt().toLong()
                val b = (sumB[bestIdx] / count[bestIdx]).toInt().toLong()
                (r shl 16) or (g shl 8) or b
            } finally {
                bitmap.recycle()
            }
        } catch (e: Exception) {
            0xFF8F73E2
        }
    }

    /** 将 hex long 转为 Color。0xRRGGBB 补上 FF alpha，0xAARRGGBB 保留 alpha。 */
    private fun longToColor(hex: Long): Color {
        return if (hex > 0xFFFFFF) Color(hex) else Color(0xFF000000 or (hex and 0xFFFFFF))
    }

    /** 将颜色调亮（向白色混合），用于生成暗色主题下的亮色变体。 */
    private fun lightenColor(color: Color, factor: Float = 0.45f): Color {
        val r = color.red + (1f - color.red) * factor
        val g = color.green + (1f - color.green) * factor
        val b = color.blue + (1f - color.blue) * factor
        return Color(r.coerceIn(0f, 1f), g.coerceIn(0f, 1f), b.coerceIn(0f, 1f))
    }

    /** 将颜色调暗（向黑色混合），用于从亮色生成暗色变体。 */
    private fun darkenColor(color: Color, factor: Float = 0.7f): Color {
        val r = color.red * factor
        val g = color.green * factor
        val b = color.blue * factor
        return Color(r.coerceIn(0f, 1f), g.coerceIn(0f, 1f), b.coerceIn(0f, 1f))
    }

    /** 应用配置覆盖，返回覆盖后的 KeyboardColorScheme。声明 dynamic_color 的条目整体转为动态配色。 */
    private fun applyConfigOverrides(context: Context, scheme: KeyboardColorScheme): KeyboardColorScheme {
        val entry = configOverrides[scheme.id] ?: return scheme
        if (entry.dynamicColor) {
            return DynamicThemes.create(context, scheme.id, entry.name.ifEmpty { scheme.name })
                ?: scheme
        }
        val primary = if (entry.primaryColor != 0L) entry.primaryColor
        else extractImageSeedColor(context, entry)
        val cfgColor = longToColor(primary)
        val cfgColorDark = if (entry.primaryColorDark != 0L) longToColor(entry.primaryColorDark)
        else lightenColor(cfgColor)
        val global = KeysConfigHelper.getKeyboardColors()
        return scheme.copy(
            name = entry.name.ifEmpty { scheme.name },
            specialKeyLight = entry.specialKeyBgColor?.let { longToColor(it) } ?: scheme.specialKeyLight,
            specialKeyDark = entry.specialKeyBgColorDark?.let { longToColor(it) } ?: scheme.specialKeyDark,
            accentLight = cfgColor,
            accentDark = cfgColorDark,
            primaryLight = cfgColor,
            primaryDark = cfgColorDark,
            keyboardBgLight = resolveBgColor(entry, isDark = false) ?: scheme.keyboardBgLight,
            keyboardBgDark = resolveBgColor(entry, isDark = true) ?: scheme.keyboardBgDark,
            keyBgLight = resolveKeyBgColor(entry, isDark = false) ?: longToColor(global.keyBgColor),
            keyBgDark = resolveKeyBgColor(entry, isDark = true) ?: longToColor(global.keyBgColorDark),
            candidateBarBgLight = resolveBgColor(entry, isDark = false) ?: scheme.candidateBarBgLight,
            candidateBarBgDark = resolveBgColor(entry, isDark = true) ?: scheme.candidateBarBgDark,
            keyTextColorLight = entry.keyTextColor?.let { longToColor(it) } ?: longToColor(global.keyTextColor),
            keyTextColorDark = entry.keyTextColorDark?.let { longToColor(it) }
                ?: longToColor(global.keyTextColorDark),
            candidateTextColorLight = entry.candidateTextColor?.let { longToColor(it) }
                ?: longToColor(global.candidateTextColor),
            candidateTextColorDark = entry.candidateTextColorDark?.let { longToColor(it) }
                ?: longToColor(global.candidateTextColorDark),
            candidateSelectedTextColorLight = entry.candidateSelectedTextColor?.let { longToColor(it) }
                ?: cfgColor,
            candidateSelectedTextColorDark = entry.candidateSelectedTextColorDark?.let { longToColor(it) }
                ?: cfgColorDark,
            keyboardBackground = entry.keyboardBackground ?: scheme.keyboardBackground,
            keyBackground = entry.keyBackground ?: scheme.keyBackground,
            candidateBarBackground = entry.candidateBarBackground ?: scheme.candidateBarBackground,
        )
    }

    /** 预计算后的主题列表（组合中读取会订阅缓存重建）。 */
    val themes: List<KeyboardColorScheme>
        get() = themesCache

    fun getThemeById(id: String): KeyboardColorScheme {
        // 先读一次状态化缓存建立 Compose 订阅，动态配色重建后 UI 自动重组
        val cache = themesCache
        return themesMapCache[id] ?: cache[0]
    }

    fun getSpecialKeyColor(themeId: String, isDark: Boolean): Color {
        val theme = getThemeById(themeId)
        return if (isDark) theme.specialKeyDark else theme.specialKeyLight
    }

    fun getAccentColor(themeId: String, isDark: Boolean): Color {
        val theme = getThemeById(themeId)
        return if (isDark) theme.accentDark else theme.accentLight
    }

    /** 特殊键文字颜色：暗色用强调色，亮色用更深的版本以提高对比度。 */
    fun getSpecialKeyTextColor(themeId: String, isDark: Boolean): Color {
        val accent = getAccentColor(themeId, isDark)
        return if (isDark) accent else Color(
            red = accent.red * 0.6f, green = accent.green * 0.6f, blue = accent.blue * 0.6f
        )
    }

    /** 非选中候选词的中性灰（浅色更深、深色更浅），主题色只保留给选中项。 */
    fun neutralCandidateTextColor(isDark: Boolean): Color =
        if (isDark) Color(0xFF9AA0A6) else Color(0xFF5F6368)

    /** WCAG 对比度（1..21）。 */
    fun contrastRatio(foreground: Color, background: Color): Float {
        val a = foreground.luminance()
        val b = background.luminance()
        val lighter = maxOf(a, b)
        val darker = minOf(a, b)
        return (lighter + 0.05f) / (darker + 0.05f)
    }

    /**
     * 保持色相，向黑/白插值，直到达到 [minRatio] 对比度。
     * 候选字、注释、选中色统一走这条逻辑，避免品牌色在浅底上「几乎不可见」。
     */
    fun ensureContrast(
        foreground: Color,
        background: Color,
        minRatio: Float = 4.5f,
    ): Color {
        if (contrastRatio(foreground, background) >= minRatio) return foreground
        val target = if (background.luminance() > 0.5f) Color.Black else Color.White
        var low = 0f
        var high = 1f
        repeat(12) {
            val mid = (low + high) / 2f
            if (contrastRatio(lerp(foreground, target, mid), background) >= minRatio) {
                high = mid
            } else {
                low = mid
            }
        }
        return lerp(foreground, target, high)
    }

    /**
     * 特殊键（确定/删除等）文字颜色：按功能键背景对比度自适应——
     * 亮背景优先 [onLightBgText]（通常为皮肤按键文字色），暗背景配柔和亮白
     * （非纯白，高对比在暗光下刺眼）。实机各布局与主题预览共用此逻辑保证一致。
     */
    fun getSpecialKeyTextColorForBackground(bgColor: Color, onLightBgText: Color): Color {
        val softWhite = Color(0xFFE8EAED)
        val chosen = if (contrastRatio(onLightBgText, bgColor) >= contrastRatio(softWhite, bgColor)) {
            onLightBgText
        } else {
            softWhite
        }
        // 设计约束：深色背景不用纯白；即使对比度略高也回落柔和亮白。
        return if (chosen == Color.White) softWhite else chosen
    }

    fun getPrimaryColor(themeId: String, isDark: Boolean): Color {
        val theme = getThemeById(themeId)
        return if (isDark) theme.primaryDark else theme.primaryLight
    }

    fun getPrimaryContainerColor(themeId: String, isDark: Boolean): Color {
        val theme = getThemeById(themeId)
        return if (isDark) theme.primaryContainerDark else theme.primaryContainerLight
    }

    fun getSurfaceColor(themeId: String, isDark: Boolean): Color {
        val theme = getThemeById(themeId)
        return if (isDark) theme.surfaceDark else theme.surfaceLight
    }

    fun getKeyboardBackgroundColor(themeId: String, isDark: Boolean): Color {
        val theme = getThemeById(themeId)
        return if (isDark) theme.keyboardBgDark else theme.keyboardBgLight
    }

    fun getKeyBackgroundColor(themeId: String, isDark: Boolean): Color {
        val theme = getThemeById(themeId)
        return if (isDark) theme.keyBgDark else theme.keyBgLight
    }

    fun getCandidateBarBackgroundColor(themeId: String, isDark: Boolean): Color {
        val theme = getThemeById(themeId)
        return if (isDark) theme.candidateBarBgDark else theme.candidateBarBgLight
    }

    fun getKeyTextColor(themeId: String, isDark: Boolean): Color {
        val theme = getThemeById(themeId)
        return if (isDark) theme.keyTextColorDark else theme.keyTextColorLight
    }

    fun getCandidateTextColor(themeId: String, isDark: Boolean): Color {
        val theme = getThemeById(themeId)
        return if (isDark) theme.candidateTextColorDark else theme.candidateTextColorLight
    }

    /** 候选选中文字色，未显式配置时回退到按键文字色。 */
    fun getCandidateSelectedTextColor(themeId: String, isDark: Boolean): Color {
        val theme = getThemeById(themeId)
        return if (isDark) theme.candidateSelectedTextColorDark else theme.candidateSelectedTextColorLight
    }

    fun getDividerColor(themeId: String, isDark: Boolean): Color {
        val theme = getThemeById(themeId)
        return if (isDark) theme.dividerColorDark else theme.dividerColorLight
    }

    /** 从状态化缓存取主题颜色，建立 Compose 订阅；主题不存在时返回 null。 */
    private inline fun themeColor(themeId: String, selector: (KeyboardColorScheme) -> Color?): Color? {
        val theme = themesCache.firstOrNull { it.id == themeId } ?: return null
        return selector(theme)
    }

    /** 按键背景色：内置/配置/自定义主题都以缓存中的最终色值为准。 */
    fun getKeyBgColorOverride(themeId: String, isDark: Boolean): Color? =
        themeColor(themeId) { if (isDark) it.keyBgDark else it.keyBgLight }

    /** 按键文字色：内置/配置/自定义主题都以缓存中的最终色值为准。 */
    fun getKeyTextColorOverride(themeId: String, isDark: Boolean): Color? =
        themeColor(themeId) { if (isDark) it.keyTextColorDark else it.keyTextColorLight }

    /** 候选文字色：内置/配置/自定义主题都以缓存中的最终色值为准。 */
    fun getCandidateTextColorOverride(themeId: String, isDark: Boolean): Color? =
        themeColor(themeId) { if (isDark) it.candidateTextColorDark else it.candidateTextColorLight }

    /** 候选选中文字色：内置/配置/自定义主题都以缓存中的最终色值为准。 */
    fun getCandidateSelectedTextColorOverride(themeId: String, isDark: Boolean): Color? =
        themeColor(themeId) { if (isDark) it.candidateSelectedTextColorDark else it.candidateSelectedTextColorLight }

    // ── 可选 token（null = 沿用既有推导，旧主题零影响）──

    /** 按压高亮色；null 时按键帽亮度自动推导。 */
    fun getKeyPressHighlightColor(themeId: String, isDark: Boolean): Color? =
        themeColor(themeId) { if (isDark) it.keyPressHighlightColorDark else it.keyPressHighlightColorLight }

    /** 强调键（回车）底色；null 时沿用功能键底色。 */
    fun getAccentKeyBackground(themeId: String, isDark: Boolean): Color? =
        themeColor(themeId) { if (isDark) it.accentKeyBackgroundDark else it.accentKeyBackgroundLight }

    /** 功能键组（退格/字符/shift）底色；null 时渲染层按普通键推导。 */
    fun getFunctionKeyBackground(themeId: String, isDark: Boolean): Color? =
        themeColor(themeId) { if (isDark) it.functionKeyBackgroundDark else it.functionKeyBackgroundLight }

    /**
     * 功能键组最终底色：主题 token 优先，否则按 Gboard 层次推导——
     * 暗色键面略提亮、浅色键面略压暗，保证两种模式都能看出分组。
     * 预览与实际键盘共用本函数，避免两处推导漂移。
     */
    fun resolveFunctionKeyColor(themeId: String, isDark: Boolean, keyBg: Color): Color =
        getFunctionKeyBackground(themeId, isDark)
            ?: if (isDark) lerp(keyBg, Color.White, 0.10f)
            else lerp(keyBg, Color.Black, 0.06f)

    /** 回车键底色：主题 token 优先，否则用主题强调色（Gboard 行为）。 */
    fun resolveEnterKeyColor(themeId: String, isDark: Boolean): Color =
        getAccentKeyBackground(themeId, isDark) ?: getAccentColor(themeId, isDark)

    /**
     * 回车键图标/文字色：主题显式 token 优先；否则按回车键底色的亮度取黑或白。
     *
     * 此前直接用功能键文字色推导，X 主题的强调色与功能键同色系时，
     * 图标会和底色融成一片——这里改为只依赖回车键自身底色。
     */
    fun highlightFirstCandidate(themeId: String): Boolean {
        val scheme = themesCache.firstOrNull { it.id == themeId } ?: return true
        return scheme.highlightFirstCandidate
    }

    /** 按方案对象推导功能键组颜色（不查缓存，模板转换等场景可用）。 */
    fun resolveFunctionKeyColor(scheme: KeyboardColorScheme, isDark: Boolean): Color {
        val keyBg = if (isDark) scheme.keyBgDark else scheme.keyBgLight
        val token = if (isDark) scheme.functionKeyBackgroundDark else scheme.functionKeyBackgroundLight
        return token ?: if (isDark) lerp(keyBg, Color.White, 0.10f) else lerp(keyBg, Color.Black, 0.06f)
    }

    /** 按方案对象推导回车键颜色（token 优先，否则用强调色）。 */
    fun resolveEnterKeyColor(scheme: KeyboardColorScheme, isDark: Boolean): Color =
        (if (isDark) scheme.accentKeyBackgroundDark else scheme.accentKeyBackgroundLight)
            ?: (if (isDark) scheme.accentDark else scheme.accentLight)

    fun resolveEnterKeyTextColor(themeId: String, isDark: Boolean): Color {
        val bg = resolveEnterKeyColor(themeId, isDark)
        getAccentKeyTextColor(themeId, isDark)?.let { return it }
        return if (bg.luminance() > 0.5f) Color(0xFF1F1F23) else Color(0xFFF2F5F8)
    }

    /** 强调键文字色；null 时沿用功能键文字色。 */
    fun getAccentKeyTextColor(themeId: String, isDark: Boolean): Color? =
        themeColor(themeId) { if (isDark) it.accentKeyTextColorDark else it.accentKeyTextColorLight }

    /** 气泡/弹窗底色；null 时沿用键帽底色。 */
    fun getPopupBackground(themeId: String, isDark: Boolean): Color? =
        themeColor(themeId) { if (isDark) it.popupBackgroundDark else it.popupBackgroundLight }

    /** 气泡/弹窗文字色；null 时沿用键帽文字色。 */
    fun getPopupTextColor(themeId: String, isDark: Boolean): Color? =
        themeColor(themeId) { if (isDark) it.popupTextColorDark else it.popupTextColorLight }

    /** 候选注释颜色；null 时与候选文字同色（并做对比度校正）。 */
    fun getCandidateCommentColor(themeId: String, isDark: Boolean): Color? =
        themeColor(themeId) { if (isDark) it.candidateCommentColorDark else it.candidateCommentColorLight }
}
