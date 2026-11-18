package com.kingzcheung.xime.viewmodel

import com.kingzcheung.xime.keyboard.KeyboardPage
import com.kingzcheung.xime.keyboard.MainType
import com.kingzcheung.xime.keyboard.OverlayRoute
import com.kingzcheung.xime.keyboard.PanelType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 符号页返回：设置开启且背后是数字/符号面板时直接回主键盘，其余组合保持原行为。 */
class SymbolBackSkipTest {

    private val numberPanel = KeyboardPage.Panel(PanelType.NUMBER, MainType.FULL)
    private val symbolPanel = KeyboardPage.Panel(PanelType.COMMON_SYMBOL, MainType.FULL)

    @Test
    fun `开启时从面板进的符号页直接回主键盘`() {
        assertTrue(symbolBackSkipsPanel(OverlayRoute.Symbol, numberPanel, true))
        assertTrue(symbolBackSkipsPanel(OverlayRoute.Symbol, symbolPanel, true))
    }

    @Test
    fun `关闭时保留二次返回`() {
        assertFalse(symbolBackSkipsPanel(OverlayRoute.Symbol, numberPanel, false))
    }

    @Test
    fun `从主键盘进的符号页不受影响`() {
        assertFalse(symbolBackSkipsPanel(OverlayRoute.Symbol, KeyboardPage.Main(MainType.FULL), true))
    }

    @Test
    fun `其他覆盖页不受影响`() {
        assertFalse(symbolBackSkipsPanel(OverlayRoute.Clipboard(), numberPanel, true))
        assertFalse(symbolBackSkipsPanel(OverlayRoute.Emoji, numberPanel, true))
    }
}
