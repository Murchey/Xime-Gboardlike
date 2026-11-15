package com.kingzcheung.xime.association

/**
 * 联想上下文判定（所有联想入口的唯一口径）。
 *
 * 只有最后一个字符是**文字**（汉字/字母）才值得推理：
 * - 空格、标点、表情不承载可接续的语义，照常推理只会凭空冒出无关候选；
 * - **数字结尾也不推理**（"2024" / "第3" / "12.5"）：后面接的通常是单位、量词或下一句，
 *   字符级模型给的多是虚词，还会把刚输完的号码/金额候选栏弄乱。
 *
 * [Character.isLetter] 对全角数字「１」同样返回 false，无需单独判断。
 */
object AssociationContextPolicy {

    fun isValid(text: String): Boolean {
        val last = text.lastOrNull() ?: return false
        return Character.isLetter(last)
    }
}
