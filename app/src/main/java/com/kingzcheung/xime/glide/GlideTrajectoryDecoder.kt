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

        // 0) 先按弧长重采样：原始点可能每 1-2px 一个，直接逐点取键会让
        //    抖动被放大成 cvcvcv 这类重复串。取键宽的 0.6 作为步长。
        val minKeyWidth = keys.minOf { it.rect.width }.coerceAtLeast(1f)
        val sampled = GlideTraceSampler.resampleByArc(points, minKeyWidth * 0.6f)

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
            // 起点与终点必然是用户意图键（滑动总要落在某个键上）。
            val isEndpoint = idx == 0 || idx == sampled.lastIndex
            val isKeyPoint = isEndpoint || (feat != null &&
                (feat.turn > TURN_STRONG_RAD || feat.dwellMs >= DWELL_STRONG_MS))
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
