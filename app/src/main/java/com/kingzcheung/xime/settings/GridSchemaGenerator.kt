package com.kingzcheung.xime.settings

/**
 * 从现有方案派生「合并键」方案（14/17/18 键 × QWERTY/ABCDE）。
 *
 * 做法：保留基础方案的词库、识别器、翻译器等全部配置，只改三处——
 * 1. schema_id / name / translator.prism 换成派生 id（prism 必须唯一，否则
 *    librime 只编一份、派生方案就没有自己的拼写表，表现为“切进去了但按组码出的是普通拼音”）；
 * 2. speller.alphabet / initials 换成组码集合；
 * 3. 在 algebra 末尾追加组码规则（组内字母 → 组码），双拼方案里它叠在双拼编码之后。
 *
 * 这样全拼（rime_ice）和 7 种双拼都能得到同一套键面，用户导入的第三方双拼也适用。
 */
object GridSchemaGenerator {

    /** 需要派生合并键方案的基础方案（全拼 + 内置全部双拼）。 */
    val BASE_SCHEMA_IDS = listOf(
        "rime_ice",
        "double_pinyin",
        "double_pinyin_abc",
        "double_pinyin_flypy",
        "double_pinyin_jiajia",
        "double_pinyin_mspy",
        "double_pinyin_sogou",
        "double_pinyin_ziguang",
    )

    /** 派生方案 id：base_14jian / base_17jian_abcde 之类。 */
    fun derivedId(baseId: String, layout: String, order: String): String {
        val l = KeyboardLayoutPreset.normalizeLayout(layout)
        val suffix = if (KeyboardLayoutPreset.normalizeOrder(order) == KeyboardLayoutPreset.ORDER_ABCDE) "_abcde" else ""
        return baseId + "_" + l + "jian" + suffix
    }

/** 派生 id 是否属于合并键方案。 */
    fun isDerived(schemaId: String): Boolean = DERIVED.containsMatchIn(schemaId)

    /** 派生 id → 基础方案 id；不是派生 id 时返回它自己。 */
    fun baseOf(schemaId: String): String = schemaId.replace(DERIVED, "")

    /** 派生 id → 键面段名（qwerty_14 / qwerty_18_abcde）。 */
    fun sectionOf(schemaId: String): String? {
        val m = DERIVED.find(schemaId) ?: return null
        val layout = m.groupValues[1]
        val abcde = m.groupValues[2]
        return "qwerty_" + layout + (if (abcde.isNotEmpty()) "_abcde" else "")
    }

    private val DERIVED = Regex("""_(14|17|18)jian(_abcde)?$""")

    /** 早期随包发布的合并键方案（pinyin_14jian 等）→ 现在由派生承载（rime_ice_14jian 等）。 */
    private val LEGACY = Regex("""^pinyin_(14|17|18)jian(_abcde)?$""")

    /**
     * 老 id 迁移：随包那 6 份 `pinyin_{14,17,18}jian[_abcde]` 已由运行时派生取代，
     * 迁移到全拼基础方案的派生版；不是老 id 时返回 null。
     */
    fun legacyToDerived(schemaId: String): String? {
        val m = LEGACY.find(schemaId) ?: return null
        return "rime_ice_" + m.groupValues[1] + "jian" + m.groupValues[2]
    }

    /** 合并键分组（键面顺序），来自 [KeyboardGridTables]。 */
    fun groupsOf(layout: String, order: String): List<String> {
        val rows = if (KeyboardLayoutPreset.normalizeOrder(order) == KeyboardLayoutPreset.ORDER_ABCDE) {
            KeyboardGridTables.abcdeRows(KeyboardGridTables.shapeOf(layout))
        } else {
            KeyboardGridTables.rowsFor(layout)
        }
        return rows.flatten().map { it.letters }
    }

    /** 组码集合（去重排序）：alphabet / initials 用它。 */
    internal fun codesOf(groups: List<String>): String =
        groups.map { it.first() }.distinct().sorted().joinToString("")

    /** 组码规则：组内字母 → 组码。 */
    internal fun rulesOf(groups: List<String>): List<String> =
        groups.filter { it.length > 1 }.map { g ->
            val cls = g.toCharArray().distinct().sorted().joinToString("")
            "    - derive/[" + cls + "]/" + g.first() + "/"
        }

    /**
     * 生成派生方案文本；基础文本缺少必要锚点（algebra / prism 等）时返回 null。
     */
    fun generate(
        baseText: String,
        baseId: String,
        targetId: String,
        displayName: String,
        groups: List<String>,
    ): String? {
        if (groups.isEmpty()) return null
        val lines = baseText.lines().toMutableList()

        fun replaceFirst(pattern: String, replacement: String): Boolean {
            val regex = Regex(pattern)
            val idx = lines.indexOfFirst { regex.matches(it) }
            if (idx < 0) return false
            lines[idx] = replacement
            return true
        }

        if (!replaceFirst("  schema_id: .*", "  schema_id: " + targetId)) return null
        replaceFirst("  name: .*", "  name: " + displayName)
        // prism：基础方案可能没写（默认取 schema_id），缺了就在 translator 段补一行
        if (!replaceFirst("  prism: .*", "  prism: " + targetId)) {
            val translatorIdx = lines.indexOfFirst { it.trim() == "translator:" }
            if (translatorIdx < 0) return null
            lines.add(translatorIdx + 1, "  prism: " + targetId)
        }

        val codes = codesOf(groups)
        if (!replaceFirst("  alphabet: .*", "  alphabet: " + codes)) return null
        if (!replaceFirst("  initials: .*", "  initials: " + codes)) return null

        val algebraIdx = lines.indexOfFirst { it.trim() == "algebra:" }
        if (algebraIdx < 0) return null
        var lastRule = algebraIdx
        for (i in algebraIdx + 1 until lines.size) {
            val line = lines[i]
            if (line.startsWith("    - ")) lastRule = i
            else if (line.isNotBlank() && !line.startsWith("      ") && !line.startsWith("    #")) break
        }
        lines.addAll(lastRule + 1, listOf("    # 合并键：组内字母 → 组码") + rulesOf(groups))

        val commentsIdx = lines.indexOfFirst { it.trim().startsWith("always_show_comments:") }
        if (commentsIdx >= 0) {
            lines[commentsIdx] = "  always_show_comments: false"
        } else {
            val translatorIdx = lines.indexOfFirst { it.trim() == "translator:" }
            if (translatorIdx >= 0) lines.add(translatorIdx + 1, "  always_show_comments: false")
        }

        val out = lines.joinToString("\n")
        if (out.contains("  prism: " + baseId + "\n")) return null
        return if (out.endsWith("\n")) out else out + "\n"
    }
}
