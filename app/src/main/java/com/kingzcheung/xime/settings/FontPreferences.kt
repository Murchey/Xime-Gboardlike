package com.kingzcheung.xime.settings

import android.content.Context
import com.kingzcheung.xime.ui.keyboard.AppFonts

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

    /** 五个槽位写入同一值（系统默认 / 导入字体路径）。 */
    fun setAll(context: Context, value: String) {
        prefs(context).edit().apply {
            FontSlot.entries.forEach { putString(it.prefKey, value) }
        }.apply()
    }

    /** 当前全局字体：以按键主字槽位为准；空值/旧内置字体统一回退系统字体。 */
    fun currentGlobalSelection(context: Context): String =
        AppFonts.normalizeSelection(get(context, FontSlot.KEY))

    fun clearAll(context: Context) {
        prefs(context).edit().clear().apply()
    }

    /** 旧版内置字体已从 APK 移除：把持久化的 @builtin 迁移为 @system。 */
    fun migrateLegacyBuiltin(context: Context) {
        val p = prefs(context)
        val editor = p.edit()
        var changed = false
        for (slot in FontSlot.entries) {
            if (p.getString(slot.prefKey, "") == AppFonts.BUILTIN_SENTINEL) {
                editor.putString(slot.prefKey, AppFonts.SYSTEM_SENTINEL)
                changed = true
            }
        }
        if (changed) editor.apply()
    }

    /** 界面字体是否生效（默认开启，选过字体就换整个 App 的字体）。 */
    fun isUiFontEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_UI_ENABLED, true)

    fun setUiFontEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_UI_ENABLED, enabled).apply()
    }

    /** 把用户选择叠加到 YAML 解析出的字体配置上（用户值优先）。 */
    fun applyOverrides(context: Context, base: KeyboardFontConfig): KeyboardFontConfig {
        // 未选字体 / 旧 @builtin 统一跟随系统；导入字体路径原样使用
        fun pick(slot: FontSlot): String = AppFonts.normalizeSelection(get(context, slot))

        return base.copy(
            keyFont = pick(FontSlot.KEY),
            keyLabelFont = pick(FontSlot.KEY_LABEL),
            candidateFont = pick(FontSlot.CANDIDATE),
            commentFont = pick(FontSlot.COMMENT),
            uiFont = pick(FontSlot.UI),
        )
    }
}
