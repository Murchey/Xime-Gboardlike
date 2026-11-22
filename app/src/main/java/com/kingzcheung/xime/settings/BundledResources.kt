package com.kingzcheung.xime.settings

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * 随 APK 分发的资源（目前只有内置语法模型 .gram）的版本化落盘。
 *
 * 旧机制只看「文件在不在」，所以升级 APK 后用户仍用着上一版资源。这里改成按版本覆盖：
 * - 资产内容一变就把 [VERSION] 加 1；
 * - 目标文件缺失，或内容仍是我们上次装进去的那一份（hash 对得上）→ 用新资产覆盖；
 * - 内容已被用户替换（导入过自己的模型/语法模型）→ 不动，尊重用户。
 */
object BundledResources {
    private const val TAG = "BundledResources"
    private const val PREFS = "bundled_resources"
    private const val KEY_VERSION = "version"
    private const val KEY_HASH = "hash_"

    /** 资源版本。改动 [items] 里任何一个资产文件的内容时都必须 +1。 */
    const val VERSION = 2

    /**
     * 随包资源清单：资产路径 → 运行时目标文件。
     *
     * 联想 ONNX 模型（原 `models/predictive-text-small/`）已不再随包，
     * 用户首次使用需自行下载/导入；资产缺失时 [install] 会安静跳过（见 [assetExists]）。
     */
    private fun items(context: Context): List<Pair<String, File>> {
        val rimeDir = SchemaManager.getRimeDir(context)
        return listOf(
            "gram/${GramBundledModel.FILE_NAME}" to File(rimeDir, GramBundledModel.FILE_NAME),
        )
    }

    data class Result(
        val replaced: List<String>,
        val kept: List<String>,
        /** 这次 APK 里没有带这份资产（当前只可能出现在旧包/被裁剪的构建里）。 */
        val absent: List<String>,
        val failed: List<String>,
        val version: Int,
    ) {
        val changed: Boolean get() = replaced.isNotEmpty()
    }

    /** 单个资源的处置方式。 */
    enum class Action {
        /** 文件缺失（首次安装 / 被删掉）→ 从资产恢复。 */
        Restore,
        /** 版本变了且内容还是我们上次装的那份 → 用新资产覆盖。 */
        Replace,
        /** 版本变了但用户换过内容 → 保留。 */
        Keep,
        /** 版本没变且文件在 → 什么都不做。 */
        Leave,
    }

    /**
     * 决定每个资源要不要动。
     *
     * 版本没变时不碰已有文件（省一次 10MB 的复制）；
     * 版本变了才判「这是不是我们上次装的那份」：有历史 hash 且对得上 → 覆盖；
     * 没有历史 hash（第一次启用这套机制）→ 覆盖，把老资源换掉；
     * hash 对不上 → 用户换过 → 保留。
     */
    fun decide(
        missing: Boolean,
        versionChanged: Boolean,
        targetHash: String?,
        storedHash: String?,
    ): Action = when {
        missing -> Action.Restore
        !versionChanged -> Action.Leave
        storedHash == null -> Action.Replace
        targetHash != null && targetHash == storedHash -> Action.Replace
        else -> Action.Keep
    }

    fun install(context: Context): Result {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val storedVersion = prefs.getInt(KEY_VERSION, 0)
        val versionChanged = storedVersion < VERSION
        val all = items(context)
        if (!versionChanged && all.all { it.second.isFile || !assetExists(context, it.first) }) {
            return Result(emptyList(), all.map { it.second.name }, emptyList(), emptyList(), storedVersion)
        }

        val replaced = mutableListOf<String>()
        val kept = mutableListOf<String>()
        val absent = mutableListOf<String>()
        val failed = mutableListOf<String>()

        for ((asset, target) in all) {
            if (!assetExists(context, asset)) {
                // lite 包不带这份资产：什么都不做，等用户自己装模型
                absent += target.name
                continue
            }
            val storedHash = prefs.getString(KEY_HASH + target.name, null)
            val existsHash = if (target.isFile) sha256(target) else null
            when (decide(!target.isFile, versionChanged, existsHash, storedHash)) {
                Action.Leave -> continue
                Action.Keep -> {
                    kept += target.name
                    Log.i(TAG, "keep user-modified " + target.name)
                    continue
                }
                Action.Restore, Action.Replace -> Unit
            }
            val written = copyAndHash(context, asset, target)
            if (written == null) {
                failed += target.name
                Log.w(TAG, "copy asset failed: " + asset + " -> " + target.absolutePath)
            } else {
                replaced += target.name
                prefs.edit().putString(KEY_HASH + target.name, written).apply()
                Log.i(TAG, "installed " + target.name + " (" + target.length() + " bytes)")
            }
        }

        if (versionChanged) prefs.edit().putInt(KEY_VERSION, VERSION).apply()
        Log.i(
            TAG,
            "bundled resources v$storedVersion -> v$VERSION: replaced=" + replaced +
                " kept=" + kept + " absent=" + absent + " failed=" + failed
        )

        // 内置语法模型：用户从没配置过时默认落盘并启用（已配置则尊重其开关）
        GramBundledModel.ensureDefault(context)

        return Result(replaced, kept, absent, failed, VERSION)
    }

    /** 资产是否随包（lite 包不带模型，这里要能安静地跳过）。 */
    private fun assetExists(context: Context, asset: String): Boolean =
        runCatching { context.assets.open(asset).close() }.isSuccess

    /** 边写边算 hash，避免为了记录 hash 再读一遍 10MB 的模型。 */
    private fun copyAndHash(context: Context, asset: String, target: File): String? = try {
        val parent = target.parentFile
        if (parent != null && !parent.exists()) parent.mkdirs()
        val tmp = File(target.parentFile, target.name + ".tmp")
        val digest = MessageDigest.getInstance("SHA-256")
        context.assets.open(asset).use { input ->
            FileOutputStream(tmp).use { output ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    output.write(buf, 0, n)
                    digest.update(buf, 0, n)
                }
                output.fd.sync()
            }
        }
        if (target.exists() && !target.delete()) return null
        if (!tmp.renameTo(target)) {
            tmp.delete()
            return null
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    } catch (e: Exception) {
        Log.w(TAG, "copyAndHash failed for " + asset, e)
        File(target.parentFile, target.name + ".tmp").delete()
        null
    }

    private fun sha256(file: File): String? = try {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                digest.update(buf, 0, n)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    } catch (e: Exception) {
        Log.w(TAG, "sha256 failed: " + file.absolutePath, e)
        null
    }
}