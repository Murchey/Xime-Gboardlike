package com.kingzcheung.xime.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AppUpdateCacheTest {
    @get:Rule val temp = TemporaryFolder()

    @Test
    fun `cleans update APKs while preserving unrelated files`() {
        val cache = temp.newFolder("cache")
        val updates = File(cache, "updates").apply { mkdirs() }
        val apk = File(updates, "XimeGboard-v2.apk").apply { writeText("apk") }
        val uppercaseApk = File(updates, "old.APK").apply { writeText("apk") }
        val other = File(updates, "notes.txt").apply { writeText("keep") }
        val outside = File(cache, "other.apk").apply { writeText("keep") }
        val directory = File(updates, "folder.apk").apply { mkdirs() }

        AppUpdateDownloader.cleanCachedApksIn(cache)

        assertFalse(apk.exists())
        assertFalse(uppercaseApk.exists())
        assertTrue(other.exists())
        assertTrue(outside.exists())
        assertTrue(directory.isDirectory)
    }

    @Test
    fun `startup cleanup preserves newer and unrelated packages`() {
        val cache = temp.newFolder("startup")
        val dir = File(cache, "updates").apply { mkdirs() }
        val installed = File(dir, "installed.apk").apply { writeText("x") }
        val newer = File(dir, "newer.apk").apply { writeText("x") }
        val other = File(dir, "other.apk").apply { writeText("x") }
        val invalid = File(dir, "invalid.apk").apply { writeText("x") }
        AppUpdateDownloader.cleanInstalledCachedApksIn(cache, "app", 10) { file ->
            when (file.name) {
                "installed.apk" -> "app" to 10L
                "newer.apk" -> "app" to 11L
                "other.apk" -> "other" to 1L
                else -> null
            }
        }
        assertFalse(installed.exists())
        assertTrue(newer.exists())
        assertTrue(other.exists())
        assertTrue(invalid.exists())
    }

    @Test
    fun `missing update directory is harmless`() {
        AppUpdateDownloader.cleanCachedApksIn(temp.newFolder("empty"))
    }
}
