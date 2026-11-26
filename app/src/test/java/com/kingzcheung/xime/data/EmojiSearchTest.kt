package com.kingzcheung.xime.data

import org.junit.Assert.assertEquals
import org.junit.Test

class EmojiSearchTest {
    private val categories = listOf(
        category("表情", "😀", "😂", "❤️‍🔥", "❤️", "☹️"),
        category("动物", "🐈‍⬛️", "🐕‍🦺", "🐶"),
        category("Cute Pets", "🐱"),
    )

    @Test
    fun `中文和英文关键词匹配笑爱猫狗`() {
        assertEquals(listOf("😀", "😂"), EmojiSearch.search("笑", categories))
        assertEquals(listOf("😀", "😂"), EmojiSearch.search("smile", categories))
        assertEquals(listOf("❤️‍🔥", "❤️"), EmojiSearch.search("爱", categories))
        assertEquals(listOf("❤️‍🔥", "❤️"), EmojiSearch.search("LOVE", categories))
        assertEquals(listOf("🐈‍⬛️", "🐱"), EmojiSearch.search("猫", categories))
        assertEquals(listOf("🐈‍⬛️", "🐱"), EmojiSearch.search("CAT", categories))
        assertEquals(listOf("🐕‍🦺", "🐶"), EmojiSearch.search("狗", categories))
        assertEquals(listOf("🐕‍🦺", "🐶"), EmojiSearch.search("dog", categories))
    }

    @Test
    fun `空白和无匹配查询返回空列表`() {
        assertEquals(emptyList<String>(), EmojiSearch.search("  	 ", categories))
        assertEquals(emptyList<String>(), EmojiSearch.search("不存在的关键词", categories))
    }

    @Test
    fun `大小写不敏感且多词需全部匹配`() {
        assertEquals(listOf("🐱"), EmojiSearch.search("cUtE pET", categories))
        assertEquals(emptyList<String>(), EmojiSearch.search("cute missing", categories))
    }

    @Test
    fun `匹配后保留完整ZWJ与VS16字符`() {
        assertEquals(listOf("🐈‍⬛️", "🐱"), EmojiSearch.search("kitten", categories))
        assertEquals(listOf("❤️‍🔥", "❤️"), EmojiSearch.search("heart", categories))
        assertEquals(listOf("🐕‍🦺", "🐶"), EmojiSearch.search("puppy", categories))
        assertEquals(listOf("☹️"), EmojiSearch.search("☹", categories))
    }

    @Test
    fun `跨分类重复表情只返回一次`() {
        val duplicateCategories = listOf(
            category("表情", "😀"),
            category("笑脸", "😀"),
        )
        assertEquals(listOf("😀"), EmojiSearch.search("happy", duplicateCategories))
    }

    @Test
    fun `内置词典按完整表情索引并保留变体`() {
        val dictionary = EmojiSearch.parseDictionary(sequenceOf(
            "舞者\t舞者 🧑‍🩰 🕺", "疲惫\t疲惫 😩", "火心\t火心 ❤️‍🔥",
            "黑猫\t黑猫 🐈‍⬛", "无效行",
        ))
        val entries = listOf(category("表情", "🧑‍🩰", "🕺", "😩", "❤️", "❤️‍🔥", "🐈‍⬛️"))
        assertEquals(listOf("🧑‍🩰", "🕺"), EmojiSearch.search("舞者", entries, dictionary))
        assertEquals(listOf("😩"), EmojiSearch.search("疲惫", entries, dictionary))
        assertEquals(listOf("❤️‍🔥"), EmojiSearch.search("火心", entries, dictionary))
        assertEquals(listOf("🐈‍⬛️"), EmojiSearch.search("黑猫", entries, dictionary))
    }

    private fun category(name: String, vararg emojis: String) = EmojiCategory(
        name = name,
        icon = "",
        emojis = emojis.toList(),
    )
}
