package com.kingzcheung.xime.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.rime.RimeConfigHelper
import com.kingzcheung.xime.rime.RimeEngine
import org.junit.Test
import org.junit.runner.RunWith

/** 恢复设备方案状态（测试/调试用）：确保有可用方案并切换过去。 */
@RunWith(AndroidJUnit4::class)
class RestoreDeviceSchemasTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun ensureUsableScheme() {
        val enabled = SchemaManager.getEnabledSchemas(context).toMutableList()
        if ("rime_ice" !in enabled) enabled += "rime_ice"
        if ("double_pinyin_flypy" !in enabled) enabled += "double_pinyin_flypy"
        SchemaManager.setEnabledSchemas(context, enabled)
        RimeConfigHelper.ensureDeployment(context)
        val engine = RimeEngine.getInstance()
        for (i in 0 until 20) {
            if (engine.switchSchema("double_pinyin_flypy")) break
            Thread.sleep(1500)
        }
        SettingsPreferences.setCurrentSchema(context, "double_pinyin_flypy")
        println("enabled=" + SchemaManager.getEnabledSchemas(context) + " current=" + SettingsPreferences.getCurrentSchema(context))
    }
}
