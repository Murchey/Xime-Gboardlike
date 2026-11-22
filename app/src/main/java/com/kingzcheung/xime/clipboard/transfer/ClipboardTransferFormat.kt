package com.kingzcheung.xime.clipboard.transfer

/**
 * 剪贴板导出的文本格式（v1）——纯文本、可手工编辑、可无损往返。
 *
 * 格式示意（每个条目以分隔行开始，内容按行保存）：
 * ```
 * ===XIME-CLIP-ENTRY===
 * @pin=1
 * 第一行
 * 第二行
 * ===XIME-CLIP-ENTRY===
 * [[img/ab12cd34.png]]
 * ```
 *
 * - 分隔行固定为 [ENTRY_SEPARATOR]，条目按文件顺序还原（顺序即界面顺序：置顶在前、越新越前）；
 * - `@pin=1` 表示置顶，可选；
 * - 图片条目整条只有一个 `[[img/<文件名>]]` 占位符，图片本体在压缩包的 [IMAGE_DIR] 目录下；
 * - 文本行若以分隔行、`@pin=`、`[[img/` 或反斜杠开头，导出时前置一个反斜杠，导入时去掉，
 *   保证任意文本原样往返；
 * - 没有分隔行的普通 txt 视为「整份文件一条记录」（方便导入随手记下的文本）。
 *
 * 纯逻辑、不依赖 Android API，可在 JVM 单测中直接验证。
 */
object ClipboardTransferFormat {

    /** 压缩包内文本条目的文件名；导入时也接受其它 `*.txt`。 */
    const val TEXT_FILE_NAME = "clipboard.txt"

    /** 压缩包内图片目录。 */
    const val IMAGE_DIR = "img"

    /** 条目分隔行。 */
    const val ENTRY_SEPARATOR = "===XIME-CLIP-ENTRY==="

    /** 置顶标记行。 */
    const val PIN_MARKER = "@pin=1"

    /** 导出文本的 BOM：Windows 记事本按 UTF-8 打开中文不串码。 */
    const val BOM = "\uFEFF"

    private const val IMAGE_PREFIX = "[[img/"
    private const val IMAGE_SUFFIX = "]]"
    private const val PIN_PREFIX = "@pin="
    private const val ESCAPE = '\\'

    /** 图片占位符文本（解析侧据此识别图片条目）。 */
    fun imagePlaceholder(fileName: String): String = IMAGE_PREFIX + fileName + IMAGE_SUFFIX

    /** 单个文件名是否可安全地用作压缩包内路径（不接受目录分隔符与 `..`）。 */
    fun isSafeFileName(name: String): Boolean =
        name.isNotBlank() && '/' !in name && '\\' !in name && name != "." && name != ".."

    /** 条目列表 → 文件文本（不含 BOM，由调用方决定是否加）。 */
    fun encode(entries: List<ClipboardTransferEntry>): String = buildString {
        for (entry in entries) {
            append(ENTRY_SEPARATOR).append('\n')
            if (entry.pinned) append(PIN_MARKER).append('\n')
            when (entry) {
                is ClipboardTransferEntry.Text ->
                    entry.text.split('\n').forEach { line ->
                        append(escapeLine(line)).append('\n')
                    }

                is ClipboardTransferEntry.Image ->
                    append(imagePlaceholder(entry.fileName)).append('\n')
            }
        }
    }

    /**
     * 文件文本 → 条目列表。
     *
     * 无分隔行时整份文件作为一条文本记录（不做反转义，文件不是本格式导出的）。
     * 分隔行之前的内容（表头/空行）忽略。
     */
    fun decode(rawText: String): List<ClipboardTransferEntry> {
        val text = rawText.removePrefix(BOM)
        val lines = text.split('\n').let { if (text.endsWith("\n")) it.dropLast(1) else it }
        val separatorIndexes = lines.indices.filter { lines[it].trimEnd('\r') == ENTRY_SEPARATOR }
        if (separatorIndexes.isEmpty()) {
            return if (text.isBlank()) emptyList() else listOf(ClipboardTransferEntry.Text(text))
        }
        val result = mutableListOf<ClipboardTransferEntry>()
        for ((position, start) in separatorIndexes.withIndex()) {
            val end = separatorIndexes.getOrNull(position + 1) ?: lines.size
            parseEntry(lines.subList(start + 1, end))?.let { result.add(it) }
        }
        return result
    }

    /** 解析单个条目块；返回 null 表示空块（只有分隔行/置顶标记）。 */
    private fun parseEntry(rawLines: List<String>): ClipboardTransferEntry? {
        var pinned = false
        var index = 0
        while (index < rawLines.size && rawLines[index].trimEnd('\r') == PIN_MARKER) {
            pinned = true
            index++
        }
        // 条目正文按原样保留（不裁掉 \r）：CRLF 文本导入后仍是 CRLF
        val body = rawLines.subList(index, rawLines.size)
        if (body.isEmpty()) return null
        // 图片条目整条只有一个占位符；先按原文判断，避免被反转义规则误判
        if (body.size == 1) {
            imageFileName(body.first())?.let { return ClipboardTransferEntry.Image(it, pinned) }
        }
        val text = body.joinToString("\n") { unescapeLine(it) }
        return if (text.isEmpty()) null else ClipboardTransferEntry.Text(text, pinned)
    }

    /** 占位符行 → 图片文件名；不是占位符或不安全返回 null（容忍 CRLF 编辑留下的 \r）。 */
    private fun imageFileName(rawLine: String): String? {
        val line = rawLine.removeSuffix("\r")
        if (!line.startsWith(IMAGE_PREFIX) || !line.endsWith(IMAGE_SUFFIX)) return null
        val name = line.removePrefix(IMAGE_PREFIX).removeSuffix(IMAGE_SUFFIX)
        return name.takeIf { isSafeFileName(it) }
    }

    private fun escapeLine(line: String): String =
        if (needsEscape(line)) ESCAPE + line else line

    private fun needsEscape(line: String): Boolean =
        line.startsWith(ENTRY_SEPARATOR) ||
            line.startsWith(PIN_PREFIX) ||
            line.startsWith(IMAGE_PREFIX) ||
            line.startsWith(ESCAPE.toString())

    private fun unescapeLine(line: String): String =
        if (line.startsWith(ESCAPE) && needsEscape(line.drop(1))) line.drop(1) else line
}

/** 导出/导入的中间条目：与数据库行解耦，便于纯 JVM 测试。 */
sealed interface ClipboardTransferEntry {

    /** 是否置顶（导出时写入 `@pin=1`，导入时还原）。 */
    val pinned: Boolean

    /** 一条文本记录。 */
    data class Text(val text: String, override val pinned: Boolean = false) : ClipboardTransferEntry

    /** 一条图片记录；[fileName] 是压缩包 `img/` 下的文件名。 */
    data class Image(val fileName: String, override val pinned: Boolean = false) : ClipboardTransferEntry
}
