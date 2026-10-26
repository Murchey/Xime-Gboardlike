package com.kingzcheung.xime.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class UpgradeTransactionTest {

    private fun tempDir() = createTempDirectory("upgrade-test").toFile()

    @Test
    fun rollbackRestoresOldPackageAndKeepsUserData() {
        val root = tempDir()
        val filesDir = File(root, "files").apply { mkdirs() }
        val rimeDir = File(filesDir, "rime").apply { mkdirs() }
        val backupDir = File(root, "backup").apply { mkdirs() }

        val oldManifest = """{"schemeId":"pkg","files":{"a.schema.yaml":{"sha256":"old"},"old_only.dict.yaml":{"sha256":"old"}}}"""
        val oldRegistry = """{"version":1,"files":{"a.schema.yaml":{"claimedBy":["pkg"]}}}"""
        File(filesDir, ".manifests").mkdirs()
        File(filesDir, ".manifests/pkg.json").writeText(oldManifest)
        File(filesDir, ".registry.json").writeText(oldRegistry)

        File(rimeDir, "a.schema.yaml").writeText("old-a")
        File(rimeDir, "old_only.dict.yaml").writeText("old-only")
        File(backupDir, "rime").mkdirs()
        File(backupDir, "rime/a.schema.yaml").writeText("old-a")
        File(backupDir, "rime/old_only.dict.yaml").writeText("old-only")

        // 模拟升级后：a 被覆盖，新增 new_only，用户数据也出现在新清单
        File(rimeDir, "a.schema.yaml").writeText("new-a")
        File(rimeDir, "new_only.dict.yaml").writeText("new-only")
        File(rimeDir, "custom_phrase.txt").writeText("user-data")
        File(filesDir, ".manifests/pkg.json").writeText(
            """{"schemeId":"pkg","files":{"a.schema.yaml":{},"new_only.dict.yaml":{},"custom_phrase.txt":{}}}"""
        )
        File(filesDir, ".registry.json").writeText("""{"version":1,"files":{}}""")

        val tx = UpgradeTransaction(
            filesDir = filesDir,
            rimeDir = rimeDir,
            packageId = "pkg",
            backupDir = backupDir,
            oldFiles = listOf("a.schema.yaml", "old_only.dict.yaml"),
            oldManifestText = oldManifest,
            oldRegistryText = oldRegistry,
        )

        assertTrue(tx.rollback())
        assertEquals("old-a", File(rimeDir, "a.schema.yaml").readText())
        assertEquals("old-only", File(rimeDir, "old_only.dict.yaml").readText())
        assertFalse(File(rimeDir, "new_only.dict.yaml").exists())
        assertTrue(File(rimeDir, "custom_phrase.txt").exists())
        assertEquals(oldManifest, File(filesDir, ".manifests/pkg.json").readText())
        assertEquals(oldRegistry, File(filesDir, ".registry.json").readText())
        assertFalse(backupDir.exists())
    }

    @Test
    fun commitDropsBackup() {
        val root = tempDir()
        val filesDir = File(root, "files").apply { mkdirs() }
        val rimeDir = File(filesDir, "rime").apply { mkdirs() }
        val backupDir = File(root, "backup").apply { mkdirs() }
        File(backupDir, "rime").mkdirs()
        File(backupDir, "rime/a.schema.yaml").writeText("old")

        val tx = UpgradeTransaction(
            filesDir = filesDir,
            rimeDir = rimeDir,
            packageId = "pkg",
            backupDir = backupDir,
            oldFiles = listOf("a.schema.yaml"),
            oldManifestText = null,
            oldRegistryText = null,
        )
        tx.commit()
        assertFalse(backupDir.exists())
    }
}
