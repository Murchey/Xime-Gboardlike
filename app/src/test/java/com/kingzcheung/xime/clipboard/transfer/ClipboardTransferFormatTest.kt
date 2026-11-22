package com.kingzcheung.xime.clipboard.transfer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 剪贴板文本格式（v1）纯逻辑测试：顺序、转义、普通 txt 兜底与占位符安全判定。
 * 不依赖 Android，可在 JVM 直接跑。
 */
class ClipboardTransferFormatTest {

    @Test
    fun `文本与图片条目按顺序往返`() {
        val entries = listOf(
            ClipboardTransferEntry.Text("第一行\n第二行", pinned = true),
            ClipboardTransferEntry.Image("abc123.png"),
            ClipboardTransferEntry.Text("尾随换行\n"),
        )
        assertEquals(entries, ClipboardTransferFormat.decode(ClipboardTransferFormat.encode(entries)))
    }

    @Test
    fun `文本行看起来像标记时也能原样往返`() {
        val tricky = listOf(
            "===XIME-CLIP-ENTRY===",
            "@pin=1",
            "[[img/evil.png]]",
            "\\反斜杠开头",
        ).joinToString("\n")
        val entries = listOf(ClipboardTransferEntry.Text(tricky))
        val encoded = ClipboardTransferFormat.encode(entries)
        // 导出时每行都加了反斜杠前缀，输出里只有开头那一行是裸的分隔行
        assertEquals(1, encoded.lines().count { it == ClipboardTransferFormat.ENTRY_SEPARATOR })
        assertEquals(entries, ClipboardTransferFormat.decode(encoded))
    }

    @Test
    fun `置顶标记还原`() {
        val entries = listOf(
            ClipboardTransferEntry.Text("a", pinned = true),
            ClipboardTransferEntry.Text("b", pinned = false),
        )
        assertEquals(entries, ClipboardTransferFormat.decode(ClipboardTransferFormat.encode(entries)))
    }

    @Test
    fun `CRLF 文本原样往返`() {
        val entries = listOf(ClipboardTransferEntry.Text("第一行\r\n第二行\r\n"))
        assertEquals(entries, ClipboardTransferFormat.decode(ClipboardTransferFormat.encode(entries)))
    }

    @Test
    fun `普通 txt 整份作为一条记录`() {
        val text = ClipboardTransferFormat.decode("\uFEFF随手记\n第二行")
        assertEquals(listOf(ClipboardTransferEntry.Text("随手记\n第二行")), text)
    }

    @Test
    fun `分隔行之前的内容被忽略`() {
        val raw = "# 头部说明\n${ClipboardTransferFormat.ENTRY_SEPARATOR}\n正文\n"
        assertEquals(listOf(ClipboardTransferEntry.Text("正文")), ClipboardTransferFormat.decode(raw))
    }

    @Test
    fun `空文件解析为空列表`() {
        assertTrue(ClipboardTransferFormat.decode("").isEmpty())
        assertTrue(ClipboardTransferFormat.decode("\uFEFF").isEmpty())
    }

    @Test
    fun `不安全的图片文件名按文本处理`() {
        val raw = ClipboardTransferFormat.ENTRY_SEPARATOR + "\n[[img/../evil.png]]\n"
        assertEquals(listOf(ClipboardTransferEntry.Text("[[img/../evil.png]]")), ClipboardTransferFormat.decode(raw))
        assertFalse(ClipboardTransferFormat.isSafeFileName("../evil.png"))
        assertFalse(ClipboardTransferFormat.isSafeFileName("a/b.png"))
        assertFalse(ClipboardTransferFormat.isSafeFileName(""))
        assertTrue(ClipboardTransferFormat.isSafeFileName("ab12cd.png"))
    }

    @Test
    fun `图片占位符文本可被识别`() {
        val raw = ClipboardTransferFormat.ENTRY_SEPARATOR + "\n" +
            ClipboardTransferFormat.imagePlaceholder("ab12.png") + "\n"
        assertEquals(listOf(ClipboardTransferEntry.Image("ab12.png")), ClipboardTransferFormat.decode(raw))
    }
}
