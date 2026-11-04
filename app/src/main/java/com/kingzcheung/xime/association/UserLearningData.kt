package com.kingzcheung.xime.association

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

/**
 * 用户学习数据（`filesDir/user_ngram_cache.json`）的可管理视图：搜索、多选、删除。
 *
 * 与 [UserNgramCache] 的分工：后者是引擎侧的内存 trie（输入时实时打分），
 * 这里只处理 JSON 快照本身——设置页没有加载模型时也能列出、删除，
 * 不必依赖 :inference 进程或引擎初始化状态。
 *
 * 删除语义：
 * - 单元组记录按「词元序列」身份键匹配；
 * - 删掉二元组「A B」时连带删掉以它为前缀的三元组「A B *」，
 *   否则三元组仍会给 B 之后续写，用户会觉得没删干净。
 */
object UserLearningData {

    private const val TAG = "UserLearningData"
    private const val KEY_BIGRAMS = "bigrams"
    private const val KEY_TRIGRAMS = "trigrams"

    /** 一条可管理的 n-gram 记录：[order] 为 2（二元组）或 3（三元组）。 */
    data class Entry(val order: Int, val tokens: List<String>, val count: Int) {
        /** 身份键：词元直接拼接（词元均为单字符，不会歧义）。 */
        val key: String get() = keyOf(tokens)

        /** 展示文本：把词元连起来，与用户输入时的观感一致。 */
        val text: String get() = tokens.joinToString("")
    }

    /** 删除结果：[json] 是删完的完整快照，[removed] 是实际删掉的记录数。 */
    data class Removal(val json: JSONObject, val removed: Int)

    fun keyOf(tokens: List<String>): String = tokens.joinToString("")

    /** 把快照解析成可管理记录（二元组在前、三元组在后，各自保持文件中的顺序）。 */
    fun parse(raw: JSONObject): List<Entry> =
        readSection(raw, KEY_BIGRAMS, 2) + readSection(raw, KEY_TRIGRAMS, 3)

    private fun readSection(raw: JSONObject, field: String, order: Int): List<Entry> {
        val array = raw.optJSONArray(field) ?: return emptyList()
        val out = ArrayList<Entry>(array.length())
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val tokens = item.optJSONArray("tokens")?.let { arr ->
                (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotEmpty() }
            } ?: continue
            if (tokens.size != order) continue
            out += Entry(order, tokens, item.optInt("count", 1))
        }
        return out
    }

    /** 在给定记录里按关键字过滤（匹配整条文本，也匹配任意单个词元）。纯函数。 */
    fun filter(entries: List<Entry>, query: String): List<Entry> {
        if (query.isEmpty()) return entries
        val needle = query.lowercase(Locale.ROOT)
        return entries.filter { entry ->
            entry.text.lowercase(Locale.ROOT).contains(needle) ||
                entry.tokens.any { it.lowercase(Locale.ROOT).contains(needle) }
        }
    }

    /**
     * 从快照里删除指定身份键的记录，返回新快照与删除条数。
     * 不修改入参 [raw]（内部先复制），便于调用方在失败时保留原数据。纯函数。
     */
    fun remove(raw: JSONObject, keys: Set<String>): Removal {
        if (keys.isEmpty()) return Removal(raw, 0)
        val bigrams = raw.optJSONArray(KEY_BIGRAMS) ?: JSONArray()
        val trigrams = raw.optJSONArray(KEY_TRIGRAMS) ?: JSONArray()

        // 先确定要删的二元组，记下它们的词元序列，供三元组连带删除
        val removedBigramTokens = mutableSetOf<List<String>>()
        val keptBigrams = JSONArray()
        for (i in 0 until bigrams.length()) {
            val item = bigrams.optJSONObject(i) ?: continue
            val tokens = tokensOf(item)
            if (tokens != null && keyOf(tokens) in keys) {
                removedBigramTokens += tokens
            } else {
                keptBigrams.put(item)
            }
        }

        val keptTrigrams = JSONArray()
        var removedTrigrams = 0
        for (i in 0 until trigrams.length()) {
            val item = trigrams.optJSONObject(i) ?: continue
            val tokens = tokensOf(item)
            val cascade = tokens != null && tokens.size == 3 && tokens.take(2) in removedBigramTokens
            if (tokens != null && (keyOf(tokens) in keys || cascade)) {
                removedTrigrams++
            } else {
                keptTrigrams.put(item)
            }
        }

        val removedBigrams = bigrams.length() - keptBigrams.length()
        val removed = removedBigrams + removedTrigrams
        if (removed == 0) return Removal(raw, 0)

        val next = JSONObject(raw.toString())
        next.put(KEY_BIGRAMS, keptBigrams)
        next.put(KEY_TRIGRAMS, keptTrigrams)
        return Removal(next, removed)
    }

    private fun tokensOf(item: JSONObject): List<String>? =
        item.optJSONArray("tokens")?.let { arr ->
            (0 until arr.length()).map { arr.optString(it) }
        }

    /** 读快照；文件不存在或损坏时返回空对象（管理页展示空表，不抛给界面）。 */
    fun read(file: File): JSONObject = runCatching {
        if (!file.exists() || file.length() <= 0L) return@runCatching JSONObject()
        JSONObject(file.readText())
    }.getOrElse {
        Log.w(TAG, "Failed to read learning data: ${it.message}")
        JSONObject()
    }

    internal fun cacheFile(context: Context): File = File(context.filesDir, UserNgramCache.CACHE_FILE_NAME)
}

/**
 * 学习数据的读改入口：内存缓存已初始化时走引擎（改完立即落盘），
 * 否则直接读写 JSON 文件，避免设置页被迫加载模型。
 */
object UserLearningStore {

    private const val TAG = "UserLearningStore"

    /**
     * 当前全部记录：内存缓存已初始化时以它为准（它可能比文件新，尚未落盘），
     * 否则直接读 JSON 文件，避免设置页为了看一眼数据而加载模型。
     */
    suspend fun load(context: Context): List<UserLearningData.Entry> = withContext(Dispatchers.IO) {
        if (AssociationManager.isInitialized()) {
            AssociationManager.getUserNgramEntries().map { entry ->
                UserLearningData.Entry(entry.tokens.size, entry.tokens, entry.count)
            }
        } else {
            UserLearningData.parse(UserLearningData.read(UserLearningData.cacheFile(context)))
        }
    }

    /**
     * 删除选中的记录。
     *
     * 内存缓存已初始化时必须走引擎：否则下次 [AssociationManager.saveUserData]
     * 会用旧内存覆盖刚改过的文件，删掉的词条又回来了。
     */
    suspend fun remove(context: Context, keys: Set<String>): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            if (keys.isEmpty()) return@runCatching 0
            if (AssociationManager.isInitialized()) {
                // 走引擎：内存 trie 与文件一起更新，避免下次 saveUserData 用旧数据覆盖
                AssociationManager.removeUserNgramEntries(keys)
            } else {
                val file = UserLearningData.cacheFile(context)
                val removal = UserLearningData.remove(UserLearningData.read(file), keys)
                if (removal.removed > 0) {
                    file.parentFile?.mkdirs()
                    file.writeText(removal.json.toString(2))
                }
                removal.removed
            }
        }.onFailure { Log.e(TAG, "Failed to remove learning data: ${it.message}", it) }
    }
}
