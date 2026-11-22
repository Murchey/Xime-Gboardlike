package com.kingzcheung.xime.handwriting

/**
 * 公式上屏时使用的 LaTeX 分隔符。
 *
 * 手写识别只出 LaTeX 源码，是否包一层标记由用户选：常见的是 `$$…$$`、
 * `\\[…\\]`、`\\(…\\)`，也可以不加。
 */
enum class FormulaDelimiter(
    val id: String,
    val label: String,
    val open: String,
    val close: String,
) {
    DOLLAR("dollar", "$$…$$", "$$", "$$"),
    BRACKET("bracket", "\\[…\\]", "\\[", "\\]"),
    PAREN("paren", "\\(…\\)", "\\(", "\\)"),
    NONE("none", "不加标记", "", "");

    fun wrap(latex: String): String = if (latex.isEmpty()) latex else open + latex + close

    companion object {
        fun of(id: String?): FormulaDelimiter = entries.firstOrNull { it.id == id } ?: DOLLAR
    }
}
