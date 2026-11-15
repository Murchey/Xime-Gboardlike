package com.kingzcheung.xime.settings

/**
 * 键盘布局预设（键数 + 字母排列）。
 *
 * 目前只承接设置页的选择与持久化：26 键是既有形态，14/17/18 键的键位映射
 * 还没接入键盘渲染。字母排列（QWERTY / ABCDE）决定键帽上字母的顺序。
 */
object KeyboardLayoutPreset {

    const val LAYOUT_26 = "26"
    const val LAYOUT_14 = "14"
    const val LAYOUT_17 = "17"
    const val LAYOUT_18 = "18"

    const val ORDER_QWERTY = "qwerty"
    const val ORDER_ABCDE = "abcde"

    val LAYOUTS = listOf(LAYOUT_26, LAYOUT_14, LAYOUT_17, LAYOUT_18)

    fun normalizeLayout(id: String?): String =
        if (id != null && id in LAYOUTS) id else LAYOUT_26

    fun normalizeOrder(id: String?): String =
        if (id == ORDER_ABCDE) ORDER_ABCDE else ORDER_QWERTY

    /** 键数是否已接入键盘（目前只有 26 键）。 */
    fun isKeyboardSupported(layout: String): Boolean = normalizeLayout(layout) == LAYOUT_26

    /** 字母表顺序的 26 键三行（ABCDE 排列）；QWERTY 用键盘现有的兜底行。 */
    val ABCDE_ROWS: List<List<String>> = listOf(
        "abcdefghij".map { it.toString() },
        "klmnopqrst".map { it.toString() },
        "uvwxyz".map { it.toString() },
    )
}
