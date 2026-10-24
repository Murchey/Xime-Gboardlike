package com.kingzcheung.xime.settings

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** 一套配色在浅色或深色模式下的六个可调颜色，统一存 #RRGGBB / #AARRGGBB。 */
data class ThemeColorSet(
    val keyboardBackground: String,
    val keyBackground: String,
    val functionKeyBackground: String,
    val keyText: String,
    val candidateText: String,
    val accent: String,
)

/** 用户自定义配色方案：浅色与深色各自一套颜色，id 以 custom_ 开头。 */
data class CustomColorScheme(
    val id: String,
    val name: String,
    val light: ThemeColorSet,
    val dark: ThemeColorSet,
)

/**
 * 自定义配色方案的本地存储与 JSON 导入导出。
 *
 * 文件放在应用私有目录 `files/themes/custom_color_schemes.json`，不依赖 Rime 配置，
 * 因此不需要部署即可生效。UI 保存后调用 [com.kingzcheung.xime.ui.theme.KeyboardThemes.reload] 刷新缓存。
 */
object CustomThemeStore {
    const val FORMAT = "xime-color-scheme"
    const val VERSION = 1
    const val ID_PREFIX = "custom_"
    const val MAX_NAME_LENGTH = 40

    private const val FILE_DIR = "themes"
    private const val FILE_NAME = "custom_color_schemes.json"

    /** Gboard 公开主题的参考色（浅色：白键 + 浅灰底 + Google 蓝；深色：实测 #1E1F21/#38393B）。 */
    val GBOARD_LIGHT = ThemeColorSet(
        keyboardBackground = "#F1F3F4",
        keyBackground = "#FFFFFF",
        functionKeyBackground = "#E8EAED",
        keyText = "#202124",
        candidateText = "#5F6368",
        accent = "#1A73E8",
    )
    val GBOARD_DARK = ThemeColorSet(
        keyboardBackground = "#1E1F21",
        keyBackground = "#38393B",
        functionKeyBackground = "#38393B",
        keyText = "#E7E8EA",
        candidateText = "#8D8E90",
        accent = "#A8C8FB",
    )

    class ThemeFormatException(message: String) : Exception(message)

    fun newId(): String = ID_PREFIX + UUID.randomUUID().toString().replace("-", "").take(12)

    fun defaultScheme(
        id: String = newId(),
        name: String = "我的配色",
    ): CustomColorScheme = CustomColorScheme(
        id = id,
        name = name,
        light = GBOARD_LIGHT,
        dark = GBOARD_DARK,
    )

    fun load(context: Context): List<CustomColorScheme> {
        val file = file(context)
        if (!file.isFile) return emptyList()
        return runCatching { importJson(file.readText()) }.getOrDefault(emptyList())
    }

    fun saveAll(context: Context, schemes: List<CustomColorScheme>) {
        val dir = file(context).parentFile ?: return
        if (!dir.exists()) dir.mkdirs()
        val target = file(context)
        val tmp = File(dir, ".${FILE_NAME}.tmp")
        tmp.writeText(exportJson(schemes))
        if (!tmp.renameTo(target)) {
            target.writeText(tmp.readText())
            tmp.delete()
        }
    }

    fun upsert(context: Context, scheme: CustomColorScheme): List<CustomColorScheme> {
        val current = load(context).toMutableList()
        val index = current.indexOfFirst { it.id == scheme.id }
        if (index >= 0) current[index] = scheme else current.add(scheme)
        saveAll(context, current)
        return current
    }

    fun delete(context: Context, id: String): List<CustomColorScheme> {
        val current = load(context).filterNot { it.id == id }
        saveAll(context, current)
        return current
    }

    fun exportJson(schemes: List<CustomColorScheme>): String {
        val root = JSONObject()
        root.put("format", FORMAT)
        root.put("version", VERSION)
        val array = JSONArray()
        schemes.forEach { array.put(it.toJson()) }
        root.put("schemes", array)
        return root.toString(2)
    }

    fun importJson(text: String): List<CustomColorScheme> {
        if (text.isBlank()) throw ThemeFormatException("文件内容为空")
        val root = try {
            JSONObject(text)
        } catch (e: Exception) {
            throw ThemeFormatException("不是有效的 JSON 文件")
        }
        val array = root.optJSONArray("schemes") ?: JSONArray().apply { put(root) }
        if (array.length() == 0) throw ThemeFormatException("文件里没有配色方案")

        val result = mutableListOf<CustomColorScheme>()
        val usedIds = mutableSetOf<String>()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val scheme = parseScheme(item)
            var id = scheme.id
            if (id in usedIds) id = newId()
            usedIds.add(id)
            result.add(scheme.copy(id = id))
        }
        if (result.isEmpty()) throw ThemeFormatException("文件里没有有效的配色方案")
        return result
    }

    private fun parseScheme(json: JSONObject): CustomColorScheme {
        val rawName = json.optString("name").trim().take(MAX_NAME_LENGTH)
        val name = rawName.ifBlank { "导入的配色" }
        val rawId = json.optString("id").trim()
        val id = if (rawId.matches(Regex("[A-Za-z0-9_-]{1,48}"))) {
            if (rawId.startsWith(ID_PREFIX)) rawId else ID_PREFIX + rawId
        } else {
            newId()
        }
        val lightJson = json.optJSONObject("light") ?: throw ThemeFormatException("缺少 light 颜色")
        val darkJson = json.optJSONObject("dark") ?: throw ThemeFormatException("缺少 dark 颜色")
        return CustomColorScheme(
            id = id,
            name = name,
            light = parseColorSet(lightJson, GBOARD_LIGHT),
            dark = parseColorSet(darkJson, GBOARD_DARK),
        )
    }

    private fun parseColorSet(json: JSONObject, fallback: ThemeColorSet): ThemeColorSet {
        fun color(key: String, default: String): String {
            val raw = json.optString(key)
            if (raw.isBlank()) return default
            return normalizeHexColor(raw)
                ?: throw ThemeFormatException("$key 不是有效颜色：$raw")
        }
        return ThemeColorSet(
            keyboardBackground = color("keyboard_background", fallback.keyboardBackground),
            keyBackground = color("key_background", fallback.keyBackground),
            functionKeyBackground = color("function_key_background", fallback.functionKeyBackground),
            keyText = color("key_text", fallback.keyText),
            candidateText = color("candidate_text", fallback.candidateText),
            accent = color("accent", fallback.accent),
        )
    }

    private fun CustomColorScheme.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("light", light.toJson())
        put("dark", dark.toJson())
    }

    private fun ThemeColorSet.toJson(): JSONObject = JSONObject().apply {
        put("keyboard_background", keyboardBackground)
        put("key_background", keyBackground)
        put("function_key_background", functionKeyBackground)
        put("key_text", keyText)
        put("candidate_text", candidateText)
        put("accent", accent)
    }

    private fun file(context: Context): File =
        File(File(context.filesDir, FILE_DIR), FILE_NAME)

    /** 校验并规范化颜色：#RRGGBB / #AARRGGBB，也接受 0x 前缀与省略 #。无效返回 null。 */
    fun normalizeHexColor(input: String): String? {
        val value = parseHexColor(input) ?: return null
        return formatHexColor(value)
    }

    /** 颜色字符串转 0xAARRGGBB；无效返回 null。 */
    fun parseHexColor(input: String): Long? {
        val raw = input.trim()
            .removePrefix("#")
            .removePrefix("0x")
            .removePrefix("0X")
        if (raw.length != 6 && raw.length != 8) return null
        if (!raw.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
        val value = raw.toLongOrNull(16) ?: return null
        return if (raw.length == 6) 0xFF000000L or value else value
    }

    fun formatHexColor(argb: Long): String {
        val alpha = (argb ushr 24) and 0xFF
        return if (alpha == 0xFFL) {
            "#%06X".format(argb and 0xFFFFFF)
        } else {
            "#%08X".format(argb and 0xFFFFFFFFL)
        }
    }
}
