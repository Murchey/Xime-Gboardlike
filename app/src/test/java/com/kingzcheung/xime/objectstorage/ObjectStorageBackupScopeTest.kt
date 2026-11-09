package com.kingzcheung.xime.objectstorage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 备份落点与范围的纯函数验证（不依赖真实桶/Context）。 */
class ObjectStorageBackupScopeTest {

    @Test
    fun `默认备份键名不含 advanced 标记且落在 userdict 前缀`() {
        // 默认档：userdict/<ts>.zip
        val key = "XimeGboard/backups/userdict/20261109_120000.zip"
        assertTrue(key.contains("/userdict/"))
        assertFalse(key.contains("/full/"))
        assertEquals("20261109_120000.zip", key.substringAfterLast('/'))
    }

    @Test
    fun `高级备份键名落在 full 前缀`() {
        val key = "XimeGboard/backups/full/20261109_120000.zip"
        assertTrue(key.contains("/full/"))
        assertTrue(ObjectStorageBackupManager.BackupEntry(key, 1, "", true).full)
    }

    @Test
    fun `两档键名不会互相混淆`() {
        val u = "XimeGboard/backups/userdict/a.zip"
        val f = "XimeGboard/backups/full/a.zip"
        assertTrue(u != f)
        assertFalse(ObjectStorageBackupManager.BackupEntry(u, 1, "", false).full)
    }

    @Test
    fun `备份记录显示名取最后一段`() {
        assertEquals(
            "a.zip",
            ObjectStorageBackupManager.BackupEntry("XimeGboard/backups/full/a.zip", 1, "", true).displayName,
        )
    }

    @Test
    fun `根前缀符合用户设计的 XimeGboard 文件夹`() {
        assertEquals("XimeGboard", ObjectStorageClient.ROOT_PREFIX)
        assertTrue("XimeGboard/backups/".startsWith(ObjectStorageClient.ROOT_PREFIX + "/"))
        assertTrue("XimeGboard/index.json".startsWith(ObjectStorageClient.ROOT_PREFIX + "/"))
    }

    @Test
    fun `内容对象路径符合 content 约定`() {
        assertEquals(
            "/XimeGboard/content/txt_abc.json",
            ObjectStorageClient.objectPath("XimeGboard/content/txt_abc.json"),
        )
    }

    @Test
    fun `图片对象路径带 files img 层级`() {
        val p = ObjectStorageClient.objectPath("XimeGboard/content/files/img/deadbeef.png")
        assertTrue(p.contains("/files/img/"))
        assertTrue(p.endsWith("deadbeef.png"))
    }

    @Test
    fun `index 路径固定为根节点 index json`() {
        assertEquals("/XimeGboard/index.json", ObjectStorageClient.objectPath("XimeGboard/index.json"))
    }
}
