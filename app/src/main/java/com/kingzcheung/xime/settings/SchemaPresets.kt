package com.kingzcheung.xime.settings

/** 输入方案预设：雾凇全拼 + 主流双拼。 */
data class SchemaPreset(
    val schemaId: String,
    val fallbackName: String,
)

object SchemaPresets {
    val ALL: List<SchemaPreset> = listOf(
        SchemaPreset("rime_ice", "雾凇全拼"),
        SchemaPreset("double_pinyin_flypy", "小鹤双拼"),
        SchemaPreset("double_pinyin", "自然码双拼"),
        SchemaPreset("double_pinyin_mspy", "微软双拼"),
        SchemaPreset("double_pinyin_sogou", "搜狗双拼"),
        SchemaPreset("double_pinyin_ziguang", "紫光双拼"),
        SchemaPreset("double_pinyin_abc", "智能 ABC 双拼"),
        SchemaPreset("double_pinyin_jiajia", "拼音加加双拼"),
    )

    val schemaIds: Set<String> = ALL.map { it.schemaId }.toSet()
}
