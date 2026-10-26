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

    fun displayName(packageId: String): String = when (packageId) {
        RIME_ICE -> "雾凇拼音"
        WUBI86 -> "五笔 86"
        PINYIN_SIMP -> "简体拼音 / 九键"
        SHARED -> "内置共享资源"
        else -> packageId
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
