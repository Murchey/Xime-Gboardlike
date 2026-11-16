package com.kingzcheung.xime.settings

/** 键绑定的构造辅助（避免在多处重复拼 LongPressAction/KeyAction 样板）。 */
object KeyBindingFactory {
    /** 只有一个候选的长按气泡动作（键面角标会取它的首项）。 */
    fun singleBubble(symbol: String): LongPressAction = LongPressAction(
        display = DisplayMode.BUBBLE,
        values = listOf(
            KeyAction(
                action = com.kingzcheung.xime.keyboard.GestureAction.COMMIT,
                value = symbol,
                label = symbol,
            ),
        ),
    )
}
