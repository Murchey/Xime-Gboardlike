package com.kingzcheung.xime.settings

import android.content.Context
import java.io.File

/** 模糊音选择（按方案保存）。 */
object FuzzyPinyinPreferences {
    private const val PREFS = "fuzzy_pinyin"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun key(schemaId: String) = "enabled_" + schemaId.replace('/', '_')

    fun enabled(context: Context, schemaId: String): Set<String> =
        prefs(context).getStringSet(key(schemaId), emptySet()) ?: emptySet()

    fun setEnabled(context: Context, schemaId: String, ids: Set<String>) {
        prefs(context).edit().putStringSet(key(schemaId), ids).apply()
    }
}

object FuzzyPinyinStore {

    fun customFile(context: Context, schemaId: String): File =
        File(SchemaManager.getRimeDir(context), "$schemaId.custom.yaml")

    /** 写入/清除当前方案的模糊音 patch，返回是否成功。 */
    fun apply(context: Context, schemaId: String, enabledIds: Set<String>): Boolean {
        return try {
            val file = customFile(context, schemaId)
            val existing = if (file.exists()) file.readText() else null
            val updated = FuzzyPinyinPatch.apply(existing, FuzzyPinyinRules.rulesFor(enabledIds))
            if (updated == null) {
                if (file.exists()) file.delete()
            } else {
                file.parentFile?.mkdirs()
                file.writeText(updated)
            }
            FuzzyPinyinPreferences.setEnabled(context, schemaId, enabledIds)
            true
        } catch (_: Exception) {
            false
        }
    }
}
