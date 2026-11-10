package com.kingzcheung.xime.glide

import org.junit.Assert.*
import org.junit.Test

class GlideTrajectoryDecoderTest {
    private fun geo(): GlideGeometrySnapshot {
        val keys = listOf(
            GlideKey("n","n",GlideKeyRect(0f,0f,100f,100f)),
            GlideKey("i","i",GlideKeyRect(100f,0f,200f,100f)),
            GlideKey("h","h",GlideKeyRect(200f,0f,300f,100f)),
            GlideKey("a","a",GlideKeyRect(300f,0f,400f,100f)),
            GlideKey("o","o",GlideKeyRect(400f,0f,500f,100f)),
        )
        return GlideGeometrySnapshot("v1", keys, 500f, 100f)
    }
    private fun p(x: Float, y: Float, t: Long) = GlidePoint(x,y,t,1)

    @Test fun `直线划过niha得到首选nihao`() {
        val pts = listOf(p(50f,50f,0), p(150f,50f,16), p(250f,50f,32), p(350f,50f,48), p(450f,50f,64))
        val hyps = GlideTrajectoryDecoder.decode(pts, geo())
        assertTrue(hyps.isNotEmpty())
        assertEquals("nihao", hyps.first().code)
    }

    @Test fun `输出多条假设而非唯一解`() {
        val pts = listOf(p(50f,50f,0), p(150f,50f,16), p(250f,50f,32))
        val hyps = GlideTrajectoryDecoder.decode(pts, geo())
        assertTrue("应有多条假设", hyps.size >= 2)
    }

    @Test fun `偏离中心的点仍能纠正到最近键`() {
        // 起点略偏 n 的右下角，仍应以 n 为首选
        val pts = listOf(p(95f,95f,0), p(150f,50f,16))
        val hyps = GlideTrajectoryDecoder.decode(pts, geo())
        assertTrue(hyps.first().code.startsWith("n"))
    }

    @Test fun `空轨迹与非法几何返回空`() {
        assertTrue(GlideTrajectoryDecoder.decode(emptyList(), geo()).isEmpty())
        assertTrue(GlideTrajectoryDecoder.decode(listOf(p(1f,1f,0)),
            GlideGeometrySnapshot("v1", emptyList(), 500f, 100f)).isEmpty())
    }

    @Test fun `假设按分数降序且编码非空`() {
        val pts = listOf(p(50f,50f,0), p(250f,50f,16), p(450f,50f,32))
        val hyps = GlideTrajectoryDecoder.decode(pts, geo())
        for (i in 1 until hyps.size) assertTrue(hyps[i-1].score >= hyps[i].score)
        assertTrue(hyps.all { it.code.isNotEmpty() })
    }

    @Test fun `保守配置下候选键数增加可提升覆盖率`() {
        val pts = listOf(p(100f,50f,0), p(200f,50f,16))  // 正好在键边界
        val one = GlideTrajectoryDecoder.decode(pts, geo(), perPointCandidates = 1)
        val two = GlideTrajectoryDecoder.decode(pts, geo(), perPointCandidates = 3)
        assertTrue(two.size >= one.size)
    }
}
