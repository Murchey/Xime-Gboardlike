package com.kingzcheung.xime.settings

import android.content.Context
import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class BuiltinSeedStateTest {

    private fun context(prefs: SharedPreferences): Context {
        val context = mock<Context>()
        whenever(context.getSharedPreferences("builtin_seed", Context.MODE_PRIVATE)).thenReturn(prefs)
        return context
    }

    @Test
    fun `默认未卸载`() {
        val prefs = mock<SharedPreferences>()
        whenever(prefs.getStringSet("disabled_packages", null)).thenReturn(null)
        whenever(prefs.getBoolean("disabled", false)).thenReturn(false)

        val context = context(prefs)
        assertFalse(BuiltinSeedState.isDisabled(context))
        assertTrue(BuiltinSeedState.disabledPackages(context).isEmpty())
    }

    @Test
    fun `旧版聚合标记等价于三个方案包都卸载`() {
        val prefs = mock<SharedPreferences>()
        whenever(prefs.getStringSet("disabled_packages", null)).thenReturn(null)
        whenever(prefs.getBoolean("disabled", false)).thenReturn(true)

        val context = context(prefs)
        assertEquals(BuiltinPackages.USER_VISIBLE.toSet(), BuiltinSeedState.disabledPackages(context))
        assertTrue(BuiltinSeedState.isDisabled(context, BuiltinPackages.RIME_ICE))
        assertFalse(BuiltinSeedState.isDisabled(context, BuiltinPackages.SHARED))
    }

    @Test
    fun `按包写入卸载标记`() {
        val prefs = mock<SharedPreferences>()
        val editor = mock<SharedPreferences.Editor>()
        whenever(prefs.getStringSet("disabled_packages", null)).thenReturn(null)
        whenever(prefs.edit()).thenReturn(editor)
        whenever(editor.putStringSet("disabled_packages", setOf(BuiltinPackages.RIME_ICE))).thenReturn(editor)
        whenever(editor.remove("disabled")).thenReturn(editor)

        val context = context(prefs)
        BuiltinSeedState.setDisabled(context, BuiltinPackages.RIME_ICE, true)

        verify(editor).putStringSet("disabled_packages", setOf(BuiltinPackages.RIME_ICE))
        verify(editor).apply()
    }
}
