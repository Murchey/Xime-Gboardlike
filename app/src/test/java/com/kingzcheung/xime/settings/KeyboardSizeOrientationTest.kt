package com.kingzcheung.xime.settings

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.content.res.Resources
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * 键盘区尺寸的横竖屏隔离：
 * 高度与左右边距（宽度）都必须是「按方向各存一份」，
 * 某一方向调过之后不能影响另一方向——否则横屏会被竖屏的宽度挤掉按键。
 */
class KeyboardSizeOrientationTest {

    /** 用内存 Map 模拟 SharedPreferences，验证读写落在哪个 key 上。 */
    private class FakePrefs : SharedPreferences {
        val ints = mutableMapOf<String, Int>()
        override fun getInt(key: String, defValue: Int): Int = ints[key] ?: defValue
        override fun getAll(): MutableMap<String, *> = ints
        override fun getString(key: String, defValue: String?) = defValue
        override fun getStringSet(key: String, defValues: MutableSet<String>?) = defValues
        override fun getLong(key: String, defValue: Long) = defValue
        override fun getFloat(key: String, defValue: Float) = defValue
        override fun getBoolean(key: String, defValue: Boolean) = defValue
        override fun contains(key: String) = ints.containsKey(key)
        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            override fun putInt(key: String, value: Int): SharedPreferences.Editor {
                ints[key] = value; return this
            }
            override fun putString(key: String, value: String?) = this
            override fun putStringSet(key: String, values: MutableSet<String>?) = this
            override fun putLong(key: String, value: Long) = this
            override fun putFloat(key: String, value: Float) = this
            override fun putBoolean(key: String, value: Boolean) = this
            override fun remove(key: String) = this
            override fun clear() = this
            override fun commit(): Boolean = true
            override fun apply() {}
        }
        override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    }

    private fun contextWithPortraitScreen(): Context {
        val prefs = FakePrefs()
        val resources: Resources = mock()
        val config = Configuration().apply {
            screenWidthDp = 360
            screenHeightDp = 800
        }
        whenever(resources.configuration).thenReturn(config)
        val context: Context = mock()
        whenever(context.resources).thenReturn(resources)
        whenever(context.getSharedPreferences(any(), any())).thenReturn(prefs)
        return context
    }

    @Test
    fun `竖屏边距不影响横屏`() {
        val context = contextWithPortraitScreen()
        SettingsPreferences.setKeyboardMarginStartDp(context, 40, isLandscape = false)
        SettingsPreferences.setKeyboardMarginEndDp(context, 30, isLandscape = false)

        assertEquals(40, SettingsPreferences.getKeyboardMarginStartDp(context, isLandscape = false))
        assertEquals(30, SettingsPreferences.getKeyboardMarginEndDp(context, isLandscape = false))
        // 横屏未设置过 → 回退 0，而不是搬竖屏的值
        assertEquals(0, SettingsPreferences.getKeyboardMarginStartDp(context, isLandscape = true))
        assertEquals(0, SettingsPreferences.getKeyboardMarginEndDp(context, isLandscape = true))
    }

    @Test
    fun `横屏边距独立保存`() {
        val context = contextWithPortraitScreen()
        SettingsPreferences.setKeyboardMarginStartDp(context, 40, isLandscape = false)
        SettingsPreferences.setKeyboardMarginStartDp(context, 12, isLandscape = true)
        SettingsPreferences.setKeyboardMarginEndDp(context, 8, isLandscape = true)

        assertEquals(40, SettingsPreferences.getKeyboardMarginStartDp(context, isLandscape = false))
        assertEquals(12, SettingsPreferences.getKeyboardMarginStartDp(context, isLandscape = true))
        assertEquals(8, SettingsPreferences.getKeyboardMarginEndDp(context, isLandscape = true))
        SettingsPreferences.setKeyboardMarginStartDp(context, 0, isLandscape = true)
        assertEquals(40, SettingsPreferences.getKeyboardMarginStartDp(context, isLandscape = false))
    }

    @Test
    fun `左右边距使用不同存储键`() {
        val context = contextWithPortraitScreen()
        SettingsPreferences.setKeyboardMarginStartDp(context, 25, isLandscape = false)
        SettingsPreferences.setKeyboardMarginEndDp(context, 15, isLandscape = false)
        assertEquals(25, SettingsPreferences.getKeyboardMarginStartDp(context, isLandscape = false))
        assertEquals(15, SettingsPreferences.getKeyboardMarginEndDp(context, isLandscape = false))
    }
}
