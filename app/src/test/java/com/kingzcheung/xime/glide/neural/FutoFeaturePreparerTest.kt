package com.kingzcheung.xime.glide.neural

import com.kingzcheung.xime.glide.GlideGeometrySnapshot
import com.kingzcheung.xime.glide.GlideKey
import com.kingzcheung.xime.glide.GlideKeyRect
import com.kingzcheung.xime.glide.GlidePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FutoFeaturePreparerTest {

    private val rows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
    private val offsets = listOf(0f, 54f, 0f)

    private fun geometry(extra: List<GlideKey> = emptyList()): GlideGeometrySnapshot {
        val keys = mutableListOf<GlideKey>()
        rows.forEachIndexed { row, letters ->
            letters.forEachIndexed { col, ch ->
                val left = offsets[row] + col * 108f
                keys += GlideKey(
                    id = "k_" + row + "_" + col,
                    code = ch.toString(),
                    rect = GlideKeyRect(left, row * 166f, left + 108f, row * 166f + 166f),
                    row = row,
                    col = col,
                )
            }
        }
        keys += extra
        return GlideGeometrySnapshot("geom@1", keys, 1080f, 498f)
    }

    private fun points(): List<GlidePoint> = listOf(
        GlidePoint(54f, 83f, 1_000L, 1),
        GlidePoint(590f, 249f, 1_400L, 1),
        GlidePoint(1026f, 415f, 2_000L, 1),
    )

    @Test
    fun normalisesAndResamplesToModelFrame() {
        val prepared = FutoFeaturePreparer.prepare(points(), geometry())
        assertNotNull(prepared)
        val p = prepared!!
        assertEquals(FutoFeaturePreparer.POINTS * 2, p.features.size)
        assertEquals(FutoFeaturePreparer.MAX_KEYS * 2, p.keys.size)
        assertEquals(26, p.slotCodes.size)
        assertEquals(('a'..'z').toList(), p.slotCodes)
        assertEquals(('a'..'z').toList(), p.alphabet)
        assertEquals(26, p.mask.count { it })

        // 标准 10 列 3 行：首点 (50,83) 应落在 FUTO QWERTY 的 (0.05, 0.167) 附近
        assertEquals(0.05f, p.features[0], 0.01f)
        assertEquals(83f / 498f, p.features[FutoFeaturePreparer.POINTS], 0.01f)
        // 末点 (950,415) 对应 (0.95, 0.833)
        assertEquals(0.95f, p.features[FutoFeaturePreparer.POINTS - 1], 0.01f)
        assertEquals(415f / 498f, p.features[FutoFeaturePreparer.POINTS * 2 - 1], 0.01f)
        // 键中心同样在 [0,1]
        for (i in 0 until 26) {
            assertTrue(p.keys[i * 2] in 0f..1f)
            assertTrue(p.keys[i * 2 + 1] in 0f..1f)
        }
    }

    @Test
    fun keepsDuplicatePhysicalKeysButMergesAlphabet() {
        val duplicateQ = GlideKey(
            id = "k_split_q",
            code = "q",
            rect = GlideKeyRect(972f, 0f, 1080f, 166f),
        )
        val prepared = FutoFeaturePreparer.prepare(points(), geometry(listOf(duplicateQ)))
        assertNotNull(prepared)
        val p = prepared!!
        assertEquals(27, p.slotCodes.size)
        assertEquals(27, p.mask.count { it })
        assertEquals(26, p.alphabet.size)
        assertEquals(2, p.slotCodes.count { it == 'q' })
    }

    @Test
    fun rejectsShortOrInvalidInput() {
        assertNull(FutoFeaturePreparer.prepare(listOf(GlidePoint(1f, 1f, 0L, 1)), geometry()))
        assertNull(FutoFeaturePreparer.prepare(points(), GlideGeometrySnapshot("", emptyList(), 0f, 0f)))
    }
}
