package com.kingzcheung.xime.glide

import com.kingzcheung.xime.rime.RimeEngine

/**
 * 阶段A的真实查询适配器：复用 RimeEngine.bypassLookup 的独立只读会话。
 *
 * 注意（计划书第六节）：bypassLookup 内部仍持有全局 rimeLock，
 * 因此这里只允许对**少量完整假设**调用，绝不逐采样调用；
 * tryLock 失败会返回空数组，这里保守判为 Busy 而不是"无候选"。
 */
class RimeGlideSentenceLookup(
    private val engine: RimeEngine = RimeEngine.getInstance(),
    private val ready: () -> Boolean = { RimeEngine.isInitialized() },
) : GlideSentenceLookup {

    override fun lookup(rawInput: String, maxCount: Int): GlideLookupResult {
        if (rawInput.isBlank()) return GlideLookupResult.NoCandidate
        if (!ready()) return GlideLookupResult.Busy
        return try {
            val raw = engine.bypassLookup(rawInput, maxCount)
            if (raw.isEmpty()) GlideLookupResult.NoCandidate
            else {
                val list = raw.mapIndexed { index, c ->
                    GlideCandidate(
                        id = "rime-" + index + "-" + c.text,
                        text = c.text,
                        rawInput = rawInput,
                        comment = c.comment,
                        score = (raw.size - index).toDouble(),
                    )
                }.filter { it.isValid }
                if (list.isEmpty()) GlideLookupResult.NoCandidate else GlideLookupResult.Candidates(list)
            }
        } catch (e: Throwable) {
            GlideLookupResult.Error(e.message ?: "整句查询异常")
        }
    }
}
