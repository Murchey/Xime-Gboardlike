package com.kingzcheung.xime.settings

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 雾凇拼音内置方案开关必须能被菜单解析（半角/全角、简/繁等）。 */
class RimeIceSwitchConfigTest {

    @Test
    fun `rime_ice switches 可解析`() {
        val switches = SchemaManager.parseSchemaSwitches(
            File("src/main/assets/rime/rime_ice.schema.yaml")
        )
        val names = switches.map { it.name }
        assertTrue("缺少 ascii_punct", "ascii_punct" in names)
        assertTrue("缺少 traditionalization", "traditionalization" in names)
        assertTrue("缺少 full_shape", "full_shape" in names)
        val fullShape = switches.first { it.name == "full_shape" }
        assertTrue(fullShape.states.size >= 2)
    }
}
