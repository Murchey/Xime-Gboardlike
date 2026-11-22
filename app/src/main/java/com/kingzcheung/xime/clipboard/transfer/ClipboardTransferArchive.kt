package com.kingzcheung.xime.clipboard.transfer

import java.io.BufferedOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 剪贴板压缩包的读写：包内一份文本（[ClipboardTransferFormat.TEXT_FILE_NAME]）+ `img/` 下图片。
 *
 * 只做容器格式，不依赖 Android API：[ClipboardTransferManager] 负责选文件与入库，
 * 这里可在 JVM 单测里直接做「写 → 读」往返验证。
 */
object ClipboardTransferArchive {

    /** 文本条目超过上限时抛出，调用方换成给用户看的提示。 */
    class TextTooLargeException : IOException("clipboard text entry too large")

    private val IMAGE_PREFIX = ClipboardTransferFormat.IMAGE_DIR + "/"

    /** zip 文件头判定（不看扩展名/MIME——各文件管理器对 txt 的声明很不一致）。 */
    fun isZipHeader(head: ByteArray): Boolean =
        head.size >= 4 && head[0] == 0x50.toByte() && head[1] == 0x4B.toByte() &&
            head[2] == 0x03.toByte() && head[3] == 0x04.toByte()

    /** 写出压缩包；[images] 的键即 `img/` 下的文件名。 */
    fun write(output: OutputStream, text: String, images: Map<String, File>) {
        ZipOutputStream(BufferedOutputStream(output)).use { zip ->
            zip.putNextEntry(ZipEntry(ClipboardTransferFormat.TEXT_FILE_NAME))
            zip.write(text.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            for ((name, file) in images) {
                zip.putNextEntry(ZipEntry(IMAGE_PREFIX + name))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    /**
     * 读出包内文本：优先 `clipboard.txt`，否则取第一份根目录 `*.txt`；都没有返回 null。
     * @throws TextTooLargeException 文本超过 [maxTextBytes]
     */
    fun readText(input: InputStream, maxTextBytes: Long): String? {
        var fallback: String? = null
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) continue
                val name = entry.name.removePrefix("./")
                if (name.startsWith(IMAGE_PREFIX)) continue
                if (!name.endsWith(".txt", ignoreCase = true)) continue
                if (name == ClipboardTransferFormat.TEXT_FILE_NAME) {
                    return zip.readEntryText(maxTextBytes)
                }
                if (fallback == null) fallback = zip.readEntryText(maxTextBytes)
            }
        }
        return fallback
    }

    /**
     * 按占位符清单（[timestamps]：文件名 → 入库时间戳）读出图片并交给 [sink]。
     *
     * 一张一张流式处理，不整包进内存；单张超过 [maxImageBytes] 的跳过。
     *
     * @return 成功交给 [sink] 且被其接受的图片文件名
     */
    suspend fun readImages(
        input: InputStream,
        timestamps: Map<String, Long>,
        maxImageBytes: Long,
        sink: suspend (name: String, bytes: ByteArray, timestamp: Long) -> Boolean,
    ): Set<String> {
        val imported = mutableSetOf<String>()
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) continue
                val name = entry.name.removePrefix("./")
                if (!name.startsWith(IMAGE_PREFIX)) continue
                val fileName = name.removePrefix(IMAGE_PREFIX)
                val timestamp = timestamps[fileName] ?: continue
                val bytes = zip.readAtMost(maxImageBytes) ?: continue
                if (sink(fileName, bytes, timestamp)) imported += fileName
            }
        }
        return imported
    }

    private fun ZipInputStream.readEntryText(maxBytes: Long): String =
        readAtMost(maxBytes)?.toString(Charsets.UTF_8) ?: throw TextTooLargeException()

    /** 读取至多 [limit] 字节；超过上限返回 null（不关闭流，zip 条目流由调用方继续推进）。 */
    private fun ZipInputStream.readAtMost(limit: Long): ByteArray? {
        val buffer = java.io.ByteArrayOutputStream()
        val chunk = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val read = read(chunk)
            if (read < 0) break
            total += read
            if (total > limit) return null
            buffer.write(chunk, 0, read)
        }
        return buffer.toByteArray()
    }
}
