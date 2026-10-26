package com.kingzcheung.xime.settings

import android.content.Context

/**
 * 内置方案种子状态（按内置包记录）。
 *
 * 卸载某个内置包后把它加入 disabled_packages，启动时不再从 assets 灌回；
 * 恢复时清除对应包标记并重新复制。
 */
object BuiltinSeedState {
    private const val PREFS = "builtin_seed"
    private const val KEY_DISABLED_PACKAGES = "disabled_packages"
    private const val KEY_LEGACY_DISABLED = "disabled"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun disabledPackages(context: Context): Set<String> {
        val p = prefs(context)
        val stored = p.getStringSet(KEY_DISABLED_PACKAGES, null)
        if (stored != null) return stored.toSet()
        // 旧版本只有聚合 builtin 标记：等价于三个方案包都卸载
        return if (p.getBoolean(KEY_LEGACY_DISABLED, false)) {
            BuiltinPackages.USER_VISIBLE.toSet()
        } else emptySet()
    }

    /** 任一内置方案包被卸载。 */
    fun isDisabled(context: Context): Boolean = disabledPackages(context).isNotEmpty()

    fun isDisabled(context: Context, packageId: String): Boolean =
        packageId in disabledPackages(context)

    fun setDisabled(context: Context, packageId: String, disabled: Boolean) {
        val current = disabledPackages(context).toMutableSet()
        if (disabled) current += packageId else current -= packageId
        prefs(context).edit()
            .putStringSet(KEY_DISABLED_PACKAGES, current)
            .remove(KEY_LEGACY_DISABLED)
            .apply()
    }

    /** 兼容旧调用：true = 所有用户可见内置包都卸载。 */
    fun setDisabled(context: Context, disabled: Boolean) {
        prefs(context).edit()
            .putStringSet(
                KEY_DISABLED_PACKAGES,
                if (disabled) BuiltinPackages.USER_VISIBLE.toSet() else emptySet(),
            )
            .remove(KEY_LEGACY_DISABLED)
            .apply()
    }

    fun clearAll(context: Context) {
        prefs(context).edit().clear().apply()
    }
}
