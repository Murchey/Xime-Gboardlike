package com.kingzcheung.xime.settings

/**
 * 单手模式：把键盘收窄到屏幕一侧，另一侧留白，便于单手握持时用拇指够到全部按键。
 *
 * 与"键盘宽度调节"共用同一套左右边距机制（[SettingsPreferences.getKeyboardMarginStartDp]），
 * 这里只负责把模式换算成"多出来的那一侧边距"，几何计算做成纯函数便于单测。
 */
object OneHandedMode {
    const val OFF = 0
    const val LEFT = 1
    const val RIGHT = 2

    /** 单手模式下键盘占可用宽度的比例：再窄会挤压 26 键的键宽，再宽则留白不够握。 */
    const val WIDTH_RATIO = 0.88f

    /** 留白宽度（dp）：屏幕宽度减去键盘宽度。 */
    fun gapDp(screenWidthDp: Int): Int {
        if (screenWidthDp <= 0) return 0
        return (screenWidthDp * (1f - WIDTH_RATIO)).toInt().coerceAtLeast(0)
    }

    /**
     * 生效的左右边距：在用户已有宽度调节的基础上，把留白集中加到另一侧。
     *
     * @return 左边距 to 右边距（dp）
     */
    fun margins(
        mode: Int,
        screenWidthDp: Int,
        baseStartDp: Int,
        baseEndDp: Int,
    ): Pair<Int, Int> = marginsWithGap(mode, gapDp(screenWidthDp), baseStartDp, baseEndDp)

    /** 用已算好的留白宽度换算左右边距（单手模式调过宽度后走这条）。 */
    fun marginsWithGap(
        mode: Int,
        gapDp: Int,
        baseStartDp: Int,
        baseEndDp: Int,
    ): Pair<Int, Int> {
        val gap = gapDp.coerceAtLeast(0)
        return when (mode) {
            LEFT -> baseStartDp to (baseEndDp + gap)
            RIGHT -> (baseStartDp + gap) to baseEndDp
            else -> baseStartDp to baseEndDp
        }
    }

    /** 首次进入单手模式默认靠右：右手拇指握持更常见，用户可用换边按钮切到左手。 */
    const val DEFAULT_SIDE = RIGHT

    /** 单手键盘宽度下限/上限（占屏宽比例）：太小按键挤，太宽留白不够握。 */
    const val MIN_WIDTH_RATIO = 0.70f
    const val MAX_WIDTH_RATIO = 0.98f

    /** 把宽度收进合法范围后换算成留白（dp）。 */
    fun gapDpForWidth(screenWidthDp: Int, keyboardWidthDp: Int): Int {
        if (screenWidthDp <= 0) return 0
        val minWidth = (screenWidthDp * MIN_WIDTH_RATIO).toInt()
        val maxWidth = (screenWidthDp * MAX_WIDTH_RATIO).toInt()
        val width = keyboardWidthDp.coerceIn(minWidth.coerceAtMost(maxWidth), maxWidth)
        return (screenWidthDp - width).coerceAtLeast(0)
    }

    /** 键盘是否贴在左侧（左手模式）；其余情况贴右。 */
    fun isKeyboardOnLeft(mode: Int): Boolean = mode == LEFT

    /** 点击切换按钮后的下一个模式。 */
    fun nextSide(mode: Int): Int = when (mode) {
        LEFT -> RIGHT
        RIGHT -> LEFT
        else -> LEFT
    }
}
