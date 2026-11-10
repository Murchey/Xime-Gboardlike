package com.kingzcheung.xime.ui.keyboard

/** 只有手势已由 glide 接管才抑制按键气泡；普通短按与原地长按照常显示。 */
internal fun shouldSuppressGlideBubble(captured: Boolean, state: SwipeState): Boolean =
    captured && !state.isLongPress && !state.isSwiping
