package com.kingzcheung.xime.glide.neural

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max

/**
 * FUTO 编码器的一次输出。
 *
 * 原始张量固定为 32 个时间步 × 65 类（64 个键槽 + blank），与传入的键数无关；
 * 只有前 slotCodes 个槽位有效。重复键（分体键盘）由 mergedLogProbs 做
 * logsumexp 合并成一个字母类。
 */
class FutoEmissions(
    val logEmissions: FloatArray,
    val intention: FloatArray,
    val alphabet: List<Char>,
    val slotCodes: List<Char>,
) {
    init {
        require(logEmissions.size == TIMESTEPS * CLASSES) {
            "logEmissions must be " + TIMESTEPS + " x " + CLASSES + ", got " + logEmissions.size
        }
        require(intention.size == TIMESTEPS) { "intention must be " + TIMESTEPS }
        require(alphabet.isNotEmpty()) { "empty alphabet" }
    }

    /** 合并后的字母 log 概率：[32][alphabet.size]。 */
    fun mergedLogProbs(): Array<FloatArray> {
        val groups = HashMap<Char, MutableList<Int>>()
        slotCodes.forEachIndexed { slot, code -> groups.getOrPut(code) { mutableListOf() }.add(slot) }
        return Array(TIMESTEPS) { step ->
            FloatArray(alphabet.size) { letter ->
                val slots = groups.getValue(alphabet[letter])
                var acc = NEG_INF
                for (slot in slots) {
                    acc = logAdd(acc, logEmissions[step * CLASSES + slot])
                }
                acc
            }
        }
    }

    fun blankLogProbs(): FloatArray =
        FloatArray(TIMESTEPS) { step -> logEmissions[step * CLASSES + BLANK_CLASS] }

    companion object {
        const val TIMESTEPS = 32
        const val CLASSES = 65
        const val BLANK_CLASS = 64
        const val NEG_INF = Float.NEGATIVE_INFINITY

        fun logAdd(a: Float, b: Float): Float {
            if (a == NEG_INF) return b
            if (b == NEG_INF) return a
            val m = max(a, b)
            return m + ln(exp(a - m) + exp(b - m))
        }
    }
}
