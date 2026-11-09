package com.kingzcheung.xime.settings

import android.content.Context

/**
 * app 内置的共享预设文件（`symbols.yaml`、`default.yaml` 等，加文件进 assets/rime 即自动纳入）：
 * 第三方方案用 `import_preset` / `__include` 引用，缺失时部署会编译失败。
 */
object RimeSharedFiles {
    private const val ASSETS_RIME_DIR = "rime"

    /** 内置共享文件名集合（按 assets 现算）。 */
    fun names(context: Context): Set<String> {
        val entries = context.assets.list(ASSETS_RIME_DIR) ?: return emptySet()
        return entries.filter { entry ->
            val isDir = (context.assets.list("$ASSETS_RIME_DIR/$entry") ?: emptyArray()).isNotEmpty()
            isSharedEntry(entry, isDir)
        }.toSet()
    }

    /**
     * assets/rime/ 下所有内置文件的相对路径（递归，含子目录）。
     *
     * 用于判断 rime/ 里某个文件是不是"app 自带的"：只有内置文件才归内置包追踪，
     * 用户手拷进来的第三方方案包不能被内置包认领（否则卸载内置包会连删，
     * 分组也会错标成内置来源）。
     */
    fun allAssetNames(context: Context): Set<String> {
        val result = mutableSetOf<String>()
        fun walk(rel: String, depth: Int) {
            if (depth > 4) return
            val entries = runCatching { context.assets.list(rel) }.getOrNull() ?: return
            for (entry in entries) {
                val childRel = if (rel.isEmpty()) entry else "$rel/$entry"
                val grandchildren = runCatching { context.assets.list(childRel) }.getOrNull()
                if (grandchildren.isNullOrEmpty()) {
                    result.add(childRel)
                } else {
                    walk(childRel, depth + 1)
                }
            }
        }
        walk(ASSETS_RIME_DIR, 0)
        // 注册表里的路径是**相对 rime 目录**的（`symbols.yaml`、`cn_dicts/base.dict.yaml`），
        // 必须去掉 "rime/" 前缀，否则一个都匹配不上（曾因此误清空整个注册表）
        val prefix = "$ASSETS_RIME_DIR/"
        return result.mapTo(mutableSetOf()) { it.removePrefix(prefix) }
    }

    /**
     * 被其他方案用 `__include` / `import_tables` 引用的**预设**方案与词表。
     *
     * 它们自身不作为独立方案启用，但缺了会让依赖方编译失败：`t9.schema.yaml` 首行就是
     * `__include: rime_ice.schema.yaml:/`，一旦 rime_ice 丢失，九键就再也部署不出来
     * （实测现象：t9 在 schema_list 里，但 build/ 永远没有产物）。
     * 因此这几个必须当作共享预设文件参与缺失修复。
     */
    private val INCLUDE_PRESET_FILES = setOf(
        "rime_ice.schema.yaml",
        "rime_ice.dict.yaml",
    )

    /** 只看预设/词表类文件，排除目录、隐藏项与方案/词典/补丁/文档。 */
    internal fun isSharedEntry(name: String, isDirectory: Boolean): Boolean {
        if (isDirectory || name.startsWith(".")) return false
        if (name in INCLUDE_PRESET_FILES) return true
        if (name.endsWith(".schema.yaml") || name.endsWith(".dict.yaml") || name.endsWith(".custom.yaml")) return false
        return name.endsWith(".yaml") || name.endsWith(".yml") || name.endsWith(".txt")
    }
}
