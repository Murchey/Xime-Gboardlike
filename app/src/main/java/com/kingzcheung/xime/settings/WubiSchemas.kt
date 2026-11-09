package com.kingzcheung.xime.settings

/**
 * 判断当前方案是否属于五笔族（五笔连打只对这类方案有意义）。
 *
 * 判定依据是方案 id 前缀 + 方案自身的 `speller/algebra` 里是否出现五笔式的
 * 四码定长规则；不看中文名（用户可自行改名）。
 */
object WubiSchemas {

    fun isWubi(schemaId: String): Boolean {
        if (schemaId.isBlank()) return false
        // 允许方案包自带前缀（如 rime_frost_wubi86），按"包含 wubi"判定更稳
        return schemaId.lowercase().contains("wubi")
    }

    /** 是否应对该方案启用连打（用户开关 + 方案是五笔族）。 */
    fun isChainedEnabledFor(context: android.content.Context, schemaId: String): Boolean =
        isWubi(schemaId) && SettingsPreferences.isWubiChainedInput(context)
}
