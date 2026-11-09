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

    /** 取一致快照；version 含代际，布局变化后旧轨迹自然失效。 */
    fun snapshot(): GlideGeometrySnapshot = GlideGeometrySnapshot(
        version = "geom@" + generation,
        keys = keys.values.toList(),
        widthPx = widthPx,
        heightPx = heightPx,
    )

    val size: Int get() = keys.size
}
