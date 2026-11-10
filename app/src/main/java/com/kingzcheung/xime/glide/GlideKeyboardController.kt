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
    /** 手势代际：每次手势结束递增，供 pointerInput 作为 key 强制重建手势循环。 */
    private val gestureEpoch = kotlinx.coroutines.flow.MutableStateFlow(0)
    val epoch: kotlinx.coroutines.flow.StateFlow<Int> get() = gestureEpoch

    private val collected = mutableListOf<GlidePoint>()
    @Volatile private var activeGeometry: GlideGeometrySnapshot? = null
    private var currentHost: String = ""
    private var currentSchema: String = ""
    /** 英文模式：轨迹只解出英文字母串，直接作为候选，不查中文词库。 */
    private var asciiMode: Boolean = false
    private var glideSessionStarted = false

    /** 当前用于绘制的轨迹点（键盘局部 px）。 */
    val trail: List<GlidePoint> get() = collected.toList()
    val isGliding: Boolean get() = arbiter.state.isGlide

    /**
     * 本次手势是否已被滑动输入接管（含 DOWN 后的 Pending 阶段）。
     * KeyButton 用它抑制点击与方向动作：Pending 期间若放行，会先打出一个字符，
     * 之后即便升级为 Glide 也已产生污染。
     */
    val isCapturing: Boolean
        get() = arbiter.state.owner == GlideGestureArbiter.Owner.PENDING || arbiter.state.isGlide
    /** 当前会话的可编辑编码（供阶段B临时送进现有 Rime 输入链路产生候选）。 */
    fun rawInputForDisplay(): String = coordinator.session.state.rawInput

    /** 当前会话的候选文本（已按分数排序），供服务层投影到候选栏。 */
    fun candidateTexts(): List<String> = coordinator.session.state.candidates.map { it.text }

    fun onDown(
        pointerId: Long,
        x: Float,
        y: Float,
        hostToken: String,
        schemaFingerprint: String,
        ascii: Boolean = false,
    ) {
        asciiMode = ascii
        val geo = registry.snapshot()
        activeGeometry = geo
        currentHost = hostToken
        currentSchema = schemaFingerprint
        glideSessionStarted = false
        collected.clear()
        collected += GlidePoint(x, y, System.currentTimeMillis(), pointerId.toInt())
        val startKey = geo.keyAt(x, y)?.id
        arbiter.onDown(pointerId, x, y, startKey)
        // 不在此创建会话：普通点按(PENDING→TAP)不该清空已有未确认组合；
        // 只有 onMove 真正升级为 Glide 时才 beginGlide。
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
            // 首次升级：以当前几何/方案为基线开启会话（此时才清空旧组合）
            if (!glideSessionStarted) {
                coordinator.session.beginGlide(currentHost, currentSchema, geo.version)
                glideSessionStarted = true
            }
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
                val points = collected.toList()
                clearTrail()
                if (geo == null || points.isEmpty()) { coordinator.session.cancel(); return UpResult.Idle }
                // 阶段C：多假设轨迹解码，而不是单一路径串键
                val hypotheses = GlideTrajectoryDecoder.decode(points, geo)
                if (hypotheses.isEmpty()) { coordinator.session.cancel(); return UpResult.Idle }
                // 英文模式：轨迹解出的字母串即候选，不查中文词库（随键盘中英文模式变化）
                val outcome = if (asciiMode) {
                    GlideCoordinator.Outcome.ShowCandidates
                } else {
                    decodeWithHypotheses(hypotheses, hostToken)
                }
                if (asciiMode) {
                    val top = hypotheses.first()
                    val session = coordinator.session
                    session.beginGlide(hostToken, session.state.schemaFingerprint, session.state.layoutFingerprint)
                    session.appendCode(top.code)
                    session.deliverCandidates(
                        session.state.traceId, session.state.revision, hostToken, top.code,
                        hypotheses.mapIndexed { i, h -> GlideCandidate("en-" + i, h.code, h.code, score = h.score) },
                    )
                }
                UpResult.Candidates(outcome)
            }
            else -> { clearTrail(); UpResult.Idle }
        }
    }

    /** 多假设：逐条查 Rime 并按假设分数合并候选，交给会话做待确认。 */
    private fun decodeWithHypotheses(
        hypotheses: List<GlideTrajectoryDecoder.Hypothesis>,
        hostToken: String,
    ): GlideCoordinator.Outcome {
        val merged = mutableListOf<GlideCandidate>()
        val seen = mutableSetOf<String>()
        for (h in hypotheses) {
            when (val r = coordinator.lookup.lookup(h.code)) {
                is GlideLookupResult.Candidates -> {
                    for ((i, c) in r.list.withIndex()) {
                        if (!seen.add(c.text)) continue
                        merged += c.copy(
                            id = "glide-" + hypotheses.indexOf(h) + "-" + i + "-" + c.text,
                            score = h.score - i * 0.1,
                        )
                    }
                }
                else -> Unit
            }
            if (merged.size >= 12) break
        }
        if (merged.isEmpty()) return GlideCoordinator.Outcome.NoCandidate
        val sorted = merged.sortedByDescending { it.score }
        val top = sorted.first()
        val session = coordinator.session
        session.beginGlide(hostToken, session.state.schemaFingerprint, session.state.layoutFingerprint)
        session.appendCode(top.rawInput)
        session.deliverCandidates(
            session.state.traceId, session.state.revision, hostToken, top.rawInput, sorted,
        )
        return GlideCoordinator.Outcome.ShowCandidates
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
        glideSessionStarted = false
        // 递增代际：让父层 pointerInput 重建，避免手势循环停在上一次的状态
        gestureEpoch.value = gestureEpoch.value + 1
    }
}
