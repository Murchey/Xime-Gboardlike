package com.kingzcheung.xime.ui.keyboard

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.kingzcheung.xime.settings.FrostedGlassConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 玻璃键面配色算法（纯逻辑）。 */
class FrostedKeyColorsTest {

    private val lightKey = Color(0xFFFFFFFF)
    private val darkKey = Color(0xFF1C1C1C)
    private val textOnLight = Color(0xFF222222)

    @Test
    fun `未开玻璃时保持原色`() {
        val config = FrostedGlassConfig(enabled = false)
        assertEquals(lightKey, frostedKeyColor(lightKey, textOnLight, config))
        assertEquals(darkKey, frostedKeyColor(darkKey, Color.White, config))
    }

    @Test
    fun `未开玻璃时按下向文字色靠拢`() {
        val config = FrostedGlassConfig(enabled = false)
        val pressed = frostedKeyColor(lightKey, textOnLight, config, pressed = true)
        assertTrue(pressed.luminance() < lightKey.luminance())
        assertTrue(pressed.luminance() > textOnLight.luminance())
    }

    @Test
    fun `玻璃下键面按不透明度输出`() {
        val config = FrostedGlassConfig(enabled = true, keyOpacity = 0.8f)
        val color = frostedKeyColor(lightKey, textOnLight, config)
        assertEquals(0.8f, color.alpha, 1e-4f)
    }

    @Test
    fun `深色近黑键帽在玻璃下提亮`() {
        val config = FrostedGlassConfig(enabled = true, keyOpacity = 0.2f)
        val raised = frostedKeyColor(darkKey, Color.White, config)
        assertTrue(raised.luminance() > darkKey.luminance())
        assertEquals(0.2f, raised.alpha, 1e-4f)
    }

    @Test
    fun `全透明与全不透明边界`() {
        val clear = FrostedGlassConfig(enabled = true, keyOpacity = 0f)
        assertEquals(0f, frostedKeyColor(lightKey, textOnLight, clear).alpha, 1e-4f)
        val solid = FrostedGlassConfig(enabled = true, keyOpacity = 1f)
        assertEquals(lightKey, frostedKeyColor(lightKey, textOnLight, solid))
    }
}
