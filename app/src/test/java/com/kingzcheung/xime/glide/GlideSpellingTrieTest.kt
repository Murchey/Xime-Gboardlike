package com.kingzcheung.xime.glide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * GlideSpellingTrie 的边界正确性。
 *
 * 它负责"当前字母序列是否仍是某个合法编码的前缀"，错了会直接
 * 让正确编码被剪掉或让非法编码混进候选，影响所有滑动结果。
 */
class GlideSpellingTrieTest {

    private fun states(trie: GlideSpellingTrie, code: String): List<Int> {
        var cur = listOf(0)
        for (ch in code) {
            val next = mutableListOf<Int>()
            val slot = if (ch == ';') 26 else ch - 'a'
            trie.extend(cur, slot, next)
            cur = next
        }
        return cur
    }

    @Test
    fun 完整编码结束后应回到状态0() {
        val trie = GlideSpellingTrie(listOf("ni", "hk", "ui", "ve"))
        assertTrue("ni 结束后应含 0", states(trie, "ni").contains(0))
        assertTrue("hk 结束后应含 0", states(trie, "hk").contains(0))
        assertTrue("ui 结束后应含 0", states(trie, "ui").contains(0))
    }

    @Test
    fun 非法前缀应被剪掉() {
        val trie = GlideSpellingTrie(listOf("ni", "hk"))
        assertTrue("n 是合法前缀", states(trie, "n").isNotEmpty())
        assertTrue("z 不是这两条编码的前缀", states(trie, "z").isEmpty())
        assertTrue("nz 非法", states(trie, "nz").isEmpty())
    }

    @Test
    fun 短编码是长编码前缀时两者都应可达() {
        // 例如同时有 "ni" 和 "nii"（构造样例）
        val trie = GlideSpellingTrie(listOf("ni", "nii"))
        val s = states(trie, "ni")
        assertTrue("ni 既是完整编码也是长编码前缀，应同时含 0 和后续状态",
            s.contains(0) && s.size >= 2)
    }

    @Test
    fun 分号键应作为真实按键处理() {
        val trie = GlideSpellingTrie(listOf("ni;"))
        val s = states(trie, "ni;")
        assertTrue("含分号的双拼编码应可达", s.contains(0))
    }

    @Test
    fun followers应返回所有可用后继() {
        val trie = GlideSpellingTrie(listOf("ni", "na"))
        val mask = trie.followers(listOf(0))
        val n = 'n' - 'a'
        assertTrue("根的后继应含 n", mask and (1 shl n) != 0)
        val i = 'i' - 'a'
        assertTrue("根的后继不应含 i", mask and (1 shl i) == 0)
    }
}
