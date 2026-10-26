package com.kingzcheung.xime.settings

import android.content.Context

/**
 * 内置方案种子状态。
 *
 * 用户卸载内置方案包后置为 true，启动时不再从 assets 自动灌回；
 * 在方案管理里点「恢复内置方案包」时清零并重新复制。
 */
object BuiltinSeedState {
    private const val PREFS = "builtin_seed"
    private const val KEY_DISABLED = "disabled"

    fun isDisabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_DISABLED, false)

    fun setDisabled(context: Context, disabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_DISABLED, disabled).apply()
    }
}
