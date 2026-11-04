package com.kingzcheung.xime.association

import android.content.Context
import android.net.Uri
import com.kingzcheung.xime.BuildConfig
import com.kingzcheung.xime.R
import com.kingzcheung.xime.i18n.tr
import com.kingzcheung.xime.util.FileLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 用户学习数据导出：把 [UserNgramCache] 落在 `filesDir/user_ngram_cache.json` 的
 * n-gram 统计导出为带 `meta` 的 JSON，供离线转换后训练预测模型。
 *
 * 导出前先调 [AssociationManager.saveUserData] 把内存缓存刷盘，避免导出旧数据；
 * 数据为空时明确失败，不写空文件。
 */
object UserDataExporter {

    private const val TAG = "UserDataExporter"
    private const val FORMAT_VERSION = 1

    data class Snapshot(val bigramCount: Int, val trigramCount: Int, val recentCount: Int)

    fun suggestFileName(): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return "xime_user_ngram_${stamp}.json"
    }

    /** 刷盘后读取原始缓存文本并追加 meta 段。 */
    suspend fun saveAndRead(context: Context): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            runCatching { AssociationManager.saveUserData() }
                .onFailure { FileLogger.w(TAG, "saveUserData failed before export: ${it.message}") }

            val cacheFile = File(context.filesDir, UserNgramCache.CACHE_FILE_NAME)
            if (!cacheFile.exists() || cacheFile.length() <= 0L) {
                throw IOException(tr(R.string.s_zanwuxuexishuju))
            }

            val raw = JSONObject(cacheFile.readText())
            val bigrams = raw.optJSONArray("bigrams")?.length() ?: 0
            val trigrams = raw.optJSONArray("trigrams")?.length() ?: 0
            val recent = raw.optJSONArray("recentInputs")?.length() ?: 0
            if (bigrams == 0 && trigrams == 0 && recent == 0) {
                throw IOException(tr(R.string.s_zanwuxuexishuju))
            }

            raw.put(
                "meta",
                JSONObject()
                    .put("format_version", FORMAT_VERSION)
                    .put("app_version", BuildConfig.VERSION_NAME)
                    .put("android_sdk", android.os.Build.VERSION.SDK_INT)
                    .put("exported_at_ms", System.currentTimeMillis())
                    .put("bigram_count", bigrams)
                    .put("trigram_count", trigrams)
                    .put("recent_count", recent)
            )
            raw.toString(2)
        }.onFailure { FileLogger.e(TAG, "saveAndRead failed: ${it.message}", it) }
    }

    /** 导出到 SAF uri，返回导出条数统计。 */
    suspend fun exportTo(context: Context, uri: Uri): Result<Snapshot> = withContext(Dispatchers.IO) {
        runCatching {
            val text = saveAndRead(context).getOrElse { throw it }
            context.contentResolver.openOutputStream(uri)?.use { output ->
                output.write(text.toByteArray(Charsets.UTF_8))
            } ?: throw IOException(tr(R.string.s_wufaxierusuoxuanwenjian))

            val json = JSONObject(text)
            val meta = json.getJSONObject("meta")
            Snapshot(
                bigramCount = meta.optInt("bigram_count"),
                trigramCount = meta.optInt("trigram_count"),
                recentCount = meta.optInt("recent_count"),
            )
        }.onFailure { FileLogger.e(TAG, "exportTo failed: ${it.message}", it) }
    }
}
