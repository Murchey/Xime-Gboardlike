package com.kingzcheung.xime.settings

/**
 * 玻璃材质参数（键面/背景各自的不透明度 + 背景模糊半径）。
 *
 * 与键盘绘制共用：键面半透明按 [keyOpacity]；深色键帽在玻璃下按自己的提亮规则保持层次，
 * 详见 `ui/keyboard/FrostedKeyColors.kt`。
 */
data class FrostedGlassConfig(
    val enabled: Boolean = false,
    val blurRadiusDp: Float = 10f,
    val backgroundOpacity: Float = 0.8f,
    val keyOpacity: Float = 0.2f,
    /** 只对浅色外观：用户手动调过背景不透明度后，白色玻璃不再自动补厚。 */
    val lightPanelCustom: Boolean = false,
) {
    fun normalized(defaults: FrostedGlassConfig = defaults(true)): FrostedGlassConfig = copy(
        blurRadiusDp = (blurRadiusDp.takeIf { it.isFinite() } ?: defaults.blurRadiusDp).coerceIn(0f, 40f),
        backgroundOpacity = (backgroundOpacity.takeIf { it.isFinite() } ?: defaults.backgroundOpacity).coerceIn(0f, 1f),
        keyOpacity = (keyOpacity.takeIf { it.isFinite() } ?: defaults.keyOpacity).coerceIn(0f, 1f),
    )

    companion object {
        fun defaults(isDark: Boolean, enabled: Boolean = false) = FrostedGlassConfig(
            enabled = enabled,
            blurRadiusDp = 10f,
            backgroundOpacity = if (isDark) 0.8f else 0.2f,
            keyOpacity = if (isDark) 0.2f else 0.8f,
        )
    }
}

/** 玻璃开关与两档不透明度、模糊半径的读写（浅色/深色分开存）。 */
object FrostedGlassPreferences {
    const val KEY_ENABLED = "frosted_glass_enabled"
    const val KEY_BLUR_RADIUS = "frosted_glass_blur_radius_dp"
    const val KEY_BACKGROUND_OPACITY = "frosted_glass_background_opacity"
    const val KEY_KEY_OPACITY = "frosted_glass_key_opacity"

    private fun appearanceKey(key: String, dark: Boolean) = key + if (dark) "_dark" else "_light"

    fun read(context: android.content.Context, isDark: Boolean): FrostedGlassConfig {
        val prefs = SettingsPreferences.getPrefsPublic(context)
        val enabled = prefs.getBoolean(KEY_ENABLED, false)
        val defaults = FrostedGlassConfig.defaults(isDark, enabled)
        return FrostedGlassConfig(
            enabled = enabled,
            blurRadiusDp = prefs.getFloat(appearanceKey(KEY_BLUR_RADIUS, isDark), defaults.blurRadiusDp),
            backgroundOpacity = prefs.getFloat(appearanceKey(KEY_BACKGROUND_OPACITY, isDark), defaults.backgroundOpacity),
            keyOpacity = prefs.getFloat(appearanceKey(KEY_KEY_OPACITY, isDark), defaults.keyOpacity),
            lightPanelCustom = !isDark && prefs.getBoolean("frosted_glass_light_panel_custom", false),
        ).normalized(defaults)
    }

    fun writeEnabled(context: android.content.Context, enabled: Boolean) {
        SettingsPreferences.getPrefsPublic(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun writeOpacities(
        context: android.content.Context,
        isDark: Boolean,
        keyOpacity: Float? = null,
        backgroundOpacity: Float? = null,
        blurRadiusDp: Float? = null,
    ) {
        val editor = SettingsPreferences.getPrefsPublic(context).edit()
        keyOpacity?.let { editor.putFloat(appearanceKey(KEY_KEY_OPACITY, isDark), it) }
        backgroundOpacity?.let { editor.putFloat(appearanceKey(KEY_BACKGROUND_OPACITY, isDark), it) }
        blurRadiusDp?.let { editor.putFloat(appearanceKey(KEY_BLUR_RADIUS, isDark), it) }
        editor.apply()
    }
}
