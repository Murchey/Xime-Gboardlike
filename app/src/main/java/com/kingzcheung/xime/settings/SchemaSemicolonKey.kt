package com.kingzcheung.xime.settings

import android.content.Context
import java.io.File

/**
 * 判断当前方案是否用 `;` 当韵母键（微软双拼、搜狗双拼、紫光双拼等的 `ing`）。
 *
 * 这些方案在 `speller/alphabet` 里显式加了 `;`，键盘上却没有任何键能发出分号，
 * 于是所有含 `ing` 的音节**一个都打不出来**。判定必须覆盖任意方案包，因此分两步：
 *
 * 1. **优先问 librime**：`speller/algebra` 取到的是 `custom.yaml` 的 `__patch`/`__append`
 *    合并**之后**的最终规则表。万象拼音这类方案把双拼规则放在独立的
 *    `wanxiang_algebra.yaml` 里再 patch 进来，读原 `.schema.yaml` 根本看不到。
 * 2. **引擎未就绪时退回读文件**：直接扫 `.schema.yaml` + 同名 `.custom.yaml`，
 *    并按 `__include`/`__patch: file:/标签` 递归跟进被引用的 yaml（有深度与数量上限）。
 *
 * 两种来源都只看"替换侧以分号开头"的 `xform` 规则——`xform/ing$/;/`（雾凇系）与
 * `xform/ing(\d)$/;$1`（万象系，带声调标记）都算；`;` 在源侧的反向规则不算。
 */
object SchemaSemicolonKey {

    /** 递归解析的最大深度，防御环形 __include。 */
    private const val MAX_DEPTH = 4

    /** 单次解析最多跟进的被引用文件数。 */
    private const val MAX_INCLUDED_FILES = 24

    /** librime 的 speller/algebra 运算符：这些不是模块引用。 */
    private val ALGEBRA_OPERATORS = setOf(
        "xform", "xlit", "derive", "abbrev", "erase", "fuzz", "table_lookup",
        "table", "simplify", "echo",
    )

    private val cache = mutableMapOf<String, Boolean>()

    fun current(context: Context): Boolean =
        forSchema(context, SettingsPreferences.getCurrentSchema(context))

    @Synchronized
    fun forSchema(context: Context, schemaId: String): Boolean {
        if (schemaId.isBlank()) return false

        // 1) librime 合并后的最终规则（覆盖 patch 注入的方案包）
        val fromEngine = runCatching {
            if (!RimeEngineAccess.isReady()) {
                false
            } else {
                anyRuleHasSemicolonFinal(RimeEngineAccess.schemaAlgebra(schemaId))
            }
        }.getOrDefault(false)
        if (fromEngine) return true

        // 2) 文件兜底（引擎未就绪、或规则在引擎里没有序列化成同样形态时）
        val rimeDir = runCatching { SchemaManager.getRimeDir(context) }.getOrNull()
            ?: return fromEngine
        val parsed = forSchemaDir(rimeDir, schemaId)
        return parsed || fromEngine
    }

    /**
     * 文件兜底入口（方案目录可注入，便于单测覆盖 patch 注入场景）。
     *
     * 依次看 `<id>.schema.yaml` 与其 `<id>.custom.yaml`：真正生效的规则往往在
     * custom 的 `__patch` 指向的独立 yaml 里，两个入口都要跟进去。
     */
    internal fun forSchemaDir(rimeDir: File, schemaId: String): Boolean {
        if (schemaId.isBlank()) return false
        val schema = File(rimeDir, schemaId + ".schema.yaml")
        if (!schema.isFile) return false
        val custom = File(rimeDir, schemaId + ".custom.yaml")
        val key = schemaId + ":" + schema.lastModified() + ":" + schema.length() +
            ":" + (if (custom.isFile) custom.lastModified() else 0L)
        cache[key]?.let { return it }
        val parsed = runCatching {
            parseFileTree(schema) || (custom.isFile && parseFileTree(custom))
        }.getOrDefault(false)
        cache[key] = parsed
        return parsed
    }

    // ── 纯函数（可单测）──────────────────────────────────────

    /** 单条 algebra 规则是否把分号作为韵母键的输出。 */
    internal fun ruleHasSemicolonFinal(rule: String): Boolean {
        val trimmed = rule.substringBefore('#').trim().removePrefix("- ").trim()
        if (!trimmed.startsWith("xform/")) return false
        val body = trimmed.removePrefix("xform/")
        // 形如 <src>/<dst>/，两侧都可能含 /，因此取"倒数第二个 /"之前为源、之后为替换
        val lastSlash = body.lastIndexOf('/')
        if (lastSlash <= 0) return false
        val beforeLast = body.substring(0, lastSlash)
        val replacement = body.substring(lastSlash + 1)
        if (replacement.isNotEmpty()) return false // 末尾还有内容说明格式不合法
        val srcSep = beforeLast.indexOf('/')
        if (srcSep < 0) return false
        val dst = beforeLast.substring(srcSep + 1)
        // 替换侧包含分号即判定为分号韵母键：可能是裸分号（xform/ing$/;/）、
        // 带捕获组的分号（xform/(.)ing$/$1;/）、或带韵律标记的（xform/ing$;/;$1/），
        // 三者都表示该方案用分号作为 ing 的韵母键
        return dst.contains(";")
    }

    /** 解析一段 yaml 文本里所有 algebra 规则。 */
    internal fun parse(text: String): Boolean =
        text.lineSequence().any { ruleHasSemicolonFinal(it) }

    /** 一组已解析好的 algebra 规则（librime 返回的合并结果）里是否有分号韵母。 */
    internal fun anyRuleHasSemicolonFinal(rules: List<String>): Boolean =
        rules.any { ruleHasSemicolonFinal(it) }

    /**
     * 递归解析一个方案文件及其 `__include` / `__patch` 引用。
     *
     * 只做"这些引用里有没有分号韵母规则"的判断，不重建完整 YAML 语义：
     * patch 的目标锚点（如 `wanxiang_algebra:/base/微软双拼`）不参与筛选，
     * 因为同一模块里通常只有一种双拼用到分号，误判代价（Shift 变分号）远小于漏判
     * （ing 打不出来）。
     */
    private fun parseFileTree(root: File): Boolean {
        val visited = mutableSetOf<String>()
        val queue = ArrayDeque<Pair<File, Int>>()
        queue.addLast(root to 0)
        var budget = MAX_INCLUDED_FILES
        while (queue.isNotEmpty() && budget-- > 0) {
            val (file, depth) = queue.removeFirst()
            if (!file.isFile) continue
            val path = file.absolutePath
            if (!visited.add(path)) continue
            val text = runCatching { file.readText() }.getOrNull() ?: continue
            if (parse(text)) return true
            if (depth >= MAX_DEPTH) continue
            referencedFiles(file, text).forEach { queue.addLast(it to depth + 1) }
        }
        return false
    }

    /**
     * 找出 yaml 里引用到的其它 yaml 文件。
     *
     * 覆盖 rime 的几种写法：
     * - `__include: file.yaml` / `- file: file.yaml`
     * - `__patch:` 下的 `- 模块名:/标签/子标签`（万象拼音就是这种：
     *   规则在 `wanxiang_algebra.yaml` 里，靠 `wanxiang_algebra:/base/微软双拼` 注入）
     * - 单行 `__patch: 模块名:/标签`
     *
     * 只认"模块名 + 冒号"形态，并要求模块名不含 `/`——避免把 `- xform/a/b/` 这类
     * 规则误当成文件引用。
     */
    private fun referencedFiles(source: File, text: String): List<File> {
        val dir = source.parentFile ?: return emptyList()
        val result = mutableListOf<File>()
        text.lineSequence().forEach { line ->
            val t = line.substringBefore('#').trim()
            val candidate = when {
                t.startsWith("__include:") -> t.removePrefix("__include:").trim()
                t.startsWith("__patch:") -> t.removePrefix("__patch:").trim()
                t.startsWith("- file:") -> t.removePrefix("- file:").trim()
                t.startsWith("file:") -> t.removePrefix("file:").trim()
                t.startsWith("- ") -> t.removePrefix("- ").trim()
                else -> null
            } ?: return@forEach
            val name = resolveYamlName(candidate) ?: return@forEach
            result.add(File(dir, name))
        }
        return result
    }

    /** 从一行引用里解析出 yaml 文件名；不是文件引用时返回 null。 */
    private fun resolveYamlName(candidate: String): String? {
        val cleaned = candidate.trim().trim('"', '\'').trim()
        if (cleaned.isEmpty()) return null
        if (cleaned.endsWith(".yaml")) {
            // 路径形态：取文件名（rime 的引用都相对 rime 目录）
            return cleaned.substringAfterLast('/').substringAfterLast('\\')
        }
        val module = cleaned.substringBefore(':').trim()
        if (module.isEmpty() || module.contains('/') || module.contains('\\')) return null
        if (!module.all { it.isLetterOrDigit() || it == '_' || it == '-' }) return null
        // algebra 规则本身也长成 `xform:...` 的样子，不能当成模块引用
        if (module.lowercase() in ALGEBRA_OPERATORS) return null
        return "$module.yaml"
    }
}

/**
 * 对 [com.kingzcheung.xime.rime.RimeEngine] 的最小依赖封装。
 *
 * 单纯的 object 单测里没有 librime，直接引用会让整个类在 JVM 测试中不可加载，
 * 因此把调用收进可 try/catch 的内部入口。
 */
internal object RimeEngineAccess {
    fun isReady(): Boolean =
        runCatching { com.kingzcheung.xime.rime.RimeEngine.isInitialized() }.getOrDefault(false)

    /** 方案合并后的 `speller/algebra` 规则列表。 */
    fun schemaAlgebra(schemaId: String): List<String> =
        runCatching {
            com.kingzcheung.xime.rime.RimeEngine.getInstance()
                .getSchemaList(schemaId, "speller/algebra")
        }.getOrDefault(emptyList())
}
