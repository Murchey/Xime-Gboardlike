package com.kingzcheung.xime.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** 符号提示排布：设成 Gboard 后，字母键的上滑提示应换成 Gboard 那套。 */
@RunWith(AndroidJUnit4::class)
class SymbolHintStyleDeviceTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun gboardStyleReplacesHints() {
        SettingsPreferences.setSymbolHintStyle(context, SettingsPreferences.SYMBOL_HINT_GBOARD)
        KeysConfigHelper.loadConfig(context)
        println(
            "gboard: s=" + KeysConfigHelper.getSwipeUpLabel("s") +
                " d=" + KeysConfigHelper.getSwipeUpLabel("d") +
                " z=" + KeysConfigHelper.getSwipeUpLabel("z")
        )
        assertEquals("#", KeysConfigHelper.getSwipeUpLabel("s"))
        assertEquals("$", KeysConfigHelper.getSwipeUpLabel("d"))

        SettingsPreferences.setSymbolHintStyle(context, SettingsPreferences.SYMBOL_HINT_FCITX5)
        KeysConfigHelper.loadConfig(context)
        println("fcitx5: s=" + KeysConfigHelper.getSwipeUpLabel("s") + " d=" + KeysConfigHelper.getSwipeUpLabel("d"))
        assertEquals("*", KeysConfigHelper.getSwipeUpLabel("s"))
    }
}
