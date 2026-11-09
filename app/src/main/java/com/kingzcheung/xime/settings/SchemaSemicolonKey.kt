package com.kingzcheung.xime.settings

import android.content.Context
import java.io.File

/**
 * 判断当前方案是否用 `;` 作为韵母键（微软双拼、搜狗双拼、紫光双拼都是 `ing -> ;`）。
 *
 * 这些方案在 `speller/alphabet` 里显式加了 `;`，键盘上却没有任何键能发出分号，
 * 于是所有含 `ing` 的音节**一个都打不出来**。这里从方案自身的 `speller/algebra`
 * 推导，而不是硬编码方案名——用户自定义方案只要写了同样的规则也自动生效。
 *
 * 判定只看"替换结果"是分号的 `xform` 规则；`xform/(\w);/$1ing/` 这类把分号
 * 放在源侧的反向规则不算。
 */
object SchemaSemicolonKey {

    private val cache = mutableMapOf<String, Boolean>()

    fun current(context: Context): Boolean =
        forSchema(context, SettingsPreferences.getCurrentSchema(context))

    @Synchronized
    fun forSchema(context: Context, schemaId: String): Boolean {
        if (schemaId.isBlank()) return false
        val file = File(SchemaManager.getRimeDir(context), schemaId + ".schema.yaml")
        if (!file.isFile) return false
        val key = schemaId + ":" + file.lastModified() + ":" + file.length()
        cache[key]?.let { return it }
        val parsed = runCatching { parse(file.readText()) }.getOrDefault(false)
        cache[key] = parsed
        return parsed
    }

    internal fun parse(text: String): Boolean =
        text.lineSequence().any { line ->
            val trimmed = line.substringBefore('#').trim()
            if (!trimmed.startsWith("- xform/")) return@any false
            val parts = trimmed.removePrefix("- xform/").split('/')
            if (parts.size < 2) return@any false
            parts[1].trim() == ";"
        }
}
