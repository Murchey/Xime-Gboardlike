package com.kingzcheung.xime.service

/**
 * 连按两次空格插入标点的判定。
 *
 * 仅当第一次空格确实以空格字符上屏、且第二次空格在 [TIMEOUT_MS] 内到达时触发；
 * 光标前字符必须仍是空格（防止用户中途移动光标或切换输入框后误替换）。
 */
internal object DoubleSpacePunctuation {
    /** 两次空格的最大间隔：超过则视为普通连续空格。 */
    const val TIMEOUT_MS = 450L

    fun shouldReplace(
        enabled: Boolean,
        previousChar: String?,
        lastSpaceAt: Long,
        now: Long,
    ): Boolean = enabled &&
        previousChar == " " &&
        lastSpaceAt > 0L &&
        now >= lastSpaceAt &&
        now - lastSpaceAt <= TIMEOUT_MS
}
