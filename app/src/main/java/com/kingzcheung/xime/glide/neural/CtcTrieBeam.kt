package com.kingzcheung.xime.glide.neural

import com.kingzcheung.xime.glide.GlideGeometryDecoder
import com.kingzcheung.xime.glide.GlideHypothesisOrigin
import com.kingzcheung.xime.glide.GlideSpellingConstraint
import kotlin.math.max
import kotlin.math.pow

/**
 * 受方案编码约束的 CTC 前缀 beam。
 *
 * 与 FUTO 的 TrieBeamSearch 同思路：状态只保留「当前编码前缀 + 所在音节节点」，
 * 空白与字母转移按 CTC 规则合并。收尾只接受停在完整音节边界的前缀，
 * 避免把半个音节当成完整编码交给 Rime。
 */
object CtcTrieBeam {

    data class Params(
        val beamWidth: Int = 600,
        val maxLetters: Int = 48,
        /** 长度归一指数；中文长句实测 0（原始 CTC）优于 FUTO 的英文参数 0.4056。 */
        val gamma: Float = 0f,
        /** 长度奖励；默认 0。 */
        val lambda: Float = 0f,
    )

    private const val NEG_INF = Float.NEGATIVE_INFINITY

    private class Entry(
        var blank: Float,
        var nonBlank: Float,
        var states: List<Int>,
    )

    fun decode(
        emissions: FutoEmissions,
        constraint: GlideSpellingConstraint,
        limit: Int,
        params: Params = Params(),
    ): List<GlideGeometryDecoder.Hypothesis> {
        if (limit <= 0) return emptyList()
        val letters = emissions.mergedLogProbs()
        val blanks = emissions.blankLogProbs()
        val alphabet = emissions.alphabet
        val letterIndices = alphabet.map { letterIndex(it) }

        var beam = HashMap<String, Entry>()
        beam[""] = Entry(0f, NEG_INF, listOf(0))

        for (step in 0 until FutoEmissions.TIMESTEPS) {
            val next = HashMap<String, Entry>(beam.size * 4)
            val blankLog = blanks[step]
            val logRow = letters[step]

            for ((prefix, entry) in beam) {
                merge(next, prefix, entry.blank + blankLog, entry.nonBlank + blankLog, entry.states)
                val last = prefix.lastOrNull()
                val total = FutoEmissions.logAdd(entry.blank, entry.nonBlank)
                for (i in alphabet.indices) {
                    val emit = logRow[i]
                    if (emit == NEG_INF) continue
                    if (prefix.length >= params.maxLetters) continue
                    val states = ArrayList<Int>(2)
                    constraint.extend(entry.states, letterIndices[i], states)
                    if (states.isEmpty()) continue
                    val nextPrefix = prefix + alphabet[i]
                    if (alphabet[i] == last) {
                        if (entry.blank == NEG_INF) continue
                        merge(next, nextPrefix, NEG_INF, entry.blank + emit, states)
                    } else {
                        if (total == NEG_INF) continue
                        merge(next, nextPrefix, NEG_INF, total + emit, states)
                    }
                }
            }

            beam = prune(next, params.beamWidth)
            if (beam.isEmpty()) return emptyList()
        }

        val scored = ArrayList<GlideGeometryDecoder.Hypothesis>(beam.size)
        for ((prefix, entry) in beam) {
            if (!entry.states.contains(0)) continue
            val raw = FutoEmissions.logAdd(entry.blank, entry.nonBlank)
            if (raw == NEG_INF) continue
            val length = max(prefix.length, 1)
            val score = raw / length.toFloat().pow(params.gamma) + params.lambda * length
            scored += GlideGeometryDecoder.Hypothesis(prefix, -score, GlideHypothesisOrigin.NEURAL)
        }
        scored.sortWith(compareBy({ it.cost }, { it.letters }))
        return if (scored.size > limit) scored.subList(0, limit).toList() else scored
    }

    private fun merge(
        map: HashMap<String, Entry>,
        prefix: String,
        blank: Float,
        nonBlank: Float,
        states: List<Int>,
    ) {
        val existing = map[prefix]
        if (existing == null) {
            map[prefix] = Entry(blank, nonBlank, states)
            return
        }
        existing.blank = FutoEmissions.logAdd(existing.blank, blank)
        existing.nonBlank = FutoEmissions.logAdd(existing.nonBlank, nonBlank)
    }

    private fun prune(map: HashMap<String, Entry>, width: Int): HashMap<String, Entry> {
        if (map.size <= width) return map
        val kept = map.entries
            .sortedByDescending { FutoEmissions.logAdd(it.value.blank, it.value.nonBlank) }
            .take(width)
        val out = HashMap<String, Entry>(kept.size * 2)
        for (entry in kept) out[entry.key] = entry.value
        return out
    }

    private fun letterIndex(ch: Char): Int = if (ch == ';') 26 else ch - 'a'
}
