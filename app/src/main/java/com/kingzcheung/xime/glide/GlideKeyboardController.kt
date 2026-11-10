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
    private val schemaSpellings: ((String) -> Collection<String>)? = null,
) {
    private val arbiter = GlideGestureArbiter(
        eligibleStart = { true },
        glideEnabled = glideEnabled,
    )
    private val collected = mutableListOf<GlidePoint>()
    @Volatile private var activeGeometry: GlideGeometrySnapshot? = null
    private var currentHost: String = ""
    private var currentSchema: String = ""
    private var activeSpellings: Collection<String>? = null
    /** 英文模式：轨迹只解出英文字母串，直接作为候选，不查中文词库。 */
    private var asciiMode: Boolean = false
    private var glideSessionStarted = false
    private var gestureId = 0L
    /**
     * 滑动真正开始（升级为 Glide）时回调：用于清空引擎既有组合。
     * 语义选「新建」而非「追加」——一次滑动代表一次独立的输入意图；
     * 若保留旧组合，Rime 会把新编码拼在旧编码后面，候选完全错乱。
     */
    var onGlideStart: (() -> Unit)? = null

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
        timeMs: Long = System.currentTimeMillis(),
    ) {
        gestureId++
        asciiMode = ascii
        val geo = registry.snapshot()
        activeGeometry = geo
        currentHost = hostToken
        currentSchema = schemaFingerprint
        activeSpellings = if (ascii) null else schemaSpellings?.invoke(schemaFingerprint)?.toList()
        glideSessionStarted = false
        collected.clear()
        collected += GlidePoint(x, y, timeMs, pointerId.toInt())
        val startKey = geo.keyAt(x, y)?.id
        arbiter.onDown(pointerId, x, y, startKey)
        // 不在此创建会话：普通点按(PENDING→TAP)不该清空已有未确认组合；
        // 只有 onMove 真正升级为 Glide 时才 beginGlide。
    }

    fun onMove(pointerId: Long, x: Float, y: Float, secondPointer: Boolean, timeMs: Long = System.currentTimeMillis()) {
        val geo = activeGeometry ?: return
        val keyId = geo.keyAt(x, y)?.id
        val decision = arbiter.onMove(pointerId, x, y, keyId, secondPointer)
        if (decision == GlideGestureArbiter.Decision.DISCARD) {
            collected.clear()
            activeGeometry = null
            return
        }
        if (arbiter.state.isGlide) {
            // 首次升级：清空既有组合并新建会话
            if (!glideSessionStarted) {
                runCatching { onGlideStart?.invoke() }
                coordinator.session.beginGlide(currentHost, currentSchema, geo.version)
                glideSessionStarted = true
            }
            collected += GlidePoint(x, y, timeMs, pointerId.toInt())
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
                // 采集真实轨迹（仅在显式开启时写入），供准确率评估与离线训练
                GlideTraceRecorder.onTraceFinished(points, geo, currentSchema)
                // 几何解码：按对齐/连线/回溯/停留代价枚举完整音节串（代价低的在前）
                val hypotheses = GlideGeometryDecoder.decode(points, geo, GLIDE_HYPOTHESES, activeSpellings)
                if (hypotheses.isEmpty()) { coordinator.session.cancel(); return UpResult.Idle }
                // 英文模式：轨迹解出的字母串即候选，不查中文词库（随键盘中英文模式变化）
                if (asciiMode) {
                    val top = hypotheses.first()
                    val session = coordinator.session
                    session.beginGlide(hostToken, session.state.schemaFingerprint, session.state.layoutFingerprint)
                    session.appendCode(top.letters)
                    session.deliverCandidates(
                        session.state.traceId, session.state.revision, hostToken, top.letters,
                        hypotheses.mapIndexed { i, h ->
                            GlideCandidate("en-" + i, h.letters, h.letters, score = -h.cost.toDouble())
                        },
                    )
                    return UpResult.Candidates(GlideCoordinator.Outcome.ShowCandidates)
                }
                UpResult.Candidates(decodeWithHypotheses(hypotheses, hostToken))
            }
            else -> { clearTrail(); UpResult.Idle }
        }
    }

    /**
     * 多假设：逐条查 Rime 并按「空间分 + 语言模型序位」合并候选。
     *
     * 排序权重说明：Rime 的候选顺序本身来自词频/语言模型，是消解相邻键歧义
     * （a/s、z/h 这类空间分几乎相同的情况）最可靠的依据。因此：
     * - 假设内部位次 (i) 的惩罚要明显大于假设之间的空间分差；
     * - 首选假设的第 0 位候选优先于次选假设的第 0 位候选。
     */
    private fun decodeWithHypotheses(
        hypotheses: List<GlideGeometryDecoder.Hypothesis>,
        hostToken: String,
    ): GlideCoordinator.Outcome {
        val sorted = lookupHypotheses(hypotheses)
        if (sorted.isEmpty()) return GlideCoordinator.Outcome.NoCandidate
        installCandidates(sorted, hostToken)
        return GlideCoordinator.Outcome.ShowCandidates
    }

    private fun lookupHypotheses(hypotheses: List<GlideGeometryDecoder.Hypothesis>): List<GlideCandidate> {
        val merged = mutableListOf<GlideCandidate>()
        val seen = mutableSetOf<String>()
        // 几何代价：越小越好；转成分数要取负
        val best = hypotheses.minOfOrNull { it.cost } ?: 0f
        for ((hIdx, h) in hypotheses.withIndex()) {
            // 已是当前方案的真实编码（含 ;），不得再做全拼字符串替换。
            val query = h.letters
            when (val r = coordinator.lookup.lookup(query)) {
                is GlideLookupResult.Candidates -> {
                    for ((i, c) in r.list.withIndex()) {
                        if (!seen.add(c.text)) continue
                        // 位次惩罚（语言模型信号）> 假设间空间分差（通常 < 0.5）
                        val rankPenalty = i * RANK_PENALTY_WEIGHT
                        val hypPenalty = hIdx * HYPOTHESIS_PENALTY_WEIGHT
                        // 几何分：以最优假设为基准，代价差越大分越低
                        val geometryScore = -(h.cost - best)
                        merged += c.copy(
                            id = "glide-" + hIdx + "-" + i + "-" + c.text,
                            score = geometryScore - rankPenalty - hypPenalty,
                        )
                    }
                }
                else -> Unit
            }
            if (merged.size >= 12) break
        }
        return merged.sortedByDescending { it.score }
    }

    private fun installCandidates(sorted: List<GlideCandidate>, hostToken: String) {
        val top = sorted.first()
        val session = coordinator.session
        session.beginGlide(hostToken, session.state.schemaFingerprint, session.state.layoutFingerprint)
        session.appendCode(top.rawInput)
        session.deliverCandidates(
            session.state.traceId, session.state.revision, hostToken, top.rawInput, sorted,
        )
    }

    internal data class DecodeRequest(
        val gestureId: Long, val hostToken: String, val schema: String,
        val geometry: GlideGeometrySnapshot, val points: List<GlidePoint>,
        val spellings: Collection<String>?, val ascii: Boolean, val final: Boolean,
    )

    internal data class DecodeResult(val request: DecodeRequest, val rawInput: String, val candidates: List<GlideCandidate>)

    /** 主线程固定快照，后台解码不读写正在采集的轨迹或会话。 */
    internal fun previewRequest(): DecodeRequest? {
        if (!isGliding) return null
        val geo = activeGeometry ?: return null
        return DecodeRequest(gestureId, currentHost, currentSchema, geo, collected.toList(), activeSpellings, asciiMode, false)
    }

    internal fun finishRequest(pointerId: Long, hostToken: String): DecodeRequest? {
        if (!isGliding) { onUp(pointerId, hostToken); return null }
        if (arbiter.onUp(pointerId) != GlideGestureArbiter.Decision.EMIT_GLIDE) return null
        val geo = activeGeometry ?: return null
        val request = DecodeRequest(gestureId, hostToken, currentSchema, geo, collected.toList(), activeSpellings, asciiMode, true)
        clearTrail()
        return request
    }

    /** 仅在后台线程执行；预览只使用独立查询，不往 Rime 主组合写编码。 */
    internal fun decode(request: DecodeRequest): DecodeResult {
        if (request.final) GlideTraceRecorder.onTraceFinished(request.points, request.geometry, request.schema)
        val hypotheses = GlideGeometryDecoder.decode(request.points, request.geometry, GLIDE_HYPOTHESES, request.spellings)
        val candidates = if (request.ascii) hypotheses.mapIndexed { i, h ->
            GlideCandidate("en-$" + i, h.letters, h.letters, score = -h.cost.toDouble())
        } else lookupHypotheses(hypotheses)
        return DecodeResult(request, candidates.firstOrNull()?.rawInput ?: hypotheses.firstOrNull()?.letters.orEmpty(), candidates)
    }

    internal fun isCurrent(request: DecodeRequest): Boolean =
        request.gestureId == gestureId && request.hostToken == currentHost

    /** 主线程应用；抬手、新一笔、取消后，旧后台结果不得覆盖。 */
    internal fun accept(result: DecodeResult): Boolean {
        val request = result.request
        if (!isCurrent(request)) return false
        if (!request.final) return isGliding
        if (isGliding) return false
        if (result.candidates.isEmpty()) coordinator.session.cancel()
        else installCandidates(result.candidates, request.hostToken)
        return true
    }

    fun onCancel() {
        gestureId++
        arbiter.onCancel()
        coordinator.session.cancel()
        clearTrail()
    }

    fun allowFallbackClick(): Boolean = arbiter.allowFallbackClick()

    private companion object {
        /** 交给词典排序的候选串数（共线键如 zhong/zong 需要前几名都在）。 */
        const val GLIDE_HYPOTHESES = 16

        /** Rime 候选位次惩罚：体现语言模型信号，需大于假设间空间分差。 */
        const val RANK_PENALTY_WEIGHT = 0.45
        /** 假设之间的惩罚：空间分已含此信息，这里只需弱化次选假设。 */
        const val HYPOTHESIS_PENALTY_WEIGHT = 0.15
    }

    private fun clearTrail() {
        collected.clear()
        activeGeometry = null
        glideSessionStarted = false
    }
}
