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
}
