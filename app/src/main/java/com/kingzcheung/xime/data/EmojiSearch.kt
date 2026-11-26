package com.kingzcheung.xime.data

import java.util.Locale

object EmojiSearch {
    private val emojiKeywordGroups = listOf(
        "笑 开心 快乐 高兴 微笑 smile smiling happy laugh joy" to
            "😀 😃 😄 😁 😆 😅 🤣 😂 🙂 🙃 😉 😊 😇 ☺️ 😸 😹 🥳",
        "哭 伤心 难过 流泪 cry crying sad tears" to "🥲 🥹 😢 😭 😥 😰 😿 😞 😔 🙁 ☹️",
        "爱 喜欢 心 爱心 love heart kiss" to
            "🥰 😍 😘 😗 😚 😙 😻 💌 💘 💝 💖 💗 💓 💞 💕 💟 ❣️ 💔 ❤️‍🔥 ❤️‍🩹 ❤️ 🩷 🧡 💛 💚 💙 🩵 💜 🤎 🖤 🩶 🤍 💋",
        "猫 小猫 cat kitten" to "🐱 🐈 🐈‍⬛ 😺 😸 😹 😻 😼 😽 🙀 😿 😾",
        "狗 小狗 犬 dog puppy" to "🐶 🐕 🦮 🐕‍🦺 🐩 🐾",
        "赞 棒 好的 thumbs up good ok" to "👍 👌 🫶 💯",
        "生气 愤怒 angry mad" to "😤 😡 😠 🤬 💢",
        "生日 蛋糕 庆祝 birthday cake party" to "🎂 🍰 🧁 🎉 🎊 🎈 🎁 🥳",
        "花 鲜花 flower rose" to "💐 🌸 🌹 🌷 🌻 🌼 🌺",
        "太阳 晴天 sun sunny" to "☀️ 🌞 🌅 🌄",
        "睡觉 困 sleep sleepy" to "😴 🥱 😪 💤",
    ).map { (keywords, emojis) -> keywords.split(' ') to emojis.split(' ').map { it.replace("\uFE0F", "") }.toSet() }

    fun search(query: String, categories: List<EmojiCategory>): List<String> {
        val normalized = query.trim().lowercase(Locale.ROOT)
        if (normalized.isEmpty()) return emptyList()
        val tokens = normalized.split(Regex("\\s+")).filter(String::isNotBlank)
        return categories.flatMap { category ->
            category.emojis.filter { emoji ->
                tokens.all { token ->
                    emoji.contains(token) || category.name.contains(token, ignoreCase = true) ||
                        emojiKeywordGroups.any { (keywords, values) ->
                            emoji.replace("\uFE0F", "") in values && keywords.any { it.contains(token) }
                        }
                }
            }
        }.distinct()
    }
}
