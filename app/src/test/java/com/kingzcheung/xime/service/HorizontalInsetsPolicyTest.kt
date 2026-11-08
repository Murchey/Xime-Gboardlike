package com.kingzcheung.xime.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 键盘内容是否按左右 inset 内缩。
 *
 * 只有横屏才避让：横屏时挖孔/刘海与三键导航栏贴屏幕左右缘；
 * 竖屏左右本该为 0，但部分 ROM（实测 MIUI）的 tappableElement/systemBars
 * 会给出非 0 值，照单全收会让键盘整体内缩、屏幕两侧留白——
 * 用户看到的就是「键盘没有占满屏幕宽度」。
 */
class HorizontalInsetsPolicyTest {

    @Test
    fun `竖屏不应用左右 inset`() {
        assertFalse(shouldApplyHorizontalInsets(isLandscape = false))
    }

    @Test
    fun `横屏应用左右 inset`() {
        assertTrue(shouldApplyHorizontalInsets(isLandscape = true))
    }
}
