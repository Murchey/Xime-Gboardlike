package com.kingzcheung.xime.clipboard.transfer

import android.content.Context
import android.net.Uri
import android.util.Log
import com.kingzcheung.xime.R
import com.kingzcheung.xime.clipboard.ClipboardImportItem
import com.kingzcheung.xime.clipboard.ClipboardManager
import com.kingzcheung.xime.i18n.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 剪贴板导出/导入（设置 → 数据与同步 → 剪贴板）。
 *
 * - **纯文本**：历史里没有图片时导出 `.txt`，格式见 [ClipboardTransferFormat]；
 * - **含图片**：导出 `.zip`，包内一份 `clipboard.txt`（图片用 `[[img/<文件名>]]` 占位）
 *   + `img/` 目录下的图片本体（文件名是内容 hash，天然去重）；
 * - **导入**：`.txt` / `.zip` 都从这里进，按**字节魔数**判定是否 zip（不信任扩展名与 MIME，
 *   各家文件管理器对 txt 的 MIME 声明很不一致）；导入的条目按文件中的顺序落成递减时间戳，
 *   与导出时的界面顺序一致。
 *
 * 读写都在 IO 线程；图片一张一张流式处理，不整包读进内存。
 */
object ClipboardTransferManager {

    private const val TAG = "ClipboardTransfer"

    /** 纯文本导入上限：剪贴板历史文本远小于此值，超过基本是选错了文件。 */
    internal const val MAX_TEXT_BYTES = 16L * 1024 * 1024

    /** 压缩包内单张图片上限，与同步导入的绝对上限（单张可配范围的最大值）一致。 */
    internal const val MAX_IMAGE_BYTES = 20L * 1024 * 1024

    /** 导出结果。 */
    data class ExportSummary(
        val entryCount: Int,
        val imageCount: Int,
        val zip: Boolean,
    )

    /** 导入结果；[missingImageCount] 是文件里提到、但没有图片数据可恢复的条数。 */
    data class ImportSummary(
        val textCount: Int,
        val imageCount: Int,
        val missingImageCount: Int,
    )

    /** 预填的导出文件名：`剪贴板-20261122-1320.txt` / `.zip`。 */
    fun exportFileName(zip: Boolean, now: Date = Date()): String {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(now)
        return "剪贴板-$stamp" + if (zip) ".zip" else ".txt"
    }

    /** 是否有图片需要打包（决定建议的文件名与 MIME）；要查文件是否存在，放 IO 线程。 */
    suspend fun hasExportableImages(context: Context): Boolean = withContext(Dispatchers.IO) {
        ClipboardManager.getInstance(context).hasExportableImages()
    }

    /**
     * 导出到文件选择器选定的位置。
     *
     * 实际格式由快照决定（有图片 → zip），调用方只需要按 [hasExportableImages] 选择文件名。
     */
    suspend fun exportTo(context: Context, uri: Uri): Result<ExportSummary> = runCatching {
        withContext(Dispatchers.IO) {
            val manager = ClipboardManager.getInstance(context)
            val items = manager.exportSnapshot()
            val imageFiles = LinkedHashMap<String, File>()
            val entries = ArrayList<ClipboardTransferEntry>(items.size)
            for (item in items) {
                if (item.isImage) {
                    val file = manager.imageFileOf(item)
                    if (file == null) {
                        Log.w(TAG, "Export: image file missing for entry ${item.id}, skipped")
                        continue
                    }
                    imageFiles[file.name] = file
                    entries += ClipboardTransferEntry.Image(file.name, item.isPinned)
                } else if (item.text.isNotBlank()) {
                    entries += ClipboardTransferEntry.Text(item.text, item.isPinned)
                }
            }
            if (entries.isEmpty()) throw IOException(tr(R.string.s_jiantiebanweikong))

            val zip = imageFiles.isNotEmpty()
            val text = ClipboardTransferFormat.BOM + ClipboardTransferFormat.encode(entries)
            val output = openOutput(context, uri)
            output.use { stream ->
                if (zip) {
                    ClipboardTransferArchive.write(stream, text, imageFiles)
                } else {
                    stream.write(text.toByteArray(Charsets.UTF_8))
                }
            }
            Log.d(TAG, "Exported ${entries.size} entries (zip=$zip, images=${imageFiles.size})")
            ExportSummary(entryCount = entries.size, imageCount = imageFiles.size, zip = zip)
        }
    }

    /**
     * 从文件选择器选定的文件导入。
     *
     * 只接受本应用导出的 txt/zip 与普通 txt；普通 txt（没有分隔行）整份作为一条记录。
     */
    suspend fun importFrom(context: Context, uri: Uri): Result<ImportSummary> = runCatching {
        withContext(Dispatchers.IO) {
            if (looksLikeZip(context, uri)) importZip(context, uri) else importPlainText(context, uri)
        }
    }

    // ── 导入 ──

    private suspend fun importPlainText(context: Context, uri: Uri): ImportSummary {
        val bytes = openInput(context, uri).use { it.readAtMost(MAX_TEXT_BYTES) }
            ?: throw IOException(tooLargeMessage())
        val entries = ClipboardTransferFormat.decode(bytes.toString(Charsets.UTF_8))
        if (entries.isEmpty()) throw IOException(tr(R.string.s_jiantieban_wenjianweikong))
        val now = System.currentTimeMillis()
        val textCount = importTexts(context, entries, now)
        val missing = entries.count { it is ClipboardTransferEntry.Image }
        return ImportSummary(textCount = textCount, imageCount = 0, missingImageCount = missing)
    }

    private suspend fun importZip(context: Context, uri: Uri): ImportSummary {
        val text = try {
            openInput(context, uri).use { ClipboardTransferArchive.readText(it, MAX_TEXT_BYTES) }
        } catch (e: ClipboardTransferArchive.TextTooLargeException) {
            throw IOException(tooLargeMessage())
        } ?: throw IOException(tr(R.string.s_jiantieban_yasuobaowenben))
        val entries = ClipboardTransferFormat.decode(text)
        if (entries.isEmpty()) throw IOException(tr(R.string.s_jiantieban_wenjianweikong))
        val now = System.currentTimeMillis()
        val textCount = importTexts(context, entries, now)
        // 占位符位置 → 时间戳：与文件顺序一致（越靠前越新）
        val imageTimestamps = entries.mapIndexedNotNull { index, entry ->
            (entry as? ClipboardTransferEntry.Image)?.let { it.fileName to (now - index) }
        }.toMap()
        val manager = ClipboardManager.getInstance(context)
        val imported = if (imageTimestamps.isEmpty()) {
            emptySet()
        } else {
            openInput(context, uri).use { input ->
                ClipboardTransferArchive.readImages(input, imageTimestamps, MAX_IMAGE_BYTES) { name, bytes, timestamp ->
                    val stored = manager.importImage(bytes, name, timestamp) != null
                    if (!stored) Log.w(TAG, "Import: image $name rejected")
                    stored
                }
            }
        }
        val missing = imageTimestamps.keys.count { it !in imported }
        Log.d(TAG, "Imported ${textCount} texts, ${imported.size} images, $missing missing")
        return ImportSummary(textCount = textCount, imageCount = imported.size, missingImageCount = missing)
    }

    /** 文本条目入库（图片由 [importZip] 单独处理）。时间戳递减以还原文件顺序。 */
    private suspend fun importTexts(
        context: Context,
        entries: List<ClipboardTransferEntry>,
        now: Long,
    ): Int {
        val items = entries.mapIndexedNotNull { index, entry ->
            (entry as? ClipboardTransferEntry.Text)
                ?.let { ClipboardImportItem(text = it.text, timestamp = now - index, pinned = it.pinned) }
        }
        if (items.isEmpty()) return 0
        return ClipboardManager.getInstance(context).importTextEntries(items)
    }

    // ── 文件访问 ──

    /** 判定是否为 zip：看字节魔数（`PK\x03\x04`），不看扩展名/MIME。 */
    private fun looksLikeZip(context: Context, uri: Uri): Boolean =
        openInput(context, uri).use { input ->
            val head = ByteArray(4)
            var read = 0
            while (read < head.size) {
                val n = input.read(head, read, head.size - read)
                if (n <= 0) break
                read += n
            }
            ClipboardTransferArchive.isZipHeader(head)
        }

    private fun openInput(context: Context, uri: Uri): InputStream =
        context.contentResolver.openInputStream(uri)
            ?: throw IOException(tr(R.string.s_wufaduqusuoxuanwenjian))

    /**
     * 打开写入流。优先 `wt`（截断）——覆盖同名文件时旧内容不能残留；
     * 少数 provider 不认 `wt`，退回默认模式。
     */
    private fun openOutput(context: Context, uri: Uri): java.io.OutputStream {
        val resolver = context.contentResolver
        val opened = runCatching { resolver.openOutputStream(uri, "wt") }.getOrNull()
        return opened ?: resolver.openOutputStream(uri)
            ?: throw IOException(tr(R.string.s_wufaxierusuoxuanwenjian))
    }

    private fun tooLargeMessage(): String =
        tr(R.string.s_jiantieban_wenjianguoda, MAX_TEXT_BYTES / 1024 / 1024)

    /**
     * 读取至多 [limit] 字节；超过上限返回 null。
     * 手写而不用 `readNBytes`：后者要 API 33。
     */
    private fun InputStream.readAtMost(limit: Long): ByteArray? {
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
