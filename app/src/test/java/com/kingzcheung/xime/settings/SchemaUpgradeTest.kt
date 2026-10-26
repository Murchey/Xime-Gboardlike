package com.kingzcheung.xime.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SchemaUpgradeTest {

    @Test
    fun `升级只删除旧包独有文件`() {
        val old = listOf(
            "rime_ice.schema.yaml",
            "cn_dicts/base.dict.yaml",
            "cn_dicts/tencent.dict.yaml",
        )
        val new = listOf(
            "rime_ice.schema.yaml",
            "cn_dicts/base.dict.yaml",
        )

        assertEquals(
            listOf("cn_dicts/tencent.dict.yaml"),
            SchemaManifestManager.obsoleteFilesOnlyInOld(old, new, emptyList())
        )
    }

    @Test
    fun `用户数据与共享文件不删`() {
        val old = listOf(
            "rime_ice.schema.yaml",
            "rime_ice.userdb/",
            "custom_phrase.txt",
            "symbols.yaml",
        )
        val new = listOf("rime_ice.schema.yaml")
        val preserved = setOf("rime_ice.userdb/", "custom_phrase.txt", "symbols.yaml")

        assertTrue(
            SchemaManifestManager.obsoleteFilesOnlyInOld(old, new, preserved).isEmpty()
        )
    }

    @Test
    fun `重复旧文件去重`() {
        val old = listOf("a.dict.yaml", "a.dict.yaml", "b.dict.yaml")
        assertEquals(
            listOf("a.dict.yaml", "b.dict.yaml"),
            SchemaManifestManager.obsoleteFilesOnlyInOld(old, emptyList(), emptyList())
        )
    }

    @Test
    fun `用户数据判定`() {
        assertTrue(SchemaManifestManager.isUserDataFile("rime_ice.userdb/"))
        assertTrue(SchemaManifestManager.isUserDataFile("rime_ice.custom.yaml"))
        assertTrue(SchemaManifestManager.isUserDataFile("custom_phrase.txt"))
        assertFalse(SchemaManifestManager.isUserDataFile("cn_dicts/base.dict.yaml"))
    }
}
