package com.kingzcheung.xime.settings

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun `backup dirs are deletable only when the custom link is valid`() {
        val root = Files.createTempDirectory("xime-files").toFile()
        try {
            val backup = File(root, "rime_default_backup_123").apply { mkdirs() }
            File(backup, "base.dict.yaml").writeText("x")
            File(root, "models").mkdirs()
            File(root, "not_a_backup").mkdirs()

            val doomed = RimeStorage.backupDirsToDelete(root, linkValid = true)
            assertEquals(listOf(backup), doomed)

            assertTrue(RimeStorage.backupDirsToDelete(root, linkValid = false).isEmpty())
            assertTrue(RimeStorage.backupDirsToDelete(File(root, "missing"), linkValid = true).isEmpty())
        } finally {
            root.deleteRecursively()
        }
    }
}
