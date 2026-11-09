package com.kingzcheung.xime.speech

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AsrProtocolCodecTest {

    @Test
    fun `run task 声明 audio asr 任务与模型`() {
        val json = JSONObject(AsrProtocolCodec.runTaskJson("qwen-audio-3.1-asr-flash-streaming", "t1"))
        val header = json.getJSONObject("header")
        assertEquals("run-task", header.getString("action"))
        assertEquals("t1", header.getString("task_id"))
        assertEquals("duplex", header.getString("streaming"))
        val payload = json.getJSONObject("payload")
        assertEquals("audio", payload.getString("task_group"))
        assertEquals("asr", payload.getString("task"))
        assertEquals("recognition", payload.getString("function"))
        assertEquals("qwen-audio-3.1-asr-flash-streaming", payload.getString("model"))
        val params = payload.getJSONObject("parameters")
        assertEquals("pcm", params.getString("format"))
        assertEquals(16000, params.getInt("sample_rate"))
    }

    @Test
    fun `finish task 复用同一个 task_id`() {
        val json = JSONObject(AsrProtocolCodec.finishTaskJson("t9"))
        assertEquals("finish-task", json.getJSONObject("header").getString("action"))
        assertEquals("t9", json.getJSONObject("header").getString("task_id"))
    }

    @Test
    fun `task started 表示可以发送音频`() {
        val event = AsrProtocolCodec.parseDashScopeEvent("""{"header":{"event":"task-started"}}""")
        assertEquals(AsrProtocolCodec.DashScopeEvent.Started, event)
    }

    @Test
    fun `result generated 按 sentence_end 分中间与最终`() {
        val partial = AsrProtocolCodec.parseDashScopeEvent(
            """{"header":{"event":"result-generated"},"payload":{"output":{"sentence":{"text":"你好","sentence_end":false}}}}"""
        )
        assertEquals(AsrProtocolCodec.DashScopeEvent.Partial("你好"), partial)

        val final = AsrProtocolCodec.parseDashScopeEvent(
            """{"header":{"event":"result-generated"},"payload":{"output":{"sentence":{"text":"你好。","sentence_end":true}}}}"""
        )
        assertEquals(AsrProtocolCodec.DashScopeEvent.Final("你好。"), final)
    }

    @Test
    fun `心跳不计入结果`() {
        val event = AsrProtocolCodec.parseDashScopeEvent(
            """{"header":{"event":"result-generated"},"payload":{"output":{"sentence":{"text":"忽略","heartbeat":true}}}}"""
        )
        assertEquals(AsrProtocolCodec.DashScopeEvent.Unknown, event)
    }

    @Test
    fun `task failed 带出错误码与原文`() {
        val event = AsrProtocolCodec.parseDashScopeEvent(
            """{"header":{"event":"task-failed","error_code":"InvalidApiKey","error_message":"Invalid API-key provided."}}"""
        )
        assertEquals(
            AsrProtocolCodec.DashScopeEvent.Failed("InvalidApiKey", "Invalid API-key provided."),
            event,
        )
    }

    @Test
    fun `非法 JSON 不抛异常`() {
        assertEquals(AsrProtocolCodec.DashScopeEvent.Unknown, AsrProtocolCodec.parseDashScopeEvent("not json"))
        assertEquals(AsrProtocolCodec.DashScopeEvent.Unknown, AsrProtocolCodec.parseDashScopeEvent("{}"))
    }

    @Test
    fun `整段转写读取顶层 text`() {
        assertEquals(
            "你好世界",
            AsrProtocolCodec.parseTranscript("""{"text":"你好世界"}""", AsrProtocol.OPENAI_TRANSCRIPTIONS),
        )
    }

    @Test
    fun `对话接口读取 choices 内容`() {
        val body = """{"choices":[{"message":{"role":"assistant","content":"识别结果"}}]}"""
        assertEquals("识别结果", AsrProtocolCodec.parseTranscript(body, AsrProtocol.OPENAI_CHAT_AUDIO))
    }

    @Test
    fun `对话接口也兜底顶层 text`() {
        assertEquals(
            "兜底",
            AsrProtocolCodec.parseTranscript("""{"text":"兜底"}""", AsrProtocol.OPENAI_CHAT_AUDIO),
        )
    }

    @Test
    fun `无文本字段返回 null 而不是空串`() {
        assertNull(AsrProtocolCodec.parseTranscript("{}", AsrProtocol.OPENAI_TRANSCRIPTIONS))
        assertNull(AsrProtocolCodec.parseTranscript("不是 json", AsrProtocol.OPENAI_TRANSCRIPTIONS))
    }

    @Test
    fun `HTTP 错误文案带状态码与建议`() {
        val unauthorized = AsrProtocolCodec.describeHttpError(401, """{"error":"bad key"}""")
        assertTrue(unauthorized.startsWith("HTTP 401"))
        assertTrue(unauthorized.contains("API Key 无效"))
        assertTrue(unauthorized.contains("bad key"))

        assertTrue(AsrProtocolCodec.describeHttpError(404, "").contains("地址或模型不存在"))
        assertTrue(AsrProtocolCodec.describeHttpError(429, "").contains("额度"))
    }

    @Test
    fun `认证头按服务商风格生成`() {
        assertEquals(
            mapOf("Authorization" to "Bearer k1"),
            AsrProtocolCodec.authHeaders(AsrPresetCatalog.OPENAI, "k1"),
        )
        val mimo = AsrProtocolCodec.authHeaders(AsrPresetCatalog.MIMO, "k2")
        assertEquals("k2", mimo["api-key"])
        assertEquals("Bearer k2", mimo["Authorization"])
        assertTrue(AsrProtocolCodec.authHeaders(AsrPresetCatalog.OPENAI, "").isEmpty())
    }
}
