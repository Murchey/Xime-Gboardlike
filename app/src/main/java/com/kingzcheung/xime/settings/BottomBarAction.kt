package com.kingzcheung.xime.settings

/**
 * 键盘底部工具条按钮可绑定的功能。
 *
 * 与雨燕输入法的「全面屏键盘优化」对齐：左右各一个按钮，可在几种常用动作间切换；
 * 中间空白区保留给手势导航，不放控件。
 */
enum class BottomBarAction {
    /** 切换输入法（系统输入法选择器）。 */
    SWITCH_IME,

    /** 中英文切换。 */
    SWITCH_LANGUAGE,

    /** 打开剪贴板面板。 */
    CLIPBOARD,

    /** 打开快捷发送面板。 */
    QUICK_PHRASE,

    /** 打开表情面板。 */
    EMOJI,

    /** 收起键盘。 */
    HIDE_KEYBOARD,

    /** 打开设置。 */
    SETTINGS,

    /** 不显示按钮。 */
    NONE;

    companion object {
        fun fromName(raw: String?): BottomBarAction =
            entries.firstOrNull { it.name == raw } ?: NONE
    }
}
