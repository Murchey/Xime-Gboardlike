package com.kingzcheung.xime.objectstorage

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ObjectStorageBackupRestoreTest {
    @get:Rule val temp = TemporaryFolder()
    private val snapshot = "# Rime user dictionary\n#@/db_name\tluna_pinyin\n#@/db_type\tuserdb\nni hao\t你好\tc=1 d=1 t=1\n"

    private fun archive(vararg entries: Pair<String, String>): File = temp.newFile().also { file ->
        ZipOutputStream(file.outputStream()).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
    }

    @Test fun validArchivePassesExtractedSnapshotToMergeAndCleansStaging() {
        val staging = File(temp.root, "staging")
        val result = ObjectStorageBackupManager.restoreDictionaryArchive(
            archive("sync/device/luna_pinyin.userdb.txt" to snapshot), staging,
        ) { files ->
            assertEquals(1, files.size)
            assertEquals(snapshot, files.single().readText())
            assertTrue(files.single().name.endsWith(".userdb.txt"))
            Result.success(files.size)
        }
        assertEquals(1, result.getOrThrow())
        assertFalse(staging.exists())
    }

    @Test fun emptyArchiveDoesNotMerge() {
        val result = ObjectStorageBackupManager.restoreDictionaryArchive(archive(), File(temp.root, "empty")) {
            fail("Empty archive must not invoke merge")
            Result.success(0)
        }
        assertTrue(result.isFailure)
    }

    @Test fun codeTableMasqueradingAsSnapshotIsRejectedBeforeMerge() {
        val result = ObjectStorageBackupManager.restoreDictionaryArchive(
            archive("sync/id/luna_pinyin.userdb.txt" to "# Rime user dictionary export\n你好\tni hao\t1"),
            File(temp.root, "invalid"),
        ) {
            fail("Invalid snapshot must not invoke merge")
            Result.success(1)
        }
        assertTrue(result.isFailure)
    }

    @Test fun zeroAndPartialMergeAreFailures() {
        for (count in listOf(0, 1)) {
            val result = ObjectStorageBackupManager.restoreDictionaryArchive(
                archive("sync/a/luna_pinyin.userdb.txt" to snapshot, "sync/b/luna_pinyin.userdb.txt" to snapshot),
                File(temp.root, "partial$count"),
            ) { Result.success(count) }
            assertTrue(result.isFailure)
        }
    }

    @Test fun mergeFailureRetainsCauseAndCleansStaging() {
        val staging = File(temp.root, "failure")
        val failure = IllegalStateException("engine unavailable")
        val result = ObjectStorageBackupManager.restoreDictionaryArchive(
            archive("sync/id/luna_pinyin.userdb.txt" to snapshot), staging,
        ) { Result.failure(failure) }
        assertSame(failure, result.exceptionOrNull())
        assertFalse(staging.exists())
    }

    @Test fun nonZipIsFailure() {
        val file = temp.newFile().apply { writeText("this is not a zip") }
        assertTrue(ObjectStorageBackupManager.restoreDictionaryArchive(file, File(temp.root, "notzip")) {
            fail("Non zip must not merge")
            Result.success(1)
        }.isFailure)
    }

    @Test fun listFailureIsNotAnEmptySuccess() {
        val result = ObjectStorageBackupManager.mapBackupList(ObjectStorageClient.Result.Failure("HTTP 403"))
        assertTrue(result.isFailure)
        assertEquals("HTTP 403", result.exceptionOrNull()?.message)
    }

    @Test fun listingFiltersUnknownTypesAndSortsNewestFirst() {
        val result = ObjectStorageBackupManager.mapBackupList(ObjectStorageClient.Result.Success(listOf(
            ObjectStorageClient.ObjectMeta("XimeGboard/backups/userdict/old.zip", 12, "2026-01-01T00:00:00Z"),
            ObjectStorageClient.ObjectMeta("XimeGboard/backups/unknown/other.zip", 1, "2027-01-01T00:00:00Z"),
            ObjectStorageClient.ObjectMeta("XimeGboard/backups/full/new.zip", 34, "2026-02-01T00:00:00Z"),
        ))).getOrThrow()
        assertEquals(listOf("new.zip", "old.zip"), result.map { it.displayName })
        assertTrue(result.first().full)
        assertFalse(result.last().full)
    }
}
