package com.kingzcheung.xime.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * 键盘布局选择的落地动作（设置页点一下实际做了什么）。
 *
 * 小鹤双拼 → 14 键应派生出 double_pinyin_flypy_14jian 并切过去；
 * 再选 26 键应恢复小鹤双拼，且派生文件不能被删。
 */
@RunWith(AndroidJUnit4::class)
class KeyboardLayoutApplierDeviceTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun schemaFile(id: String) =
        File(SchemaManager.getRimeDir(context), id + ".schema.yaml")

    @Test
    fun flypyToFourteenThenBackTo26() = runBlocking {
        // 应用启动时已完成 rime 初始化与资产落盘（测试里再 init 会与后台部署抢锁而死等）
        // 准备：当前方案是小鹤双拼、布局是 26 键
        SettingsPreferences.setCurrentSchema(context, "double_pinyin_flypy")
        SettingsPreferences.setKeyboardLayoutPreset(context, KeyboardLayoutPreset.LAYOUT_26)

        val applied = KeyboardLayoutApplier.apply(
            context, KeyboardLayoutPreset.LAYOUT_14, KeyboardLayoutPreset.ORDER_QWERTY,
            deployImmediately = false,
        )
        assertEquals("double_pinyin_flypy_14jian", applied)
        assertEquals("double_pinyin_flypy_14jian", SettingsPreferences.getCurrentSchema(context))
        assertEquals("double_pinyin_flypy", SettingsPreferences.getSchemaBeforeGrid(context))
        assertTrue("派生文件应写盘", schemaFile("double_pinyin_flypy_14jian").isFile)
        assertEquals(KeyboardLayoutPreset.LAYOUT_14, SettingsPreferences.getKeyboardLayoutPreset(context))

        val back = KeyboardLayoutApplier.apply(
            context, KeyboardLayoutPreset.LAYOUT_26, KeyboardLayoutPreset.ORDER_QWERTY,
            deployImmediately = false,
        )
        assertEquals("double_pinyin_flypy", back)
        assertEquals("double_pinyin_flypy", SettingsPreferences.getCurrentSchema(context))
        assertTrue("派生文件不该被删", schemaFile("double_pinyin_flypy_14jian").isFile)
        assertEquals(KeyboardLayoutPreset.LAYOUT_26, SettingsPreferences.getKeyboardLayoutPreset(context))
        assertNotNull(context)
    }
}
