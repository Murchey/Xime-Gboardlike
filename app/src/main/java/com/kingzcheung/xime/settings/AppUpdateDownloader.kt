package com.kingzcheung.xime.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/** 下载进度 */
sealed class DownloadProgress {
    data class Progress(val fraction: Float) : DownloadProgress()
    data object Completed : DownloadProgress()
    data object Failed : DownloadProgress()
}

/** 内置 GitHub 加速代理源 */
val PROXY_SOURCES = listOf(
    "https://gh-proxy.org/",
    "https://ghfast.top/",
    "https://ghp.ci/",
)

object AppUpdateDownloader {

    private const val TAG = "AppUpdateDownloader"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    /** 构建下载 URL，支持 GitHub 代理 */
    fun buildDownloadUrl(apkUrl: String, useProxy: Boolean, proxyPrefix: String? = null): String {
        if (!useProxy || apkUrl.isBlank()) return apkUrl
        val prefix = proxyPrefix?.takeIf { it.isNotBlank() } ?: PROXY_SOURCES.first()
        return prefix.trimEnd('/') + "/" + apkUrl
    }

    private fun apkFile(context: Context, fileName: String): File {
        val dir = File(context.cacheDir, "updates")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, fileName)
    }

    /**
     * 流式下载 APK 到 cacheDir，实时发射进度。
     * 下载完成后由调用方触发 [install]，安装后由 [cleanCachedApks] 清理。
     */
    fun download(
        context: Context,
        url: String,
        fileName: String,
        useProxy: Boolean = false,
        proxyPrefix: String? = null,
    ): Flow<DownloadProgress> = callbackFlow {
        try {
            val finalUrl = buildDownloadUrl(url, useProxy, proxyPrefix)
            val file = apkFile(context, fileName)

            // 命中缓存直接返回
            if (file.exists() && file.length() > 0) {
                trySend(DownloadProgress.Completed)
            } else {
                if (file.exists()) file.delete()
                val request = Request.Builder()
                    .url(finalUrl)
                    .header("User-Agent", "XimeGboard-Update")
                    .build()
                val response = withContext(Dispatchers.IO) { client.newCall(request).execute() }
                try {
                    if (!response.isSuccessful) {
                        trySend(DownloadProgress.Failed)
                    } else {
                        val body = response.body ?: run {
                            trySend(DownloadProgress.Failed)
                            response.close()
                            awaitClose {}
                            return@callbackFlow
                        }
                        val totalBytes = body.contentLength()
                        var downloaded = 0L
                        withContext(Dispatchers.IO) {
                            body.byteStream().use { input ->
                                FileOutputStream(file).use { output ->
                                    val buf = ByteArray(8192)
                                    while (true) {
                                        val n = input.read(buf)
                                        if (n <= 0) break
                                        output.write(buf, 0, n)
                                        downloaded += n
                                        if (totalBytes > 0) {
                                            trySend(DownloadProgress.Progress(
                                                (downloaded.toFloat() / totalBytes).coerceIn(0f, 0.99f)
                                            ))
                                        }
                                    }
                                }
                            }
                        }
                        if (file.exists() && file.length() > 0) {
                            trySend(DownloadProgress.Completed)
                        } else {
                            trySend(DownloadProgress.Failed)
                        }
                    }
                } finally {
                    response.close()
                }
            }
        } catch (_: Exception) {
            trySend(DownloadProgress.Failed)
        }
        awaitClose {}
    }

    /**
     * 触发 APK 安装。
     *
     * Android 8.0+ 需要 REQUEST_INSTALL_PACKAGES 权限且用户在系统里允许本应用
     * 「安装未知应用」；未授权时系统静默拒绝，因此这里主动检测并引导用户去授权页。
     *
     * @return true 表示已成功拉起安装器
     */
    fun install(context: Context, fileName: String): Boolean {
        val file = apkFile(context, fileName)
        if (!file.exists() || file.length() <= 0L) return false

        // 未授权「安装未知应用」时先引导到系统授权页，避免点击无反应或闪退
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            openInstallPermissionSettings(context)
            return false
        }

        return try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                true
            } else {
                openInstallPermissionSettings(context)
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "install failed: ${e.message}", e)
            false
        }
    }

    /** 打开「安装未知应用」授权页。 */
    private fun openInstallPermissionSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "open install settings failed: ${e.message}", e)
        }
    }

    /** 清理下载缓存（安装后或取消后调用） */
    fun cleanCachedApks(context: Context) {
        try {
            val dir = File(context.cacheDir, "updates")
            dir.listFiles()?.forEach { it.delete() }
        } catch (_: Exception) {}
    }
}
