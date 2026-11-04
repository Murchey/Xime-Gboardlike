package com.kingzcheung.xime.settings

import android.content.Context
import android.util.Log
import java.io.File

/**
 * 内置语法模型（随 APK 分发的 XimeGboard.gram）。
 *
 * 首次安装、用户从未配置过语法模型时：自动落盘到 rime 目录并默认启用；
 * 用户停用或删除后（偏好键已存在）完全尊重用户选择，不再干预。
 * 同名文件已存在时不覆盖，用户自行导入的模型优先。
 */
object GramBundledModel {
    private const val TAG = "GramBundledModel"
    private const val ASSET_PATH = "gram/XimeGboard.gram"
    const val FILE_NAME = "XimeGboard.gram"
    const val LANGUAGE = "XimeGboard"

    /** @return 是否执行了默认启用。 */
    fun ensureDefault(context: Context): Boolean {
        if (GramPreferences.isConfigured(context)) return false
        val dir = SchemaManager.getRimeDir(context)
        val target = File(dir, FILE_NAME)
        if (!target.isFile && !copyAsset(context, dir, target)) return false
        GramPreferences.setActiveLanguage(context, LANGUAGE)
        GramActivation.applyGlobal(context)
        Log.i(TAG, "bundled grammar model enabled by default")
        return true
    }

    /** 资产落盘（临时文件 + 改名，magic 校验同 [GramImporter]）。 */
    private fun copyAsset(context: Context, dir: File, target: File): Boolean = try {
        if (!dir.exists()) dir.mkdirs()
        val tmp = File(dir, "$FILE_NAME.tmp")
        context.assets.open(ASSET_PATH).use { input ->
            tmp.outputStream().use { output -> input.copyTo(output) }
        }
        if (!GramImporter.isGramFile(tmp)) {
            tmp.delete()
            false
        } else {
            if (target.exists()) target.delete()
            if (!tmp.renameTo(target)) {
                tmp.delete()
                false
            } else {
                true
            }
        }
    } catch (e: Exception) {
        Log.w(TAG, "copy bundled grammar failed", e)
        false
    }
}
