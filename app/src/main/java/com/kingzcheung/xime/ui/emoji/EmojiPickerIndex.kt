package com.kingzcheung.xime.ui.emoji

internal object EmojiPickerIndex {
    /** 每组包含一个全宽标题，最近使用与插件空分类还包含一个空态项。 */
    fun sectionStarts(
        categoryItemCounts: List<Int>,
        recentCount: Int? = null,
        emptyCategoryPlaceholder: Boolean = false,
    ): List<Int> = buildList {
        var index = 0
        if (recentCount != null) {
            add(index)
            index += 1 + recentCount.coerceAtLeast(1)
        }
        categoryItemCounts.forEach { count ->
            add(index)
            index += 1 + if (emptyCategoryPlaceholder) count.coerceAtLeast(1) else count
        }
    }

    fun selectedSection(sectionStarts: List<Int>, firstVisibleItem: Int): Int =
        sectionStarts.indexOfLast { it <= firstVisibleItem }.coerceAtLeast(0)
}
