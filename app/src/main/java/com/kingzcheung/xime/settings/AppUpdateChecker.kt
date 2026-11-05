package com.kingzcheung.xime.settings

import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class UpdateInfo(
    val versionName: String,
    val tagName: String,
    val releaseNotes: String,
    val downloadUrl: String,
    val apkUrl: String,
    val source: String,
)

object AppUpdateChecker {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    const val DEFAULT_GITHUB_REPO = "Murchey/Xime-Gboardlike"
    const val DEFAULT_GITEE_REPO = ""

    /** 检查 GitHub Releases（用 API v3） */
    suspend fun checkGitHub(context: Context, repo: String = DEFAULT_GITHUB_REPO): UpdateInfo? =
        withContext(Dispatchers.IO) {
            try {
                val url = "https://api.github.com/repos/$repo/releases/latest"
                val request = Request.Builder()
                    .url(url)
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "XimeGboard-UpdateChecker")
                    .build()
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                val json = JSONObject(body)
                val tagName = json.optString("tag_name", "")
                val latest = tagName.removePrefix("v").removePrefix("V")
                if (latest.isBlank()) return@withContext null
                if (!isNewerVersion(latest, getCurrentVersion(context))) return@withContext null

                // 找 APK 资产
                val assets = json.optJSONArray("assets") ?: JSONArray()
                var apkUrl = ""
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name", "")
                    if (name.endsWith(".apk")) {
                        apkUrl = asset.optString("browser_download_url", "")
                        break
                    }
                }
                UpdateInfo(
                    versionName = latest,
                    tagName = tagName,
                    releaseNotes = json.optString("body", ""),
                    downloadUrl = json.optString("html_url", ""),
                    apkUrl = apkUrl,
                    source = "GitHub",
                )
            } catch (_: Exception) { null }
        }

    /** 检查 Gitee Releases */
    suspend fun checkGitee(context: Context, repo: String = DEFAULT_GITEE_REPO): UpdateInfo? =
        withContext(Dispatchers.IO) {
            if (repo.isBlank()) return@withContext null
            try {
                val url = "https://gitee.com/api/v5/repos/$repo/releases/latest"
                val request = Request.Builder()
                    .url(url)
                    .header("Accept", "application/json")
                    .build()
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                val json = JSONObject(body)
                val tagName = json.optString("tag_name", "")
                val latest = tagName.removePrefix("v").removePrefix("V")
                if (latest.isBlank()) return@withContext null
                if (!isNewerVersion(latest, getCurrentVersion(context))) return@withContext null

                val assets = json.optJSONArray("assets") ?: JSONArray()
                var apkUrl = ""
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name", "")
                    if (name.endsWith(".apk")) {
                        apkUrl = asset.optString("browser_download_url", "")
                        break
                    }
                }
                UpdateInfo(
                    versionName = latest,
                    tagName = tagName,
                    releaseNotes = json.optString("body", ""),
                    downloadUrl = json.optString("html_url", ""),
                    apkUrl = apkUrl,
                    source = "Gitee",
                )
            } catch (_: Exception) { null }
        }

    /** 综合检查：按用户配置的仓库检查，先 GitHub 再 Gitee。 */
    suspend fun checkAll(context: Context): UpdateInfo? {
        val github = SettingsPreferences.getUpdateGithubRepo(context).ifBlank { DEFAULT_GITHUB_REPO }
        val gitee = SettingsPreferences.getUpdateGiteeRepo(context)
        return checkGitHub(context, github) ?: checkGitee(context, gitee)
    }

    fun getCurrentVersion(context: Context): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0.0.0"
        } catch (_: PackageManager.NameNotFoundException) { "0.0.0" }
    }

    private fun isNewerVersion(latest: String, current: String): Boolean {
        val l = latest.split(".").map { it.toIntOrNull() ?: 0 }
        val c = current.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(l.size, c.size)) {
            val lv = l.getOrElse(i) { 0 }
            val cv = c.getOrElse(i) { 0 }
            if (lv > cv) return true
            if (lv < cv) return false
        }
        return false
    }
}
