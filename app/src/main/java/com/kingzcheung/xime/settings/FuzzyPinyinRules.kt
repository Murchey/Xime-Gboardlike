package com.kingzcheung.xime.settings

/** 一组模糊音规则（成对启用，避免单向派生导致只认一半）。 */
data class FuzzyPinyinRule(
    val id: String,
    val label: String,
    val rules: List<String>,
)

object FuzzyPinyinRules {
    val ALL: List<FuzzyPinyinRule> = listOf(
        FuzzyPinyinRule(
            id = "zh_z",
            label = "zh / z、ch / c、sh / s",
            rules = listOf(
                "derive/^([zcs])h/\$1/",
                "derive/^([zcs])([^h])/\$1h\$2/",
            ),
        ),
        FuzzyPinyinRule("n_l", "n / l", listOf("derive/^l/n/", "derive/^n/l/")),
        FuzzyPinyinRule("f_h", "f / h", listOf("derive/^f/h/", "derive/^h/f/")),
        FuzzyPinyinRule("l_r", "l / r", listOf("derive/^l/r/", "derive/^r/l/")),
        FuzzyPinyinRule("g_k", "g / k", listOf("derive/^g/k/", "derive/^k/g/")),
        FuzzyPinyinRule("an_ang", "an / ang", listOf("derive/ang\$/an/", "derive/an\$/ang/")),
        FuzzyPinyinRule("en_eng", "en / eng", listOf("derive/eng\$/en/", "derive/en\$/eng/")),
        FuzzyPinyinRule("in_ing", "in / ing", listOf("derive/in\$/ing/", "derive/ing\$/in/")),
        FuzzyPinyinRule("ian_iang", "ian / iang", listOf("derive/ian\$/iang/", "derive/iang\$/ian/")),
        FuzzyPinyinRule("uan_uang", "uan / uang", listOf("derive/uan\$/uang/", "derive/uang\$/uan/")),
    )

    fun rulesFor(enabledIds: Set<String>): List<String> =
        ALL.filter { it.id in enabledIds }.flatMap { it.rules }.distinct()
}

/**
 * 把模糊音写进 <schema>.custom.yaml。
 *
 * 用 librime 的列表插入补丁 speller/algebra/0+，把规则插到 algebra 最前面：
 * 双拼方案需要先做模糊音派生，再做双拼键位映射。只改这一个 key，保留用户其他 patch。
 */
object FuzzyPinyinPatch {
    const val KEY = "speller/algebra/0+"

    fun apply(existing: String?, rules: List<String>): String? {
        val base = (existing ?: "").replace("\r\n", "\n")
        if (rules.isEmpty()) return removeKey(base)
        val block = renderBlock(rules)
        val lines = base.lines().toMutableList()
        val patchIdx = lines.indexOfFirst { isPatchHeader(it) }
        if (patchIdx < 0) {
            val prefix = when {
                base.isBlank() -> ""
                base.endsWith("\n") -> base
                else -> base + "\n"
            }
            return prefix + "patch:\n" + block + "\n"
        }
        val end = patchEnd(lines, patchIdx)
        val keyIdx = (patchIdx + 1 until end).firstOrNull { isOurKey(lines[it]) }
        if (keyIdx != null) {
            val keyEnd = keyBlockEnd(lines, keyIdx)
            lines.subList(keyIdx, keyEnd).clear()
            lines.addAll(keyIdx, block.lines())
            return lines.joinToString("\n")
        }
        var insert = end
        while (insert > patchIdx + 1 && lines[insert - 1].isBlank()) insert--
        lines.addAll(insert, block.lines())
        return lines.joinToString("\n")
    }

    private fun removeKey(base: String): String? {
        if (base.isBlank()) return null
        val lines = base.lines().toMutableList()
        val patchIdx = lines.indexOfFirst { isPatchHeader(it) }
        if (patchIdx < 0) return base
        val end = patchEnd(lines, patchIdx)
        val keyIdx = (patchIdx + 1 until end).firstOrNull { isOurKey(lines[it]) }
        if (keyIdx != null) {
            val keyEnd = keyBlockEnd(lines, keyIdx)
            lines.subList(keyIdx, keyEnd).clear()
        }
        val hasOther = lines.any { it.isNotBlank() && it.trim() != "patch:" }
        return if (hasOther) lines.joinToString("\n") else null
    }

    private fun keyBlockEnd(lines: List<String>, keyIdx: Int): Int {
        val keyIndent = lines[keyIdx].takeWhile { it == ' ' }.length
        var end = keyIdx + 1
        while (end < lines.size) {
            val line = lines[end]
            if (line.isBlank()) {
                end++
                continue
            }
            val indent = line.takeWhile { it == ' ' }.length
            if (indent <= keyIndent) break
            end++
        }
        return end
    }

    private fun isPatchHeader(line: String): Boolean =
        line.trim() == "patch:" && line.isNotEmpty() && line[0] != ' ' && line[0] != '\t'

    private fun patchEnd(lines: List<String>, patchIdx: Int): Int {
        var end = patchIdx + 1
        while (end < lines.size) {
            val line = lines[end]
            if (line.isNotBlank() && line[0] != ' ' && line[0] != '\t') break
            end++
        }
        return end
    }

    private fun isOurKey(line: String): Boolean {
        val trimmed = line.trim()
        return trimmed.startsWith("'$KEY':") ||
            trimmed.startsWith("\"$KEY\":") ||
            trimmed.startsWith("$KEY:")
    }

    private fun renderBlock(rules: List<String>): String = buildString {
        append("  '").append(KEY).append("':\n")
        rules.forEach { append("    - '").append(it.replace("'", "''")).append("'\n") }
    }
}
