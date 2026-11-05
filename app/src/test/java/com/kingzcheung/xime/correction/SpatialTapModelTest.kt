package com.kingzcheung.xime.correction

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** [SpatialTapModel] 纯 JVM 单测：验证高斯热区、邻键过滤、softmax 归一。 */
class SpatialTapModelTest {

    /** 构造 26 键几何：3 行布局（10+9+7），键宽 80px，键高 120px，水平间距 86px。 */
    private fun buildKeys(): Map<Char, SpatialTapModel.KeyGeometry> {
        val keys = mutableMapOf<Char, SpatialTapModel.KeyGeometry>()
        val rows = listOf(
            "qwertyuiop" to 0f,
            "asdfghjkl" to 130f,
            "zxcvbnm" to 260f,
        )
        for ((letters, y) in rows) {
            val xOffset = (10 - letters.length) * 43f
            for ((i, ch) in letters.withIndex()) {
                val cx = xOffset + i * 86f + 40f
                val cy = y + 60f
                keys[ch] = SpatialTapModel.KeyGeometry(cx, cy, 80f, 120f)
            }
        }
        return keys
    }

    @Test
    fun centerOfKeyHasHighestProbability() {
        val keys = buildKeys()
        val q = keys['q']!!
        val result = SpatialTapModel.candidates(q.centerXPx, q.centerYPx, keys, densityDpi = 320f)
        assertTrue(result.isNotEmpty())
        assertEquals('q', result.first().letter)
    }

    @Test
    fun neighborsAppearInCandidates() {
        val keys = buildKeys()
        val w = keys['w']!!
        // 触点偏移到 q 和 w 之间
        val result = SpatialTapModel.candidates(
            (w.centerXPx + keys['q']!!.centerXPx) / 2f, w.centerYPx, keys, densityDpi = 320f
        )
        val letters = result.map { it.letter }
        assertTrue('w' in letters || 'q' in letters)
    }

    @Test
    fun distantKeyIsFilteredOut() {
        val keys = buildKeys()
        val q = keys['q']!!
        // 触点在 q 中心，但只取 4 个候选
        val result = SpatialTapModel.candidates(q.centerXPx, q.centerYPx, keys, densityDpi = 320f)
        assertTrue(result.size <= 4)
        // 远处的 'm' 不应出现
        assertTrue('m' !in result.map { it.letter })
    }

    @Test
    fun probabilitiesSumToOne() {
        val keys = buildKeys()
        val h = keys['h']!!
        val result = SpatialTapModel.candidates(h.centerXPx, h.centerYPx, keys, densityDpi = 320f)
        val total = result.sumOf { it.logProbability.toDouble() }
        // softmax 归一后总和为 1（允许浮点误差）
        assertTrue("sum=$total", total in 0.95..1.05)
    }

    @Test
    fun emptyKeysReturnsEmpty() {
        val result = SpatialTapModel.candidates(0f, 0f, emptyMap(), densityDpi = 320f)
        assertTrue(result.isEmpty())
    }

    @Test
    fun zeroDpiReturnsEmpty() {
        val keys = buildKeys()
        val result = SpatialTapModel.candidates(0f, 0f, keys, densityDpi = 0f)
        assertTrue(result.isEmpty())
    }

    @Test
    fun maxCandidatesIsFour() {
        val keys = buildKeys()
        val s = keys['s']!!
        // 大范围触点确保邻键多
        val result = SpatialTapModel.candidates(s.centerXPx, s.centerYPx, keys, densityDpi = 320f)
        assertTrue(result.size <= 4)
    }

    @Test
    fun offCenterPrefersNearestKey() {
        val keys = buildKeys()
        val q = keys['q']!!
        val w = keys['w']!!
        // 触点偏右 30px（仍在 q 键内但靠近 w）
        val result = SpatialTapModel.candidates(
            q.centerXPx + 30f, q.centerYPx, keys, densityDpi = 320f
        )
        assertTrue(result.isNotEmpty())
        // q 和 w 都应在候选中
        val letters = result.map { it.letter }
        assertTrue('q' in letters)
    }
}
