package com.kingzcheung.xime.settings

import com.kingzcheung.xime.BuildConfig

/**
 * 本分支基于上游 Xime 3.3.0 改造，但对插件/方案/布局索引仍按上游 API 版本判定兼容性。
 *
 * [COMPAT] 同时写入 AndroidManifest 的 `xime.host.compat_version`，由 plugin-core 读取；
 * [DISPLAY] 是本分支自己的版本号（1.x），仅用于关于页/诊断信息，不能参与 minHostVersion 比较。
 */
object HostVersion {
    const val COMPAT = BuildConfig.XIME_HOST_COMPAT_VERSION
    const val DISPLAY = BuildConfig.VERSION_NAME
}
