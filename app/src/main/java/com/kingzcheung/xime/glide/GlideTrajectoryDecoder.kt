package com.kingzcheung.xime.glide

import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * 阶段C：轨迹→多假设编码的解码器（纯逻辑，可 JVM 单测）。
 *
 * 与阶段B的 [GlideTraceToCode] 的本质区别：
 * - 阶段B：取每个点"最近的键"，去重后拼成一个串（路径唯一，容错为零）。
 * - 阶段C：每个重采样点保留**多个候选键及其概率**，用 beam search 搜索
 *   整条路径上的高概率编码序列；输出若干条假设，交给 Rime 取候选后重排。
 *
 * 这是 Gboard "轨迹→多假设→语言模型重排" 结构的自实现版本；不复制其模型。
 */
object GlideTrajectoryDecoder {

    /** 回摆去重窗口：最近几个键内重复出现视为抖动，不再追加字符。 */
    private const val DISTINCT_WINDOW = 3

    /**
     * 转角超过该值（弧度）视为转折点。真实键盘滑动在键与键之间会有明显转向
     * （同一行内约为 0，跨行可达 π/2 以上），阈值取 0.55 可覆盖跨行转移。
     */
    private const val TURN_STRONG_RAD = 0.55f
    /** 驻留超过该时长（ms）视为停留点。 */
    private const val DWELL_STRONG_MS = 24L
    /** 转折/停留点的置信度加成。 */
    private const val KEY_POINT_BOOST = 1.2
    /** 行程内最少采样点数（判定"停留"而非"掠过"）。 */
    private const val MIN_RUN_SAMPLES = 2
    /** 停留判定：行程采样数需达到平均值的该倍数。 */
    private const val DWELL_RATIO = 1.35

    /** 相邻点所选键的最大合理行列距离；超过视为不合理跳跃。 */
    private const val MAX_GRID_JUMP = 2
    /** 不合理跳跃的惩罚。 */
    private const val JUMP_PENALTY = 1.5

    /** 某采样点上的候选键：key 键、对数概率、是否为关键点（产生字符）。 */
    private data class PointCand(val key: GlideKey, val logP: Double, val isKeyPoint: Boolean)

    /** 一个解码假设。 */
    data class Hypothesis(
        val code: String,
        /** 对数概率（越大越好），由空间分累加。 */
        val score: Double,
        /** 该假设经过的物理键序列（保留重复，便于后续对齐/编辑）。 */
        val keyIds: List<String>,
    )

    /**
     * @param points 已按弧长重采样的轨迹点（键盘局部 px）
     * @param geometry 冻结的几何快照
     * @param perPointCandidates 每个点保留的候选键数（默认 2，太大则分支爆炸）
     * @param beamWidth beam 宽度
     * @param maxHypotheses 返回的假设数
     */
    fun decode(
        points: List<GlidePoint>,
        geometry: GlideGeometrySnapshot,
        perPointCandidates: Int = 2,
        beamWidth: Int = 8,
        maxHypotheses: Int = 5,
    ): List<Hypothesis> {
        if (points.isEmpty() || !geometry.isValid) return emptyList()
        val keys = geometry.eligibleKeys
        if (keys.isEmpty()) return emptyList()

        // 0) 按键行程重采样：保证每个被轨迹穿过的键都留下一个代表点。
        //    等弧长采样（旧做法，步长 0.6×键宽）会整键落在两个采样点之间而丢键：
        //    实测 q→w→e 解成 qe、q→s→d 解成 qd。
        val sampled = GlideTraceSampler.resampleByKeyRun(points, geometry)
        val runCounts = GlideTraceSampler.lastRunCounts

        // 1) 每个点取距离最近的若干键，转成对数概率。
        //    转折点（转角大）与停留点（驻留长）是 Gboard 判定"用户想按这个键"的强信号，
        //    这里给这类点更高的置信度，并在其附近限制候选键数（减少歧义）。
        val features = GlideTraceSampler.extractFeatures(sampled, geometry, Float.MAX_VALUE)
        // 关键修正：真实滑动中，手指从 A 键划到 B 键会经过中间键，
        // 但这些中间键**不是**用户想输入的字符（Gboard 用 HMM 区分"经过"与"停留"）。
        // 这里把点分成两类：
        //   - key point（方向有转折、或驻留明显）：产生字符
        //   - 过渡点：只参与打分，不产生字符
        val perPoint = sampled.mapIndexed { idx, p ->
            val feat = features.getOrNull(idx)
            // 采样点已按"键行程"归一。用行程内的采样点数区分"停留"与"掠过"：
            // 手指停在某键上时该键会有较多采样点；快速掠过只会有 1 个点。
            // 只让停留行程产出字符，避免把过渡键当成输入。
            val runs = runCounts.getOrNull(idx) ?: 1
            val isEndpoint = idx == 0 || idx == sampled.lastIndex
            // 固定阈值：相对阈值（达到平均值倍数）实测会丢键（asdf→af、zhong→zg），
            // 固定阈值更稳定；当前的已知代价是会插入部分过渡键。
            val isKeyPoint = sampled.size <= 2 || isEndpoint || runs >= MIN_RUN_SAMPLES
            val take = if (isKeyPoint) 1 else perPointCandidates
            val boost = if (isKeyPoint) KEY_POINT_BOOST else 0.0
            val cands = keys.map { key -> key to key.rect.distanceToEdge(p.x, p.y) }
                .sortedBy { it.second }
                .take(take)
                .map { (key, dist) -> key to (spatialLogProb(dist, key) + boost) }
            cands.map { c -> PointCand(c.first, c.second, isKeyPoint) }
        }

        // 2) beam search：状态为"编码 + 最后键 + 累计分"
        data class Node(val code: String, val lastKeyId: String, val score: Double, val keyIds: List<String>)
        var beam = listOf(Node("", "", 0.0, emptyList()))

        for ((i, cands) in perPoint.withIndex()) {
            val next = mutableListOf<Node>()
            for (node in beam) {
                for (c in cands) {
                    val key = c.key
                    val logP = c.logP
                    val isKeyPoint = c.isKeyPoint
                    // 路径合理性：真实滑动不会从 q 直接跳到 m。
                    val lastKey = node.keyIds.lastOrNull()?.let { id -> keys.firstOrNull { it.id == id } }
                    val jumpPenalty = if (lastKey != null) {
                        val d = lastKey.gridDistance(key)
                        if (d > MAX_GRID_JUMP) JUMP_PENALTY else 0.0
                    } else 0.0
                    val recent = node.keyIds.takeLast(DISTINCT_WINDOW)
                    val repeated = recent.contains(key.id)
                    // 只有 key point 才产生字符；过渡点仅累计分数。
                    val emits = isKeyPoint && !repeated
                    val newCode = if (emits) node.code + key.code else node.code
                    val penalized = if (repeated) logP - 0.6 else logP - jumpPenalty
                    next += Node(
                        code = newCode,
                        lastKeyId = key.id,
                        score = node.score + penalized,
                        keyIds = if (isKeyPoint) node.keyIds + key.id else node.keyIds,
                    )
                }
            }
            // 按分数保留 top beam；末点若 beam 为空说明配置异常
            beam = next.sortedByDescending { it.score }.take(beamWidth)
            if (beam.isEmpty()) return emptyList()
        }

        // 3) 去重（同编码只留最高分），截断返回
        return beam
            .groupBy { it.code }
            .map { (code, group) -> group.maxByOrNull { it.score }!! }
            .sortedByDescending { it.score }
            .take(maxHypotheses)
            .filter { it.code.isNotEmpty() }
            .map { Hypothesis(it.code, it.score, it.keyIds) }
    }

    /**
     * 触点到键边界的距离 -> 对数概率。
     * 用与 Gboard 空间模型同构的高斯衰减；距离为 0（落在键内）给满分。
     */
    private fun spatialLogProb(distancePx: Float, key: GlideKey): Double {
        val sigma = maxOf(key.rect.width, key.rect.height) * 0.45f
        if (sigma <= 0f) return -10.0
        val d = distancePx / sigma
        return -0.5 * d * d
    }
}
