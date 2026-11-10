package com.kingzcheung.xime.glide

import kotlin.math.hypot
import kotlin.math.sqrt

/** 一个原始触点（键盘局部 px，时间单调 ms）。 */
data class GlidePoint(val x: Float, val y: Float, val timeMs: Long, val pointerId: Int)

/** 解码用到的特征点：保留重复键、驻留与转角信息。 */
data class GlideFeature(
    val x: Float, val y: Float, val timeMs: Long,
    val keyId: String?, val code: String?,
    /** 从起点累计的弧长，px。 */
    val arcLength: Float,
    /** 相对上一段的方向变化，弧度；起点为 0。 */
    val turn: Float,
    /** 在该点附近的驻留时长估计，ms。 */
    val dwellMs: Long,
)

/**
 * 一条连续滑动轨迹。原始点与解码特征分开保存，不把"经过哪些键"去重成字符串。
 */
data class GlideTrace(
    val traceId: Long,
    val geometryVersion: String,
    val schemaFingerprint: String,
    val hostToken: String,
    val points: List<GlidePoint>,
) {
    val start: GlidePoint? get() = points.firstOrNull()
    val end: GlidePoint? get() = points.lastOrNull()
    val durationMs: Long get() = if (points.size < 2) 0 else end!!.timeMs - start!!.timeMs
    val isValid: Boolean get() = points.size >= 2 && geometryVersion.isNotEmpty()

    /** 累计弧长。 */
    val totalArcLength: Float
        get() {
            var acc = 0f
            for (i in 1 until points.size) {
                val a = points[i - 1]; val b = points[i]
                acc += hypot(b.x - a.x, b.y - a.y)
            }
            return acc
        }
}

/** 轨迹采样与特征提取（纯函数，单测可覆盖）。 */
object GlideTraceSampler {

    /** 按最小空间间隔抽稀原始点，但强制保留起点、终点和明显驻留点。 */
    fun downsample(points: List<GlidePoint>, minDistancePx: Float): List<GlidePoint> {
        if (points.size <= 2 || minDistancePx <= 0f) return points
        val out = mutableListOf(points.first())
        var last = points.first()
        for (i in 1 until points.lastIndex) {
            val p = points[i]
            if (hypot(p.x - last.x, p.y - last.y) >= minDistancePx) {
                out += p; last = p
            }
        }
        out += points.last()
        return out
    }

    /** 按等弧长重采样，保留时间线性插值；轨迹过短时返回原端点。 */
    fun resampleByArc(points: List<GlidePoint>, stepPx: Float): List<GlidePoint> {
        if (points.size < 2 || stepPx <= 0f) return points
        data class Seg(val a: GlidePoint, val b: GlidePoint, val len: Float, val acc: Float)
        var total = 0f
        val segs = mutableListOf<Seg>()
        for (i in 1 until points.size) {
            val a = points[i - 1]; val b = points[i]
            val len = hypot(b.x - a.x, b.y - a.y)
            segs += Seg(a, b, len, total)
            total += len
        }
        if (total <= 0f) return listOf(points.first(), points.last())
        val out = mutableListOf(points.first())
        var target = stepPx
        var si = 0
        while (target < total && si < segs.size) {
            val s = segs[si]
            if (s.acc + s.len < target) { si++; continue }
            if (s.len <= 0f) { si++; continue }
            val t = ((target - s.acc) / s.len).coerceIn(0f, 1f)
            val x = s.a.x + (s.b.x - s.a.x) * t
            val y = s.a.y + (s.b.y - s.a.y) * t
            val time = s.a.timeMs + ((s.b.timeMs - s.a.timeMs) * t).toLong()
            out += GlidePoint(x, y, time, s.b.pointerId)
            target += stepPx
        }
        val last = points.last()
        if (out.last().let { hypot(it.x - last.x, it.y - last.y) } > 0.01f) out += last
        return out
    }

    /**
     * 按键行程重采样：按轨迹**先后穿过键的顺序**，每个键取轨迹落在该键内、
     * 且最接近键中心的点作为代表点。
     *
     * 相比 [resampleByArc] 的等弧长采样，它从根上保证**每个被穿过的键都留下一个点**，
     * 不会因为固定步长把整键落在两个采样点之间而丢键
     * （实测等弧长步长 0.6×键宽时 q→w→e 会解成 qe）。
     *
     * 同一键的连续行程只保留一个代表点；若轨迹离开该键后又回来（回摆），
     * 会作为新的行程再次保留，交由解码器的回摆去重逻辑处理。
     */
    /** 最近一次 [resampleByKeyRun] 各行程的采样点数，供解码器判定"停留"与"掠过"。 */
    @Volatile var lastRunCounts: List<Int> = emptyList()
        private set

    fun resampleByKeyRun(
        points: List<GlidePoint>,
        geometry: GlideGeometrySnapshot,
    ): List<GlidePoint> {
        if (points.size < 2 || !geometry.isValid) return points
        val out = mutableListOf<GlidePoint>()
        var currentKeyId: String? = null
        var best: GlidePoint? = null
        var bestDist = Float.MAX_VALUE

        // 记录每个行程的采样点数（作为驻留时长的代理指标）
        val runCounts = mutableListOf<Int>()
        var runCount = 0

        fun flush() {
            best?.let {
                out += it
                runCounts += runCount
            }
            best = null
            bestDist = Float.MAX_VALUE
            currentKeyId = null
            runCount = 0
        }

        for (p in points) {
            val key = geometry.keyAt(p.x, p.y) ?: geometry.nearestKey(p.x, p.y, Float.MAX_VALUE)
            if (key == null) continue
            if (key.id != currentKeyId) {
                flush()
                currentKeyId = key.id
            }
            runCount++
            val d = key.rect.distanceTo(p.x, p.y)
            if (d < bestDist) {
                bestDist = d
                best = p
            }
        }
        flush()
        lastRunCounts = runCounts

        // 至少保留首尾，保证起止点不丢
        if (out.isEmpty()) return listOf(points.first(), points.last())
        if (out.first() !== points.first()) out.add(0, points.first())
        if (out.last() !== points.last()) out += points.last()
        return out
    }

    /**
     * 从重采样点提取特征：最近键、累计弧长、转角和驻留估计。
     * 不在此做解码，也不把连续相同键折叠。
     */
    fun extractFeatures(
        points: List<GlidePoint>,
        geometry: GlideGeometrySnapshot,
        maxKeyDistancePx: Float,
    ): List<GlideFeature> {
        if (points.isEmpty()) return emptyList()
        var arc = 0f
        var prevDx = 0f; var prevDy = 0f; var hasDir = false
        val result = mutableListOf<GlideFeature>()
        for (i in points.indices) {
            val p = points[i]
            // 当前段方向（与上一有效段比较得到转角，再更新 prev 方向）
            var turn = 0f
            if (i > 0) {
                val q = points[i - 1]
                val dx = p.x - q.x; val dy = p.y - q.y
                val len = hypot(dx, dy)
                arc += len
                if (len > 0.5f) {
                    if (hasDir) {
                        val prevLen = hypot(prevDx, prevDy)
                        if (prevLen > 0.5f) {
                            val dot = (prevDx * dx + prevDy * dy) / (prevLen * len).coerceAtLeast(1e-6f)
                            turn = kotlin.math.acos(dot.coerceIn(-1f, 1f))
                        }
                    }
                    prevDx = dx; prevDy = dy; hasDir = true
                }
            }
            val key = geometry.nearestKey(p.x, p.y, maxKeyDistancePx)
            // 驻留：相邻点时间差（后一点相对当前点的停留粗估）
            val dwell = if (i < points.lastIndex) (points[i + 1].timeMs - p.timeMs).coerceAtLeast(0) else 0
            result += GlideFeature(
                x = p.x, y = p.y, timeMs = p.timeMs,
                keyId = key?.id, code = key?.code,
                arcLength = arc, turn = turn, dwellMs = dwell,
            )
        }
        return result
    }

    /** 供调试/评测使用的经过键序列（保留重复，不做去重）。 */
    fun visitedCodes(features: List<GlideFeature>): List<String> =
        features.mapNotNull { it.code }
}
