package com.kingzcheung.xime.glide

import org.junit.Assert.assertEquals
import org.junit.Test

/** 音节数分流：短词走几何、长词才跑神经解码。 */
class GlideSyllableCountTest {

    @Test
    fun countsVowelGroups() {
        assertEquals(0, glideSyllableCount(""))
        assertEquals(0, glideSyllableCount("bpm"))
        assertEquals(1, glideSyllableCount("shi"))
        assertEquals(1, glideSyllableCount("xian"))
        assertEquals(2, glideSyllableCount("nihao"))
        assertEquals(2, glideSyllableCount("zhongguo"))
    }

    @Test
    fun longPhraseCrossesNeuralThreshold() {
        // 超过三个字才用神经解码
        val three = glideSyllableCount("jintiantian")
        val four = glideSyllableCount("jintiantianqi")
        assertEquals(3, three)
        assertEquals(4, four)
        org.junit.Assert.assertTrue(three < 4)
        org.junit.Assert.assertTrue(four >= 4)
    }
}
