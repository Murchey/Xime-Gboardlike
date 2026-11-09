package com.kingzcheung.xime.glide

/**
 * 阶段A能力边界：把一条完整编码串解析成整句候选。
 *
 * 真实实现走 Rime 独立会话；接口刻意与 Android / Rime 解耦，便于 JVM 单测
 * 和后续阶段C替换为"轨迹→多假设→有界复核"的解码器。
 *
 * 契约：
 * - 返回的是**整句消费**的候选，调用方不负责再拼后半段。
 * - NoCandidate 与 Busy／Error／Unsupported 是不同结果，不能一律当空。
 * - 调用方负责带 traceId/revision；本接口不持有会话状态。
 */
interface GlideSentenceLookup {
    fun lookup(rawInput: String, maxCount: Int = 8): GlideLookupResult
}

sealed class GlideLookupResult {
    data class Candidates(val list: List<GlideCandidate>) : GlideLookupResult()
    object NoCandidate : GlideLookupResult()
    object Busy : GlideLookupResult()
    object Unsupported : GlideLookupResult()
    data class Error(val message: String) : GlideLookupResult()
}

/** 确认时的提交动作，由服务层注入；隔离具体 InputConnection / Rime 选词。 */
interface GlideCommitter {
    /** 把候选对应的编码装进主组合，返回是否成功；成功后由调用方走正常选词/学习。 */
    fun installRawInput(rawInput: String): Boolean
    /** 选中当前组合中与该候选一致的词（走 Rime 正常学习路径），返回是否成功。 */
    fun selectCandidate(rawInput: String, text: String): Boolean
}
