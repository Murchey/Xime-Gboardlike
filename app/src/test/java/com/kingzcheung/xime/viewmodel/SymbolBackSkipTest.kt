package com.kingzcheung.xime.viewmodel

import com.kingzcheung.xime.keyboard.KeyboardPage
import com.kingzcheung.xime.keyboard.MainType
import com.kingzcheung.xime.keyboard.OverlayRoute
import com.kingzcheung.xime.keyboard.PanelType
import com.kingzcheung.xime.ui.keyboard.KeyboardLayoutState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 符号页返回：数字/符号键盘（面板页或主键盘分支的数字布局）都要直接回字母主键盘。 */
class SymbolBackSkipTest {

    private val numberPanel = KeyboardPage.Panel(PanelType.NUMBER, MainType.FULL)
    private val symbolPanel = KeyboardPage.Panel(PanelType.COMMON_SYMBOL, MainType.FULL)
    private val main = KeyboardPage.Main(MainType.FULL)

    @Test
    fun `开启时从面板进的符号页直接回主键盘`() {
        assertTrue(symbolBackSkipsPanel(OverlayRoute.Symbol, numberPanel, KeyboardLayoutState.Chinese, true))
        assertTrue(symbolBackSkipsPanel(OverlayRoute.Symbol, symbolPanel, KeyboardLayoutState.English, true))
    }

    @Test
    fun `主键盘分支的数字或符号布局也要回主键盘`() {
        assertTrue(symbolBackSkipsPanel(OverlayRoute.Symbol, main, KeyboardLayoutState.Number, true))
        assertTrue(symbolBackSkipsPanel(OverlayRoute.Symbol, main, KeyboardLayoutState.CommonSymbol, true))
    }

    @Test
    fun `字母主布局不受影响`() {
        assertFalse(symbolBackSkipsPanel(OverlayRoute.Symbol, main, KeyboardLayoutState.Chinese, true))
        assertFalse(symbolBackSkipsPanel(OverlayRoute.Symbol, main, KeyboardLayoutState.English, true))
    }

    @Test
    fun `关闭设置时保留二次返回`() {
        assertFalse(symbolBackSkipsPanel(OverlayRoute.Symbol, numberPanel, KeyboardLayoutState.Chinese, false))
        assertFalse(symbolBackSkipsPanel(OverlayRoute.Symbol, main, KeyboardLayoutState.Number, false))
    }

    @Test
    fun `其他覆盖页不受影响`() {
        assertFalse(symbolBackSkipsPanel(OverlayRoute.Clipboard(), numberPanel, KeyboardLayoutState.Chinese, true))
        assertFalse(symbolBackSkipsPanel(OverlayRoute.Emoji, main, KeyboardLayoutState.Number, true))
    }
}