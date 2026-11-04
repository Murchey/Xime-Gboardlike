package com.kingzcheung.xime.association

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 学习数据管理（搜索 / 多选 / 删除）里不依赖 Android 的部分。
 *
 * 真正的文件读写与引擎分支由真机验证覆盖；这里锁住解析口径与删除语义，
 * 尤其是「删二元组要连带删三元组」这条容易漏掉的规则。
 */
class UserLearningDataTest {

    private fun snapshot(
        bigrams: List<List<String>>,
        trigrams: List<List<String>> = emptyList(),
        recent: List<String> = emptyList(),
    ): JSONObject {
        fun section(rows: List<List<String>>) = JSONArray().also { array ->
            rows.forEach { tokens ->
                array.put(
                    JSONObject()
                        .put("tokens", JSONArray().also { t -> tokens.forEach { t.put(it) } })
                        .put("count", 2)
                )
            }
        }
        return JSONObject()
            .put("bigrams", section(bigrams))
            .put("trigrams", section(trigrams))
            .put("recentInputs", JSONArray().also { a -> recent.forEach { a.put(it) } })
    }

    @Test
    fun parseCollectsBigramsThenTrigrams() {
        val raw = snapshot(
            bigrams = listOf(listOf("你", "好")),
            trigrams = listOf(listOf("你", "好", "吗")),
        )

        val entries = UserLearningData.parse(raw)

        assertEquals(listOf(2, 3), entries.map { it.order })
        assertEquals("你好", entries[0].text)
        assertEquals("你好吗", entries[1].text)
        assertEquals(2, entries[0].count)
    }

    @Test
    fun parseSkipsMalformedRows() {
        val raw = JSONObject()
            .put("bigrams", JSONArray().put(JSONObject().put("tokens", JSONArray().put("单字"))))
            .put("trigrams", "not-an-array")

        assertTrue(UserLearningData.parse(raw).isEmpty())
    }

    @Test
    fun filterMatchesWholeTextAndSingleToken() {
        val entries = listOf(
            UserLearningData.Entry(2, listOf("你", "好"), 3),
            UserLearningData.Entry(3, listOf("我", "们", "走"), 1),
        )

        assertEquals(1, UserLearningData.filter(entries, "你好").size)
        // 只匹配到一个词元也应命中，便于单独搜某个字
        assertEquals(1, UserLearningData.filter(entries, "走").size)
        assertTrue(UserLearningData.filter(entries, "").size == 2)
        assertTrue(UserLearningData.filter(entries, "不存在").isEmpty())
    }

    @Test
    fun removeDeletesOnlySelectedBigram() {
        val raw = snapshot(bigrams = listOf(listOf("你", "好"), listOf("我", "们")))

        val removal = UserLearningData.remove(raw, setOf(UserLearningData.keyOf(listOf("你", "好"))))

        assertEquals(1, removal.removed)
        val left = UserLearningData.parse(removal.json).map { it.text }
        assertEquals(listOf("我们"), left)
    }

    @Test
    fun removeBigramAlsoDropsItsTrigrams() {
        // 删「你好」必须连带删「你好吗」，否则三元组仍会给"好"后面续写
        val raw = snapshot(
            bigrams = listOf(listOf("你", "好")),
            trigrams = listOf(listOf("你", "好", "吗"), listOf("我", "们", "走")),
        )

        val removal = UserLearningData.remove(raw, setOf(UserLearningData.keyOf(listOf("你", "好"))))

        assertEquals(2, removal.removed)
        assertEquals(listOf("我们走"), UserLearningData.parse(removal.json).map { it.text })
    }

    @Test
    fun removeKeepsOtherSectionsUntouched() {
        val raw = snapshot(
            bigrams = listOf(listOf("你", "好")),
            recent = listOf("你", "好"),
        )

        val removal = UserLearningData.remove(raw, setOf(UserLearningData.keyOf(listOf("你", "好"))))

        // recentInputs 是最近输入的滚动窗口，不参与删除，保持原样
        assertEquals(2, removal.json.getJSONArray("recentInputs").length())
    }

    @Test
    fun removeWithUnknownKeyChangesNothing() {
        val raw = snapshot(bigrams = listOf(listOf("你", "好")))

        val removal = UserLearningData.remove(raw, setOf("不存在"))

        assertEquals(0, removal.removed)
        assertEquals(1, UserLearningData.parse(removal.json).size)
    }

    @Test
    fun removeDoesNotMutateInputSnapshot() {
        // 删除失败时调用方要能保留原始数据 ⇒ remove 必须返回新对象
        val raw = snapshot(bigrams = listOf(listOf("你", "好")))

        UserLearningData.remove(raw, setOf(UserLearningData.keyOf(listOf("你", "好"))))

        assertEquals(1, raw.getJSONArray("bigrams").length())
    }

    @Test
    fun entryKeyJoinsTokens() {
        assertEquals("你好", UserLearningData.Entry(2, listOf("你", "好"), 1).key)
        assertEquals("你好吗", UserLearningData.Entry(3, listOf("你", "好", "吗"), 1).key)
    }
}
