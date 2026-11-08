package com.kingzcheung.xime.service

import android.content.Context
import android.os.Build
import android.util.Log
import android.view.Window
import android.view.WindowInsets
import kotlin.math.roundToInt

private const val INSETS_TAG = "ImeWindowInsets"

/** 获取导航栏高度（dp）。 */
internal fun tryGetNavBarHeightDp(context: Context, imeWindow: Window?): Int {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val decorView = imeWindow?.decorView
            if (decorView != null) {
                val insets = decorView.rootWindowInsets
                if (insets != null) {
                    val px = insets.getInsetsIgnoringVisibility(
                        WindowInsets.Type.navigationBars()
                    ).bottom
                    val dp = (px / context.resources.displayMetrics.density).roundToInt()
                    return dp
                }
            }
        }
        val resId = context.resources.getIdentifier("navigation_bar_height", "dimen", "android")
        val dp = if (resId > 0) (context.resources.getDimensionPixelSize(resId) / context.resources.displayMetrics.density).roundToInt() else 0
        return dp
    } catch (e: Exception) {
        Log.w(INSETS_TAG, "NavBar: error", e)
        0
    }
}

/** 获取可见导航栏高度（dp）。 */
internal fun tryGetVisibleNavBarHeightDp(context: Context, imeWindow: Window?): Int {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val decorView = imeWindow?.decorView
            if (decorView != null) {
                val insets = decorView.rootWindowInsets
                if (insets != null) {
                    val px = insets.getInsets(
                        WindowInsets.Type.navigationBars()
                    ).bottom
                    val dp = if (px > 0) (px / context.resources.displayMetrics.density).roundToInt() else 0
                    return dp
                }
            }
        }
        0
    } catch (e: Exception) {
        Log.w(INSETS_TAG, "NavBar: visibleOnly error", e)
        0
    }
}

/** 获取状态栏高度（dp）。 */
internal fun tryGetStatusBarHeightDp(context: Context, imeWindow: Window?): Int {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val decorView = imeWindow?.decorView
            if (decorView != null) {
                val insets = decorView.rootWindowInsets
                if (insets != null) {
                    val px = insets.getInsetsIgnoringVisibility(
                        WindowInsets.Type.statusBars()
                    ).top
                    if (px > 0) return (px / context.resources.displayMetrics.density).roundToInt()
                }
            }
        }
        val resId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resId > 0) (context.resources.getDimensionPixelSize(resId) / context.resources.displayMetrics.density).roundToInt() else 0
    } catch (e: Exception) { 0 }
}

/**
 * 多类型检测底部 inset。
 * 与主流输入法一致：navigationBars 与 mandatorySystemGestures 取最大，
 * 全部为空时再回退 systemGestures / tappableElement。
 * 手势导航下 navigationBars 通常为 0，但 mandatorySystemGestures / systemGestures
 * 会返回手势条高度（约 20~32dp），阈值不能过滤掉它们，否则底部手势条区域会露出窗口背景。
 */
internal fun extractBottomInset(
    insets: WindowInsets
): Int {
    val sys = insets.getInsets(WindowInsets.Type.systemBars()).bottom
    val nav = insets.getInsets(WindowInsets.Type.navigationBars()).bottom
    val tappable = insets.getInsets(WindowInsets.Type.tappableElement()).bottom
    val mandatory = insets.getInsets(WindowInsets.Type.mandatorySystemGestures()).bottom
    val gestures = insets.getInsets(WindowInsets.Type.systemGestures()).bottom
    val primary = maxOf(nav, mandatory)
    val fallback = maxOf(gestures, tappable)
    val pick = if (primary > 0) primary else fallback
    Log.d(INSETS_TAG, "bottom: sys=$sys nav=$nav tappable=$tappable mandatory=$mandatory gestures=$gestures pick=$pick")
    return pick
}

/** 获取当前活跃的底部 inset（px）。 */
internal fun getActiveBottomInsetPx(imeWindow: Window?): Int {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return 0
    return try {
        val decorView = imeWindow?.decorView ?: return 0
        val insets = decorView.rootWindowInsets ?: return 0
        val px = extractBottomInset(insets)
        Log.d(INSETS_TAG, "getActiveBottomInsetPx=$px (rootWindowInsets)")
        px
    } catch (e: Exception) { 0 }
}

/**
 * 多类型检测左右 inset（px），取 displayCutout / systemBars / tappableElement
 * 各方向的最大值。
 *
 * **只在横屏生效**：挖孔/刘海在横屏时位于屏幕左右边缘、三键导航栏横屏贴左右缘，
 * 这些确实需要避让。竖屏时左右本该为 0，但部分 ROM（实测 MIUI）的
 * tappableElement/systemBars 会给出左右非 0 值，照单全收会把键盘整体内缩、
 * 屏幕上留出两条边——表现为「键盘没有占满屏幕宽度」。
 *
 * 有意不含手势区（systemGestures / mandatorySystemGestures）：
 * 竖屏返回手势区贴左右缘且全高，计入会把竖屏键盘内容整体内缩。
 */
/**
 * 键盘内容是否需要按左右 inset 内缩。
 *
 * 只有横屏才避让：横屏时挖孔/刘海与三键导航栏贴屏幕左右缘；
 * 竖屏左右本该为 0，但部分 ROM（实测 MIUI）的 tappableElement/systemBars
 * 会给出非 0 值，照单全收会把键盘整体内缩、屏幕两侧留白——
 * 用户看到的就是「键盘没有占满屏幕宽度」。
 */
internal fun shouldApplyHorizontalInsets(isLandscape: Boolean): Boolean = isLandscape

internal fun extractHorizontalInsets(
    insets: WindowInsets,
    isLandscape: Boolean,
): Pair<Int, Int> {
    if (!shouldApplyHorizontalInsets(isLandscape)) return 0 to 0
    val cutout = insets.getInsets(WindowInsets.Type.displayCutout())
    val sys = insets.getInsets(WindowInsets.Type.systemBars())
    val tappable = insets.getInsets(WindowInsets.Type.tappableElement())
    val left = maxOf(cutout.left, sys.left, tappable.left)
    val right = maxOf(cutout.right, sys.right, tappable.right)
    Log.d(INSETS_TAG, "horizontal: left=$left right=$right")
    return left to right
}

/** 获取当前活跃的左右 inset（px）。 */
internal fun getActiveHorizontalInsetsPx(imeWindow: Window?, isLandscape: Boolean): Pair<Int, Int> {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return 0 to 0
    return try {
        val decorView = imeWindow?.decorView ?: return 0 to 0
        val insets = decorView.rootWindowInsets ?: return 0 to 0
        extractHorizontalInsets(insets, isLandscape)
    } catch (e: Exception) { 0 to 0 }
}
