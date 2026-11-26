package com.kingzcheung.xime.data

import android.content.Context
import java.util.Locale

object EmojiSearch {
    private val emojiKeywordGroups = listOf(
        "笑 开心 快乐 高兴 微笑 smile smiling happy laugh joy" to "😀 😃 😄 😁 😆 😅 🤣 😂 🙂 🙃 😉 😊 😇 ☺️ 😸 😹 🥳",
        "哭 伤心 难过 流泪 cry crying sad tears" to "🥲 🥹 😢 😭 😥 😰 😿 😞 😔 🙁 ☹️",
        "爱 喜欢 心 爱心 love heart kiss" to "🥰 😍 😘 😗 😚 😙 😻 💌 💘 💝 💖 💗 💓 💞 💕 💟 ❣️ 💔 ❤️‍🔥 ❤️‍🩹 ❤️ 🩷 🧡 💛 💚 💙 🩵 💜 🤎 🖤 🩶 🤍 💋",
        "猫 小猫 cat kitten" to "🐱 🐈 🐈‍⬛ 😺 😸 😹 😻 😼 😽 🙀 😿 😾",
        "狗 小狗 犬 dog puppy" to "🐶 🐕 🦮 🐕‍🦺 🐩 🐾",
        "赞 棒 好的 thumbs up good ok" to "👍 👌 🫶 💯",
        "生气 愤怒 angry mad" to "😤 😡 😠 🤬 💢",
        "生日 蛋糕 庆祝 birthday cake party" to "🎂 🍰 🧁 🎉 🎊 🎈 🎁 🥳",
        "花 鲜花 flower rose" to "💐 🌸 🌹 🌷 🌻 🌼 🌺",
        "太阳 晴天 sun sunny" to "☀️ 🌞 🌅 🌄",
        "睡觉 困 sleep sleepy" to "😴 🥱 😪 💤",
    ).map { (keywords, emojis) -> keywords.split(' ') to emojis.split(' ').map(::key).toSet() }

    private val whitespace = Regex("\\s+")
    private val fallbackKeywords: Map<String, Set<String>> = buildMap {
        emojiKeywordGroups.forEach { (keywords, emojis) ->
            emojis.forEach { emoji -> put(emoji, get(emoji).orEmpty() + keywords) }
        }
    }
    @Volatile private var cachedDictionary: Map<String, Set<String>>? = null

    /** 在后台调用，内置方案与表情搜索共用同一份关键词。 */
    @Synchronized
    fun loadDictionary(context: Context): Map<String, Set<String>> {
        cachedDictionary?.let { return it }
        return context.assets.open("rime/opencc/emoji.txt").bufferedReader().use { reader ->
            parseDictionary(reader.lineSequence())
        }.also { cachedDictionary = it }
    }

    internal fun parseDictionary(lines: Sequence<String>): Map<String, Set<String>> {
        val result = mutableMapOf<String, MutableSet<String>>()
        lines.forEach { line ->
            val parts = line.split('\t', limit = 2)
            if (parts.size != 2 || parts[0].isBlank() || parts[0].startsWith('#')) return@forEach
            val keyword = parts[0].trim().lowercase(Locale.ROOT)
            val candidates = parts[1].split(whitespace)
            candidates.drop(1).filter(String::isNotBlank).forEach { emoji ->
                result.getOrPut(key(emoji)) { mutableSetOf() }.add(keyword)
            }
        }
        return result
    }

    fun search(query: String, categories: List<EmojiCategory>, dictionary: Map<String, Set<String>> = emptyMap()): List<String> {
        val normalized = query.trim().lowercase(Locale.ROOT)
        if (normalized.isEmpty()) return emptyList()
        val tokens = normalized.split(whitespace)
        val result = linkedSetOf<String>()
        categories.forEach { category ->
            category.emojis.forEach { emoji ->
                val emojiKey = key(emoji)
                val keywords = fallbackKeywords[emojiKey].orEmpty() + dictionary[emojiKey].orEmpty()
                if (tokens.all { token ->
                        emoji.contains(token) || category.name.contains(token, ignoreCase = true) ||
                            keywords.any { it.contains(token) }
                    }) result.add(emoji)
            }
        }
        return result.toList()
    }

    private fun key(value: String): String = value.replace("\uFE0F", "")
}
