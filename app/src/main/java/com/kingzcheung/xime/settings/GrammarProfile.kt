package com.kingzcheung.xime.settings

/**
 * 语法模型（librime-octagram）调参。
 *
 * 默认值来自本仓库模型的离线评测（modelTrainning/scripts/eval_gram.py）：
 * - collocationMinLength = 2：长上下文平局率 36.4% → 0（本模型值域下唯一干净的收益项）；
 * - 惩罚保持 -12 / -24：本模型存的是 lnP + 10，改成上游推荐的 -4 会把命中压到地板分，
 *   所有候选同分，gram 直接退化为"不起作用"；
 * - collocationMaxLength = 5：与上游一致，上下文最多取 4 个汉字（n = max - 1）。
 *
 * P1 起 profile 随模型走（registry 持久化），这里只提供默认值。
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
}

/** 整数形式的小数按整数输出（-12.0 → -12），便于人工阅读且 YAML/Rime 都能解析。 */
private fun Double.asYamlNumber(): String =
    if (this == toLong().toDouble()) toLong().toString() else toString()
