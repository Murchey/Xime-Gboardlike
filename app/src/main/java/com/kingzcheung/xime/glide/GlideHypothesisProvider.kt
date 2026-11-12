package com.kingzcheung.xime.glide

/** 一条假设的来源；几何与神经共用同一候选融合链路。 */
enum class GlideHypothesisOrigin { GEOMETRY, NEURAL }

/**
 * 假设来源接口：把「轨迹 → 合法编码串」与后续的 Rime 查询/重排解耦。
 *
 * 返回 null 表示当前不可用（模型缺失、ABI 不支持、超时、异常），调用方必须
 * 回退几何解码；返回空列表表示确实没有可用假设。
 */
interface GlideHypothesisProvider {
    fun hypotheses(
        points: List<GlidePoint>,
        geometry: GlideGeometrySnapshot,
        limit: Int,
        spellings: Collection<String>?,
        densityDpi: Float,
    ): List<GlideGeometryDecoder.Hypothesis>?
}

/** 不限定编码的约束：英文模式或没有方案编码表时使用。 */
object OpenGlideSpellingConstraint : GlideSpellingConstraint {
    override fun followers(states: List<Int>): Int = (1 shl 27) - 1

    override fun extend(states: List<Int>, letter: Int, next: MutableList<Int>) {
        next.clear()
        if (letter in 0..26) next.add(0)
    }
}
