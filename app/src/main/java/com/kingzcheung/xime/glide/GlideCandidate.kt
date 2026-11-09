package com.kingzcheung.xime.glide

/**
 * 一次滑动得到的整句候选。
 *
 * 与引擎候选索引解耦：id 在一次 [GlideCompositionSession] 内稳定，
 * 不随候选栏分页或重算改变。rawInput 是该候选对应的可编辑编码，
 * 供确认时重新验证，不使用显示文本反推。
 */
data class GlideCandidate(
    val id: String,
    val text: String,
    val rawInput: String,
    val comment: String = "",
    /** 编码到候选字符的粗略对齐，供局部编辑；索引为 rawInput 的字符区间。 */
    val spans: List<GlideSpan> = emptyList(),
    val score: Double = 0.0,
) {
    /** 是否可用于确认：文本和编码都不能为空。 */
    val isValid: Boolean get() = text.isNotEmpty() && rawInput.isNotEmpty()
}

/** 候选字符区间与对应编码区间，用于退格时定位受影响后缀。 */
data class GlideSpan(
    val charStart: Int,
    val charEnd: Int,
    val codeStart: Int,
    val codeEnd: Int,
)
