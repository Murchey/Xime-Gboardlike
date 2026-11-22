package com.kingzcheung.xime.ui.keyboard

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.kingzcheung.xime.settings.FrostedGlassConfig

/** 按下时键面向文字色靠拢的比例（所有键型共用）。 */
internal const val PRESSED_KEY_TINT = 0.14f

/** 键帽最亮通道低于此值视为近黑；其他主题的深色键帽都高于此值。 */
internal const val NEAR_BLACK_KEY_PEAK = .18f

/** 近黑键帽在玻璃下的提亮倍数。 */
internal var glassNearBlackLift = 3f

/**
 * 玻璃键面颜色：键帽只做半透明，文字/图标保持主题色。
 *
 * 深色键帽不能既压暗又减透明度（#1C1C1C 的 20% 叠在黑底上会变成 #060606），
 * 所以按不透明度的倒数提亮色相、保持主题的通道比例；近黑键帽只提亮固定倍数。
 * 非玻璃主题的按下反馈统一成"向本键文字色靠 14%"。
 */
internal fun frostedKeyColor(
    background: Color,
    foreground: Color,
    config: FrostedGlassConfig,
    legacyStateColor: Color = background,
    pressed: Boolean = false,
    highlighted: Boolean = false,
    opacityScale: Float = 1f,
): Color {
    if (!config.enabled) {
        return if (pressed) lerp(background, foreground.copy(alpha = background.alpha), PRESSED_KEY_TINT)
        else legacyStateColor
    }
    val opacity = config.keyOpacity.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0.22f
    val base = if (opacity < 1f) glassKeyBase(background) else background
    val raisedTint = glassKeyTint(base, foreground, opacity, nearBlackLift = base == background)
    val feedback = when {
        pressed -> 0.08f
        highlighted -> 0.04f
        else -> 0f
    }
    val feedbackTarget = if (maxOf(
            kotlin.math.abs(raisedTint.red - foreground.red),
            kotlin.math.abs(raisedTint.green - foreground.green),
            kotlin.math.abs(raisedTint.blue - foreground.blue),
        ) < 0.05f
    ) base else foreground
    val tint = lerp(raisedTint, feedbackTarget, feedback)
    return tint.copy(alpha = opacity * opacityScale.coerceIn(0f, 1f))
}

private fun glassKeyTint(
    background: Color,
    foreground: Color,
    opacity: Float,
    nearBlackLift: Boolean = true,
): Color {
    if (opacity <= 0f || opacity >= 1f) return background
    val darkSurface = foreground.luminance() > background.luminance()
    val r = if (darkSurface) background.red else 1f - background.red
    val g = if (darkSurface) background.green else 1f - background.green
    val b = if (darkSurface) background.blue else 1f - background.blue
    val peak = maxOf(r, g, b)
    if (peak <= 0f) return background
    val lift = if (nearBlackLift && darkSurface && peak < NEAR_BLACK_KEY_PEAK) glassNearBlackLift else 1f / opacity
    val scale = minOf(lift, 1f / peak)
    fun channel(value: Float) = if (darkSurface) value * scale else 1f - value * scale
    return Color(channel(r), channel(g), channel(b), background.alpha)
}

/** 玻璃下沿用改色前的近黑键（#1C1C1C / #353535）：提亮后是一层淡灰、后面的页面透得出来。 */
internal fun glassKeyBase(color: Color): Color = when (color) {
    Color(0xFF55555A) -> Color(0xFF1C1C1C)
    Color(0xFF3A3A3C) -> Color(0xFF353535)
    else -> color
}
