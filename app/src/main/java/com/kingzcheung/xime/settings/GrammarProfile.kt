package com.kingzcheung.xime.settings

import org.json.JSONObject

/**
 * 语法模型（librime-octagram）调参。
 *
 * 默认值来自本仓库模型的离线评测（modelTrainning/scripts/eval_gram.py）：
 * - collocationMinLength = 2：长上下文平局率 36.4% → 0（本模型值域下唯一干净的收益项）；
 * - 惩罚保持 -12 / -24：本模型存的是 lnP + 10，改成上游推荐的 -4 会把命中压到地板分，
 *   所有候选同分，gram 直接退化为"不起作用"；
 * - collocationMaxLength = 5：与上游一致，上下文最多取 4 个汉字（n = max - 1）。
 *
 * **不同模型的打分尺度不同**（lnP 与 lnP+10 差一个数量级），所以参数必须可调、
 * 并且按模型保存：用户从别处装的 .gram 用默认值可能完全不起作用。
 */
data class GrammarProfile(
    val collocationMaxLength: Int = 5,
    val collocationMinLength: Int = 2,
    val collocationPenalty: Double = -12.0,
    val nonCollocationPenalty: Double = -12.0,
    val weakCollocationPenalty: Double = -24.0,
    val rearPenalty: Double = -18.0,
    val contextualSuggestions: Boolean = true,
) {
    /** 写入 schema patch 的键值对（命名空间与 librime-octagram 的 Octagram 构造一致）。 */
    fun patchEntries(): List<Pair<String, String>> = listOf(
        "grammar/collocation_max_length" to collocationMaxLength.toString(),
        "grammar/collocation_min_length" to collocationMinLength.toString(),
        "grammar/collocation_penalty" to collocationPenalty.asYamlNumber(),
        "grammar/non_collocation_penalty" to nonCollocationPenalty.asYamlNumber(),
        "grammar/weak_collocation_penalty" to weakCollocationPenalty.asYamlNumber(),
        "grammar/rear_penalty" to rearPenalty.asYamlNumber(),
        "translator/contextual_suggestions" to contextualSuggestions.toString(),
    )

    /** 收敛到合法范围：滑杆/外部输入都先过这一层，避免写出引擎无法解析的值。 */
    fun coerce(): GrammarProfile {
        val max = collocationMaxLength.coerceIn(MAX_LENGTH_RANGE)
        return copy(
            collocationMaxLength = max,
            collocationMinLength = collocationMinLength.coerceIn(1, max),
            collocationPenalty = collocationPenalty.coerceIn(PENALTY_RANGE),
            nonCollocationPenalty = nonCollocationPenalty.coerceIn(PENALTY_RANGE),
            weakCollocationPenalty = weakCollocationPenalty.coerceIn(PENALTY_RANGE),
            rearPenalty = rearPenalty.coerceIn(PENALTY_RANGE),
        )
    }

    fun toJson(): String = JSONObject().apply {
        put("collocationMaxLength", collocationMaxLength)
        put("collocationMinLength", collocationMinLength)
        put("collocationPenalty", collocationPenalty)
        put("nonCollocationPenalty", nonCollocationPenalty)
        put("weakCollocationPenalty", weakCollocationPenalty)
        put("rearPenalty", rearPenalty)
        put("contextualSuggestions", contextualSuggestions)
    }.toString()

    companion object {
        val MAX_LENGTH_RANGE = 1..8
        val PENALTY_RANGE = -40.0..0.0

        /** 内置模型（本仓库评测值）。 */
        val BUNDLED = GrammarProfile()

        /**
         * 各模型的推荐值：
         * - 内置模型用本仓库评测值；
         * - rime-ice 配套的 zh-moqi 按其方案自带值（non_collocation_penalty = -4）；
         * - 其他未知模型回落内置值，并提示用户按模型文档调整。
         */
        fun recommended(language: String): GrammarProfile = when {
            language.isBlank() -> BUNDLED
            language.equals("XimeGboard", ignoreCase = true) -> BUNDLED
            language.startsWith("zh-moqi", ignoreCase = true) -> BUNDLED.copy(nonCollocationPenalty = -4.0)
            else -> BUNDLED
        }

        fun fromJson(raw: String?): GrammarProfile? = runCatching {
            if (raw.isNullOrBlank()) return null
            val obj = JSONObject(raw)
            val base = BUNDLED
            GrammarProfile(
                collocationMaxLength = obj.optInt("collocationMaxLength", base.collocationMaxLength),
                collocationMinLength = obj.optInt("collocationMinLength", base.collocationMinLength),
                collocationPenalty = obj.optDouble("collocationPenalty", base.collocationPenalty),
                nonCollocationPenalty = obj.optDouble("nonCollocationPenalty", base.nonCollocationPenalty),
                weakCollocationPenalty = obj.optDouble("weakCollocationPenalty", base.weakCollocationPenalty),
                rearPenalty = obj.optDouble("rearPenalty", base.rearPenalty),
                contextualSuggestions = obj.optBoolean("contextualSuggestions", base.contextualSuggestions),
            ).coerce()
        }.getOrNull()
    }
}

/** 整数形式的小数按整数输出（-12.0 → -12），便于人工阅读且 YAML/Rime 都能解析。 */
private fun Double.asYamlNumber(): String =
    if (this == toLong().toDouble()) toLong().toString() else toString()
