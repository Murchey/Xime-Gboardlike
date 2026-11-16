package com.kingzcheung.xime.settings

/**
 * 方案分类：给方案列表做二级筛选（合并键方案单独一类，不和全拼/双拼混在一起）。
 *
 * 只按方案 id 归类，不看运行时绑定：13 个内置方案里，合并键变体都带 \`jian\` 后缀
 * （\`pinyin_14jian\`、\`pinyin_17jian_abcde\`、\`double_pinyin_14jian\` …）。
 */
enum class SchemaCategory(val label: String) {
    ALL("全部"),
    FULL("全拼"),
    SHUANGPIN("双拼"),
    MERGED("合并键"),
    OTHER("其他"),
}

object SchemaCategories {

    /** 合并键方案：\`<base>_<14|17|18>jian[_abcde]\`。 */
    private val MERGED = Regex("""_(14|17|18)jian(_abcde)?$""")

    fun of(schemaId: String): SchemaCategory {
        if (schemaId.isBlank()) return SchemaCategory.OTHER
        if (MERGED.containsMatchIn(schemaId)) return SchemaCategory.MERGED
        val id = schemaId.lowercase()
        return when {
            id.startsWith("double_pinyin") -> SchemaCategory.SHUANGPIN
            id == "rime_ice" || id.contains("pinyin") && !id.contains("t9") -> SchemaCategory.FULL
            else -> SchemaCategory.OTHER
        }
    }

    /** 是否命中某个筛选条件（[SchemaCategory.ALL] 恒真）。 */
    fun matches(schemaId: String, filter: SchemaCategory): Boolean =
        filter == SchemaCategory.ALL || of(schemaId) == filter
}
