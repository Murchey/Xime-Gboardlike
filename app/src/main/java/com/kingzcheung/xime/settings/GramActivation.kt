package com.kingzcheung.xime.settings

import android.content.Context
import java.io.File

/**
 * 语法模型启用状态（全局，一份配置对所有方案生效）。
 * activeLanguage 为 .gram 的语言名（去扩展名），空串表示停用。
 */
object GramPreferences {
    private const val PREFS = "gram_model"
    private const val KEY_ACTIVE = "active_language"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun activeLanguage(context: Context): String =
        prefs(context).getString(KEY_ACTIVE, "").orEmpty()

    fun setActiveLanguage(context: Context, language: String) {
        prefs(context).edit().putString(KEY_ACTIVE, language).apply()
    }
}

/**
 * 把语法模型配置写进 <schema>.custom.yaml。
 *
 * 只改 grammar/language 与 translator/contextual_suggestions 两个 key，
 * 保留用户其他 patch（合并方式与 FuzzyPinyinPatch 一致：键级替换，不动其他块）。
 * language 为 null 时清除这两个 key，文件里没有其他内容时删除整个文件。
 */
object GramPatch {
    val KEYS = listOf("grammar/language", "translator/contextual_suggestions")

    /**
     * 规范化写入：先移除我们的全部旧键，再把新块统一追加到 patch 区末尾。
     * 输出恒定带一个结尾换行，同参数重复 apply 结果逐字节一致（幂等）。
     */
    fun apply(existing: String?, language: String?): String? {
        val base = (existing ?: "").replace("\r\n", "\n").trimEnd('\n')
        if (language.isNullOrBlank()) return removeKeys(base)
        val lines = if (base.isBlank()) mutableListOf() else base.lines().toMutableList()
        if (lines.indexOfFirst { isPatchHeader(it) } < 0) {
            lines.add("patch:")
        }
        val patchIdx = lines.indexOfFirst { isPatchHeader(it) }
        removeOurKeys(lines, patchIdx)
        // 只插到 patch 区末尾（下一个顶格行之前），避免追加到其他顶层块里
        val insertAt = patchEnd(lines, patchIdx).coerceAtMost(lines.size)
        lines.addAll(insertAt, renderLines(language))
        return normalize(lines)
    }

    private fun removeKeys(base: String): String? {
        if (base.isBlank()) return null
        val lines = base.lines().toMutableList()
        val patchIdx = lines.indexOfFirst { isPatchHeader(it) }
        if (patchIdx < 0) return normalize(lines)
        removeOurKeys(lines, patchIdx)
        val hasOther = lines.any { it.isNotBlank() && it.trim() != "patch:" }
        return if (hasOther) normalize(lines) else null
    }

    /** 从 patch 区移除我们的全部键（含各自的值块）。 */
    private fun removeOurKeys(lines: MutableList<String>, patchIdx: Int) {
        if (patchIdx < 0) return
        var end = patchEnd(lines, patchIdx)
        var idx = patchIdx + 1
        while (idx < end) {
            if (isOurKey(lines[idx])) {
                val keyEnd = keyBlockEnd(lines, idx)
                lines.subList(idx, keyEnd).clear()
                end = patchEnd(lines, patchIdx)
            } else {
                idx++
            }
        }
    }

    private fun renderLines(language: String): List<String> = KEYS.map { key ->
        val value = if (key == "grammar/language") "'$language'" else "true"
        "  '$key': $value"
    }

    private fun normalize(lines: List<String>): String {
        val trimmed = lines.dropLastWhile { it.isBlank() }
        return trimmed.joinToString("\n") + "\n"
    }

    private fun keyBlockEnd(lines: List<String>, keyIdx: Int): Int {
        val keyIndent = lines[keyIdx].takeWhile { it == ' ' }.length
        var end = keyIdx + 1
        while (end < lines.size) {
            val line = lines[end]
            if (line.isBlank()) {
                end++
                continue
            }
            val indent = line.takeWhile { it == ' ' }.length
            if (indent <= keyIndent) break
            end++
        }
        return end
    }

    private fun isPatchHeader(line: String): Boolean =
        line.trim() == "patch:" && line.isNotEmpty() && line[0] != ' ' && line[0] != '\t'

    private fun patchEnd(lines: List<String>, patchIdx: Int): Int {
        var end = patchIdx + 1
        while (end < lines.size) {
            val line = lines[end]
            if (line.isNotBlank() && line[0] != ' ' && line[0] != '\t') break
            end++
        }
        return end
    }

    private fun isOurKey(line: String): Boolean {
        val trimmed = line.trim()
        return KEYS.any { key ->
            trimmed.startsWith("'$key':") ||
                trimmed.startsWith("\"$key\":") ||
                trimmed.startsWith("$key:")
        }
    }
}

/**
 * 语法模型全局启用/停用：给全部启用方案写入或清除 grammar patch，
 * 写完由调用方触发部署（RimeConfigHelper.deployInstalledSchema）。
 */
object GramActivation {

    fun customFile(context: Context, schemaId: String): File =
        File(SchemaManager.getRimeDir(context), "$schemaId.custom.yaml")

    /**
     * 按当前偏好写入/清除全部启用方案的 grammar patch。
     *
     * @return 失败的方案数（0 = 全部成功）
     */
    fun applyGlobal(context: Context): Int {
        val language = GramPreferences.activeLanguage(context).ifBlank { null }
        var failed = 0
        for (schemaId in SchemaManager.getEnabledSchemas(context)) {
            try {
                val file = customFile(context, schemaId)
                val existing = if (file.exists()) file.readText() else null
                val updated = GramPatch.apply(existing, language)
                if (updated == null) {
                    if (file.exists()) file.delete()
                } else {
                    file.parentFile?.mkdirs()
                    file.writeText(updated)
                }
            } catch (_: Exception) {
                failed++
            }
        }
        return failed
    }
}
