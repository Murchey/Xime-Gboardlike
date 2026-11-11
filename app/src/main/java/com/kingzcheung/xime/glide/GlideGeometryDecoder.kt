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

    /**
     * 一笔最多重采样成多少个点。
     *
     * 等弧长重采样的点数上限。步长 0.25 键，长句弧长可达 80 键；
     * 128 会把实际步长顶到 0.63 键，但仍小于一个键宽，不会跨过整个键。
     * 实测提到 320 反而变差（按点累加的代价同步放大，beam 更分散），保持 128。
     */
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
    /**
     * 与前一个字母相同时的额外价（笔画画不出连按两次）。
     *
     * 双拼每音节固定两键，"同键连续"只在跨音节边界时合法，
     * 所以这个值偏低（叠上 LETTER_COST 才 1.6）时，手指在键内稍停就会被读成按了两次。
     * 但实测调到 3.0 / 6.0 对理想轨迹与真实轨迹指标都没有变化——
     * 说明当前数据里"多吐一个键"主要是对齐跨度问题，不是这条权重能压住的。
     * 保留原值，待 P2 对齐搜索修好后再回来标定。
     */
    private const val REPEAT_COST = 0.8f
    /**
     * 同键折返**没有停留证据**时的额外代价（正数=惩罚）。
     *
     * 手指快速滑过一个键再折回，会被 GlideRepeatGesture 当成"同键两次"的线索；
     * 但那只是经过，不构成本键被按了两次。旧实现无论有无停留都直接减 2.4 分，
     * 于是样本 g111046-12 的 "jue qu liang shi" 盖掉了真实的 "jue qu ling shi"。
     *
     * 实测（16 条真实样本）：只要由「奖励」改成「不奖励」，可达率就从 5/16 提到 8/16；
     * 数值在 0.05~4.0 之间结果一致，说明起决定作用的是"是否给折扣"而非罚分大小。
     * 取 0.2 保留"无停留仍应轻微惩罚"的语义。
     */
    private const val REPEAT_NO_DWELL_PENALTY = 0.2f
    /** 沿连线反方向每走一个键的代价。 */
    private const val BACKTRACK_COST = 2.0f
    /**
     * 没有字母对齐到的停留的罚分。
     *
     * 原来 4.0：比"把一次停留读成两次按键"（LETTER_COST+REPEAT_COST=1.6）贵得多，
     * 于是理想轨迹（精确经过键中心并停留 150ms）也会被解成 vhgr→vhggrr 这种多键串。
     * 降到 1.0 后，"点到即对齐"比"多吐一个键"更便宜，理想轨迹 top-1 由 3/5 升到 4/5，
     * 真实轨迹的几何可达率由 4/16 升到 5/16。
     */
    /**
     * 路径经过一个停留点的代价。
     *
     * 存在阈值效应（实测 16 条真实样本的端到端平均编辑距离）：
     * - 去掉该项 → 1.94
     * - 取 1.0 → **1.44**
     * - 取 2.0 / 4.0 → 1.44（与 1.0 相同）
     *
     * 说明 1.0 已跨过"能让排序翻转"的阈值，再加大只会在所有候选上等比放大，
     * 不产生额外区分度。保持 1.0。
     *
     * 注意它生效的前提是 [dwellPositions] 能真正检出停留：早期判据要求
     * "一次 >= DWELL_MS 的时间空洞"，在真机上漏检率 90%，导致该项长期形同虚设。
     */
    private const val SKIPPED_DWELL_COST = 1.0f
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
            // 停留判定：在 DWELL_RADIUS 内持续 DWELL_MS 以上。
            //
            // 旧实现额外要求「出现一次 >= DWELL_MS 的单次时间空洞」，这在真机上
            // 几乎永不成立：手指静止时设备仍以 3~4ms 的间隔持续上报同一坐标。
            // 实测 20 条样本旧判据共只检出 11 个停留点，而按停留键统计应有 160+ 个，
            // 漏检率超过 90%，SKIPPED_DWELL_COST 从来没真正生效过。
            //
            // 去掉空洞要求后，检出点有 94% 落在期望编码对应的键上，
            // 说明这个判据抓的确实是"用户有意停顿的键"。
            // 慢速连续经过的保护由 DWELL_RADIUS 提供：途经时手指持续移动，
            // 位移会超出半径而切断判定。
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
        /**
         * 一次"同键折返"线索。
         *
         * [hasDwell] 是关键：折返区间里若**没有停留证据**（手指只是经过该键又折回），
         * 这次重复没有独立按键的语义，只可能是路径噪声；有停留才算真的按了两次。
         * 实测样本 g111046-12：手指从 k 快速滑到 l（每步 1~4ms，无停留）再折回 k
         * 并在 k 上停住（距键心 4px），正确解读是"经过 l、停在 k"（lk=ling），
         * 但旧逻辑把 l 读成了两次按键（ll=liang）。
         */
        private data class Repeat(val key: Int, val start: Int, val end: Int, val hasDwell: Boolean)
        private val repeats = repeatCues.map { cue ->
            val start = if (traceLength > 0f) (cue.startArc / traceLength * samples.lastIndex).toInt() else 0
            val end = if (traceLength > 0f) ceil(cue.endArc / traceLength * samples.lastIndex).toInt() else 0
            val s = start.coerceIn(0, samples.lastIndex)
            val e = end.coerceIn(0, samples.lastIndex)
            val dwell = (s..e).any { samples[it].dwell }
            Repeat(cue.key, s, e, dwell)
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
                        // 停留点额外计费。注意 line 是"从起点累计"的，而搜索里的
                        // best = cost - line[i] 会把它转成区间增量，
                        // 所以不同路径走的区间不同，累计的停留点也不同——它**不是**常数偏移。
                        // 实测删掉这一行会让平均距离从 1.44 退化到 1.94，确认有效。
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
                                    // 只有横跨一次完整折返的同键转移才享受折扣，
                                    // 且折返区间内必须有停留证据：手指只是"经过该键又折回"
                                    // 不构成两次按键（实测 g111046-12 的 l→折返 属这一类）。
                                    val delta = if (repeat.hasDwell) -2.4f else REPEAT_NO_DWELL_PENALTY
                                    cost = min(cost, before + line[j - 1] + al[j] + LETTER_COST + delta)
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

    /**
     * 每个采样点保留若干条最有希望的前缀。
     *
     * 按已对齐字母数分层保留：同一采样点上，"已走 8 个字母"和"只走 6 个字母"
     * 的路径不再互相挤占 TOKENS_PER_SAMPLE 名额。
     *
     * 实测说明：这一改动对现有 16 条样本的指标**没有变化**（top-1 4/16、
     * 可达 5/16 与改前一致），保留它是为了修掉"长短路径互相挤"这个结构隐患，
     * 不作为准确率提升。层内排序仍用累计代价。
     */
    private fun prune(prefixes: List<Prefix>, count: Int): List<Prefix> {
        val byPoint = Array(count) { HashMap<Int, MutableList<Triple<Float, Int, Int>>>() }
        prefixes.forEachIndexed { p, prefix ->
            val len = prefix.letters.size
            prefix.costs.forEachIndexed { entry, (j, cost) ->
                byPoint[j].getOrPut(len) { mutableListOf() }.add(Triple(cost, p, entry))
            }
        }
        val keep = Array(prefixes.size) { BooleanArray(prefixes[it].costs.size) }
        for (groups in byPoint) {
            if (groups.isEmpty()) continue
            // BEAM 的参照系必须**按组**取：字母数多的路径累计代价天然更高，
            // 用跨组的最小值当 floor 会把它们整组筛掉，分层裁剪就白做了。
            for (tokens in groups.values) {
                val floor = tokens.minOf { it.first }
                if (tokens.size > TOKENS_PER_SAMPLE) {
                    val trimmed = tokens.sortedBy { it.first }.take(TOKENS_PER_SAMPLE)
                    tokens.clear()
                    tokens.addAll(trimmed)
                }
                for ((cost, p, entry) in tokens) {
                    if (cost <= floor + BEAM) keep[p][entry] = true
                }
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
