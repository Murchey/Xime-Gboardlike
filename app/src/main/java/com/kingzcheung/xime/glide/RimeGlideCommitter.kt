package com.kingzcheung.xime.glide

import com.kingzcheung.xime.rime.RimeEngine

/**
 * 阶段A/ B 的真实提交适配器：安装编码到主组合，再走 Rime 正常选词。
 *
 * 注意：这是阶段B的简化实现——installRawInput 用 setInput 替换主组合，
 * selectCandidate 用文本匹配在当前候选中定位后选词；阶段D会换成带
 * 消费范围/分段的原生候选桥接。任一步失败由 GlideCoordinator 回滚。
 */
class RimeGlideCommitter(
    private val engine: RimeEngine = RimeEngine.getInstance(),
) : GlideCommitter {
    override fun installRawInput(rawInput: String): Boolean {
        if (rawInput.isEmpty()) return false
        return engine.setInput(rawInput)
    }

    override fun selectCandidate(rawInput: String, text: String): Boolean {
        val all = engine.getAllCandidates(200)
        val index = all.indexOfFirst { it.text == text }
        if (index < 0) return false
        return engine.selectCandidateByGlobalIndex(index)
    }
}
