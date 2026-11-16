package com.kingzcheung.xime.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** 符号提示风格切换后，取值接口应立即返回新符号（键盘无需重启）。 */
@RunWith(AndroidJUnit4::class)
class SymbolHintRuntimeDeviceTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun styleSwitchAppliesImmediately() {
        // 先确保有启用的方案，否则配置加载不出按键
        val enabled = SchemaManager.getEnabledSchemas(context)
        if (enabled.isEmpty()) {
            SchemaManager.setEnabledSchemas(context, listOf("double_pinyin_flypy"))
        }
        KeysConfigHelper.loadConfig(context)

        SettingsPreferences.setSymbolHintStyle(context, SettingsPreferences.SYMBOL_HINT_FCITX5)
        val fcitx5 = KeysConfigHelper.getSwipeUpLabel("f")
        println("fcitx5 f=" + fcitx5)

        SettingsPreferences.setSymbolHintStyle(context, SettingsPreferences.SYMBOL_HINT_GBOARD)
        val gboardF = KeysConfigHelper.getSwipeUpLabel("f")
        val gboardZ = KeysConfigHelper.getSwipeUpLabel("z")
        val action = KeysConfigHelper.getSwipeUpAction("n")
        val longPress = KeysConfigHelper.longPressForStyle("c")
        println("gboard f=" + gboardF + " z=" + gboardZ + " action(n)=" + action +
            " longPress(c)=" + longPress?.values?.map { if (it.label.isNotEmpty()) it.label else it.value })

        assertEquals("_", gboardF)
        assertEquals("、", gboardZ)
        assertEquals(com.kingzcheung.xime.keyboard.GestureAction.COMMIT, action)
        // C 的智能引号动作要在（排在符号后面）
        org.junit.Assert.assertTrue(
            longPress!!.values.drop(1).any { it.action == com.kingzcheung.xime.keyboard.GestureAction.SMART_QUOTE },
        )
    }
}
