package com.kingzcheung.xime.service

/**
 * 五笔连打的累积缓冲区。
 *
 * 五笔方案下每打满一码，librime 就直接 commit 一个字。开启连打后不能让这些字
 * 立刻落进输入框，否则退格、整句修改都很麻烦——把它们先攒在这里，候选栏展示
 * 累积结果，按空格/回车时一次性上屏。
 *
 * 纯状态操作，便于单测：不碰 InputConnection，也不依赖任何 Android API。
 */
class ChainedCommitBuffer {

    private val builder = StringBuilder()

    /** 已累积但尚未上屏的文本。 */
    val text: String get() = builder.toString()

    val isEmpty: Boolean get() = builder.isEmpty()

    /** 追加一段新上屏的文本；空白内容忽略。 */
    fun append(segment: String): String {
        if (segment.isNotEmpty()) builder.append(segment)
        return builder.toString()
    }

    /** 删除末尾一个字符（代理对整体删除，避免把 emoji 劈成半个）。 */
    fun deleteLast(): String {
        if (builder.isEmpty()) return ""
        val lastChar = builder[builder.length - 1]
        // 末位是低代理，说明与它配对的高代理也在末尾，一起删
        val removeCount =
            if (Character.isLowSurrogate(lastChar) && builder.length >= 2 &&
                Character.isHighSurrogate(builder[builder.length - 2])
            ) 2 else 1
        builder.setLength(builder.length - removeCount)
        return builder.toString()
    }

    /** 取出全部内容并清空（上屏时调用）。 */
    fun drain(): String {
        val out = builder.toString()
        builder.setLength(0)
        return out
    }

    fun clear() {
        builder.setLength(0)
    }
}
