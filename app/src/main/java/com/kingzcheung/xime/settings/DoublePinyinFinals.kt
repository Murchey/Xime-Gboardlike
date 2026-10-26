package com.kingzcheung.xime.settings

import android.content.Context
import java.io.File

/**
 * 从双拼 schema 的 speller/algebra 中提取“韵母 -> 按键”映射。
 *
 * 读取 xform 到带圈字母的规则（如 iu$/Ⓠ/），并保留小鹤方案的 sh/ch/zh
 * 声母提示；跳过零声母派生规则和 xlit。结果用于键面右上角显示当前双拼提示。
 */
object DoublePinyinFinals {

    private const val TAG = "DoublePinyinFinals"
    private const val CIRCLED = "ⓆⓌⓇⓉⓎⓊⒾⓄⓅⓈⒹⒻⒼⒽⒿⓀⓁⓏⓍⒸⓋⒷⓃⓂ"
    private const val LETTERS = "qwrtyuiopsdfghjklzxcvbnm"
    private val circledToKey: Map<Char, Char> =
        CIRCLED.toList().zip(LETTERS.toList()).toMap()

    private val cache = mutableMapOf<String, Map<Char, String>>()

    fun isDoublePinyin(schemaId: String): Boolean =
        schemaId.startsWith("double_pinyin")

    fun current(context: Context): Map<Char, String> =
        forSchema(context, SettingsPreferences.getCurrentSchema(context))

    @Synchronized
    fun forSchema(context: Context, schemaId: String): Map<Char, String> {
        if (!isDoublePinyin(schemaId)) return emptyMap()
        val file = File(SchemaManager.getRimeDir(context), schemaId + ".schema.yaml")
        if (!file.isFile) return emptyMap()
        val key = schemaId + ":" + file.lastModified() + ":" + file.length()
        cache[key]?.let { return it }
        val parsed = try {
            parse(file.readText())
        } catch (_: Exception) {
            emptyMap()
        }
        cache[key] = parsed
        return parsed
    }

    internal fun parse(text: String): Map<Char, String> {
        // 不同双拼方案的 xlit 键位并不相同（如智能 ABC 的 Ⓔ=e、紫光的 Ⓐ=a），
        // 优先从方案自身的 xlit 规则建立“带圈字母 -> 键位”映射；旧方案没有
        // xlit 时再回退到小鹤排列，保证自定义/精简 schema 也能解析。
        val symbolToKey = linkedMapOf<Char, Char>().apply {
            putAll(circledToKey)
            text.lineSequence().forEach { line ->
                val trimmed = line.substringBefore('#').trim()
                if (!trimmed.startsWith("- xlit/")) return@forEach
                val parts = trimmed.removePrefix("- xlit/").split('/')
                if (parts.size < 2) return@forEach
                val symbols = parts[0]
                val keys = parts[1]
                symbols.zip(keys).forEach { (symbol, key) ->
                    if (symbol != key && key.isLetter()) this[symbol] = key.lowercaseChar()
                }
            }
        }
        val result = linkedMapOf<Char, MutableList<String>>()
        text.lineSequence().forEach { line ->
            val trimmed = line.substringBefore('#').trim()
            if (!trimmed.startsWith("- xform/")) return@forEach
            val body = trimmed.removePrefix("- xform/")
            val parts = body.split('/')
            if (parts.size < 2) return@forEach
            val source = parts[0]
            val replacement = parts[1]
            val key = replacement.lastOrNull { symbolToKey.containsKey(it) }
                ?.let { symbolToKey[it] } ?: return@forEach
            if (!key.isLetter()) return@forEach
            val finals = expand(source)
            if (finals.isEmpty()) return@forEach
            result.getOrPut(key) { mutableListOf() }.addAll(finals)
        }
        // a/o/e 自身也是零声母韵母
        for (key in listOf('a', 'o', 'e')) {
            if (result[key].isNullOrEmpty()) result[key] = mutableListOf(key.toString())
        }
        return result.mapValues { (_, finals) ->
            finals.distinct().joinToString("\n") { if (it == "v") "ü" else it }
        }
    }

    private fun expand(pattern: String): List<String> {
        var p = pattern.trim()
        if (p.startsWith("^([aoe]")) return emptyList() // 零声母派生，不是韵母键位
        p = p.removePrefix("^").removeSuffix("$")
        // (.) 只是辅音占位，不进入韵母
        p = p.replace(Regex("^\\([^)]*\\)"), "")
        if (p.contains("([aoe]")) return emptyList()
        val alternatives = p.split('|')
        val out = mutableListOf<String>()
        for (raw in alternatives) {
            var alt = raw.trim().removePrefix("^").removeSuffix("$").replace("$", "")
            if (alt.isEmpty()) continue
            // [iu] -> i/u, [uv] -> u/v
            val classMatch = Regex("\\[([^]]+)]").find(alt)
            if (classMatch != null) {
                val chars = classMatch.groupValues[1].filter { it.isLetter() }
                val prefix = alt.substring(0, classMatch.range.first)
                val suffix = alt.substring(classMatch.range.last + 1)
                for (c in chars) out.add(prefix + c + suffix)
            } else {
                out.add(alt)
            }
        }
        return out.flatMap { expandOptional(it) }
            .map { it.replace("?", "") }
            .filter { it.isNotEmpty() && it.all { c -> c.isLetter() } }
    }

    /** i?ong -> ong/iong；其他不带 ? 的原样返回。 */
    private fun expandOptional(s: String): List<String> {
        val idx = s.indexOf('?')
        if (idx < 0) return listOf(s)
        if (idx == 0) return listOf(s.substring(1))
        val prev = s[idx - 1]
        return listOf(
            s.removeRange(idx - 1, idx + 1), // 去掉可选字母
            s.removeRange(idx, idx + 1),     // 保留可选字母
        )
    }
}
