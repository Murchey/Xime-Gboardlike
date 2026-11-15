package com.kingzcheung.xime.association

/**
 * 联想结果的后置过滤。
 *
 * 模型的 top-k 里常混着标点（实测「下一个就是标点」占 **10.5%**），
 * 而标点作为**联想**几乎没用——用户不会去点候选栏里的「，」。
 * rime 自己的候选栏负责标点，联想栏只管接续文字。
 */
object AssociationPostFilter {

    /** 中英标点与空白；只含这些字符的候选整条丢掉。 */
    private val PUNCT = (
        "，。！？、；：“”‘’（）《》〈〉【】…—·～" +
            ",.!?;:\"'()[]{}<>~-_\u3000"
        ).toSet()

    /** 丢掉纯标点候选；含至少一个文字/数字的保留。 */
    fun dropPunctuation(list: List<AssociationCandidate>): List<AssociationCandidate> =
        if (list.isEmpty()) list
        else list.filter { c -> c.text.any { ch -> ch !in PUNCT && !ch.isWhitespace() } }

    /** 是否纯标点（供调用方单点判断）。 */
    fun isPunctuationOnly(text: String): Boolean =
        text.isNotEmpty() && text.all { it in PUNCT || it.isWhitespace() }

    /**
     * 把多字候选挪到它自己的首字后面。
     *
     * 融合是按分数排序的，词候选被钳在首字之下以后仍可能被别的单字挤开，
     * 于是候选栏会出现「有 … 没 有些」——词和它的首字分家，读起来莫名其妙。
     * 这里只调顺序、不改分数：单字按原顺序输出，紧跟其后的是以它开头的词。
     */
    fun groupPhrasesWithSeeds(list: List<AssociationCandidate>): List<AssociationCandidate> {
        if (list.size < 2) return list
        val singles = list.filter { it.text.length == 1 }.map { it.text }.toHashSet()
        if (singles.isEmpty()) return list

        val bySeed = HashMap<String, MutableList<AssociationCandidate>>()
        for (c in list) {
            if (c.text.length < 2) continue
            val seed = c.text.substring(0, 1)
            if (seed in singles) bySeed.getOrPut(seed) { mutableListOf() }.add(c)
        }
        if (bySeed.isEmpty()) return list

        val emitted = HashSet<String>()
        val out = ArrayList<AssociationCandidate>(list.size)
        for (c in list) {
            if (c.text.length >= 2 && c.text.substring(0, 1) in singles) {
                if (!emitted.add(c.text)) continue   // 已随首字输出过
                continue
            }
            out.add(c)
            if (c.text.length == 1) {
                bySeed[c.text]?.forEach { phrase ->
                    if (emitted.add(phrase.text)) out.add(phrase)
                }
            }
        }
        return out
    }
}