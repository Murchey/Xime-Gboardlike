package com.kingzcheung.xime.association

import kotlin.math.exp

/**
 * 把模型的「下一个字」候选扩成「下一个词」。
 *
 * 模型词表是字符级的，联想栏因此永远只有单字。这里让模型自己续写：
 * 用 context + 首字再推理一次，拿到第二个字拼成双字候选（「不」→「不错」）。
 * 续写是否可信用 top-k 的相对 softmax 份额判定，低置信度宁可不拼，
 * 避免把一个错的字替用户按下去。
 */
object AssociationExpander {

    data class Config(
        /** 只给前 N 个首字续写；每多一个就多一次推理。 */
        val maxSeeds: Int = 2,
        /** 续写候选只取 top-k 参与份额计算。 */
        val continuationTopK: Int = 8,
        /**
         * 续写首选在 top-k 里的概率份额下限，低于此值不拼词。
         *
         * 实测（v5 模型，val3 随机 113 个位置）：份额中位数 0.349、P25 0.273、P10 0.224。
         * 取 0.30 时约 59% 上下文会拼词；0.55 只有 13% 触发（功能几乎看不见），
         * 0.35 会把「今天天气→不(0.327)」「我想→起(0.340)」这类正常情形也挡掉。
         */
        val minShare: Float = 0.30f,
        /** 拼词惩罚：两个 logit 相加后要扣掉，避免词无脑压过单字。 */
        val completionTax: Float = 1.0f,
        /** 词候选与它自己的首字之间留的固定间距。 */
        val seedGap: Float = 0.5f,
    )

    val DEFAULT = Config()

    /** 只对单个汉字续写；标点/空白/英文不拼。 */
    fun isExpandable(text: String): Boolean =
        text.length == 1 && text[0] in '\u4e00'..'\u9fff'

    /**
     * 从一次续写的候选里挑出可信的一步；不可信返回 null。
     *
     * 返回的是原始候选（logit 未改），份额只用于判定。
     */
    fun bestContinuation(list: List<AssociationCandidate>, cfg: Config = DEFAULT): AssociationCandidate? {
        val top = ArrayList<AssociationCandidate>(cfg.continuationTopK)
        for (c in list) {
            if (c.text.length != 1) continue
            top.add(c)
            if (top.size >= cfg.continuationTopK) break
        }
        if (top.isEmpty()) return null
        val maxLogit = top.maxOf { it.score }
        var sum = 0.0
        for (c in top) sum += exp((c.score - maxLogit).toDouble())
        if (sum <= 0.0) return null
        val share = exp((top[0].score - maxLogit).toDouble()) / sum
        return if (share >= cfg.minShare) top[0] else null
    }

    /**
     * 为前 [Config.maxSeeds] 个可扩展首字各续一个字，返回拼好的双字候选（不含原单字）。
     *
     * [predict] 注入推理函数，便于 JVM 单测；真实调用是 OnnxAssociationEngine.predict。
     */
    suspend fun expand(
        context: String,
        seeds: List<AssociationCandidate>,
        cfg: Config = DEFAULT,
        predict: suspend (String) -> List<AssociationCandidate>,
    ): List<AssociationCandidate> {
        if (context.isEmpty() || seeds.isEmpty()) return emptyList()
        val out = ArrayList<AssociationCandidate>(cfg.maxSeeds)
        val seen = HashSet<String>()
        var attempts = 0
        for (seed in seeds) {
            if (attempts >= cfg.maxSeeds) break
            if (!isExpandable(seed.text)) continue
            attempts++
            val next = bestContinuation(predict(context + seed.text), cfg) ?: continue
            val phrase = seed.text + next.text
            if (!seen.add(phrase)) continue
            // 词不越过自己的首字：候选栏读起来是「不 / 不错 / 是 / 是的」
            val score = minOf(seed.score + next.score - cfg.completionTax, seed.score - cfg.seedGap)
            out.add(AssociationCandidate(phrase, score))
        }
        return out
    }
}
