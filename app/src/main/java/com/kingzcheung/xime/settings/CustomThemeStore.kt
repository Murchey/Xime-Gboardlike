package com.kingzcheung.xime.settings

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * 一套配色在浅色或深色模式下的可调颜色，统一存 #RRGGBB / #AARRGGBB。
 *
 * [enterKeyBackground]/[enterKeyIconColor] 为空串表示"跟随主题强调色"（回车键沿用 accent，
 * 图标色按对比度自动取），仅自定义配色面板会写入具体值。
 */
data class ThemeColorSet(
    val keyboardBackground: String,
    val keyBackground: String,
    val functionKeyBackground: String,
    val keyText: String,
    val candidateText: String,
    val accent: String,
    val enterKeyBackground: String = "",
    val enterKeyIconColor: String = "",
)

/**
 * 自定义配色的键盘底图与「玻璃」效果。
 *
 * 图片是用户从相册选进 `files/themes/backgrounds/` 的副本；[imagePath] 以 `/` 开头时
 * 相对 `files/` 根目录（如 `/themes/backgrounds/xxx.jpg`），与主题图片路径规则一致。
 * 空串表示不用底图，只用 [ThemeColorSet.keyboardBackground] 的纯色。
 */
data class ThemeBackgroundSettings(
    val imagePath: String = "",
    /** 图片填充方式，取值同 [BackgroundConfig.fit]。 */
    val fit: String = FIT_COVER,
    /** 拖动位置：0..1，0.5 为居中；只在图片有可移动余量时生效。 */
    val offsetX: Float = 0.5f,
    val offsetY: Float = 0.5f,
    /** 蒙版（压暗层）强度 0..1。 */
    val overlayAlpha: Float = DEFAULT_OVERLAY_ALPHA,
    /** 蒙版层高斯模糊：模糊蒙版下方的底图；半径单位是像素。 */
    val maskBlurEnabled: Boolean = false,
    val maskBlurRadius: Int = DEFAULT_BLUR_RADIUS,
    /** 按键透明度 0..1（1 = 不透明）。 */
    val keyOpacity: Float = 1f,
    /** 按键毛玻璃：键帽内绘制模糊后的底图；半径单位是像素。 */
    val keyBlurEnabled: Boolean = false,
    val keyBlurRadius: Int = DEFAULT_BLUR_RADIUS,
) {
    val hasImage: Boolean get() = imagePath.isNotBlank()

    /** 导入的文件不可信：数值钳到合法区间，路径去掉 `..` 之类的越界写法。 */
    fun normalized(): ThemeBackgroundSettings = copy(
        imagePath = imagePath.trim().takeIf { ".." !in it } ?: "",
        fit = fit.takeIf { it in FITS } ?: FIT_COVER,
        offsetX = offsetX.coerceIn(0f, 1f),
        offsetY = offsetY.coerceIn(0f, 1f),
        overlayAlpha = overlayAlpha.coerceIn(0f, 1f),
        maskBlurRadius = maskBlurRadius.coerceIn(0, MAX_BLUR_RADIUS),
        keyOpacity = keyOpacity.coerceIn(0f, 1f),
        keyBlurRadius = keyBlurRadius.coerceIn(0, MAX_BLUR_RADIUS),
    )

    companion object {
        const val FIT_COVER = "cover"
        const val FIT_CONTAIN = "contain"
        const val FIT_FILL = "fill"
        const val FIT_FIT_WIDTH = "fit_width"
        const val FIT_FIT_HEIGHT = "fit_height"
        const val FIT_NONE = "none"

        val FITS = setOf(FIT_COVER, FIT_CONTAIN, FIT_FILL, FIT_FIT_WIDTH, FIT_FIT_HEIGHT, FIT_NONE)

        const val DEFAULT_OVERLAY_ALPHA = 0.25f
        const val DEFAULT_BLUR_RADIUS = 16
        const val MAX_BLUR_RADIUS = 60
    }
}

/**
 * 用户自定义配色方案：浅色与深色各自一套颜色，id 以 custom_ 开头。
 *
 * [highlightFirstCandidate] = false 时不显著标注第一个候选词（去掉候选底色与强调文字色）。
 * [background] 是键盘底图与透明度/毛玻璃参数（没有底图时它只影响按键透明度）。
 */
data class CustomColorScheme(
    val id: String,
    val name: String,
    val light: ThemeColorSet,
    val dark: ThemeColorSet,
    val highlightFirstCandidate: Boolean = true,
    val background: ThemeBackgroundSettings = ThemeBackgroundSettings(),
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
            // 缺省 true：旧文件没有该字段时保持既有"标注第一个候选"的行为
            highlightFirstCandidate = json.optBoolean("highlight_first_candidate", true),
            background = parseBackground(json.optJSONObject("background")),
        )
    }

    /** 底图参数：缺省用默认值（旧文件没有这一段），数值一律钳到合法区间。 */
    private fun parseBackground(json: JSONObject?): ThemeBackgroundSettings {
        if (json == null) return ThemeBackgroundSettings()
        val defaults = ThemeBackgroundSettings()
        return ThemeBackgroundSettings(
            imagePath = json.optString("image_path", defaults.imagePath),
            fit = json.optString("fit", defaults.fit),
            offsetX = json.optDouble("offset_x", defaults.offsetX.toDouble()).toFloat(),
            offsetY = json.optDouble("offset_y", defaults.offsetY.toDouble()).toFloat(),
            overlayAlpha = json.optDouble("overlay_alpha", defaults.overlayAlpha.toDouble()).toFloat(),
            maskBlurEnabled = json.optBoolean("mask_blur_enabled", defaults.maskBlurEnabled),
            maskBlurRadius = json.optInt("mask_blur_radius", defaults.maskBlurRadius),
            keyOpacity = json.optDouble("key_opacity", defaults.keyOpacity.toDouble()).toFloat(),
            keyBlurEnabled = json.optBoolean("key_blur_enabled", defaults.keyBlurEnabled),
            keyBlurRadius = json.optInt("key_blur_radius", defaults.keyBlurRadius),
        ).normalized()
    }

    private fun parseColorSet(json: JSONObject, fallback: ThemeColorSet): ThemeColorSet {
        fun color(key: String, default: String): String {
            val raw = json.optString(key)
            if (raw.isBlank()) return default
            return normalizeHexColor(raw)
                ?: throw ThemeFormatException("$key 不是有效颜色：$raw")
        }
        // 可选色：空串 = 跟随主题强调色；写了非法值仍报错，避免静默忽略。
        fun optionalColor(key: String, default: String): String {
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
            enterKeyBackground = optionalColor("enter_key_background", fallback.enterKeyBackground),
            enterKeyIconColor = optionalColor("enter_key_icon_color", fallback.enterKeyIconColor),
        )
    }

    private fun CustomColorScheme.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("highlight_first_candidate", highlightFirstCandidate)
        put("light", light.toJson())
        put("dark", dark.toJson())
        put("background", background.toJson())
    }

    private fun ThemeBackgroundSettings.toJson(): JSONObject = JSONObject().apply {
        put("image_path", imagePath)
        put("fit", fit)
        put("offset_x", offsetX.toDouble())
        put("offset_y", offsetY.toDouble())
        put("overlay_alpha", overlayAlpha.toDouble())
        put("mask_blur_enabled", maskBlurEnabled)
        put("mask_blur_radius", maskBlurRadius)
        put("key_opacity", keyOpacity.toDouble())
        put("key_blur_enabled", keyBlurEnabled)
        put("key_blur_radius", keyBlurRadius)
    }

    private fun ThemeColorSet.toJson(): JSONObject = JSONObject().apply {
        put("keyboard_background", keyboardBackground)
        put("key_background", keyBackground)
        put("function_key_background", functionKeyBackground)
        put("key_text", keyText)
        put("candidate_text", candidateText)
        put("accent", accent)
        if (enterKeyBackground.isNotBlank()) put("enter_key_background", enterKeyBackground)
        if (enterKeyIconColor.isNotBlank()) put("enter_key_icon_color", enterKeyIconColor)
    }

    private fun file(context: Context): File =
        File(File(context.filesDir, FILE_DIR), FILE_NAME)

    /** 相册导入的底图副本目录：`files/themes/backgrounds/`（随配色一起管理）。 */
    fun backgroundsDir(context: Context): File =
        File(File(context.filesDir, FILE_DIR), "backgrounds")

    /** 底图的存储路径（相对 `files/`，以 `/` 开头，供 [BackgroundConfig.src] 使用）。 */
    fun newBackgroundPath(schemeId: String, extension: String): String {
        val ext = extension.lowercase().takeIf { it in setOf("jpg", "jpeg", "png", "webp") } ?: "jpg"
        return "/$FILE_DIR/backgrounds/${schemeId}_${System.currentTimeMillis()}.$ext"
    }

    /** 删除某个底图文件（删除配色时回收）；路径不在底图目录内时不动。 */
    fun deleteBackground(context: Context, path: String) {
        if (path.isBlank() || ".." in path) return
        val target = File(context.filesDir, path.trimStart('/')).canonicalFile
        val dir = backgroundsDir(context).canonicalFile
        if (!target.path.startsWith(dir.path + File.separator)) return
        runCatching { target.delete() }
    }

    /**
     * 回收没有任何配色引用的底图文件。
     *
     * 换图/清除底图时**不立即删**旧图：草稿可能被放弃，立即删会让已保存的方案指向不存在的文件。
     * 保存成功后再统一扫一遍，只留仍被引用的图片。
     */
    fun pruneOrphanBackgrounds(context: Context) {
        val dir = backgroundsDir(context)
        val files = dir.listFiles() ?: return
        if (files.isEmpty()) return
        val referenced = load(context)
            .mapNotNull { it.background.imagePath.takeIf(String::isNotBlank) }
            .mapNotNull { path ->
                runCatching { File(context.filesDir, path.trimStart('/')).canonicalFile.path }.getOrNull()
            }
            .toSet()
        files.forEach { file ->
            val path = runCatching { file.canonicalFile.path }.getOrNull() ?: return@forEach
            if (path !in referenced) runCatching { file.delete() }
        }
    }

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
