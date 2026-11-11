package com.kingzcheung.xime.glide

/**
 * 测试用小鹤双拼编码表：把 [GlideSyllableTrie] 的 421 个**真实拼音音节**
 * 投影成"声母键 + 韵母键"的两键编码。
 *
 * 之前评测台用的是"声母 × 韵母"的笛卡尔积，会造出 yp / yie 这类不存在的音节，
 * 解码器可以挑这些假音节，导致评测既可能误判失败、也可能虚高。
 * 这里只保留真实音节，与线上 RimeEngine.getGlideSpellings 的口径一致。
 */
object FlypySpellings {

    /** 声母（长声母优先匹配）。 */
    private val INITIALS = listOf(
        "zh", "ch", "sh", "b", "p", "m", "f", "d", "t", "n", "l",
        "g", "k", "h", "j", "q", "x", "r", "z", "c", "s", "y", "w",
    )

    /** 韵母 -> 键（小鹤双拼）。 */
    private val FINAL_KEY = mapOf(
        "a" to 'a', "o" to 'o', "e" to 'e', "i" to 'i', "u" to 'u', "v" to 'v',
        "ai" to 'd', "ei" to 'w', "ui" to 'v', "ao" to 'c', "ou" to 'z',
        "iu" to 'q', "ie" to 'p', "ve" to 't', "ue" to 't', "er" to 'r',
        "an" to 'j', "en" to 'f', "in" to 'b', "un" to 'y', "vn" to 'y',
        "ang" to 'h', "eng" to 'g', "ing" to 'k', "ong" to 's',
        "ia" to 'x', "ua" to 'x', "uo" to 'o', "uai" to 'k', "uan" to 'r',
        "uang" to 'l', "ian" to 'm', "iang" to 'l', "iao" to 'n', "iong" to 's',
    )

    /** 声母 -> 首键。小鹤里 zh/ch/sh 用 v/i/u。 */
    private fun initialKey(initial: String): Char = when (initial) {
        "zh" -> 'v'
        "ch" -> 'i'
        "sh" -> 'u'
        else -> initial[0]
    }

    /**
     * 单个音节 -> 两键编码；无法映射时返回 null。
     * 零声母音节按小鹤规则处理（a/o/e 双写，ang/eng/ong 用 a/e/o 作首键）。
     */
    fun encode(syllable: String): String? {
        val initial = INITIALS.firstOrNull { syllable.startsWith(it) && syllable.length > it.length }
        if (initial != null) {
            val final = syllable.substring(initial.length)
            val k = FINAL_KEY[final] ?: return null
            return "" + initialKey(initial) + k
        }
        // 零声母
        return when (syllable) {
            "a" -> "aa"
            "o" -> "oo"
            "e" -> "ee"
            "ang" -> "ah"
            "eng" -> "eg"
            "ong" -> "os"
            "er" -> "er"
            // ai/ei/ao/ou/an/en 等：小鹤里保持两字母拼写本身
            "ai", "ei", "ao", "ou", "an", "en" -> syllable
            else -> null
        }
    }

    /** 全部真实音节的两键编码集合。 */
    fun all(): List<String> {
        val out = LinkedHashSet<String>()
        for (s in GlideSyllableTrie.syllables()) encode(s)?.let { out += it }
        return out.toList()
    }
}
