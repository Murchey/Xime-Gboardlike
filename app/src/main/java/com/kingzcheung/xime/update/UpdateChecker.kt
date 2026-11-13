package com.kingzcheung.xime.update

import android.content.Context
import com.kingzcheung.xime.BuildConfig
import com.kingzcheung.xime.settings.SettingsPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

/** 检测到的新版本。 */
data class UpdateInfo(val latestVersion: String, val releaseUrl: String)

/**
 * 版本更新检测：读 GitHub 仓库的 tag 列表，取最高版本与当前版本比较。
 *
 * 只在用户进入设置时调用一次，带 6 小时节流与"跳过此版本"记忆，
 * 断网/接口异常一律静默失败——更新提示不能影响正常使用。
 */
object UpdateChecker {

    private const val TAGS_URL = "https://api.github.com/repos/Murchey/Xime-Gboardlike/tags"
    private const val RELEASES_URL = "https://github.com/Murchey/Xime-Gboardlike/releases"
    private const val CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L
    private const val KEY_LAST_CHECK_AT = "update_last_check_at"
    private const val KEY_DISMISSED_VERSION = "update_dismissed_version"

    /** 返回有新版本时的信息；无需提示（已是最新/节流中/已跳过/网络失败）返回 null。 */
    suspend fun checkForUpdate(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        val prefs = SettingsPreferences.getPrefsPublic(context)
        val now = System.currentTimeMillis()
        if (now - prefs.getLong(KEY_LAST_CHECK_AT, 0L) < CHECK_INTERVAL_MS) return@withContext null
        val latest = runCatching { fetchLatestVersion() }.getOrNull() ?: return@withContext null
        prefs.edit().putLong(KEY_LAST_CHECK_AT, now).apply()
        val current = BuildConfig.VERSION_NAME
        if (!isNewerVersion(latest, current)) return@withContext null
        if (prefs.getString(KEY_DISMISSED_VERSION, null) == latest) return@withContext null
        UpdateInfo(latest, RELEASES_URL)
    }

    /** 记住用户选择"稍后"，同一版本不再打扰。 */
    fun dismissVersion(context: Context, version: String) {
        SettingsPreferences.getPrefsPublic(context).edit()
            .putString(KEY_DISMISSED_VERSION, version)
            .apply()
    }

    private fun fetchLatestVersion(): String? {
        val connection = (URL(TAGS_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 5000
            readTimeout = 5000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "XimeGboard-Android")
        }
        try {
            if (connection.responseCode !in 200..299) return null
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val tags = JSONArray(body)
            var best: String? = null
            for (i in 0 until tags.length()) {
                val name = tags.optJSONObject(i)?.optString("name").orEmpty()
                val version = normalizeTag(name) ?: continue
                if (best == null || isNewerVersion(version, best)) best = version
            }
            return best
        } finally {
            connection.disconnect()
        }
    }
}

/** 把 tag 名规整成 x.y.z（支持 v1.2.3 / V1.2 / 1.2.3-beta 这类写法）。 */
internal fun normalizeTag(raw: String): String? {
    val trimmed = raw.trim().removePrefix("v").removePrefix("V")
    val core = trimmed.takeWhile { it.isDigit() || it == '.' }
    if (core.isEmpty() || core.split('.').any { it.isEmpty() }) return null
    return core.trimEnd('.')
}

/** 语义化版本比较：a 比 b 新返回 true；解析不了的一律返回 false。 */
internal fun isNewerVersion(a: String, b: String): Boolean {
    val left = normalizeTag(a)?.split('.')?.mapNotNull { it.toIntOrNull() } ?: return false
    val right = normalizeTag(b)?.split('.')?.mapNotNull { it.toIntOrNull() } ?: return false
    val size = maxOf(left.size, right.size)
    for (i in 0 until size) {
        val l = left.getOrElse(i) { 0 }
        val r = right.getOrElse(i) { 0 }
        if (l != r) return l > r
    }
    return false
}
