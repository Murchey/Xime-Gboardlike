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
    private val densityDpi: () -> Float = { 160f },
    /** 神经假设来源；null 或运行失败时自动回退几何解码。 */
    private val neuralProvider: GlideHypothesisProvider? = null,
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
    private var activeDensityDpi = 160f
    /** 英文模式：轨迹只解出英文字母串，直接作为候选，不查中文词库。 */
    private var asciiMode: Boolean = false
    private var glideSessionStarted = false
    private var gestureId = 0L
    private var activePrefix = ""
    /** 滑动升级时返回当前未确认编码，作为本次轨迹的追加前缀；无可追加内容时返回空串。 */
    var onGlideStart: (() -> String)? = null
    /** 外部确认或清空候选：丢弃内部会话的旧编码。 */
    var onGlideReset: (() -> Unit)? = null

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

    fun resetCompositionSession() {
        gestureId++
        activePrefix = ""
        coordinator.session.onHostChanged()
        clearTrail()
    }

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
        activePrefix = ""
        asciiMode = ascii
        activeDensityDpi = densityDpi().takeIf { it.isFinite() && it > 0f } ?: 160f
        val geo = registry.snapshot()
        activeGeometry = geo
        currentHost = hostToken
        currentSchema = schemaFingerprint
        activeSpellings = if (ascii) null else schemaSpellings?.invoke(schemaFingerprint)?.toList()
        glideSessionStarted = false
        // 前文只在手势开始取一次：读 InputConnection 有开销，且同一次手势里它不会变
        gestureContext = runCatching { precedingText().orEmpty() }.getOrDefault("")
        gramBaseline = null
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
            // 首次升级：已有候选时保留其编码作前缀，新建本次手势会话。
            if (!glideSessionStarted) {
                activePrefix = if (asciiMode) "" else runCatching { onGlideStart?.invoke().orEmpty() }.getOrDefault("")
                coordinator.session.beginGlide(currentHost, currentSchema, geo.version,
                    keepCandidates = activePrefix.isNotEmpty())
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
                // 几何解码：按对齐/连线/回溯/停留代价枚举完整音节串（代价低的在前）
                val hypotheses = hypothesesFor(points, geo, activeSpellings, activeDensityDpi, allowNeural = true)
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
        val sorted = lookupHypotheses(hypotheses, activePrefix)
        if (sorted.isEmpty()) return GlideCoordinator.Outcome.NoCandidate
        installCandidates(sorted, hostToken)
        return GlideCoordinator.Outcome.ShowCandidates
    }

    private fun lookupHypotheses(hypotheses: List<GlideGeometryDecoder.Hypothesis>, prefix: String = ""): List<GlideCandidate> {
        val merged = mutableListOf<GlideCandidate>()
        /** 文本 -> 在 merged 中的下标，用于按分数择优去重（避免 O(n²) 线性查找）。 */
        val indexByText = HashMap<String, Int>()
        // 几何代价：越小越好；转成分数要取负
        val best = hypotheses.minOfOrNull { it.cost } ?: 0f
        // 查完所有假设再统一排序：前几个错误假设的碎词不能占满候选池，
        // 否则排在后面的正确整句编码没有机会被 Rime 查到。
        for ((hIdx, h) in hypotheses.withIndex()) {
            val query = prefix + h.letters
            when (val r = coordinator.lookup.lookup(query)) {
                is GlideLookupResult.Candidates -> {
                    // 同一编码连续产出同一词根的整句候选，如「你说什么/你说什么呢/…」，
                    // 比只有一个勉强成串、随后退回「你」的编码更可信。
                    val head = r.list.firstOrNull()?.text.orEmpty()
                    val coherent = if (head.length >= 4) r.list.take(3).count {
                        it.text.length >= head.length && it.text.startsWith(head)
                    } else 0
                    val fullPhraseDepth = if (head.length >= 4) r.list.take(4).count {
                        it.text.length >= 4 && it.text.startsWith(head.take(2))
                    } else 0
                    val shortFallback = r.list.take(4).count { it.text.length <= 2 }
                    for ((i, c) in r.list.withIndex()) {
                        // 同一文本可能由多个编码假设产出，分数各不相同。
                        // 旧实现用 seen.add 在**计分前**去重，等于"先到先得"，
                        // 可能留下低分那条、丢掉高分那条。改为先算分再择优保留。
                        // 所有项统一到 log-domain（自然对数），逐项都有物理含义：
                        //   geometryLogP  几何似然：h.cost 越小越可能，直接取负
                        //   rimeLogP      Rime 位次推出的近似 log 概率
                        //   gramLogP      n-gram 条件概率（文本级，跨编码可比）
                        //   phraseLogP    短语连贯性（同编码下多个候选同根）
                        //   hypLogP       假设先验（几何首选假设更可信）
                        // 旧实现是五项不同量纲直接相加，0.45/0.15/1.1 都是手感常数。
                        val geometryLogP = -h.cost
                        val rimeLogP = -rimeRankLogProb(i)
                        val gramLogP = gramDeltaFor(c.text)
                        val phraseLogP = phraseLogProb(coherent, fullPhraseDepth, shortFallback)
                        val hypLogP = -hIdx * HYPOTHESIS_LOG_PENALTY
                        val scored = c.copy(
                            id = "glide-" + hIdx + "-" + i + "-" + c.text,
                            score = geometryLogP + rimeLogP + gramLogP + phraseLogP + hypLogP,
                        )
                        val known = indexByText[c.text]
                        if (known == null) {
                            indexByText[c.text] = merged.size
                            merged += scored
                        } else if (scored.score > merged[known].score) {
                            // 同文本保留分数更高的一条（通常是更低代价假设产出的）
                            merged[known] = scored
                        }
                    }
                }
                else -> Unit
            }
        }
        return merged.sortedByDescending { it.score }.take(MAX_GLIDE_CANDIDATES)
    }

    /**
     * Rime 位次 -> 近似的负对数概率。
     *
     * Rime 的候选顺序来自词频/语言模型，但接口只给位次不给分数。
     * 经验上候选概率随位次近似几何衰减（第 0 位最常见，之后快速变稀疏），
     * 用 p ≈ RANK_DECAY^(i) 折算成 -ln p = i * (-ln RANK_DECAY)。
     * 这样它和 gram 的 -ln P 是同一量纲，可以直接相加。
     */
    private fun rimeRankLogProb(rank: Int): Double =
        if (rank <= 0) 0.0 else rank * RANK_LOG_STEP

    /**
     * 短语连贯性的 log 证据。
     *
     * 同一编码连续产出同一词根的整句候选（"你说什么"/"你说什么呢"），
     * 比只勉强成串再退回单词更可信。用对数几率形式：每多一条支撑候选取 +ln(2)。
     */
    private fun phraseLogProb(coherent: Int, depth: Int, shortFallback: Int): Double {
        val support = coherent + depth * PHRASE_DEPTH_WEIGHT
        val penalty = shortFallback * PHRASE_SHORT_WEIGHT
        return (support - penalty) * LN2
    }

    /** 本次手势的 gram 基准分（首个候选），用于把绝对对数概率转成相对增量。 */
    private var gramBaseline: Double? = null

    /**
     * 编辑器前文（最近几个字），由宿主在滑动开始时注入。
     *
     * 空串表示拿不到前文（或宿主没提供）：此时 gram 退化为无条件词频，
     * 跟改动前行为一致。只在手势开始时取一次，避免每次候选都读 InputConnection。
     */
    var precedingText: () -> String = { "" }
    private var gestureContext: String = ""

    /**
     * 候选文本的 gram 相对增量。
     *
     * 第一次调用记录基准，之后返回 (score - baseline) * GRAM_WEIGHT。
     * 没有 gram 信号（方案无语法模型 / 引擎未就绪）时恒返回 0，行为与改动前一致。
     */
    /**
     * gram 打分入口（context, word）-> ln P。可注入：单测里替换成假实现，
     * 生产环境默认走 RimeEngine 的 octagram。返回 GRAM_UNAVAILABLE 表示无信号。
     */
    var gramScorer: (context: String, word: String) -> Double = { ctx, word ->
        val schema = currentSchema
        // 纯 JVM 单测里 RimeEngine 的 native 库加载不了（NoClassDefFoundError），
        // 而且真实场景下方案也可能没挂语法模型：两种情况都必须静默退回原排序。
        try {
            com.kingzcheung.xime.rime.RimeEngine.getInstance().queryGramScore(schema, ctx, word)
        } catch (_: Throwable) {
            com.kingzcheung.xime.rime.RimeEngine.GRAM_UNAVAILABLE
        }
    }

    private fun gramDeltaFor(text: String): Double {
        if (currentSchema.isEmpty() || text.isEmpty()) return 0.0
        val score = gramScorer(gestureContext, text)
        if (score <= com.kingzcheung.xime.rime.RimeEngine.GRAM_UNAVAILABLE) return 0.0
        val base = gramBaseline ?: score.also { gramBaseline = it }
        return (score - base) * GRAM_WEIGHT
    }

    private fun installCandidates(sorted: List<GlideCandidate>, hostToken: String) {
        val top = sorted.first()
        val session = coordinator.session
        session.beginGlide(hostToken, session.state.schemaFingerprint, session.state.layoutFingerprint,
            keepCandidates = session.state.rawInput.isNotEmpty())
        session.deliverCandidates(
            session.state.traceId, session.state.revision, hostToken, top.rawInput, sorted,
        )
    }

    internal data class DecodeRequest(
        val gestureId: Long, val hostToken: String, val schema: String,
        val geometry: GlideGeometrySnapshot, val points: List<GlidePoint>,
        val spellings: Collection<String>?, val ascii: Boolean, val densityDpi: Float,
        val final: Boolean, val prefix: String = "",
    )

    /** @param traceId 本次落盘的轨迹 id（未采集时为空串），确认上屏时据此自动标注 */
    internal data class DecodeResult(
        val request: DecodeRequest,
        val rawInput: String,
        val candidates: List<GlideCandidate>,
        val traceId: String = "",
    )

    /** 主线程固定快照，后台解码不读写正在采集的轨迹或会话。 */
    internal fun previewRequest(): DecodeRequest? {
        if (!isGliding) return null
        val geo = activeGeometry ?: return null
        return DecodeRequest(gestureId, currentHost, currentSchema, geo, collected.toList(), activeSpellings, asciiMode, activeDensityDpi, false, activePrefix)
    }

    internal fun finishRequest(pointerId: Long, hostToken: String): DecodeRequest? {
        if (!isGliding) { onUp(pointerId, hostToken); return null }
        if (arbiter.onUp(pointerId) != GlideGestureArbiter.Decision.EMIT_GLIDE) return null
        val geo = activeGeometry ?: return null
        val request = DecodeRequest(gestureId, hostToken, currentSchema, geo, collected.toList(), activeSpellings, asciiMode, activeDensityDpi, true, activePrefix)
        clearTrail()
        return request
    }

    /** 仅在后台线程执行；预览只使用独立查询，不往 Rime 主组合写编码。 */
    internal fun decode(request: DecodeRequest): DecodeResult {
        val hypotheses = hypothesesFor(request.points, request.geometry, request.spellings, request.densityDpi, allowNeural = request.final)
        val candidates = if (request.ascii) hypotheses.mapIndexed { i, h ->
            GlideCandidate("en-$" + i, h.letters, h.letters, score = -h.cost.toDouble())
        } else lookupHypotheses(hypotheses, request.prefix)
        // 预览尚未解出字母时，编码至少仍是已有前缀，避免显示短于当前组合的旧串。
        val rawInput = candidates.firstOrNull()?.rawInput
            ?: (request.prefix + hypotheses.firstOrNull()?.letters.orEmpty()).takeIf { it.isNotEmpty() }
            ?: request.prefix
        // 采集：抬手即落盘（不等确认）。解码可能是错的，但轨迹本身必须无损保留，
        // 事后由「滑动标记」补上真实意图。
        val traceId = if (request.final) {
            GlideTraceRecorder.record(
                points = request.points,
                geometry = request.geometry,
                schemaFingerprint = request.schema,
                prefix = request.prefix,
                code = rawInput,
                candidates = hypotheses.take(TRACE_CANDIDATE_LIMIT).map { request.prefix + it.letters },
            )
        } else ""
        return DecodeResult(request, rawInput, candidates, traceId)
    }

    internal fun isCurrent(request: DecodeRequest): Boolean =
        request.gestureId == gestureId && request.hostToken == currentHost

    /** 主线程应用；抬手、新一笔、取消后，旧后台结果不得覆盖。 */
    internal fun accept(result: DecodeResult): Boolean {
        val request = result.request
        if (!isCurrent(request)) return false
        if (!request.final) return isGliding
        if (isGliding) return false
        if (result.candidates.isEmpty()) {
            // 有可追加前缀但本次未解出新候选：保留既有组合与候选，不清空。
            if (request.prefix.isEmpty()) coordinator.session.cancel()
        } else {
            installCandidates(result.candidates, request.hostToken)
            lastTraceId = result.traceId
        }
        return true
    }

    /**
     * 最近一次安装候选对应的轨迹 id（未采集时为空串）。
     * 服务层确认上屏时用它给该条轨迹自动打一个「确认文本」标注；
     * 该标注只是猜测，手动标记会覆盖。
     */
    @Volatile
    var lastTraceId: String = ""
        private set

    fun onCancel() {
        gestureId++
        arbiter.onCancel()
        coordinator.session.cancel()
        clearTrail()
    }

    fun allowFallbackClick(): Boolean = arbiter.allowFallbackClick()

    /**
     * 先尝试神经假设，失败/为空时回退几何解码。
     *
     * 预览（final=false）仍走几何，避免每 100ms 都跑一次模型；
     * 抬手后的最终解码才用神经。
     */
    private fun hypothesesFor(
        points: List<GlidePoint>,
        geometry: GlideGeometrySnapshot,
        spellings: Collection<String>?,
        densityDpi: Float,
        allowNeural: Boolean,
    ): List<GlideGeometryDecoder.Hypothesis> {
        if (allowNeural) {
            val neural = runCatching {
                neuralProvider?.hypotheses(points, geometry, NEURAL_HYPOTHESES, spellings, densityDpi)
            }.getOrNull()
            if (!neural.isNullOrEmpty()) return neural
        }
        return GlideGeometryDecoder.decode(points, geometry, GLIDE_HYPOTHESES, spellings, densityDpi)
    }

    private companion object {
        /** 交给词典排序的候选串数（共线键如 zhong/zong 需要前几名都在）。 */
        const val GLIDE_HYPOTHESES = 16
        /** 神经侧多送一些假设：真实轨迹上 top-64 才覆盖到第 10/16 条正解。 */
        const val NEURAL_HYPOTHESES = 64

        /** Rime 每往后一位的 log 差（≈ ln 1.5，位次概率约按 2/3 衰减）。 */
        const val RANK_LOG_STEP = 0.4
        /** 整句深度支撑项的权重（相对 coherent 的 1.0）。 */
        const val PHRASE_DEPTH_WEIGHT = 0.7
        /** 短词兜底项的权重。 */
        const val PHRASE_SHORT_WEIGHT = 1.0
        /** 假设先验：每个位次差对应的 log 惩罚。 */
        const val HYPOTHESIS_LOG_PENALTY = 0.15
        /** ln 2，用于"多一条独立证据"的对数几率。 */
        const val LN2 = 0.6931471805599453
        const val MAX_GLIDE_CANDIDATES = 16

        /**
         * 跨假设 gram 的权重。
         *
         * **实测数据（真机，rime_ice）**：
         * | 前文   | 起来   | 起凉   | 差值 |
         * |--------|--------|--------|------|
         * | 空     | -12.00 | -12.00 | 0    |
         * | 我们   |  -3.56 |  -9.84 | 6.28 |
         * | 天气   |  -2.18 |  -9.24 | 7.06 |
         *
         * 所以取 1.0 时，gram 差值（6~7）**远大于**本批样本的几何分差（0.5~2）——
         * 语义信号会直接决定排序，而不是"只在几何难分时起作用"。
         *
         * 这符合预期：双拼每音节只有两键，几何本来就分不开同音候选，
         * 这正是需要语言模型的地方。风险在于前文很短或误判时会带偏，
         * 因此拿不到前文时 gramDelta 恒为 0（退化为原排序）。
         *
         * 待数据验证：等高频率的"同音不同词"样本攒够后，再定是否需要下调。
         */
        const val GRAM_WEIGHT = 1.0
        /** 采集用的候选文本上限：只留前几名，足够判断几何排序是否出错。 */
        const val TRACE_CANDIDATE_LIMIT = 8
    }

    private fun clearTrail() {
        collected.clear()
        activeGeometry = null
        glideSessionStarted = false
    }
}
