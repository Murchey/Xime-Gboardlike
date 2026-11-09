package com.kingzcheung.xime.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AsrPresetCatalogTest {

    @Test
    fun `预置 id 唯一`() {
        val ids = AsrPresetCatalog.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `默认服务商是阿里百炼`() {
        assertEquals(AsrPresetCatalog.ALIYUN, AsrPresetCatalog.default)
        assertEquals(AsrPresetCatalog.ALIYUN, AsrPresetCatalog.findOrDefault(""))
        assertEquals(AsrPresetCatalog.ALIYUN, AsrPresetCatalog.findOrDefault("不存在"))
    }

    @Test
    fun `阿里百炼走 inference 流式端点`() {
        val aliyun = AsrPresetCatalog.ALIYUN
        assertEquals(AsrProtocol.DASHSCOPE_WS, aliyun.protocol)
        assertTrue(aliyun.baseUrl.startsWith("wss://"))
        assertTrue(aliyun.baseUrl.endsWith("/inference/"))
        assertEquals(aliyun.baseUrl, aliyun.endpoint())
    }

    @Test
    fun `MiMo 走对话接口并有正确模型名`() {
        val mimo = AsrPresetCatalog.MIMO
        assertEquals(AsrProtocol.OPENAI_CHAT_AUDIO, mimo.protocol)
        assertEquals("https://api.xiaomimimo.com/v1/chat/completions", mimo.endpoint())
        assertEquals("mimo-v2.5-asr", mimo.defaultModel)
        assertEquals(AsrAuthStyle.API_KEY_AND_BEARER, mimo.authStyle)
    }

    @Test
    fun `MiniMax 默认国内站且路径与模型正确`() {
        val minimax = AsrPresetCatalog.MINIMAX
        assertEquals(AsrProtocol.OPENAI_TRANSCRIPTIONS, minimax.protocol)
        assertEquals("https://api.minimaxi.com/v1/speech_to_text", minimax.endpoint())
        assertEquals("asr-1.0", minimax.defaultModel)
        assertTrue(minimax.keyHelp.contains("api.minimax.io"))
    }

    @Test
    fun `OpenAI 走 transcriptions`() {
        val openai = AsrPresetCatalog.OPENAI
        assertEquals("https://api.openai.com/v1/audio/transcriptions", openai.endpoint())
        assertTrue(openai.modelOptions.contains("whisper-1"))
    }

    @Test
    fun `自定义服务商允许无 Key 且地址必须由用户填写`() {
        val custom = AsrPresetCatalog.CUSTOM_OPENAI
        assertFalse(custom.requiresKey)
        assertEquals("", custom.baseUrl)
        assertTrue(custom.modelOptions.isEmpty())
        assertEquals(
            "https://proxy.example.com/audio/transcriptions",
            custom.endpoint("https://proxy.example.com/"),
        )
    }

    @Test
    fun `所有预置的 Base URL 都可编辑`() {
        assertTrue(AsrPresetCatalog.all.all { it.baseUrlEditable })
    }

    @Test
    fun `覆盖地址时忽略多余斜杠`() {
        assertEquals(
            "https://api.openai.com/v1/audio/transcriptions",
            AsrPresetCatalog.OPENAI.endpoint("https://api.openai.com/v1/"),
        )
    }
}
