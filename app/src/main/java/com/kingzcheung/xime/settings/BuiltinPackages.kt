package com.kingzcheung.xime.settings

/** 内置资产按方案族拆分，便于单独卸载/恢复。 */
object BuiltinPackages {
    const val RIME_ICE = "builtin_rime_ice"
    const val WUBI86 = "builtin_wubi86"
    const val PINYIN_SIMP = "builtin_pinyin_simp"
    const val SHARED = "builtin_shared"

    /** 用户可见、可卸载的内置包。shared 始终保留。 */
    val USER_VISIBLE = listOf(RIME_ICE, WUBI86, PINYIN_SIMP)
    val ALL = USER_VISIBLE + SHARED

    fun isBuiltin(packageId: String): Boolean = packageId in ALL

    /** 包版本从代表方案的 version 字段读取；shared 无版本。 */
    fun versionSchema(packageId: String): String? = when (packageId) {
        RIME_ICE -> "rime_ice"
        WUBI86 -> "wubi86"
        PINYIN_SIMP -> "t9_pinyin"
        else -> null
    }

    /**
     * 包的展示名。
     *
     * 注意：包 **内部 ID 保持不变**（如 builtin_rime_ice）。改名只影响展示——
     * disabled 状态、清单、市场备份都按 ID 记录；改 ID 会让已卸载过该包的用户
     * 看到它重新出现，需要额外写迁移。
     */
    fun displayName(packageId: String): String = when (packageId) {
        RIME_ICE -> "简繁全拼双拼包"
        WUBI86 -> "五笔 86"
        PINYIN_SIMP -> "九键拼音"
        SHARED -> "内置共享资源"
        else -> packageId
    }

    /**
     * 该相对路径是否确实是 app 内置资产。
     *
     * apk 的 assets/rime/ 下有哪些文件，就以它为唯一判据。用户自己拷进 rime/ 的
     * 第三方方案包不在这里面，不能归到任何内置包——否则会被 builtin 包"认领"，
     * 卸载内置包时连带删掉，分组也显示成内置来源。
     */
    fun isBuiltinAsset(relPath: String, assetNames: Set<String>? = null): Boolean {
        val path = relPath.removePrefix("/").replace('\\', '/')
        if (path.isEmpty()) return false
        assetNames?.let { return it.contains(path) }
        // 拿不到资产清单时**保守返回 true**（保持认领），宁可少清理也不能误删用户文件
        return true
    }

    /** 资产相对路径 → 所属内置包。未知文件一律归 shared，避免误删。 */
    fun packageFor(relPath: String): String {
        val path = relPath.removePrefix("/")
        return when {
            path.startsWith("cn_dicts/") ||
                path.startsWith("en_dicts/") ||
                path.startsWith("rime_ice") ||
                path.startsWith("double_pinyin") ||
                path.startsWith("melt_eng") ||
                path.startsWith("radical_pinyin") -> RIME_ICE

            path.startsWith("wubi86") ||
                path.startsWith("wubi98") ||
                path.startsWith("handwriting") -> WUBI86

            path.startsWith("pinyin_simp") ||
                path.startsWith("t9") -> PINYIN_SIMP

            else -> SHARED
        }
    }
}
