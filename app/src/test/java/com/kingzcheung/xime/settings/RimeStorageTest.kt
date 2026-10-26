package com.kingzcheung.xime.settings

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RimeStorageTest {

    @Test
    fun `raw document id resolves an existing directory`() {
        val dir = Files.createTempDirectory("xime-rime").toFile()
        try {
            assertEquals(dir.canonicalFile, RimeStorage.pathFromDocumentId("raw:${dir.absolutePath}"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `unsupported document id returns null`() {
        assertNull(RimeStorage.pathFromDocumentId("home:Documents"))
        assertNull(RimeStorage.pathFromDocumentId("raw:/path/that/does/not/exist"))
    }
}
