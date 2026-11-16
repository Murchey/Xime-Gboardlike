package com.kingzcheung.xime.settings

/**
 * Gboard 风格的字母键右上角符号提示。
 *
 * 来源是 Gboard 的键面带符号：上排 q..p 对应 1..0，中排 @ # $ - & - + ( )，
 * 下排 ` " ' : ; ! ?。fcitx5 那套（@ * + - = / # （ ） / 引号括号…）仍是默认，
 * 用户在「键盘偏好」里可以切换。中文模式下问号、感叹号、顿号仍按中文标点规则输出。
 */
object SymbolHintStyles {
    /** keyId（单字母）→ 提示符号。 */
    private val GBOARD = mapOf(
        "q" to "1", "w" to "2", "e" to "3", "r" to "4", "t" to "5",
        "y" to "6", "u" to "7", "i" to "8", "o" to "9", "p" to "0",
        "a" to "@", "s" to "#", "d" to "\$", "f" to "_", "g" to "&",
        "h" to "-", "j" to "+", "k" to "(", "l" to ")",
        "z" to "、", "x" to "\"", "c" to ".", "v" to ":", "b" to ";",
        "n" to "!", "m" to "?",
    )

    /** 指定风格下该键的提示符号；没有对应样式时返回 null（保持配置里的原值）。 */
    fun hintFor(style: String, key: String): String? =
        if (style == SettingsPreferences.SYMBOL_HINT_GBOARD) GBOARD[key] else null
}