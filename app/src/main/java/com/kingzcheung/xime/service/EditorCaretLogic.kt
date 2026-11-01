package com.kingzcheung.xime.service

/**
 * 标记面板方向键的纯计算部分：选区折叠端点、光标钳制与边界判定。
 *
 * 编辑器在选区边界再收到方向键会把选区原地折叠成单点，
 * 因此这里统一给出折叠目标与钳制后的位置，避免端点越界后方向键失效。
 */
internal object EditorCaretLogic {

    /** 按方向折叠选区时的落点：左/上取选区头，右/下取选区尾。 */
    fun collapseTarget(selectionStart: Int, selectionEnd: Int, direction: Int): Int =
        if (direction < 0) selectionStart else selectionEnd

    /** 把端点钳制到 [0, total]；编辑器钳制过的位置同样适用。 */
    fun clampCaret(position: Int, total: Int): Int = position.coerceIn(0, maxOf(total, 0))

    /** 在文本范围内按 [direction] 步进一格；到端点后原地不动。 */
    fun stepCaret(position: Int, direction: Int, total: Int): Int =
        clampCaret(clampCaret(position, total) + direction, total)

    /**
     * 是否已经顶到整段文本的首/尾：此时同方向的方向键不会再移动。
     *
     * 只按整段文本的首尾判定，不按段落/视觉行——编辑器自己是按视觉行移动的，
     * 用文本位置推段落边界会误判，反而把能正常移动的方向键吞掉。
     */
    fun isAtBoundary(position: Int, direction: Int, total: Int): Boolean {
        val caret = clampCaret(position, total)
        return if (direction < 0) caret <= 0 else caret >= maxOf(total, 0)
    }
}
