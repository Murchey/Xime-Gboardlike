package com.kingzcheung.xime.ui.keyboard

/** 滑动输入开启时不闪现起点字母气泡，原地长按符号选择仍保留。 */
internal fun hideGlidePressBubble(enabled: Boolean, state: SwipeState): Boolean =
    enabled && state.isPressed && !state.isLongPress && !state.isSwiping &&
        (state.isLetterPress || state.pressedText?.let { text ->
            text.length == 1 && (text[0].lowercaseChar() in 'a'..'z' || text == ";")
        } == true)
