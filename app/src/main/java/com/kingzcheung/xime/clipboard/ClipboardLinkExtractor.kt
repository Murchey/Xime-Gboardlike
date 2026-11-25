package com.kingzcheung.xime.clipboard

import java.net.URI

/** 从分享文本中提取可直接粘贴的 HTTP(S) 链接。 */
object ClipboardLinkExtractor {
    private val pattern = Regex("(?i)https?://[^\\s<>\"'\u3000\uff0c\u3002\uff01\uff1f\uff1b\uff09\u3011\u300b]+")

    fun extract(text: String): List<String> = pattern.findAll(text).mapNotNull { match ->
        var value = match.value.trimEnd('.', ',', ';', '!', '?', ':')
        while (value.endsWith(')') && value.count { it == ')' } > value.count { it == '(' }) {
            value = value.dropLast(1)
        }
        val uri = runCatching { URI(value) }.getOrNull()
        value.takeIf { uri?.host != null && uri.userInfo == null }
    }.distinct().toList()

    fun preview(item: ClipboardItem): String {
        val text = if (item.isExtractedLink) "↗ " + item.text.substringAfter("://") else item.text
        val limit = if (item.isExtractedLink) 32 else 8
        return text.take(limit) + if (text.length > limit) "…" else ""
    }

    fun candidates(items: List<ClipboardItem>, enabled: Boolean = true): List<ClipboardItem> = if (!enabled) items else items.flatMap { item ->
        if (item.isImage) listOf(item) else {
            val links = extract(item.text).filter { it != item.text.trim() }
            listOf(item) + links.take(5).map { item.copy(text = it, isExtractedLink = true) }
        }
    }
}
