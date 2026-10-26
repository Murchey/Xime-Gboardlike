package com.kingzcheung.xime.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SchemaPresetsTest {

    @Test
    fun `包含雾凇与七个双拼`() {
        assertEquals(8, SchemaPresets.ALL.size)
        assertEquals("rime_ice", SchemaPresets.ALL.first().schemaId)
        assertEquals(7, SchemaPresets.ALL.count { it.schemaId.startsWith("double_pinyin") })
    }

    @Test
    fun `预设 ID 不重复`() {
        val ids = SchemaPresets.ALL.map { it.schemaId }
        assertEquals(ids.size, ids.distinct().size)
        assertTrue(ids.all { it.isNotBlank() })
    }
}
