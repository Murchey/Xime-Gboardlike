package com.kingzcheung.xime.glide

/**
 * 阶段A协调器：把一次滑动的"抬手→解码→候选→确认/取消"串成闭环。
 *
 * 不做手势采集（阶段B）、不做轨迹解码（阶段C）；[rawInput] 由调用方给出，
 * 本类负责保证提交契约：不提前上屏、确认只调用一次提交、过期结果不覆盖。
 */
class GlideCoordinator(
    private val lookup: GlideSentenceLookup,
    private val committer: GlideCommitter,
) {
    val session = GlideCompositionSession()

    sealed class Outcome {
        object ShowCandidates : Outcome()
        object NoCandidate : Outcome()
        object Busy : Outcome()
        object Unsupported : Outcome()
        data class Failed(val message: String) : Outcome()
    }

    /** 抬手：对当前编码做一次整句查询，结果写入会话（仍不提交）。 */
    fun onTraceFinished(hostToken: String, schemaFingerprint: String, layoutFingerprint: String): Outcome {
        val raw = session.state.rawInput
        if (raw.isEmpty()) return Outcome.NoCandidate
        val traceId = session.state.traceId
        val revision = session.state.revision
        session.markDecoding(traceId, revision)
        return when (val result = lookup.lookup(raw)) {
            is GlideLookupResult.Candidates -> {
                val accepted = session.deliverCandidates(traceId, revision, hostToken, raw, result.list)
                if (accepted) Outcome.ShowCandidates else Outcome.NoCandidate
            }
            GlideLookupResult.NoCandidate -> { session.cancel(); Outcome.NoCandidate }
            GlideLookupResult.Busy -> { session.cancel(); Outcome.Busy }
            GlideLookupResult.Unsupported -> { session.cancel(); Outcome.Unsupported }
            is GlideLookupResult.Error -> { session.cancel(); Outcome.Failed(result.message) }
        }
    }

    sealed class CommitOutcome {
        data class Committed(val text: String) : CommitOutcome()
        object Rejected : CommitOutcome()
        object InstallFailed : CommitOutcome()
        object SelectFailed : CommitOutcome()
    }

    /** 用户明确确认：安装编码 → 走 Rime 正常选词。任一步失败都不算已上屏。 */
    fun confirm(candidateId: String, hostToken: String): CommitOutcome {
        return when (val result = session.confirm(candidateId, hostToken)) {
            is GlideCompositionSession.ConfirmResult.Confirmed -> {
                val c = result.candidate
                if (!committer.installRawInput(c.rawInput)) {
                    session.rollbackFailedConfirm(c.id)
                    return CommitOutcome.InstallFailed
                }
                if (!committer.selectCandidate(c.rawInput, c.text)) {
                    session.rollbackFailedConfirm(c.id)
                    return CommitOutcome.SelectFailed
                }
                CommitOutcome.Committed(c.text)
            }
            GlideCompositionSession.ConfirmResult.Rejected -> CommitOutcome.Rejected
            GlideCompositionSession.ConfirmResult.AlreadyConfirmed -> CommitOutcome.Rejected
        }
    }
}
