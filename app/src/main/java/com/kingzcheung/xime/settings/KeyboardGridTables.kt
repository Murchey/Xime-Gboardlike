package com.kingzcheung.xime.settings

/**
 * 14 / 17 / 18 键的组键表（哪几个字母共用一个键、按哪个组码送进引擎）。
 *
 * QWERTY 排布取自 \`app/src/main/assets/xime.yaml\` 的 \`qwerty_14 / qwerty_17 / qwerty_18\`
 * 段；14 键与 18 键的组键与参考实现「水杉输入法（msime）」的
 * \`FourteenKeyLayout\` / \`EighteenKeyLayout\` 逐键相同（见 msime 仓库同名 Java/Kotlin/Swift 表），
 * 17 键是本仓库在 18 键基础上把 \`y\`/\`u\` 也并成一键得到的。
 *
 * 每个合并键点一下送的是**组的首字母**（组码）：QW 送 \`q\`、WE 送 \`w\`。
 * 引擎侧要按组码做歧义解码（26 键是直接送字母，不需要解码），
 * 这就是这三套布局至今只有键面、还不能真正打字的原因。
 */
object KeyboardGridTables {

    /** 一个合并键：1~2 个小写字母；组码 = 首字母。 */
    data class Key(val letters: String) {
        init {
            require(letters.length in 1..2) { "合并键只能带 1~2 个字母" }
            require(letters.all { it in 'a'..'z' }) { "合并键只能用小写字母" }
        }

        /** 点按送给引擎的组码。 */
        val code: Char get() = letters[0]

        /** 键面（大写，多字母键按参考实现连写，不做间距）。 */
        val face: String get() = letters.uppercase()
    }

    private fun row(vararg groups: String) = groups.map { Key(it) }

    /** 14 键 QWERTY：5 / 5 / 4，与 msime \`FourteenKeyLayout\` 相同。 */
    val ROWS_14_QWERTY: List<List<Key>> = listOf(
        row("qw", "er", "ty", "ui", "op"),
        row("as", "df", "gh", "jk", "l"),
        row("zx", "cv", "bn", "m"),
    )

    /** 17 键 QWERTY：6 / 6 / 5（本仓库在 18 键基础上把 y/u 并组）。 */
    val ROWS_17_QWERTY: List<List<Key>> = listOf(
        row("q", "we", "rt", "yu", "i", "op"),
        row("a", "sd", "fg", "h", "jk", "l"),
        row("z", "xc", "v", "bn", "m"),
    )

    /** 18 键 QWERTY：7 / 6 / 5，与 msime \`EighteenKeyLayout\` 相同。 */
    val ROWS_18_QWERTY: List<List<Key>> = listOf(
        row("q", "we", "rt", "y", "u", "io", "p"),
        row("a", "sd", "fg", "h", "jk", "l"),
        row("z", "xc", "v", "bn", "m"),
    )

    /**
     * 字母表顺序（ABCDE）排布：按字母序把 a..z 依次填进同样形状的行，
     * 行内先尽量凑双字母键、再补单字母键，保证每行键数与原排布一致。
     *
     * 例：14 键 → \`ab cd ef gh ij / kl mn op qr st / uv wx y z\`。
     */
    fun abcdeRows(shape: List<Int>): List<List<Key>> {
        require(shape.isNotEmpty()) { "行形状不能为空" }
        val totalKeys = shape.sum()
        require(totalKeys in 1..26) { "键数必须在 1..26 之间：$totalKeys" }
        val doubles = 26 - totalKeys
        require(doubles <= totalKeys) { "双字母键数不能超过键数" }

        // 双字母键按行均摊：行越长分到越多，余数给前面的行
        val perRow = IntArray(shape.size)
        for (i in 0 until doubles) perRow[i % shape.size]++
        // 某行的双字母键不能超过该行键数
        for (i in shape.indices) perRow[i] = perRow[i].coerceAtMost(shape[i])
        var assign = perRow.sum()
        var i = 0
        while (assign < doubles) {           // 兜底：均摊后仍不够就继续往能放的行里塞
            val r = i % shape.size
            if (perRow[r] < shape[r]) {
                perRow[r]++
                assign++
            }
            i++
            check(i < shape.size * 64) { "无法把双字母键分配进行形状" }
        }

        var letter = 'a'
        val out = ArrayList<List<Key>>(shape.size)
        for (r in shape.indices) {
            val keys = ArrayList<Key>(shape[r])
            for (k in 0 until shape[r]) {
                val take = if (k < perRow[r]) 2 else 1
                val group = buildString { repeat(take) { append(letter++) } }
                keys.add(Key(group))
            }
            out.add(keys)
        }
        check(letter == '{') { "字母没有用完：剩余到 $letter" }
        return out
    }

    /** 按布局 id（26/14/17/18）取 QWERTY 组键表；26 键返回空表（走既有全键盘）。 */
    fun rowsFor(layout: String): List<List<Key>> = when (layout) {
        KeyboardLayoutPreset.LAYOUT_14 -> ROWS_14_QWERTY
        KeyboardLayoutPreset.LAYOUT_17 -> ROWS_17_QWERTY
        KeyboardLayoutPreset.LAYOUT_18 -> ROWS_18_QWERTY
        else -> emptyList()
    }

    /** 行形状（每行键数）。 */
    fun shapeOf(layout: String): List<Int> = rowsFor(layout).map { it.size }

    /** 字母 → 组码（没有这个字母时返回 null）。 */
    fun groupCode(rows: List<List<Key>>, letter: Char): Char? {
        val lower = letter.lowercaseChar()
        return rows.flatten().firstOrNull { lower in it.letters }?.code
    }
}
