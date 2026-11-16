package com.kingzcheung.xime.settings

import android.content.Context
import com.kingzcheung.xime.util.FileLogger
import java.io.File

/**
 * 合并键方案的按需生成与落盘。
 *
 * 只生成用户当前要用的那几个（8 个基础方案 × 3 布局 × 2 排列 = 48 份，全铺开会白编几十份 prism、
 * 多占几百 MB）。文件写进 rime 目录后，[KeysConfigHelper] 在加载配置时会把它们登记到对应键面段。
 */
object GridSchemaInstaller {
    private const val TAG = "GridSchemaInstaller"

    /** 生成（或覆盖）一个派生方案文件；已是最新内容时跳过。返回是否真的写了文件。 */
    fun install(
        context: Context,
        baseId: String,
        layout: String,
        order: String,
    ): String? {
        val rimeDir = SchemaManager.getRimeDir(context)
        val baseFile = File(rimeDir, baseId + ".schema.yaml")
        if (!baseFile.isFile) {
            FileLogger.w(TAG, "install(" + baseId + "): 基础方案不存在")
            return null
        }
        val targetId = GridSchemaGenerator.derivedId(baseId, layout, order)
        val groups = GridSchemaGenerator.groupsOf(layout, order)
        val text = GridSchemaGenerator.generate(
            baseText = baseFile.readText(),
            baseId = baseId,
            targetId = targetId,
            displayName = baseId + " · " + layout + " 键" +
                (if (order == KeyboardLayoutPreset.ORDER_ABCDE) " ABCDE" else ""),
            groups = groups,
        ) ?: run {
            FileLogger.w(TAG, "install(" + baseId + " -> " + targetId + "): 生成失败")
            return null
        }
        val target = File(rimeDir, targetId + ".schema.yaml")
        if (target.isFile && target.readText() == text) return targetId
        return runCatching {
            target.writeText(text)
            FileLogger.i(TAG, "installed " + target.name)
            targetId
        }.getOrElse { e ->
            FileLogger.e(TAG, "install(" + targetId + ") 写盘失败", e)
            null
        }
    }

    /**
     * 启动时补齐：把已启用列表里的派生方案重新生成到磁盘。
     *
     * 旧版本会把这类"不在 assets 清单里的内置包文件"当残留删掉；即使删了，
     * 只要 schema_list 里还留着条目，下一次启动就应当把它们补回来，否则方案凭空消失。
     */
    fun restoreInstalled(context: Context): Int {
        val enabled = SchemaManager.getEnabledSchemas(context)
        var restored = 0
        for (id in enabled) {
            if (!GridSchemaGenerator.isDerived(id)) continue
            val m = Regex("""_(14|17|18)jian(_abcde)?$""").find(id) ?: continue
            val layout = m.groupValues[1]
            val order = if (m.groupValues[2].isNotEmpty()) KeyboardLayoutPreset.ORDER_ABCDE
            else KeyboardLayoutPreset.ORDER_QWERTY
            val base = GridSchemaGenerator.baseOf(id)
            if (install(context, base, layout, order) != null) restored++
        }
        if (restored > 0) FileLogger.i(TAG, "restored " + restored + " derived schema(s)")
        return restored
    }

    /** 把派生方案登记进启用列表（没有变化时原样返回）。 */
    fun enable(context: Context, targetId: String): Boolean {
        val enabled = SchemaManager.getEnabledSchemas(context)
        if (targetId in enabled) return false
        SchemaManager.setEnabledSchemas(context, enabled + targetId)
        return true
    }

    /**
     * 把当前方案换成"同一基础方案的合并键版本"；返回目标 id 或 null。
     *
     * baseId 由调用方给出（当前方案 strip 掉派生后缀），不在内置八个基础方案里时回退到 rime_ice。
     */
    fun installAndEnable(
        context: Context,
        baseSchemeId: String,
        layout: String,
        order: String,
    ): String? {
        val base = baseSchemeId.takeIf { it in GridSchemaGenerator.BASE_SCHEMA_IDS } ?: "rime_ice"
        val target = install(context, base, layout, order) ?: return null
        enable(context, target)
        return target
    }
}
