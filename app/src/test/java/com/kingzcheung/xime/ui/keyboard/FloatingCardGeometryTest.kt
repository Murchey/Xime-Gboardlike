package com.kingzcheung.xime.ui.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 悬浮卡片几何纯逻辑测试（[FloatingCardGeometry]）。
 *
 * 触摸区与拖动钳制都消费这里的推导，数值是悬浮卡片不错位的底线：
 * - 卡宽 = 短边 × 0.85（横竖屏一致，卡片恒为竖屏形态）
 * - offsetY 下界 = 导航栏高（卡片不得压进导航栏），上界 = 屏高 - 卡高
 */
class FloatingCardGeometryTest {

    @Test
    fun `card width is 85 percent of portrait width`() {
        assertEquals(349, FloatingCardGeometry.cardWidthDp(411))
        assertEquals(0, FloatingCardGeometry.cardWidthDp(0))
    }

    @Test
    fun `width fraction equals card scale in portrait`() {
        val fraction = FloatingCardGeometry.widthFraction(portraitScreenWidthDp = 411, windowWidthDp = 411)
        assertEquals(FloatingCardGeometry.CARD_SCALE, fraction, 0.001f)
    }

    @Test
    fun `width fraction shrinks below card scale in landscape window`() {
        // 横屏窗口 891dp：卡宽仍按短边 411dp 算，占窗口 349/891 ≈ 0.392
        val fraction = FloatingCardGeometry.widthFraction(portraitScreenWidthDp = 411, windowWidthDp = 891)
        assertEquals(349f / 891f, fraction, 0.0001f)
    }

    @Test
    fun `degenerate window width falls back to card scale`() {
        assertEquals(FloatingCardGeometry.CARD_SCALE, FloatingCardGeometry.widthFraction(411, 0), 0.0001f)
    }

    @Test
    fun `half margin is half of window minus card`() {
        assertEquals(31, FloatingCardGeometry.halfMarginDp(windowWidthDp = 411, portraitScreenWidthDp = 411))
        assertEquals(271, FloatingCardGeometry.halfMarginDp(windowWidthDp = 891, portraitScreenWidthDp = 411))
    }

    @Test
    fun `half margin never negative when window narrower than card`() {
        assertEquals(0, FloatingCardGeometry.halfMarginDp(windowWidthDp = 300, portraitScreenWidthDp = 411))
    }

    @Test
    fun `fallback card height is scaled keyboard plus drag bar plus padding`() {
        // 0.85×280 = 238，+18 拖条 +16 留白
        assertEquals(272, FloatingCardGeometry.fallbackCardHeightDp(keyboardHeightDp = 280, bottomPaddingDp = 16))
    }

    @Test
    fun `clamp offsetY pushes card above navigation bar`() {
        // 下界 = minY（导航栏高）：拖动不允许把卡片压进导航栏
        assertEquals(16, FloatingCardGeometry.clampOffsetY(-5, minY = 16, screenHeightDp = 800, cardHeightDp = 272))
    }

    @Test
    fun `clamp offsetY caps card inside screen leaving drag bar visible`() {
        // maxOffsetY = screenH - cardH - topGuard = 800 - 272 - 18 = 510
        assertEquals(510, FloatingCardGeometry.clampOffsetY(600, minY = 0, screenHeightDp = 800, cardHeightDp = 272))
    }

    @Test
    fun `clamp offsetY with explicit topGuard zero matches old behaviour`() {
        // topGuardDp=0 → 旧行为
        assertEquals(528, FloatingCardGeometry.clampOffsetY(600, minY = 0, screenHeightDp = 800, cardHeightDp = 272, topGuardDp = 0))
    }

    @Test
    fun `clamp keeps minY reachable when card taller than screen`() {
        // 卡高超过屏高时上界塌缩到 minY，钳制区间不反向
        assertEquals(16, FloatingCardGeometry.clampOffsetY(50, minY = 16, screenHeightDp = 100, cardHeightDp = 272))
    }

    @Test
    fun `landscape caps keyboard height so card leaves drag room`() {
        // 横屏可用高 369dp：竖屏键盘 302dp 封顶到 369×0.5/0.85 = 217dp
        assertEquals(217, FloatingCardGeometry.capKeyboardHeightDp(302, 369))
        val card = FloatingCardGeometry.fallbackCardHeightDp(217, 14)
        assertTrue(369 - card >= 150)
    }

    @Test
    fun `portrait keyboard height is not capped`() {
        assertEquals(302, FloatingCardGeometry.capKeyboardHeightDp(302, 849))
    }

    @Test
    fun `unknown available height leaves keyboard height unchanged`() {
        assertEquals(302, FloatingCardGeometry.capKeyboardHeightDp(302, 0))
    }

    @Test
    fun `floating height uses user value inside bounds`() {
        val bounds = FloatingCardGeometry.floatingHeightBoundsDp(369)
        assertEquals(108, bounds.first)
        assertEquals(217, bounds.last)
        // 用户值在区间内原样保留
        assertEquals(200, FloatingCardGeometry.floatingKeyboardHeightDp(200, 302, 369))
        // 超出上界夹到上界
        assertEquals(217, FloatingCardGeometry.floatingKeyboardHeightDp(300, 302, 369))
    }

    @Test
    fun `floating height falls back to capped docked height`() {
        assertEquals(
            FloatingCardGeometry.capKeyboardHeightDp(302, 369),
            FloatingCardGeometry.floatingKeyboardHeightDp(0, 302, 369),
        )
    }

    @Test
    fun `floating width percent shrinks card and keeps it centered`() {
        assertEquals(349, FloatingCardGeometry.cardWidthDpFor(411, 100))
        assertEquals(175, FloatingCardGeometry.cardWidthDpFor(411, 50))
        // 下限 50%：再低也按 50 算
        assertEquals(175, FloatingCardGeometry.cardWidthDpFor(411, 10))
        assertEquals(349, FloatingCardGeometry.cardWidthDpFor(411, 200))
        // 收窄后水平行程随之变大：(411 - 175) / 2 = 118
        assertEquals(118, FloatingCardGeometry.halfMarginDp(411, 411, 50))
    }

    @Test
    fun `fallback bounds center card and honor offsets`() {
        val bounds = FloatingCardGeometry.fallbackBounds(
            windowWidthPx = 1080,
            windowHeightPx = 2000,
            offsetXdp = 10,
            offsetYdp = 30,
            cardWidthDp = 349,
            cardHeightDp = 272,
            density = 2f,
        )
        // 卡宽 698px 水平居中 → left 191，再加 offsetX 20px
        assertEquals(211, bounds.left)
        assertEquals(909, bounds.right)
        // 底边 = 窗口底 - offsetY(60px)，顶边 = 底边 - 卡高(544px)
        assertEquals(1940, bounds.bottom)
        assertEquals(1396, bounds.top)
        assertEquals(544, bounds.heightPx)
    }

    @Test
    fun `fallback bounds with zero offset sits at window bottom`() {
        val bounds = FloatingCardGeometry.fallbackBounds(
            windowWidthPx = 1080,
            windowHeightPx = 2000,
            offsetXdp = 0,
            offsetYdp = 0,
            cardWidthDp = 349,
            cardHeightDp = 272,
            density = 2f,
        )
        assertEquals(2000, bounds.bottom)
    }
}
