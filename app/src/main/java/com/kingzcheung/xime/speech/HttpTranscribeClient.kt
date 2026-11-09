package com.kingzcheung.xime.speech

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/**
 * 整段转写客户端（非流式）。
 *
 * 覆盖两种 OpenAI 兼容形态：
 * - [AsrProtocol.OPENAI_TRANSCRIPTIONS]：multipart `file` + `model`（OpenAI / MiniMax / 自建反代）
 * - [AsrProtocol.OPENAI_CHAT_AUDIO]：`chat/completions` + `input_audio` 内容块（MiMo）
 *
 * 说话结束才发请求，因此这里不做流式；结果是同步返回的完整文本。
 */
internal class HttpTranscribeClient(
    private val client: OkHttpClient = defaultClient(),
) {

    companion object {
        private const val SAMPLE_RATE = 16000

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            // 整段转写：长音频需要较长读超时，60s 覆盖 10 分钟以内的语音
            .readTimeout(60, TimeUnit.SECONDS)
            .build()

        /** PCM16 单声道 → WAV（44 字节头）。 */
        fun pcm16ToWav(pcm: ByteArray, sampleRate: Int = SAMPLE_RATE): ByteArray {
            val out = ByteArrayOutputStream()
            val dataLen = pcm.size
            val byteRate = sampleRate * 2
            fun le32(v: Int) = byteArrayOf(
                (v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte(),
                ((v shr 16) and 0xFF).toByte(), ((v shr 24) and 0xFF).toByte(),
            )
            fun le16(v: Int) = byteArrayOf((v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte())
            out.write("RIFF".toByteArray()); out.write(le32(36 + dataLen))
            out.write("WAVE".toByteArray()); out.write("fmt ".toByteArray())
            out.write(le32(16)); out.write(le16(1)); out.write(le16(1))
            out.write(le32(sampleRate)); out.write(le32(byteRate))
            out.write(le16(2)); out.write(le16(16))
            out.write("data".toByteArray()); out.write(le32(dataLen)); out.write(pcm)
            return out.toByteArray()
        }
    }

    /** 转写结果：成功带文本，失败带可展示的错误串。 */
    sealed interface Result {
        data class Success(val text: String) : Result
        data class Failure(val message: String) : Result
    }

    fun transcribe(
        preset: AsrPreset,
        pcm: ByteArray,
        model: String,
        apiKey: String,
        baseUrlOverride: String = "",
        language: String = "",
    ): Result {
        if (pcm.isEmpty()) return Result.Failure("没有采集到音频")
        val endpoint = preset.endpoint(baseUrlOverride)
        if (endpoint.isBlank()) return Result.Failure("未填写请求地址")
        val wav = pcm16ToWav(pcm)
        val request = when (preset.protocol) {
            AsrProtocol.OPENAI_CHAT_AUDIO -> chatAudioRequest(preset, endpoint, wav, model, apiKey, language)
            else -> multipartRequest(preset, endpoint, wav, model, apiKey, language)
        }
        return try {
            client.newCall(request).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return Result.Failure(AsrProtocolCodec.describeHttpError(resp.code, body))
                }
                val text = AsrProtocolCodec.parseTranscript(body, preset.protocol)
                if (text.isNullOrBlank()) Result.Failure("未识别到内容") else Result.Success(text)
            }
        } catch (e: Exception) {
            Result.Failure(e.message ?: "转写请求失败")
        }
    }

    private fun multipartRequest(
        preset: AsrPreset,
        endpoint: String,
        wav: ByteArray,
        model: String,
        apiKey: String,
        language: String,
    ): Request {
        val builder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", "audio.wav", wav.toRequestBody("audio/wav".toMediaType()))
            .addFormDataPart("model", model)
        if (language.isNotBlank()) builder.addFormDataPart("language", language)
        val req = Request.Builder().url(endpoint).post(builder.build())
        AsrProtocolCodec.authHeaders(preset, apiKey).forEach { (k, v) -> req.header(k, v) }
        return req.build()
    }

    private fun chatAudioRequest(
        preset: AsrPreset,
        endpoint: String,
        wav: ByteArray,
        model: String,
        apiKey: String,
        language: String,
    ): Request {
        val base64 = android.util.Base64.encodeToString(wav, android.util.Base64.NO_WRAP)
        val json = JSONObject().apply {
            put("model", model)
            put(
                "messages",
                JSONArray().put(
                    JSONObject().apply {
                        put("role", "user")
                        put(
                            "content",
                            JSONArray().put(
                                JSONObject().apply {
                                    put("type", "input_audio")
                                    put(
                                        "input_audio",
                                        JSONObject().put("data", "data:audio/wav;base64,$base64"),
                                    )
                                },
                            ),
                        )
                    },
                ),
            )
            if (language.isNotBlank()) {
                put("asr_options", JSONObject().put("language", language))
            }
            put("stream", false)
        }
        val req = Request.Builder()
            .url(endpoint)
            .post(json.toString().toRequestBody("application/json".toMediaType()))
        AsrProtocolCodec.authHeaders(preset, apiKey).forEach { (k, v) -> req.header(k, v) }
        return req.build()
    }
}
