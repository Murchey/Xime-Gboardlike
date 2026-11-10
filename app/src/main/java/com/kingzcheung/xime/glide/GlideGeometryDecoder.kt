package com.kingzcheung.xime.glide

import com.kingzcheung.xime.correction.SpatialTapModel
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * 全拼滑行解码器：按几何代价枚举完整音节组成的字母串，代价低的在前。
 *
 * 算法与参数移植自水杉输入法（msime，GPL-3.0）的 `crates/engine/src/pinyin/glide.rs`。
 * 与"按采样点判停留/经过"的做法不同，它用**连续的几何代价**而不是离散阈值，
 * 因此不依赖设备采样密度：
 *
 * 1. 每个对齐点付它到字母键中心距离的平方 —— 笔画可偏离键位一部分键宽；
 * 2. 两个对齐字母之间的每个采样点付它到**两键连线**距离的平方 ——
 *    笔画拐弯处必须对齐一个字母，只是经过的键不会额外付费；
 * 3. 沿连线方向**往回走**要付费 —— 所以来回折返的 `keneng` 不会被读成 `keng`；
 * 4. 对齐时**跳过的停留**付固定罚分 —— 在键上停一下即确认该键；
 * 5. 每个字母付一个小的固定价 —— 同样好的两个串里短的那个胜出。
 *
 * 坐标除以键宽键高归一化，一个键即单位正方形，与像素、密度、键距无关。
 * 词典重排由调用方（Rime）完成，本类只产出字母串。
 */
object GlideGeometryDecoder {

    /** 一笔最多重采样成多少个点。 */
    private const val MAX_SAMPLES = 128
    /** 重采样步长（键为单位）。 */
    private const val SAMPLE_STEP = 0.25f
    /** 一个字母对齐代价为 1 所对应的距离平方（约半个键）。 */
    private const val ALIGN_SCALE = 0.3f
    private const val GAUSSIAN_WEIGHT = 0.5f
    /** 偏离两键连线多远（距离平方）时，每单位笔画长度代价为 1。 */
    private const val LINE_SCALE = 0.25f
    /** 每个字母的固定价。小值：让词典而不是几何去分 zhong / zong。 */
    private const val LETTER_COST = 0.8f
    /** 与前一个字母相同时的额外价（笔画画不出连按两次）。 */
    private const val REPEAT_COST = 0.8f
    /** 沿连线反方向每走一个键的代价。 */
    private const val BACKTRACK_COST = 2.0f
    /** 没有字母对齐到的停留的罚分。 */
    private const val SKIPPED_DWELL_COST = 4.0f
    /** 离每个采样点都超过此距离（键为单位）的键不可能在串里。 */
    private const val NEAR_DISTANCE = 1.4f
    /** 每个采样点保留的前缀数。 */
    private const val TOKENS_PER_SAMPLE = 6
    /** 比同点最便宜前缀贵出此值的前缀丢弃。 */
    private const val BEAM = 10.0f
    /** 考虑的最长字母串。 */
    private const val MAX_GLIDE_LETTERS = 30
    /** 手指在 DWELL_RADIUS 以内停留这么久的毫秒数算一次停留。 */
    private const val DWELL_MS = 120L
    private const val DWELL_RADIUS = 0.35f

    /** 一个解码结果：小写全拼字母（不带分隔符，每个音节完整）。 */
    data class Hypothesis(val letters: String, val cost: Float)

    private data class Sample(val x: Float, val y: Float, val dwell: Boolean)

    private class Prefix(
        val letters: MutableList<Int> = mutableListOf(),
        val states: MutableList<Int> = mutableListOf(),
        val costs: MutableList<Pair<Int, Float>> = mutableListOf(),
    )

    /**
     * @param points 轨迹点（键盘局部坐标，px）
     * @param keys 键字母到矩形中心的映射（只用到 a..z）
     * @param limit 返回的假设数
     */
    fun decode(
        points: List<GlidePoint>,
        geometry: GlideGeometrySnapshot,
        limit: Int = 8,
        spellings: Collection<String>? = null,
        densityDpi: Float = 160f,
        gaussianCorrection: Boolean = true,
    ): List<Hypothesis> {
        if (limit <= 0 || points.size < 2 || !geometry.isValid) return emptyList()

        val centers = centersOf(geometry) ?: return emptyList()
        val keyWidth = minKeyWidth(geometry)
        val keyHeight = minKeyHeight(geometry)
        if (keyWidth <= 0f || keyHeight <= 0f) return emptyList()

        val normalized = points.map { GlidePoint(it.x / keyWidth, it.y / keyHeight, it.timeMs, it.pointerId) }
        val samples = resample(normalized)
        if (samples.size < 2) return emptyList()

        val keyRects = geometry.eligibleKeys.filter { it.code.length == 1 }.associate { it.code[0].lowercaseChar() to it.rect }
        val gaussianCosts = if (gaussianCorrection && densityDpi.isFinite() && densityDpi > 0f) {
            Array(centers.size) { letter ->
                val ch = if (letter == SEMICOLON_INDEX) ';' else 'a' + letter
                val rect = keyRects.getValue(ch)
                val geo = SpatialTapModel.KeyGeometry(rect.centerX, rect.centerY, rect.width, rect.height)
                FloatArray(samples.size) { j ->
                    // 与连线/回溯代价融合的空间项，固定权重避免双重累加旧距离项。
                    SpatialTapModel.alignmentCost(samples[j].x * keyWidth, samples[j].y * keyHeight, geo, densityDpi)
                        ?.times(GAUSSIAN_WEIGHT)
                        ?: ((samples[j].x - rect.centerX / keyWidth).pow(2) +
                            (samples[j].y - rect.centerY / keyHeight).pow(2)) / ALIGN_SCALE
                }
            }
        } else null
        val repeats = GlideRepeatGesture.detect(normalized, centers.map { (x, y) -> x / keyWidth to y / keyHeight })
        return Decoder(centers, keyWidth, keyHeight, samples, spellings?.let { GlideSpellingTrie(it) }, repeats,
            normalized.zipWithNext().sumOf { (a, b) -> distance(a, b).toDouble() }.toFloat(), gaussianCosts).run(limit)
    }

    /** 分号键（双拼 ing 韵母）的字母索引，输出时展开为 "ing"。 */
    const val SEMICOLON_INDEX = 26

    private fun centersOf(geometry: GlideGeometrySnapshot): Array<Pair<Float, Float>>? {
        val out = arrayOfNulls<Pair<Float, Float>>(27)
        for (key in geometry.eligibleKeys) {
            if (key.code.length != 1) continue
            val c = key.code[0].lowercaseChar()
            if (c in 'a'..'z') out[c - 'a'] = key.rect.centerX to key.rect.centerY
            else if (c == ';') out[SEMICOLON_INDEX] = key.rect.centerX to key.rect.centerY
        }
        // a-z 必须齐全；分号键可选（双拼方案才有）
        if (out.take(26).any { it == null }) return null
        // 无分号键时收缩到 26，保证索引连续
        val size = if (out[SEMICOLON_INDEX] != null) 27 else 26
        return (0 until size).map { out[it]!! }.toTypedArray()
    }

    private fun minKeyWidth(geometry: GlideGeometrySnapshot): Float =
        geometry.eligibleKeys.minOfOrNull { it.rect.width } ?: 0f

    private fun minKeyHeight(geometry: GlideGeometrySnapshot): Float =
        geometry.eligibleKeys.minOfOrNull { it.rect.height } ?: 0f

    private fun distance(a: GlidePoint, b: GlidePoint): Float =
        sqrt((a.x - b.x).pow(2) + (a.y - b.y).pow(2))

    /** 各次停留的弧长位置：手指在 DWELL_RADIUS 内停留 DWELL_MS 或更久之处。 */
    private fun dwellPositions(points: List<GlidePoint>, arc: List<Float>): List<Float> {
        val dwells = mutableListOf<Float>()
        var start = 0
        while (start < points.size) {
            var end = start
            while (end + 1 < points.size && distance(points[start], points[end + 1]) <= DWELL_RADIUS) {
                end++
            }
            val rested = (points[end].timeMs - points[start].timeMs) >= DWELL_MS
            if (rested) {
                dwells += (arc[start] + arc[end]) / 2f
                start = end + 1
            } else {
                start++
            }
        }
        return dwells
    }

    /** 沿笔画等距的采样点，含两端；离每次停留最近的采样点做标记。 */
    private fun resample(points: List<GlidePoint>): List<Sample> {
        val arc = ArrayList<Float>(points.size)
        var length = 0f
        arc.add(0f)
        for (i in 1 until points.size) {
            length += distance(points[i - 1], points[i])
            arc.add(length)
        }
        val step = max(SAMPLE_STEP, length / (MAX_SAMPLES - 1).toFloat())
        val count = (ceil(length / step).toInt() + 1).coerceIn(2, MAX_SAMPLES)
        val samples = ArrayList<Sample>(count)
        var segment = 0
        for (index in 0 until count) {
            val target = length * index.toFloat() / (count - 1).toFloat()
            while (segment + 2 < arc.size && arc[segment + 1] < target) segment++
            val a = points[segment]
            val b = points[segment + 1]
            val span = arc[segment + 1] - arc[segment]
            val t = if (span > 0f) ((target - arc[segment]) / span).coerceIn(0f, 1f) else 0f
            samples += Sample(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, false)
        }
        if (length > 0f) {
            for (dwell in dwellPositions(points, arc)) {
                val index = ((dwell / length) * (count - 1)).toInt().coerceIn(0, count - 1)
                samples[index] = samples[index].copy(dwell = true)
            }
        }
        return samples
    }

    private class Decoder(
        private val centers: Array<Pair<Float, Float>>,
        keyWidth: Float,
        keyHeight: Float,
        private val samples: List<Sample>,
        private val schemaTrie: GlideSpellingConstraint?,
        repeatCues: List<GlideRepeatGesture.Cue>,
        traceLength: Float,
        private val gaussianCosts: Array<FloatArray>?,
    ) {
        private data class Repeat(val key: Int, val start: Int, val end: Int)
        private val repeats = repeatCues.map { cue ->
            val start = if (traceLength > 0f) (cue.startArc / traceLength * samples.lastIndex).toInt() else 0
            val end = if (traceLength > 0f) ceil(cue.endArc / traceLength * samples.lastIndex).toInt() else 0
            Repeat(cue.key, start.coerceIn(0, samples.lastIndex), end.coerceIn(0, samples.lastIndex))
        }.filter { it.end > it.start }
        private val letterCount = centers.size
        /** 把字母 l 对齐到采样点 j 的代价。 */
        private val align = arrayOfNulls<FloatArray>(letterCount)
        /** 离各字母键 NEAR_DISTANCE 以内的采样点（升序）。 */
        private val reach = Array(letterCount) { mutableListOf<Int>() }
        /** 字母对的逐点累计代价：偏离连线、反向走、跳过停留。 */
        private val lines = arrayOfNulls<FloatArray>(letterCount * letterCount)
        val count: Int = samples.size

        init {
            val length = samples.zipWithNext().sumOf { (a, b) ->
                sqrt((a.x - b.x).pow(2) + (a.y - b.y).pow(2)).toDouble()
            }.toFloat()
            val weight = if (count > 1) length / (count - 1).toFloat() else 0f
            val near = NEAR_DISTANCE * NEAR_DISTANCE

            val nc = centers.map { (x, y) -> (x / keyWidth) to (y / keyHeight) }
            val squared = Array(letterCount) { l ->
                val (cx, cy) = nc[l]
                FloatArray(count) { j ->
                    val s = samples[j]
                    (s.x - cx).pow(2) + (s.y - cy).pow(2)
                }
            }
            for (l in 0 until letterCount) {
                for (j in 0 until count) {
                    if (squared[l][j] <= near) reach[l].add(j)
                }
                if (reach[l].isNotEmpty()) {
                    align[l] = gaussianCosts?.get(l) ?: FloatArray(count) { j -> squared[l][j] / ALIGN_SCALE }
                }
            }
            for (from in 0 until letterCount) {
                for (to in 0 until letterCount) {
                    if (align[from] == null || align[to] == null) continue
                    val a = nc[from]
                    val b = nc[to]
                    val dx = b.first - a.first
                    val dy = b.second - a.second
                    val span = dx * dx + dy * dy
                    val segLen = sqrt(span)
                    var total = 0f
                    var previous: Sample? = null
                    val arr = FloatArray(count)
                    for (j in 0 until count) {
                        val s = samples[j]
                        val p = previous
                        if (p != null && segLen > 0f) {
                            val along = ((s.x - p.x) * dx + (s.y - p.y) * dy) / segLen
                            total += max(-along, 0f) * BACKTRACK_COST
                        }
                        previous = s
                        val t = if (span > 0f) {
                            (((s.x - a.first) * dx + (s.y - a.second) * dy) / span).coerceIn(0f, 1f)
                        } else 0f
                        val off = (s.x - a.first - dx * t).pow(2) + (s.y - a.second - dy * t).pow(2)
                        total += off / LINE_SCALE * weight
                        if (s.dwell) total += SKIPPED_DWELL_COST
                        arr[j] = total
                    }
                    lines[from * letterCount + to] = arr
                }
            }
        }

        fun run(limit: Int): List<Hypothesis> {
            val trie = schemaTrie ?: GlideSyllableTrie.get()
            val last = count - 1
            val near = (0 until letterCount).filter { align[it] != null }
            val states = mutableListOf<Int>()
            var level = mutableListOf<Prefix>()

            for (letter in near) {
                if (reach[letter].firstOrNull() != 0) continue
                if (schemaTrie != null) trie.extend(listOf(0), letter, states)
                else extendLetter(trie, listOf(0), letter, states)
                if (states.isEmpty()) continue
                val cost = align[letter]!![0] + LETTER_COST
                level += Prefix(mutableListOf(letter), states.toMutableList(), mutableListOf(0 to cost))
            }

            val finals = mutableListOf<Hypothesis>()
            val floors = FloatArray(count)
            repeat(MAX_GLIDE_LETTERS - 1) {
                floors.fill(Float.POSITIVE_INFINITY)
                val next = mutableListOf<Prefix>()
                for (prefix in level) {
                    val from = prefix.letters.last()
                    val followers = trie.followers(prefix.states)
                    val earliest = prefix.costs.first().first
                    for (letter in near) {
                        // ; 的可接性按 ing 首字母 i 判定
                        val probe = if (schemaTrie == null && letter == SEMICOLON_INDEX) ('i' - 'a') else letter
                        if (followers and (1 shl probe) == 0) continue
                        if ((reach[letter].lastOrNull() ?: -1) <= earliest) continue
                        if (schemaTrie != null) trie.extend(prefix.states, letter, states)
                        else extendLetter(trie, prefix.states, letter, states)
                        if (states.isEmpty()) continue
                        val line = lines[from * letterCount + letter] ?: continue
                        val al = align[letter]!!
                        val step = if (letter == from) LETTER_COST + REPEAT_COST else LETTER_COST
                        val candidateCosts = mutableListOf<Pair<Int, Float>>()
                        var best = Float.POSITIVE_INFINITY
                        var taken = 0
                        for (j in reach[letter]) {
                            while (taken < prefix.costs.size && prefix.costs[taken].first < j) {
                                val (i, cost) = prefix.costs[taken]
                                best = min(best, cost - line[i])
                                taken++
                            }
                            if (!best.isFinite()) continue
                            var cost = best + line[j - 1] + al[j] + step
                            if (letter == from) {
                                for (repeat in repeats) {
                                    if (repeat.key != letter || j < repeat.end) continue
                                    val before = prefix.costs.filter { it.first <= repeat.start }
                                        .minOfOrNull { (i, c) -> c - line[i] } ?: continue
                                    // 只有横跨一次完整折返的同键转移才获得奖励，不能重复消费同一折返。
                                    cost = min(cost, before + line[j - 1] + al[j] + LETTER_COST - 2.4f)
                                }
                            }
                            if (cost <= floors[j] + BEAM) {
                                floors[j] = min(floors[j], cost)
                                candidateCosts += j to cost
                            }
                        }
                        if (candidateCosts.isEmpty()) continue
                        next += Prefix(
                            (prefix.letters + letter).toMutableList(),
                            states.toMutableList(),
                            candidateCosts.toMutableList(),
                        )
                    }
                }
                val pruned = prune(next, count)
                for (prefix in pruned) {
                    val lastCost = prefix.costs.lastOrNull() ?: continue
                    if (lastCost.first == last && prefix.states.contains(0)) {
                        finals += Hypothesis(
                            prefix.letters.map { if (it == SEMICOLON_INDEX) (if (schemaTrie != null) ";" else "ing") else ('a' + it).toString() }.joinToString(""),
                            lastCost.second,
                        )
                    }
                }
                if (finals.size >= limit) {
                    val sorted = finals.sortedBy { it.cost }
                    finals.clear()
                    finals += sorted.take(limit)
                    val ceiling = finals.maxOf { it.cost }
                    val kept = pruned.mapNotNull { p ->
                        val costs = p.costs.filter { it.second < ceiling }
                        if (costs.isEmpty()) null else Prefix(p.letters, p.states, costs.toMutableList())
                    }
                    if (kept.isEmpty()) return finals.sortedBy { it.cost }
                    level = kept.toMutableList()
                } else {
                    if (pruned.isEmpty()) return finals.sortedBy { it.cost }
                    level = pruned.toMutableList()
                }
            }
            return finals.sortedWith(compareBy({ it.cost }, { it.letters })).take(limit)
        }
    }

    /**
     * 把一个"字母"接入音节树状态。分号键展开为 ing 三个字母，
     * 其余按单字母扩展；任一字母无法接入时清空 next。
     */
    private fun extendLetter(trie: GlideSpellingConstraint, states: List<Int>, letter: Int, next: MutableList<Int>) {
        if (letter != SEMICOLON_INDEX) {
            trie.extend(states, letter, next)
            return
        }
        var cur = states
        for (l in intArrayOf('i' - 'a', 'n' - 'a', 'g' - 'a')) {
            val nxt = mutableListOf<Int>()
            trie.extend(cur, l, nxt)
            if (nxt.isEmpty()) { next.clear(); return }
            cur = nxt
        }
        next.clear(); next.addAll(cur)
    }

    /** 每个采样点只保留 TOKENS_PER_SAMPLE 个最便宜且与最便宜者相差不超过 BEAM 的前缀。 */
    private fun prune(prefixes: List<Prefix>, count: Int): List<Prefix> {
        val at = Array(count) { mutableListOf<Triple<Float, Int, Int>>() }
        prefixes.forEachIndexed { p, prefix ->
            prefix.costs.forEachIndexed { entry, (j, cost) ->
                at[j].add(Triple(cost, p, entry))
            }
        }
        val keep = Array(prefixes.size) { BooleanArray(prefixes[it].costs.size) }
        for (tokens in at) {
            if (tokens.size > TOKENS_PER_SAMPLE) {
                val trimmed = tokens.sortedBy { it.first }.take(TOKENS_PER_SAMPLE)
                tokens.clear()
                tokens.addAll(trimmed)
            }
            if (tokens.isEmpty()) continue
            val floor = tokens.minOf { it.first }
            for ((cost, p, entry) in tokens) {
                if (cost <= floor + BEAM) keep[p][entry] = true
            }
        }
        val out = mutableListOf<Prefix>()
        prefixes.forEachIndexed { p, prefix ->
            val costs = prefix.costs.filterIndexed { i, _ -> keep[p][i] }
            if (costs.isNotEmpty()) out += Prefix(prefix.letters, prefix.states, costs.toMutableList())
        }
        return out
    }
}
