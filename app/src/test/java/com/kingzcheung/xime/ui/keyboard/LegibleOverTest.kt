package com.kingzcheung.xime.ui.keyboard

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import com.kingzcheung.xime.settings.FrostedGlassConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 玻璃可读性下限（legibleOver）的纯逻辑测试。 */
class LegibleOverTest {

    private fun contrast(a: Color, b: Color): Float {
        val light = maxOf(a.luminance(), b.luminance())
        val dark = minOf(a.luminance(), b.luminance())
        return (light + 0.05f) / (dark + 0.05f)
    }

    @Test
    fun `太透的玻璃会被抬到达标`() {
        val key = Color(0xFFFFFFFF)
        val text = Color(0xFF222222)
        val config = FrostedGlassConfig(enabled = true, backgroundOpacity = 0.2f, keyOpacity = 0.2f)
        val fixed = config.legibleOver(key, listOf(key to text))
        assertTrue(fixed.keyOpacity > config.keyOpacity)
        assertTrue(fixed.backgroundOpacity > config.backgroundOpacity)
        // 最坏背景（白/黑）下键面文字对比度达标
        val worst = listOf(Color.White, Color.Black).minOf { backdrop ->
            val panel = key.copy(alpha = fixed.backgroundOpacity).compositeOver(backdrop)
            contrast(text, frostedKeyColor(key, text, fixed).compositeOver(panel))
        }
        assertTrue(worst >= 4.5f)
    }

    @Test
    fun `已经清楚的设置保持不变`() {
        val key = Color(0xFFFFFFFF)
        val text = Color(0xFF000000)
        val config = FrostedGlassConfig(enabled = true, backgroundOpacity = 1f, keyOpacity = 1f)
        assertEquals(config, config.legibleOver(key, listOf(key to text)))
    }

    @Test
    fun `玻璃关闭时原样返回`() {
        val config = FrostedGlassConfig(enabled = false, keyOpacity = 0.1f)
        assertEquals(config, config.legibleOver(Color.White, listOf(Color.White to Color.Black)))
    }
}
