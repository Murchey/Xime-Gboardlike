package com.kingzcheung.xime.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RimeDataExporterTest {

    @Test
    fun `导出可读配置与方案`() {
        assertTrue(RimeDataExporter.shouldExport("rime_ice.schema.yaml"))
        assertTrue(RimeDataExporter.shouldExport("double_pinyin_flypy.custom.yaml"))
        assertTrue(RimeDataExporter.shouldExport("cn_dicts/base.dict.yaml"))
        assertTrue(RimeDataExporter.shouldExport("lua/main.lua"))
    }

    @Test
    fun `排除编译产物和用户词库二进制`() {
        assertFalse(RimeDataExporter.shouldExport("build/rime_ice.table.bin"))
        assertFalse(RimeDataExporter.shouldExport("build/rime_ice.prism.bin"))
        assertFalse(RimeDataExporter.shouldExport("rime_ice.userdb/LOG"))
        assertFalse(RimeDataExporter.shouldExport("en_dicts/en.dict.yaml.db"))
        assertFalse(RimeDataExporter.shouldExport(".git/config"))
    }
}
