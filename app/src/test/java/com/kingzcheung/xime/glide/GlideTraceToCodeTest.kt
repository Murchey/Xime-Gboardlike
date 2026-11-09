package com.kingzcheung.xime.glide

import org.junit.Assert.*
import org.junit.Test

class GlideTraceToCodeTest {
    private fun geo() = GlideGeometrySnapshot("v1", listOf(
        GlideKey("q","q",GlideKeyRect(0f,0f,100f,100f)),
        GlideKey("w","w",GlideKeyRect(100f,0f,200f,100f)),
        GlideKey("e","e",GlideKeyRect(200f,0f,300f,100f)),
    ),300f,100f)
    private fun p(x:Float,y:Float,t:Long)=GlidePoint(x,y,t,1)

    @Test fun `直线划过qwe得到qwe`() {
        val pts=listOf(p(50f,50f,0),p(90f,50f,16),p(150f,50f,32),p(190f,50f,48),p(250f,50f,64))
        assertEquals("qwe", GlideTraceToCode.toRawInput(pts, geo()))
    }

    @Test fun `同键抖动不重复计码`() {
        val pts=listOf(p(50f,50f,0),p(52f,51f,16),p(55f,50f,32),p(58f,50f,48))
        assertEquals("q", GlideTraceToCode.toRawInput(pts, geo()))
    }

    @Test fun `回摆保留序列不去重为集合`() {
        // q -> w -> q 不是 qwq 被压成 qw
        val pts=listOf(p(50f,50f,0),p(150f,50f,16),p(55f,50f,32))
        assertEquals("qwq", GlideTraceToCode.toRawInput(pts, geo()))
    }

    @Test fun `终点补录防止尾键丢失`() {
        val pts=listOf(p(50f,50f,0),p(150f,50f,16),p(250f,50f,32))
        assertEquals("qwe", GlideTraceToCode.toRawInput(pts, geo()))
    }

    @Test fun `空轨迹或非法几何返回空`() {
        assertEquals("", GlideTraceToCode.toRawInput(emptyList(), geo()))
        assertEquals("", GlideTraceToCode.toRawInput(listOf(p(50f,50f,0)), GlideGeometrySnapshot("v1", emptyList(),100f,100f)))
    }
}
