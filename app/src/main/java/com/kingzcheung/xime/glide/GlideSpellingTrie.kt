package com.kingzcheung.xime.glide

/** 编码前缀约束；0 表示恰好结束一个音节。 */
interface GlideSpellingConstraint {
    fun followers(states: List<Int>): Int
    fun extend(states: List<Int>, letter: Int, next: MutableList<Int>)
}

/** 从当前 Rime 方案投影出的合法编码建树，分号作为真实按键参与搜索。 */
class GlideSpellingTrie(spellings: Collection<String>) : GlideSpellingConstraint {
    private val children = mutableListOf(IntArray(27))
    private val complete = mutableListOf(false)

    init {
        for (code in spellings.distinct()) {
            require(code.isNotEmpty() && code.all { it in 'a'..'z' || it == ';' })
            var node = 0
            for (ch in code) {
                val slot = if (ch == ';') 26 else ch - 'a'
                if (children[node][slot] == 0) {
                    children[node][slot] = children.size
                    children.add(IntArray(27))
                    complete.add(false)
                }
                node = children[node][slot]
            }
            complete[node] = true
        }
    }

    override fun followers(states: List<Int>): Int = states.fold(0) { mask, node ->
        children[node].indices.fold(mask) { bits, ch ->
            if (children[node][ch] != 0) bits or (1 shl ch) else bits
        }
    }

    override fun extend(states: List<Int>, letter: Int, next: MutableList<Int>) {
        next.clear()
        for (node in states) {
            val child = children[node].getOrElse(letter) { 0 }
            if (child == 0) continue
            if (complete[child] && 0 !in next) next.add(0)
            if (children[child].any { it != 0 } && child !in next) next.add(child)
        }
    }
}
