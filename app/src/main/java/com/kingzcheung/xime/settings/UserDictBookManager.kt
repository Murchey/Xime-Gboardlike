package com.kingzcheung.xime.settings

import android.content.Context
import com.kingzcheung.xime.rime.RimeConfigHelper
import com.kingzcheung.xime.rime.RimeEngine
import com.kingzcheung.xime.util.FileLogger
import java.io.File

/**
 * 词库本管理：新建 / 删除 / 指定「当前记录词库」。
 *
 * 指定后会把所有方案（磁盘上的全部 schema，含未启用的）的 translator/user_dict
 * 改写成该库，并触发一次部署，让引擎按新词库加载——这样任何方案输入的新词都记到同一本。
 */
object UserDictBookManager {
    private const val TAG = "UserDictBookManager"
    private const val CREATE_TEMP_NAME = "userdict_create.txt"

    /** 词库名规则：字母/数字/下划线/短横线，1–32 个字符（会被写进 yaml 与文件名）。 */
    private val NAME_REGEX = Regex("""[A-Za-z0-9_-]{1,32}""")

    /** 校验新词库名；返回错误文案，null 表示可用。 */
    internal fun validateName(name: String, existing: List<String>): String? {
        val trimmed = name.trim()
        return when {
            trimmed.isEmpty() -> "名称不能为空"
            !NAME_REGEX.matches(trimmed) -> "只能用字母、数字、下划线或短横线（1–32 个字符）"
            trimmed in existing -> "同名词库已存在"
            else -> null
        }
    }

    fun active(context: Context): String = SettingsPreferences.getUserDictActive(context)

    fun exists(context: Context, name: String): Boolean =
        File(SchemaManager.getRimeDir(context), name + ".userdb").exists()

    /** 新建一本空词库（librime 的 Import 会顺带建库）。 */
    fun create(context: Context, name: String): Result<Unit> = runCatching {
        val trimmed = name.trim()
        validateName(trimmed, UserDictIoManager.list(context).map { it.name })?.let { error(it) }
        if (!RimeEngine.isInitialized()) error("输入法引擎未就绪，请稍后再试")
        val temp = File(context.cacheDir, CREATE_TEMP_NAME)
        try {
            temp.writeText("")
            RimeEngine.getInstance().importUserDict(trimmed, temp.absolutePath)
        } finally {
            runCatching { temp.delete() }
        }
        if (!exists(context, trimmed)) error("创建失败")
        FileLogger.i(TAG, "created user dict " + trimmed)
    }

    /** 删除一本词库（当前记录中的那本不允许删）。 */
    fun delete(context: Context, name: String): Result<Unit> = runCatching {
        if (name == active(context)) error("不能删除当前正在记录的词库")
        val dir = File(SchemaManager.getRimeDir(context), name + ".userdb")
        if (dir.exists() && !dir.deleteRecursively()) error("删除失败")
        FileLogger.i(TAG, "deleted user dict " + name)
    }

    /** 指定当前记录词库：改写所有方案的 translator/user_dict 并重新部署。 */
    fun setActive(context: Context, name: String): Result<Unit> = runCatching {
        if (!exists(context, name)) error("词库不存在")
        SettingsPreferences.setUserDictActive(context, name)
        RimeConfigHelper.unifyUserDictTo(context, name)
        RimeConfigHelper.deployInstalledSchema(context)
        FileLogger.i(TAG, "active user dict -> " + name)
    }
}
