package com.kingzcheung.xime.settings

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomThemeStoreTest {

    private fun sample(id: String = "custom_test") = CustomThemeStore.defaultScheme(id = id, name = "测试配色")

    @Test
    fun `颜色规范化支持三种写法`() {
        assertEquals("#1A73E8", CustomThemeStore.normalizeHexColor("#1a73e8"))
        assertEquals("#1A73E8", CustomThemeStore.normalizeHexColor("0x1A73E8"))
        assertEquals("#1A73E8", CustomThemeStore.normalizeHexColor("1A73E8"))
        assertEquals("#801A73E8", CustomThemeStore.normalizeHexColor("#801a73e8"))
        assertNull(CustomThemeStore.normalizeHexColor("#12345"))
        assertNull(CustomThemeStore.normalizeHexColor("not-a-color"))
    }

    @Test
    fun `导出再导入保持配色与名称`() {
        val original = sample().copy(
            name = "我的 Gboard",
            light = CustomThemeStore.GBOARD_LIGHT.copy(accent = "#123456"),
        )
        val json = CustomThemeStore.exportJson(listOf(original))
        val restored = CustomThemeStore.importJson(json).single()

        assertEquals(original.id, restored.id)
        assertEquals("我的 Gboard", restored.name)
        assertEquals("#123456", restored.light.accent)
        assertEquals(CustomThemeStore.GBOARD_DARK, restored.dark)
    }

    @Test
    fun `支持单方案对象导入`() {
        val original = sample()
        val root = JSONObject(CustomThemeStore.exportJson(listOf(original)))
            .getJSONArray("schemes")
            .getJSONObject(0)
        val restored = CustomThemeStore.importJson(root.toString()).single()
        assertEquals(original.id, restored.id)
        assertEquals(original.light, restored.light)
    }

    @Test
    fun `缺失颜色回退到默认色板`() {
        val root = JSONObject()
            .put("id", "custom_min")
            .put("name", "极简")
            .put("light", JSONObject().put("key_background", "#101010"))
            .put("dark", JSONObject())
        val restored = CustomThemeStore.importJson(root.toString()).single()

        assertEquals("#101010", restored.light.keyBackground)
        assertEquals(CustomThemeStore.GBOARD_LIGHT.accent, restored.light.accent)
        assertEquals(CustomThemeStore.GBOARD_DARK.keyboardBackground, restored.dark.keyboardBackground)
    }

    @Test
    fun `非法颜色会被拒绝`() {
        val root = JSONObject()
            .put("id", "custom_bad")
            .put("name", "坏配色")
            .put("light", JSONObject().put("accent", "red"))
            .put("dark", JSONObject())
        val error = runCatching { CustomThemeStore.importJson(root.toString()) }.exceptionOrNull()
        assertTrue(error is CustomThemeStore.ThemeFormatException)
    }

    @Test
    fun `导入时重复 id 会自动去重`() {
        val json = CustomThemeStore.exportJson(listOf(sample(), sample()))
        val restored = CustomThemeStore.importJson(json)
        assertEquals(2, restored.size)
        assertNotEquals(restored[0].id, restored[1].id)
    }

    @Test
    fun `自定义 id 自动补 custom 前缀`() {
        val json = CustomThemeStore.exportJson(listOf(sample("dark_blue")))
        val restored = CustomThemeStore.importJson(json).single()
        assertEquals("custom_dark_blue", restored.id)
    }
}
