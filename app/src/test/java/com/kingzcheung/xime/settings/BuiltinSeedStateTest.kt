package com.kingzcheung.xime.settings

import android.content.Context
import android.content.SharedPreferences
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class BuiltinSeedStateTest {

    @Test
    fun `默认未卸载`() {
        val context = mock<Context>()
        val prefs = mock<SharedPreferences>()
        whenever(context.getSharedPreferences("builtin_seed", Context.MODE_PRIVATE)).thenReturn(prefs)
        whenever(prefs.getBoolean("disabled", false)).thenReturn(false)

        assertFalse(BuiltinSeedState.isDisabled(context))
    }

    @Test
    fun `卸载标记写入后为 true`() {
        val context = mock<Context>()
        val prefs = mock<SharedPreferences>()
        val editor = mock<SharedPreferences.Editor>()
        whenever(context.getSharedPreferences("builtin_seed", Context.MODE_PRIVATE)).thenReturn(prefs)
        whenever(prefs.edit()).thenReturn(editor)
        whenever(editor.putBoolean("disabled", true)).thenReturn(editor)
        whenever(prefs.getBoolean("disabled", false)).thenReturn(true)

        BuiltinSeedState.setDisabled(context, true)

        verify(editor).putBoolean("disabled", true)
        verify(editor).apply()
        assertTrue(BuiltinSeedState.isDisabled(context))
    }
}
