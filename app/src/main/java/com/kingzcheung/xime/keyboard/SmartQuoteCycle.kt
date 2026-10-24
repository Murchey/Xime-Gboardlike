package com.kingzcheung.xime.keyboard

/**
 * 长按 C 的智能引号状态：第一次左引号，第二次右引号，交替。
 * 每个新输入会话由服务层 reset。
 */
internal class SmartQuoteCycle(
    private val left: String = "“",
    private val right: String = "”",
) {
    private var nextIsLeft = true

    fun next(): String {
        val quote = if (nextIsLeft) left else right
        nextIsLeft = !nextIsLeft
        return quote
    }

    fun reset() {
        nextIsLeft = true
    }
}
