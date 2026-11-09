package com.kingzcheung.xime.glide

/**
 * 滑动输入的组合会话（纯状态，不依赖 Android / Rime）。
 *
 * 约束（来自计划书第六节）：
 * - 抬手后进入 PENDING_CONFIRM，但**不提交**；只有 [confirm] 成功才允许上屏。
 * - 确认必须带匹配的 hostToken 与 revision，且幂等：一次滑动只上屏一次。
 * - 编辑（继续滑、退格、点选改码）递增 revision，旧异步解码结果不得覆盖。
 * - 取消 / 宿主切换 / 方案切换恢复可编辑组合，不产生提交。
 */
class GlideCompositionSession {

    enum class Phase { IDLE, COLLECTING, DECODING, PENDING_CONFIRM, CONFIRMED, CANCELLED }

    data class State(
        val phase: Phase = Phase.IDLE,
        val traceId: Long = 0L,
        val revision: Long = 0L,
        val hostToken: String = "",
        val schemaFingerprint: String = "",
        val layoutFingerprint: String = "",
        val rawInput: String = "",
        val candidates: List<GlideCandidate> = emptyList(),
        /** 上次成功确认的候选 id；幂等用。 */
        val confirmedCandidateId: String? = null,
    ) {
        val isComposing: Boolean get() = phase != Phase.IDLE && phase != Phase.CONFIRMED && phase != Phase.CANCELLED
        val canConfirm: Boolean get() = phase == Phase.PENDING_CONFIRM
    }

    @Volatile
    private var _state = State()
    val state: State get() = _state

    /** 开始一次新滑动。返回本次 traceId；已有 PENDING_CONFIRM 时追加编码而非新建。 */
    fun beginGlide(hostToken: String, schemaFingerprint: String, layoutFingerprint: String): Long {
        val cur = _state
        val traceId = cur.traceId + 1
        _state = cur.copy(
            phase = Phase.COLLECTING,
            traceId = traceId,
            revision = cur.revision + 1,
            hostToken = hostToken,
            schemaFingerprint = schemaFingerprint,
            layoutFingerprint = layoutFingerprint,
            candidates = emptyList(),
            confirmedCandidateId = null,
        )
        return traceId
    }

    /** 追加未确认编码，重算时清空旧候选并递增 revision。 */
    fun appendCode(suffix: String): Boolean {
        val cur = _state
        if (!cur.isComposing || cur.phase == Phase.DECODING) return false
        _state = cur.copy(
            rawInput = cur.rawInput + suffix,
            revision = cur.revision + 1,
            phase = Phase.COLLECTING,
            candidates = emptyList(),
        )
        return true
    }

    /** 退格：按字符删除编码末尾，递增 revision；空编码时不产生负状态。 */
    fun backspace(): Boolean {
        val cur = _state
        if (!cur.isComposing || cur.rawInput.isEmpty()) return false
        _state = cur.copy(
            rawInput = cur.rawInput.dropLast(1),
            revision = cur.revision + 1,
            phase = Phase.COLLECTING,
            candidates = emptyList(),
        )
        return true
    }

    fun markDecoding(traceId: Long, revision: Long) {
        val cur = _state
        if (cur.traceId != traceId || cur.revision != revision) return
        _state = cur.copy(phase = Phase.DECODING)
    }

    /**
     * 提交解码结果。只有 traceId/revision/hostToken 全部匹配、且仍处于本次解码阶段才接受，
     * 否则静默丢弃（旧异步结果不得覆盖新编辑）。返回结果是否被采纳。
     */
    fun deliverCandidates(
        traceId: Long,
        revision: Long,
        hostToken: String,
        rawInput: String,
        candidates: List<GlideCandidate>,
    ): Boolean {
        val cur = _state
        if (cur.traceId != traceId || cur.revision != revision || cur.hostToken != hostToken) return false
        if (cur.phase != Phase.COLLECTING && cur.phase != Phase.DECODING) return false
        val valid = candidates.filter { it.isValid }
        _state = cur.copy(
            phase = if (valid.isEmpty()) Phase.IDLE else Phase.PENDING_CONFIRM,
            rawInput = rawInput,
            candidates = valid,
        )
        return true
    }

    sealed class ConfirmResult {
        data class Confirmed(val candidate: GlideCandidate) : ConfirmResult()
        object Rejected : ConfirmResult()
        object AlreadyConfirmed : ConfirmResult()
    }

    /** 明确确认。只有带匹配的 hostToken/revision 且处于待确认态才产生一次上屏动作。 */
    fun confirm(candidateId: String, hostToken: String): ConfirmResult {
        val cur = _state
        if (cur.confirmedCandidateId == candidateId) return ConfirmResult.AlreadyConfirmed
        if (!cur.canConfirm || cur.hostToken != hostToken) return ConfirmResult.Rejected
        val candidate = cur.candidates.firstOrNull { it.id == candidateId } ?: return ConfirmResult.Rejected
        _state = cur.copy(phase = Phase.CONFIRMED, confirmedCandidateId = candidate.id)
        return ConfirmResult.Confirmed(candidate)
    }

    /** 取消：恢复可编辑组合但清空候选；已确认态不回退。 */
    fun cancel() {
        val cur = _state
        if (cur.phase == Phase.CONFIRMED) return
        _state = cur.copy(phase = Phase.IDLE, candidates = emptyList(), revision = cur.revision + 1)
    }

    /**
     * 确认动作在真正提交前失败（install/select 失败）时回滚：
     * 清除已记录的确认，恢复为待确认态，允许重试，但不产生上屏。
     */
    fun rollbackFailedConfirm(candidateId: String) {
        val cur = _state
        if (cur.phase != Phase.CONFIRMED || cur.confirmedCandidateId != candidateId) return
        _state = cur.copy(phase = Phase.PENDING_CONFIRM, confirmedCandidateId = null, revision = cur.revision + 1)
    }

    /** 宿主切换：任何进行中的会话作废，不产生提交。 */
    fun onHostChanged() {
        _state = State()
    }

    /** 方案或布局切换：取消当前会话，保留编码文本以便重新解码，不提交。 */
    fun onEnvironmentChanged() {
        val cur = _state
        if (!cur.isComposing) return
        _state = cur.copy(phase = Phase.IDLE, candidates = emptyList(), revision = cur.revision + 1)
    }
}
