package com.kingzcheung.xime.clipboard.transfer

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 剪贴板压缩包往返测试（纯 JVM）：写 zip → 读回文本与图片，并校验上限与容错。
 */
class ClipboardTransferArchiveTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun imageFile(name: String, bytes: ByteArray): File =
        File(tempFolder.root, name).apply { writeBytes(bytes) }

    private fun zipBytes(text: String, images: Map<String, File> = emptyMap()): ByteArray {
        val output = ByteArrayOutputStream()
        ClipboardTransferArchive.write(output, text, images)
        return output.toByteArray()
    }

    @Test
    fun `压缩包内的文本与图片可原样读回`() = runBlocking {
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 1, 2, 3)
        val entries = listOf(
            ClipboardTransferEntry.Text("你好\n世界", pinned = true),
            ClipboardTransferEntry.Image("ab12.png"),
        )
        val text = ClipboardTransferFormat.BOM + ClipboardTransferFormat.encode(entries)
        val zip = zipBytes(text, mapOf("ab12.png" to imageFile("ab12.png", png)))

        val readText = ByteArrayInputStream(zip).use {
            ClipboardTransferArchive.readText(it, 1024 * 1024)
        }
        assertEquals(entries, ClipboardTransferFormat.decode(readText.orEmpty()))

        val seen = mutableListOf<Triple<String, ByteArray, Long>>()
        val imported = ByteArrayInputStream(zip).use { input ->
            ClipboardTransferArchive.readImages(input, mapOf("ab12.png" to 1700000000000L), 1024 * 1024) { name, bytes, ts ->
                seen += Triple(name, bytes, ts)
                true
            }
        }
        assertEquals(setOf("ab12.png"), imported)
        assertEquals(1, seen.size)
        assertEquals("ab12.png", seen[0].first)
        assertArrayEquals(png, seen[0].second)
        assertEquals(1700000000000L, seen[0].third)
    }

    @Test
    fun `压缩包只含文本时不读图片`() = runBlocking {
        val entries = listOf(ClipboardTransferEntry.Text("只有文本"))
        val zip = zipBytes(ClipboardTransferFormat.encode(entries))
        val readText = ByteArrayInputStream(zip).use { ClipboardTransferArchive.readText(it, 1024 * 1024) }
        assertEquals(entries, ClipboardTransferFormat.decode(readText.orEmpty()))
        val imported = ByteArrayInputStream(zip).use { input ->
            ClipboardTransferArchive.readImages(input, emptyMap(), 1024 * 1024) { _, _, _ -> true }
        }
        assertTrue(imported.isEmpty())
    }

    @Test
    fun `没有文本条目时返回 null`() {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("img/x.png"))
            zip.write(byteArrayOf(1, 2, 3))
            zip.closeEntry()
        }
        val zip = output.toByteArray()
        val readText = ByteArrayInputStream(zip).use { ClipboardTransferArchive.readText(it, 1024 * 1024) }
        assertNull(readText)
    }

    @Test(expected = ClipboardTransferArchive.TextTooLargeException::class)
    fun `文本条目超过上限时抛异常`() {
        val zip = zipBytes("x".repeat(64 * 1024))
        ByteArrayInputStream(zip).use { ClipboardTransferArchive.readText(it, 1024) }
    }

    @Test
    fun `图片超过上限时跳过且不影响其余图片`() = runBlocking {
        val big = ByteArray(4096)
        val small = byteArrayOf(1, 2, 3)
        val zip = zipBytes(
            ClipboardTransferFormat.encode(listOf(ClipboardTransferEntry.Image("big.png"))),
            mapOf(
                "big.png" to imageFile("big.png", big),
                "small.png" to imageFile("small.png", small),
            ),
        )
        val imported = ByteArrayInputStream(zip).use { input ->
            ClipboardTransferArchive.readImages(input, mapOf("big.png" to 1L, "small.png" to 2L), 1024) { _, _, _ -> true }
        }
        assertEquals(setOf("small.png"), imported)
    }

    @Test
    fun `zip 文件头判定`() {
        assertTrue(ClipboardTransferArchive.isZipHeader(zipBytes("x").take(4).toByteArray()))
        assertFalse(ClipboardTransferArchive.isZipHeader(byteArrayOf(0x3D, 0x3D, 0x3D, 0x58)))
        assertFalse(ClipboardTransferArchive.isZipHeader(byteArrayOf(0x50)))
    }
}
