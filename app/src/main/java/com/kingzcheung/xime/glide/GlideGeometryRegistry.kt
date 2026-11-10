package com.kingzcheung.xime.glide

import java.util.concurrent.ConcurrentHashMap

/**
 * 阶段B：键盘物理键位的可观察注册表（纯 JVM 可测，不依赖 Compose）。
 *
 * 由每个字母键的 onGloballyPositioned 上报；允许同一编码多个物理实例
 * （分体键盘左右两半）。布局变化时 [beginFrame] 递增 generation，
 * [snapshot] 只在完整一帧内取一致快照；DOWN 时冻结，generation 变化即取消轨迹。
 */
class GlideGeometryRegistry {
    private val keys = ConcurrentHashMap<String, GlideKey>()
    @Volatile private var generation = 0L
    @Volatile private var widthPx = 0f
    @Volatile private var heightPx = 0f

    /** 布局开始重建：清空并开启新代际；旧快照立即失效。 */
    @Synchronized
    fun beginFrame(width: Float, height: Float) {
        generation++
        keys.clear()
        widthPx = width
        heightPx = height
    }

    /** 上报一个物理键；id 必须稳定（行/列/分区组合），不能用会变化的显示文字。 */
    fun register(key: GlideKey) {
        if (key.isValid) keys[key.id] = key
    }

    fun unregister(id: String) { keys.remove(id) }

    val currentGeneration: Long get() = generation

    /**
     * 取一致快照；version 含代际，布局变化后旧轨迹自然失效。
     * 行列不依赖键组件上报，而由坐标聚类推导：同一行的 top 相同，
     * 这样分体键盘左右两半、自定义布局都能得到正确行列。
     */
    fun snapshot(): GlideGeometrySnapshot {
        val raw = keys.values.toList()
        // 用容差聚类：同排键的 top/left 常有亚像素差异，精确 distinct 会把每个键
        // 判成独立行列，导致"相邻键"约束把所有路径都判为不合理跳跃。
        val tolerance = (raw.minOfOrNull { it.rect.height }?.coerceAtLeast(1f) ?: 1f) * 0.4f
        fun cluster(values: List<Float>): List<Float> {
            val sorted = values.sorted()
            val centers = mutableListOf<Float>()
            for (v in sorted) {
                if (centers.isEmpty() || v - centers.last() > tolerance) centers += v
            }
            return centers
        }
        fun indexOf(centers: List<Float>, v: Float): Int {
            var best = 0
            var bestD = Float.MAX_VALUE
            for (i in centers.indices) {
                val d = kotlin.math.abs(centers[i] - v)
                if (d < bestD) { bestD = d; best = i }
            }
            return best
        }
        val rowCenters = cluster(raw.map { it.rect.top })
        val colCenters = cluster(raw.map { it.rect.left })
        val withGrid = raw.map { k ->
            k.copy(
                row = indexOf(rowCenters, k.rect.top),
                col = indexOf(colCenters, k.rect.left),
            )
        }
        return GlideGeometrySnapshot(
            version = "geom@" + generation,
            keys = withGrid,
            widthPx = widthPx,
            heightPx = heightPx,
        )
    }

    val size: Int get() = keys.size
}
