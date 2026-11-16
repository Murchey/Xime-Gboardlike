package com.kingzcheung.xime.settings

/**
 * 键盘布局预设（键数 + 字母排列）。
 *
 * 26 键是原生全键盘；14/17/18 键对应运行时派生的 rime 方案（见 GridSchemaGenerator），
 * 字母排列（QWERTY / ABCDE）决定合并键怎么分组、组码是什么。
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

    /** 键数是否已接入键盘（26 键是原生全键盘，14/17/18 走对应的 rime 方案）。 */
    fun isKeyboardSupported(layout: String): Boolean = true

    /** 14/17/18 键对应的 rime 方案 id；26 键返回 null（沿用用户当前方案，只换字母排列）。 */
    fun schemaIdFor(layout: String, order: String): String? {
        val l = normalizeLayout(layout)
        if (l == LAYOUT_26) return null
        val o = normalizeOrder(order)
        return if (o == ORDER_ABCDE) "pinyin_${l}jian_abcde" else "pinyin_${l}jian"
    }

    /** 是否是本仓库自带的 14/17/18 键方案（用于「切回 26 键时恢复原方案」）。 */
    fun isGridSchema(schemaId: String): Boolean =
        schemaId.startsWith("pinyin_14jian") || schemaId.startsWith("pinyin_17jian") ||
            schemaId.startsWith("pinyin_18jian")

    /** 字母表顺序的 26 键三行（ABCDE 排列）；QWERTY 用键盘现有的兜底行。 */
    val ABCDE_ROWS: List<List<String>> = listOf(
        "abcdefghij".map { it.toString() },
        "klmnopqrst".map { it.toString() },
        "uvwxyz".map { it.toString() },
    )
}
