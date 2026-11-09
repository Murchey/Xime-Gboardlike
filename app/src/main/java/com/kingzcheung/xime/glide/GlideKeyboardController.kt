package com.kingzcheung.xime.glide

/**
 * 阶段B编排层（纯逻辑，可 JVM 单测）：把几何、仲裁、轨迹、会话、查询串起来。
 * Compose 层只负责把 DOWN/MOVE/UP 转发进来并显示 [trail] 与结果，不在这里写 UI 状态机。
 *
 * 说明：当前 rawInput 由"经过键序列"生成，是阶段B的最小可跑链路；
 * 阶段C会替换为"轨迹→多假设→字词图整句 beam"，届时本类接口保持不变。
 */
class GlideKeyboardController(
    private val registry: GlideGeometryRegistry,
    private val coordinator: GlideCoordinator,
    glideEnabled: () -> Boolean,
) {
    private val arbiter = GlideGestureArbiter(
        eligibleStart = { true },
        glideEnabled = glideEnabled,
    )
    private val collected = mutableListOf<GlidePoint>()
    @Volatile private var activeGeometry: GlideGeometrySnapshot? = null

    /** 当前用于绘制的轨迹点（键盘局部 px）。 */
    val trail: List<GlidePoint> get() = collected.toList()
    val isGliding: Boolean get() = arbiter.state.isGlide

    fun onDown(pointerId: Long, x: Float, y: Float, hostToken: String, schemaFingerprint: String) {
        val geo = registry.snapshot()
        activeGeometry = geo
        collected.clear()
        collected += GlidePoint(x, y, System.currentTimeMillis(), pointerId.toInt())
        val startKey = geo.keyAt(x, y)?.id
        arbiter.onDown(pointerId, x, y, startKey)
        // 每次新滑动以当前几何/方案为基线；已有未确认组合由 appendCode 续接（阶段D细化）
        if (arbiter.state.owner == GlideGestureArbiter.Owner.PENDING ||
            arbiter.state.owner == GlideGestureArbiter.Owner.NONE) {
            coordinator.session.beginGlide(hostToken, schemaFingerprint, geo.version)
        }
    }

    fun onMove(pointerId: Long, x: Float, y: Float, secondPointer: Boolean) {
        val geo = activeGeometry ?: return
        val keyId = geo.keyAt(x, y)?.id
        val decision = arbiter.onMove(pointerId, x, y, keyId, secondPointer)
        if (decision == GlideGestureArbiter.Decision.DISCARD) {
            collected.clear()
            activeGeometry = null
            return
        }
        if (arbiter.state.isGlide) {
            collected += GlidePoint(x, y, System.currentTimeMillis(), pointerId.toInt())
        }
    }

    fun onLongPressTimeout() {
        arbiter.onLongPressTimeout()
    }

    sealed class UpResult {
        object Idle : UpResult()
        object Tap : UpResult()
        data class Candidates(val outcome: GlideCoordinator.Outcome) : UpResult()
        object Discarded : UpResult()
    }

    fun onUp(pointerId: Long, hostToken: String): UpResult {
        val decision = arbiter.onUp(pointerId)
        return when (decision) {
            GlideGestureArbiter.Decision.EMIT_TAP -> { clearTrail(); UpResult.Tap }
            GlideGestureArbiter.Decision.DISCARD -> { coordinator.session.cancel(); clearTrail(); UpResult.Discarded }
            GlideGestureArbiter.Decision.EMIT_GLIDE -> {
                val geo = activeGeometry
                val codes = if (geo != null) {
                    GlideTraceSampler.visitedCodes(
                        GlideTraceSampler.extractFeatures(collected, geo, Float.MAX_VALUE)
                    )
                } else emptyList()
                clearTrail()
                val raw = codes.joinToString("")
                if (raw.isEmpty()) { coordinator.session.cancel(); return UpResult.Idle }
                coordinator.session.appendCode(raw)
                val outcome = coordinator.onTraceFinished(
                    hostToken, coordinator.session.state.schemaFingerprint,
                    coordinator.session.state.layoutFingerprint)
                UpResult.Candidates(outcome)
            }
            else -> { clearTrail(); UpResult.Idle }
        }
    }

    fun onCancel() {
        arbiter.onCancel()
        coordinator.session.cancel()
        clearTrail()
    }

    fun allowFallbackClick(): Boolean = arbiter.allowFallbackClick()

    private fun clearTrail() {
        collected.clear()
        activeGeometry = null
    }
}
