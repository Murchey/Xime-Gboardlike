package com.kingzcheung.xime.settings

/**
 * 字母键长按的变体字符表（大小写 + 注音/变音字母）。
 *
 * 与 fcitx5-android 的 `PopupPreset`（Latin 段）同源，只是**去掉了首项符号**：
 * 符号由当前「符号提示排布」（fcitx5 配置 / Gboard 角标）提供，本表只补字符变体，
 * 这样无论哪一套符号排布，长按都能选到大写/小写与带声调、变音符号的字母。
 */
object LetterLongPressVariants {

    /** 单字母 → 变体列表（不含该小写字母本身；首项符号另由键盘配置提供）。 */
    private val VARIANTS: Map<Char, List<String>> = mapOf(
        'q' to listOf("Q"),
        'w' to listOf("W"),
        'e' to listOf("E", "ê", "ë", "ē", "é", "ě", "è", "ė", "ę", "ȩ", "ḝ", "ə"),
        'r' to listOf("R"),
        't' to listOf("T"),
        'y' to listOf("Y", "ÿ", "ұ", "ү", "ӯ", "ў"),
        'u' to listOf("U", "û", "ü", "ū", "ú", "ǔ", "ù"),
        'i' to listOf("I", "î", "ï", "ī", "í", "ǐ", "ì", "į", "ı"),
        'o' to listOf("O", "ô", "ö", "ō", "ó", "ǒ", "ò", "œ", "ø", "õ"),
        'p' to listOf("P"),
        'a' to listOf("A", "â", "ä", "ā", "á", "ǎ", "à", "æ", "ã", "å", "ą"),
        's' to listOf("S", "ß", "ś", "š", "ş"),
        'd' to listOf("D", "ð"),
        'f' to listOf("F"),
        'g' to listOf("G", "ğ"),
        'h' to listOf("H"),
        'j' to listOf("J"),
        'k' to listOf("K", "[", "{"),
        'l' to listOf("L", "]", "}", "ł"),
        'z' to listOf("Z", "`", "ž", "ź", "ż"),
        'x' to listOf("X", "×"),
        'c' to listOf("C", "ç", "ć", "č"),
        'v' to listOf("V", "¿", "ü", "ǖ", "ǘ", "ǚ", "ǜ"),
        'b' to listOf("B", "¡"),
        'n' to listOf("N", "ñ", "ń"),
        'm' to listOf("M", "\\"),
    )

    /** 该字母的变体；非字母或没有变体时返回空表。 */
    fun forLetter(letter: Char): List<String> = VARIANTS[letter.lowercaseChar()].orEmpty()

    /** 是否为需要长按变体的键（单字母键）。 */
    fun supports(key: String): Boolean =
        key.length == 1 && key[0].isLetter() && VARIANTS.containsKey(key[0].lowercaseChar())
}
