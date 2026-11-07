package com.kingzcheung.xime.settings.declarative

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 声明式设置层纯逻辑单测：默认值、读写、越界收敛、枚举回落与可见性求值。
 * 这些语义是各设置页迁移后行为一致的保证。
 */
class DeclarativeSettingsTest {

    private fun switchSpec(default: Boolean = true) = SettingSpec.Switch(
        key = "sound_enabled",
        titleRes = 1,
        default = default,
    )

    private fun sliderSpec() = SettingSpec.Slider(
        key = "sound_volume",
        titleRes = 2,
        default = 20,
        min = 0,
        max = 100,
        step = 5,
        suffix = "%",
    )

    private fun choiceSpec() = SettingSpec.Choice(
        key = "haptic_mode",
        titleRes = 3,
        default = "following_system",
        options = listOf(
            ChoiceOption("following_system", 4),
            ChoiceOption("enabled", 5),
            ChoiceOption("disabled", 6),
        ),
    )

    @Test
    fun `未设置时读取声明默认值`() {
        val store = MapSettingsStore()
        assertEquals(SettingValue.Flag(true), switchSpec().read(store))
        assertEquals(SettingValue.Number(20), sliderSpec().read(store))
        assertEquals(SettingValue.Text("following_system"), choiceSpec().read(store))
    }

    @Test
    fun `写入后读回新值并落到存储`() {
        val store = MapSettingsStore()
        switchSpec().write(store, SettingValue.Flag(false))
        sliderSpec().write(store, SettingValue.Number(75))
        choiceSpec().write(store, SettingValue.Text("enabled"))

        assertFalse(store.flag("sound_enabled", true))
        assertEquals(75, store.number("sound_volume", 0))
        assertEquals("enabled", store.text("haptic_mode", ""))
    }

    @Test
    fun `滑杆写入越界时收敛到声明范围`() {
        val store = MapSettingsStore()
        sliderSpec().write(store, SettingValue.Number(250))
        assertEquals(100, store.number("sound_volume", -1))
        sliderSpec().write(store, SettingValue.Number(-30))
        assertEquals(0, store.number("sound_volume", -1))
    }

    @Test
    fun `滑杆读取已存的越界值时同样收敛`() {
        val store = MapSettingsStore(mapOf("sound_volume" to 999))
        assertEquals(SettingValue.Number(100), sliderSpec().read(store))
    }

    @Test
    fun `单选遇到未知存储值回落默认且不崩溃`() {
        val store = MapSettingsStore(mapOf("haptic_mode" to "legacy_value"))
        assertEquals(SettingValue.Text("following_system"), choiceSpec().read(store))
    }

    @Test
    fun `单选默认值不在选项里时取第一项`() {
        val spec = choiceSpec().copy(default = "unknown")
        assertEquals(SettingValue.Text("following_system"), spec.defaultValue())
    }

    @Test(expected = IllegalStateException::class)
    fun `单选写入未知值直接抛错以暴露调用方 bug`() {
        val store = MapSettingsStore()
        choiceSpec().write(store, SettingValue.Text("unknown"))
    }

    @Test
    fun `可见性按开关求值`() {
        val visibility = SettingVisibility.whenFlag("sound_enabled")
        assertTrue(visibility.evaluate(MapSettingsStore(mapOf("sound_enabled" to true))))
        assertFalse(visibility.evaluate(MapSettingsStore(mapOf("sound_enabled" to false))))
        // 未设置时按 false 处理
        assertFalse(visibility.evaluate(MapSettingsStore()))
    }

    @Test
    fun `复合可见性要求所有条件同时满足`() {
        val visibility = SettingVisibility.all(
            SettingVisibility.whenText("haptic_mode", "enabled"),
            SettingVisibility.whenTrue { true },
        )
        assertTrue(visibility.evaluate(MapSettingsStore(mapOf("haptic_mode" to "enabled"))))
        assertFalse(visibility.evaluate(MapSettingsStore(mapOf("haptic_mode" to "following_system"))))
    }

    @Test
    fun `设备能力可见性为假时隐藏且不读取偏好`() {
        val hidden = SettingVisibility.whenTrue { false }
        assertFalse(hidden.evaluate(MapSettingsStore(mapOf("haptic_mode" to "enabled"))))
    }

    @Test
    fun `可见性按枚举值求值`() {
        val visibility = SettingVisibility.whenText("haptic_mode", "enabled", "following_system")
        assertTrue(visibility.evaluate(MapSettingsStore(mapOf("haptic_mode" to "enabled"))))
        assertTrue(visibility.evaluate(MapSettingsStore(mapOf("haptic_mode" to "following_system"))))
        assertFalse(visibility.evaluate(MapSettingsStore(mapOf("haptic_mode" to "disabled"))))
        assertFalse(visibility.evaluate(MapSettingsStore()))
    }
}
