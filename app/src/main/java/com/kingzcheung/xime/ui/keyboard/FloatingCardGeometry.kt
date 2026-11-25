package com.kingzcheung.xime.ui.keyboard

import kotlin.math.roundToInt

/**
 * 悬浮键盘卡片几何的唯一权威定义（无 Compose 依赖，可单测）。
 *
 * 卡片恒为竖屏形态：宽度 = 短边 × [CARD_SCALE]（横屏下占窗口宽的比例相应变小），
 * 高度 = 键盘内容高 × [CARD_SCALE] + 底部控制栏 + 底部留白。
 *
 * 触摸区与拖动钳制一律以 FloatingKeyboardContainer 实测上报的矩形为准
 * （XimeInputMethodService.floatingCardBounds，窗口坐标）；本对象只承载常量与
 * "首帧实测前"的兜底推算。禁止在调用处复制 0.85/18 等字面量——历史上的
 * 多处复制漂移正是悬浮卡片错位/跳位的根源。
 */
object FloatingCardGeometry {
    /** 卡片缩放系数：宽度、键盘内容高与字号统一按此比例缩小 */
    const val CARD_SCALE = 0.85f

    /** 卡片底部控制栏高度（dp），与 FloatingKeyboardContainer 共用 */
    const val DRAG_BAR_HEIGHT_DP = 56

    /** 悬浮卡片矩形（窗口坐标，px），由 onGloballyPositioned 实测回传 */
    data class CardBounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val heightPx: Int get() = bottom - top
    }

    fun cardWidthDp(portraitScreenWidthDp: Int): Int =
        (portraitScreenWidthDp * CARD_SCALE).roundToInt()

    /** 悬浮卡片宽度百分比下限：再窄按键就没法点了 */
    const val MIN_WIDTH_PERCENT = 50

    /** 按用户调过的宽度百分比取卡宽（[MIN_WIDTH_PERCENT]~100） */
    fun cardWidthDpFor(portraitScreenWidthDp: Int, widthPercent: Int): Int {
        val p = widthPercent.coerceIn(MIN_WIDTH_PERCENT, 100)
        return (cardWidthDp(portraitScreenWidthDp) * p / 100f).roundToInt()
    }

    /** 悬浮键盘高度的可调区间（缩放前的键盘高，dp）：卡高占可用高的 25%~[MAX_CARD_HEIGHT_FRACTION] */
    fun floatingHeightBoundsDp(availableHeightDp: Int): IntRange {
        if (availableHeightDp <= 0) return 120..400
        val low = (availableHeightDp * 0.25f / CARD_SCALE).toInt()
        val high = (availableHeightDp * MAX_CARD_HEIGHT_FRACTION / CARD_SCALE).toInt()
        return low.coerceAtLeast(1)..high.coerceAtLeast(low + 1)
    }

    /** 卡片宽度相对宿主（全屏宽）的比例：竖屏 = [CARD_SCALE]，横屏按短边折算 */
    fun widthFraction(
        portraitScreenWidthDp: Int,
        windowWidthDp: Int,
        widthPercent: Int = 100,
    ): Float = if (windowWidthDp <= 0) CARD_SCALE
    else cardWidthDpFor(portraitScreenWidthDp, widthPercent) / windowWidthDp.toFloat()

    /** 卡片水平可移动半幅（dp）：offsetX ∈ [-halfMargin, +halfMargin] */
    fun halfMarginDp(
        windowWidthDp: Int,
        portraitScreenWidthDp: Int,
        widthPercent: Int = 100,
    ): Int = maxOf(0, (windowWidthDp - cardWidthDpFor(portraitScreenWidthDp, widthPercent)) / 2)

    /** 卡片内容高占可用高度的上限：横屏短边上沿用竖屏键盘高会把卡片撑满屏，没有拖动行程 */
    const val MAX_CARD_HEIGHT_FRACTION = 0.5f

    /** 悬浮键盘高度按可用高度封顶（缩放前的键盘高，dp）；可用高度未知时原样返回 */
    fun capKeyboardHeightDp(keyboardHeightDp: Int, availableHeightDp: Int): Int {
        if (availableHeightDp <= 0) return keyboardHeightDp
        val maxDp = (availableHeightDp * MAX_CARD_HEIGHT_FRACTION / CARD_SCALE).toInt()
        return keyboardHeightDp.coerceAtMost(maxDp)
    }

    /**
     * 悬浮卡片用的键盘内容高（dp，缩放前）：用户调过就用调过的值，
     * 否则沿用停靠键盘高并按可用高封顶。渲染、拖动钳制、触摸区兜底共用同一入口。
     */
    fun floatingKeyboardHeightDp(
        userHeightDp: Int,
        dockedHeightDp: Int,
        availableHeightDp: Int,
    ): Int = if (userHeightDp > 0) {
        userHeightDp.coerceIn(floatingHeightBoundsDp(availableHeightDp))
    } else {
        capKeyboardHeightDp(dockedHeightDp, availableHeightDp)
    }

    /** 首帧实测前的兜底卡高（dp） */
    fun fallbackCardHeightDp(keyboardHeightDp: Int, bottomPaddingDp: Int): Int =
        (keyboardHeightDp * CARD_SCALE).roundToInt() + DRAG_BAR_HEIGHT_DP + bottomPaddingDp

    /**
     * 垂直位置钳制：offsetY 是"卡片底边离窗口底边的距离"，下界 [minY]（导航栏高，
     * 卡片不得压进导航栏），上界保证卡片顶边不超出窗口。
     * [topGuardDp] 为卡片顶部保留额外空间。
     */
    fun clampOffsetY(offsetY: Int, minY: Int, screenHeightDp: Int, cardHeightDp: Int,
                     topGuardDp: Int = 18): Int {
        val maxOffsetY = (screenHeightDp - cardHeightDp - topGuardDp).coerceAtLeast(minY)
        return offsetY.coerceIn(minY, maxOffsetY)
    }

    /**
     * 首帧实测前的兜底触摸区（窗口坐标，px）。卡片水平居中后加 offsetX，
     * 底边 = 窗口底 - offsetY，顶边 = 底边 - 卡高。
     */
    fun fallbackBounds(
        windowWidthPx: Int,
        windowHeightPx: Int,
        offsetXdp: Int,
        offsetYdp: Int,
        cardWidthDp: Int,
        cardHeightDp: Int,
        density: Float,
    ): CardBounds {
        val cardWidthPx = (cardWidthDp * density).toInt()
        val cardHeightPx = (cardHeightDp * density).toInt()
        val left = (windowWidthPx - cardWidthPx) / 2 + (offsetXdp * density).toInt()
        val bottom = windowHeightPx - (offsetYdp * density).toInt()
        return CardBounds(left, bottom - cardHeightPx, left + cardWidthPx, bottom)
    }
}
