package com.kingzcheung.xime.ui.keyboard

import android.content.Context

/**
 * 供 [com.kingzcheung.xime.settings.KeysConfigHelper] 在取提示符号时拿到 Context 的小桥。
 *
 * 键面提示（swipe_up）不只来自配置：用户切到 Gboard 风格时要在取值时就地覆盖，
 * 否则必须等配置重载，而配置重载的时机不由键盘控制。
 */
object SymbolHintRuntime {
    @Volatile
    var appContext: Context? = null
}
