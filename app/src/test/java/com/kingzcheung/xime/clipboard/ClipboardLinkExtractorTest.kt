package com.kingzcheung.xime.clipboard

import org.junit.Assert.*
import org.junit.Test

class ClipboardLinkExtractorTest {
    @Test fun extractsDomainFollowedImmediatelyByChineseText() {
        assertEquals(listOf("https://www.baidu.com"),
            ClipboardLinkExtractor.extract("https://www.baidu.com我说的"))
        assertEquals(listOf("https://example.com/中文?内容=完整#片段"),
            ClipboardLinkExtractor.extract("https://example.com/中文?内容=完整#片段"))
    }

    @Test fun extractsLinksPreservingQueryAndFragment() {
        assertEquals(listOf("https://example.com/a?x=1&y=2#part", "http://other.org/path"),
            ClipboardLinkExtractor.extract("分享 https://example.com/a?x=1&y=2#part，另一个 http://other.org/path。"))
    }
    @Test fun removesSurroundingPunctuationAndDuplicates() {
        assertEquals(listOf("https://example.com/a_(b)"), ClipboardLinkExtractor.extract(
            "(https://example.com/a_(b)), https://example.com/a_(b)。"))
    }
    @Test fun ignoresNonWebTextAndMalformedUrls() {
        assertTrue(ClipboardLinkExtractor.extract("hello user@example.com ftp://example.com https:// https://user:pass@example.com").isEmpty())
    }
    @Test fun preservesSourceIdentityAndOriginalText() {
        val original = ClipboardItem(id = 42, text = "分享：https://example.com/long?query=完整#片段", timestamp = 123)
        val result = ClipboardLinkExtractor.candidates(listOf(original))
        assertEquals(original, result.first())
        assertEquals(2, result.size)
        assertEquals(42L, result[1].id)
        assertEquals(123L, result[1].timestamp)
        assertEquals("https://example.com/long?query=完整#片段", result[1].text)
        assertTrue(result[1].isExtractedLink)
        assertTrue(ClipboardLinkExtractor.preview(result[1]).startsWith("↗ example.com"))
    }
    @Test fun singleUrlAndImagesAreNotDuplicated() {
        val url = ClipboardItem(text = "https://example.com")
        val image = ClipboardItem(text = "", type = "image", id = 7)
        assertEquals(listOf(url, image), ClipboardLinkExtractor.candidates(listOf(url, image)))
    }
    @Test fun disabledExtractionPreservesOriginalItems() {
        val original = ClipboardItem(id = 7, text = "分享 https://example.com")
        assertEquals(listOf(original), ClipboardLinkExtractor.candidates(listOf(original), enabled = false))
    }
    @Test fun scansFullTextAndBoundsDerivedCandidates() {
        val item = ClipboardItem(text = "长文本".repeat(100) + (1..10).joinToString(" ") { "https://example.com/$it" })
        assertEquals(6, ClipboardLinkExtractor.candidates(listOf(item)).size)
    }
}
