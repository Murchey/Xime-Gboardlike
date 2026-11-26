package com.kingzcheung.xime.ui.emoji

import org.junit.Assert.assertEquals
import org.junit.Test

class EmojiPickerIndexTest {
    @Test
    fun emptyRecentRetainsAllCategoryHeadings() {
        assertEquals(listOf(0, 2, 6, 7), EmojiPickerIndex.sectionStarts(listOf(3, 0, 2), recentCount = 0))
    }

    @Test
    fun recentItemCountShiftsEveryFollowingCategory() {
        assertEquals(listOf(0, 10, 14), EmojiPickerIndex.sectionStarts(listOf(3, 2), recentCount = 9))
    }

    @Test
    fun pluginCountsIncludeEmptyPlaceholdersWithoutRecentOffset() {
        assertEquals(
            listOf(0, 4, 6, 8),
            EmojiPickerIndex.sectionStarts(listOf(3, 0, 0, 2), emptyCategoryPlaceholder = true),
        )
    }

    @Test
    fun itemIndexesAreIndependentOfGridRowRounding() {
        assertEquals(listOf(0, 10, 12), EmojiPickerIndex.sectionStarts(listOf(9, 1, 8)))
    }

    @Test
    fun scrollPositionSelectsHeadingAndContentInBothDirections() {
        val starts = listOf(0, 4, 6, 9)
        val positions = listOf(0, 3, 4, 5, 6, 8, 9, 100, 5, 0)
        assertEquals(listOf(0, 0, 1, 1, 2, 2, 3, 3, 1, 0), positions.map {
            EmojiPickerIndex.selectedSection(starts, it)
        })
    }

    @Test
    fun emptyPageHasNoHeadingsAndSelectionStaysSafe() {
        assertEquals(emptyList<Int>(), EmojiPickerIndex.sectionStarts(emptyList()))
        assertEquals(0, EmojiPickerIndex.selectedSection(emptyList(), 0))
    }
}
