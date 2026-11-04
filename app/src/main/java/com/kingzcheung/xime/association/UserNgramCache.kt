package com.kingzcheung.xime.association

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class UserNgramCache(private val context: Context) {
    companion object {
        private const val TAG = "UserNgramCache"
        internal const val CACHE_FILE_NAME = "user_ngram_cache.json"
        private const val MAX_ENTRIES = 1000
        private const val MAX_HISTORY_SIZE = 100
    }
    
    private val bigramTrie = NgramTrie()
    private val trigramTrie = NgramTrie()
    private val recentInputs = mutableListOf<String>()
    
    private val cacheFile: File
        get() = File(context.filesDir, CACHE_FILE_NAME)
    
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (cacheFile.exists()) {
                val json = JSONObject(cacheFile.readText())
                loadFromJson(json)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize cache", e)
            false
        }
    }
    
    fun recordInput(text: String) {
        if (text.isBlank()) return
        
        val tokens = tokenize(text)
        if (tokens.isEmpty()) return
        
        updateRecentInputs(tokens)
        
        tokens.windowed(2).forEach { window ->
            bigramTrie.insert(window)
        }
        
        tokens.windowed(3).forEach { window ->
            trigramTrie.insert(window)
        }
        
    }
    
    private fun tokenize(text: String): List<String> {
        val punctuation = setOf('，', '。', '！', '？', '、', '；', '：', '"', '"', '\'', '\'', ' ', '\n', '\t')
        return text.filter { it.isLetterOrDigit() || it in punctuation }
            .map { it.toString() }
    }
    
    private fun updateRecentInputs(newTokens: List<String>) {
        recentInputs.addAll(newTokens)
        if (recentInputs.size > MAX_HISTORY_SIZE) {
            recentInputs.subList(0, recentInputs.size - MAX_HISTORY_SIZE).clear()
        }
    }
    
    fun getBigramScore(word1: String, word2: String): Float {
        return bigramTrie.getFrequency(listOf(word1, word2))
    }
    
    fun getTrigramScore(word1: String, word2: String, word3: String): Float {
        return trigramTrie.getFrequency(listOf(word1, word2, word3))
    }
    
    fun getContextualScore(context: String, candidate: String): Float {
        val tokens = tokenize(context)
        if (tokens.isEmpty()) return 0f
        
        val lastToken = tokens.last()
        val bigramScore = getBigramScore(lastToken, candidate)
        
        val trigramScore = if (tokens.size >= 2) {
            getTrigramScore(tokens[tokens.size - 2], lastToken, candidate)
        } else {
            0f
        }
        
        return maxOf(bigramScore, trigramScore)
    }
    
    fun getUserCandidates(context: String, topK: Int = 5): List<Pair<String, Float>> {
        val tokens = tokenize(context)
        if (tokens.isEmpty()) {
            return emptyList()
        }
        
        val lastToken = tokens.last()
        val candidates = mutableListOf<Pair<String, Float>>()
        
        bigramTrie.getAllEntries().forEach { (ngram, count) ->
            if (ngram.size == 2 && ngram[0] == lastToken) {
                val score = getBigramScore(ngram[0], ngram[1])
                if (score > 0) {
                    candidates.add(ngram[1] to score)
                }
            }
        }
        
        if (tokens.size >= 2) {
            trigramTrie.getAllEntries().forEach { (ngram, count) ->
                if (ngram.size == 3 && ngram[0] == tokens[tokens.size - 2] && ngram[1] == lastToken) {
                    val score = getTrigramScore(ngram[0], ngram[1], ngram[2])
                    if (score > 0) {
                        candidates.add(ngram[2] to score)
                    }
                }
            }
        }
        
        return candidates.sortedByDescending { it.second }.take(topK)
    }
    
    suspend fun save() = withContext(Dispatchers.IO) {
        try {
            val json = toJson()
            cacheFile.writeText(json.toString(2))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save cache", e)
        }
    }
    
    fun clear() {
        bigramTrie.clear()
        trigramTrie.clear()
        recentInputs.clear()
        if (cacheFile.exists()) {
            cacheFile.delete()
        }
    }
    
    fun getCacheSize(): Int = bigramTrie.size() + trigramTrie.size()

    // ── 用户私有数据管理（「学习数据」页搜索 / 多选 / 删除用） ──

    /** 一条 n-gram 记录：[tokens] 是词元序列（2 词对 / 3 词三元组），[count] 是出现次数。 */
    data class NgramEntry(val tokens: List<String>, val count: Int)

    /**
     * 列出全部可管理的 n-gram 记录（先二元组后三元组，各自按词元序）。
     * 只读，不改动缓存。
     */
    fun getAllEntries(): List<NgramEntry> =
        (bigramTrie.getAllEntries() + trigramTrie.getAllEntries())
            .map { (tokens, count) -> NgramEntry(tokens, count) }

    /**
     * 删除指定记录并立即落盘。
     *
     * 删除二元组「A B」时连带删除以它为前缀的三元组「A B *」——
     * 否则三元组还会给 A B 之后的候选续写，用户会觉得没删干净。
     *
     * @return 实际删除的记录数（不存在于缓存的键会被忽略）
     */
    fun removeEntries(keys: Set<String>): Int {
        if (keys.isEmpty()) return 0
        val bigrams = bigramTrie.getAllEntries()
        val trigrams = trigramTrie.getAllEntries()

        val removedBigrams = bigrams.filter { entryKey(it.first) in keys }
        // 被删除的二元组的词元序列 ⇒ 用来连带裁掉以它开头的三元组
        val removedBigramTokens = removedBigrams.map { it.first }.toSet()
        val removedTrigrams = trigrams.filter { (tokens, _) ->
            entryKey(tokens) in keys || (tokens.size == 3 && tokens.take(2) in removedBigramTokens)
        }

        val removedCount = removedBigrams.size + removedTrigrams.size
        if (removedCount == 0) return 0

        rebuild(bigrams - removedBigrams.toSet(), trigrams - removedTrigrams.toSet())
        return removedCount
    }

    /** 记录的身份键：词元无分隔拼接（词元均为单字符，不会歧义）。纯函数。 */
    internal fun entryKey(tokens: List<String>): String = tokens.joinToString("")

    /** 清空后用剩余记录重建两个 trie（条目上限 1000，重建比实现 trie 删除更稳）。 */
    private fun rebuild(
        bigrams: List<Pair<List<String>, Int>>,
        trigrams: List<Pair<List<String>, Int>>,
    ) {
        bigramTrie.clear()
        trigramTrie.clear()
        for ((tokens, count) in bigrams) repeat(count) { bigramTrie.insert(tokens) }
        for ((tokens, count) in trigrams) repeat(count) { trigramTrie.insert(tokens) }
    }
    
    private fun toJson(): JSONObject {
        val json = JSONObject()
        
        val bigrams = JSONArray()
        bigramTrie.getAllEntries().forEach { (tokens, count) ->
            val entry = JSONObject()
            entry.put("tokens", JSONArray(tokens))
            entry.put("count", count)
            bigrams.put(entry)
        }
        json.put("bigrams", bigrams)
        
        val trigrams = JSONArray()
        trigramTrie.getAllEntries().forEach { (tokens, count) ->
            val entry = JSONObject()
            entry.put("tokens", JSONArray(tokens))
            entry.put("count", count)
            trigrams.put(entry)
        }
        json.put("trigrams", trigrams)
        
        json.put("recentInputs", JSONArray(recentInputs))
        
        return json
    }
    
    private fun loadFromJson(json: JSONObject) {
        bigramTrie.clear()
        trigramTrie.clear()
        recentInputs.clear()
        
        val bigrams = json.optJSONArray("bigrams") ?: JSONArray()
        for (i in 0 until bigrams.length()) {
            val entry = bigrams.getJSONObject(i)
            val tokens = entry.getJSONArray("tokens").let { arr ->
                (0 until arr.length()).map { arr.getString(it) }
            }
            val count = entry.getInt("count")
            repeat(count) { bigramTrie.insert(tokens) }
        }
        
        val trigrams = json.optJSONArray("trigrams") ?: JSONArray()
        for (i in 0 until trigrams.length()) {
            val entry = trigrams.getJSONObject(i)
            val tokens = entry.getJSONArray("tokens").let { arr ->
                (0 until arr.length()).map { arr.getString(it) }
            }
            val count = entry.getInt("count")
            repeat(count) { trigramTrie.insert(tokens) }
        }
        
        val recentArray = json.optJSONArray("recentInputs") ?: JSONArray()
        for (i in 0 until recentArray.length()) {
            recentInputs.add(recentArray.getString(i))
        }
    }
}