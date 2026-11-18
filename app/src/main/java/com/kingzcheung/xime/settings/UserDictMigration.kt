package com.kingzcheung.xime.settings

import android.content.Context
import com.kingzcheung.xime.rime.RimeConfigHelper
import com.kingzcheung.xime.rime.RimeEngine
import com.kingzcheung.xime.util.FileLogger
import java.io.File

/**
 * 升级到「用户词库记录为一本」后的数据迁移：把本机原有的多本 userdb 合并进统一词库。
 *
 * 只在版本迁移标记未写时跑一次（即用户更新到该版本后的首次启动）；
 * 合并走 librime 的 Import（同词条取较大频率，不清空目标库），旧库文件保留不删（便于回退/排查）。
 */
object UserDictMigration {
    private const val TAG = "UserDictMigration"

    /** 迁移标记版本：以后若再需要合并规则变更，递增即可重跑。 */
    private const val VERSION = 1

    /** 需要合并进统一词库的旧库名（排除目标库本身与空名）。 */
    internal fun booksToMerge(names: List<String>, target: String): List<String> =
        names.asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != target }
            .distinct()
            .toList()

    /**
     * 执行一次性迁移；返回合并成功的条目数。
     *
     * 引擎未就绪时不写标记，留到下一次启动再试。
     */
    fun migrateIfNeeded(context: Context): Int {
        if (SettingsPreferences.getUserDictMergeVersion(context) >= VERSION) return 0
        if (!RimeEngine.isInitialized()) return 0
        val target = RimeConfigHelper.UNIFIED_USER_DICT_NAME
        val books = booksToMerge(UserDictIoManager.list(context).map { it.name }, target)
        if (books.isEmpty()) {
            SettingsPreferences.setUserDictMergeVersion(context, VERSION)
            return 0
        }
        val engine = RimeEngine.getInstance()
        var merged = 0
        for (name in books) {
            val text = runCatching { engine.readUserDictText(name) }.getOrNull().orEmpty()
            if (text.isBlank()) continue
            val tmp = File(context.cacheDir, "userdict_migrate_" + name.hashCode() + ".txt")
            val imported = runCatching {
                tmp.writeText(text)
                engine.importUserDict(target, tmp.absolutePath)
            }.getOrDefault(-1)
            runCatching { tmp.delete() }
            if (imported > 0) merged += imported
            FileLogger.i(TAG, "migrate " + name + " -> " + target + ": " + imported)
        }
        SettingsPreferences.setUserDictMergeVersion(context, VERSION)
        FileLogger.i(TAG, "user dict migration done: merged " + merged + " entr(ies) from " + books.size + " book(s)")
        return merged
    }
}
