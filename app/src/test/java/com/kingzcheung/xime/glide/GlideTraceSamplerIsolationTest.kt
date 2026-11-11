package com.kingzcheung.xime.glide

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 记录一个容易被误判的事实：GlideTraceSampler 及其数据结构在**生产路径上没有调用方**。
 *
 * 解码器 GlideGeometryDecoder 用的是自己的 resample/dwellPositions 实现；
 * GlideTraceSampler 的 downsample / resampleByArc / resampleByKeyRun /
 * extractFeatures / visitedCodes 只被 GlideTraceTest 使用。
 *
 * 这个测试不修改行为，只是把"改这里不会影响准确率"变成可验证的事实，
 * 避免后续把时间花在调这些参数上（它们不参与解码）。
 */
class GlideTraceSamplerIsolationTest {

    @Test
    fun 解码器不使用GlideTraceSampler() {
        val decoderSrc = File("src/main/java/com/kingzcheung/xime/glide/GlideGeometryDecoder.kt")
        if (!decoderSrc.isFile) { println("[isolation] 源码路径不可用，跳过"); return }
        val text = decoderSrc.readText()
        val hits = listOf("GlideTraceSampler", "resampleByArc", "resampleByKeyRun", "extractFeatures")
            .filter { text.contains(it) }
        assertTrue(
            "解码器不应依赖 GlideTraceSampler，实际引用: " + hits,
            hits.isEmpty(),
        )
    }

    @Test
    fun 解码器使用自己的重采样实现() {
        val decoderSrc = File("src/main/java/com/kingzcheung/xime/glide/GlideGeometryDecoder.kt")
        if (!decoderSrc.isFile) { println("[isolation] 源码路径不可用，跳过"); return }
        val text = decoderSrc.readText()
        assertTrue("解码器应有自己的 resample", text.contains("private fun resample("))
        assertTrue("解码器应有自己的 dwellPositions", text.contains("private fun dwellPositions("))
    }
}
