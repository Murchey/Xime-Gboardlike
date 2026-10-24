package com.kingzcheung.xime.settings

import android.content.Context

/** 字体槽位：四类键盘字体 + 全局界面字体。 */
enum class FontSlot(val prefKey: String) {
    KEY("font_key"),
    KEY_LABEL("font_key_label"),
    CANDIDATE("font_candidate"),
    COMMENT("font_comment"),
    UI("font_ui"),
}

/**
 * 字体覆盖设置（独立 SharedPreferences）。
 *
 * 存的是相对 `files/rime/` 的路径（如 `fonts/xime_kai.ttf`），由 [FontImporter] 写入。
 * 与 xime.yaml 的关系：用户选过的槽位覆盖 YAML 值，未选过则沿用 YAML。
 */
object FontPreferences {
    private const val PREFS_NAME = "font_settings"
    private const val KEY_UI_ENABLED = "font_ui_enabled"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun get(context: Context, slot: FontSlot): String =
        prefs(context).getString(slot.prefKey, "") ?: ""

    fun set(context: Context, slot: FontSlot, value: String) {
        prefs(context).edit().putString(slot.prefKey, value).apply()
    }

    fun clear(context: Context, slot: FontSlot) {
        prefs(context).edit().remove(slot.prefKey).apply()
    }

    fun clearAll(context: Context) {
        prefs(context).edit().clear().apply()
    }

    /** 界面字体是否生效（默认开启，选过字体就换整个 App 的字体）。 */
    fun isUiFontEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_UI_ENABLED, true)

    fun setUiFontEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_UI_ENABLED, enabled).apply()
    }

    /** 把用户选择叠加到 YAML 解析出的字体配置上（用户值优先）。 */
    fun applyOverrides(context: Context, base: KeyboardFontConfig): KeyboardFontConfig {
        fun pick(slot: FontSlot, fallback: String): String =
            get(context, slot).ifBlank { fallback }

        return base.copy(
            keyFont = pick(FontSlot.KEY, base.keyFont),
            keyLabelFont = pick(FontSlot.KEY_LABEL, base.keyLabelFont),
            candidateFont = pick(FontSlot.CANDIDATE, base.candidateFont),
            commentFont = pick(FontSlot.COMMENT, base.commentFont),
            uiFont = get(context, FontSlot.UI),
        )
    }
}
