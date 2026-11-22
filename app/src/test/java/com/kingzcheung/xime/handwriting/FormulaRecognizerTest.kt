package com.kingzcheung.xime.handwriting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 公式手写预处理/反记号化的纯逻辑测试（不加载 ONNX 模型）。
 */
class FormulaRecognizerTest {

    private val meta = FormulaRecognizer.Meta(
        segLen = 16,
        segFeat = 8,
        maxPoints = 1024,
        maxTokens = 200,
        minStep = 0.015f,
        wideRatio = 8f,
        bos = 1,
        eos = 2,
    )

    @Test
    fun `反记号化在命令后补空格`() {
        val table = listOf("<pad>", "<bos>", "<eos>", "\\alpha", "x", "+", "1")
        assertEquals("\\alpha x+1", FormulaRecognizer.detokenize(listOf(3, 4, 5, 6), table))
    }

    @Test
    fun `反记号化跳过控制记号与越界 id`() {
        val table = listOf("<pad>", "<bos>", "<eos>", "1", "+", "2")
        assertEquals("1+2", FormulaRecognizer.detokenize(listOf(0, 3, 4, 5, 99), table))
    }

    @Test
    fun `点太少时特征化为空`() {
        assertEquals(null, FormulaRecognizer.featurize(listOf(listOf(1f to 1f)), meta))
    }

    @Test
    fun `单笔水平线特征化并切段`() {
        val strokes = listOf((0..40).map { it.toFloat() to 10f })
        val feats = FormulaRecognizer.featurize(strokes, meta)
        assertTrue(feats != null && feats!!.size == 1)
        val segs = FormulaRecognizer.segments(feats!!, meta)
        assertTrue(segs.isNotEmpty())
        // 段内点坐标相对段中心（|x| 偏移不超过半段宽）
        val seg = segs.first()
        assertTrue(seg.feat.size == meta.segFeat)
        assertTrue(seg.points.size <= meta.segLen)
    }

    @Test
    fun `点数超上限时逐步放宽点距`() {
        // 400 个等距点：默认 0.015 点距会超 1024 上限吗？这里直接构造超限场景：
        // 画 40 条独立短笔画，每条 40 点 = 1600 点，序列化成 40 段，放宽点距后才放得下
        val strokes = (0 until 40).map { s -> (0..40).map { i -> (i.toFloat() + s * 50f) to (s * 3f).toFloat() } }
        val feats = FormulaRecognizer.featurize(strokes, meta)
        assertTrue(feats != null)
        assertTrue(feats!!.sumOf { it.size } <= meta.maxPoints)
    }

    @Test
    fun `分隔符包裹公式`() {
        assertEquals("\$\$x+1\$\$", FormulaDelimiter.DOLLAR.wrap("x+1"))
        assertEquals("", FormulaDelimiter.DOLLAR.wrap(""))
        assertEquals("x+1", FormulaDelimiter.NONE.wrap("x+1"))
        assertEquals("\\[x+1\\]", FormulaDelimiter.BRACKET.wrap("x+1"))
        assertEquals(FormulaDelimiter.DOLLAR, FormulaDelimiter.of(null))
        assertEquals(FormulaDelimiter.PAREN, FormulaDelimiter.of("paren"))
    }

    @Test
    fun `模型清单包含四个文件且总量约 32MB`() {
        assertEquals(4, FormulaModelDownloader.FILES.size)
        val total = FormulaModelDownloader.totalBytes()
        assertTrue(total in 32_000_000..35_000_000)
        assertEquals(
            "formula_encoder.onnx",
            FormulaModelDownloader.FILES.first().first,
        )
    }
}
