package com.kingzcheung.xime.speech

import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 「测试连接」探针：判断 Key 与 Base URL 是否可用，**不产生转写额度**。
 *
 * - OpenAI 兼容类：`GET {base}/models`，只要能列出模型即说明地址与凭据都对；
 * - DashScope：建 WebSocket 并发 `run-task`，看 `task-started` / `task-failed`，
 *   不上传音频，握手完立刻 `finish-task` 关闭。
 *
 * 结果只用于界面提示；判定失败不影响正式识别流程（用户仍可直接使用）。
 */
object AsrConnectionTester {

    /** 探测结果。 */
    sealed interface ProbeResult {
        data object Ok : ProbeResult
        data object Unauthorized : ProbeResult
        data object EndpointNotFound : ProbeResult
        data object NoKey : ProbeResult
        data class Failed(val message: String) : ProbeResult
    }

    private const val TAG = "AsrConnectionTester"

    fun probe(
        preset: AsrPreset,
        apiKey: String,
        baseUrlOverride: String = "",
        client: OkHttpClient = defaultClient(),
    ): ProbeResult {
        if (apiKey.isBlank() && preset.requiresKey) return ProbeResult.NoKey
        return when (preset.protocol) {
            AsrProtocol.DASHSCOPE_WS -> probeDashScope(preset, apiKey, baseUrlOverride)
            else -> probeHttpModels(preset, apiKey, baseUrlOverride, client)
        }
    }

    private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private fun probeHttpModels(
        preset: AsrPreset,
        apiKey: String,
        baseUrlOverride: String,
        client: OkHttpClient,
    ): ProbeResult {
        val base = baseUrlOverride.trim().ifBlank { preset.baseUrl }.trimEnd('/')
        if (base.isBlank()) return ProbeResult.Failed("未填写请求地址")
        val request = Request.Builder().url("$base/models").get()
        AsrProtocolCodec.authHeaders(preset, apiKey).forEach { (k, v) -> request.header(k, v) }
        return try {
            client.newCall(request.build()).execute().use { resp ->
                when {
                    resp.isSuccessful -> ProbeResult.Ok
                    resp.code == 401 || resp.code == 403 -> ProbeResult.Unauthorized
                    resp.code == 404 -> ProbeResult.EndpointNotFound
                    else -> ProbeResult.Failed(
                        AsrProtocolCodec.describeHttpError(resp.code, resp.body?.string().orEmpty())
                    )
                }
            }
        } catch (e: Exception) {
            ProbeResult.Failed(e.message ?: "网络错误")
        }
    }

    /**
     * DashScope：连上后发 run-task，等 task-started（成功）或 task-failed（读错误码）。
     * 10 秒内没有回音按超时处理，避免设置页卡住。
     */
    private fun probeDashScope(
        preset: AsrPreset,
        apiKey: String,
        baseUrlOverride: String,
    ): ProbeResult {
        val endpoint = preset.endpoint(baseUrlOverride)
        if (endpoint.isBlank()) return ProbeResult.Failed("未填写请求地址")
        val latch = java.util.concurrent.CountDownLatch(1)
        val outcome = java.util.concurrent.atomic.AtomicReference<ProbeResult>(
            ProbeResult.Failed("连接超时")
        )
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
        val taskId = java.util.UUID.randomUUID().toString().replace("-", "")
        val request = Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer $apiKey")
            .build()
        val socket = client.newWebSocket(
            request,
            object : okhttp3.WebSocketListener() {
                override fun onOpen(webSocket: okhttp3.WebSocket, response: okhttp3.Response) {
                    webSocket.send(
                        AsrProtocolCodec.runTaskJson(preset.defaultModel, taskId)
                    )
                }

                override fun onMessage(webSocket: okhttp3.WebSocket, text: String) {
                    when (val event = AsrProtocolCodec.parseDashScopeEvent(text)) {
                        is AsrProtocolCodec.DashScopeEvent.Started -> {
                            outcome.set(ProbeResult.Ok)
                            webSocket.send(AsrProtocolCodec.finishTaskJson(taskId))
                            webSocket.close(1000, "probe")
                            latch.countDown()
                        }

                        is AsrProtocolCodec.DashScopeEvent.Failed -> {
                            outcome.set(
                                if (event.code.contains("401") || event.code.contains("InvalidApiKey")) {
                                    ProbeResult.Unauthorized
                                } else {
                                    ProbeResult.Failed("识别失败 [${event.code}]：${event.message}")
                                }
                            )
                            webSocket.close(1000, "probe-failed")
                            latch.countDown()
                        }

                        else -> Unit
                    }
                }

                override fun onFailure(
                    webSocket: okhttp3.WebSocket,
                    t: Throwable,
                    response: okhttp3.Response?,
                ) {
                    val code = response?.code ?: 0
                    outcome.set(
                        when {
                            code == 401 || code == 403 -> ProbeResult.Unauthorized
                            code == 404 -> ProbeResult.EndpointNotFound
                            else -> ProbeResult.Failed(t.message ?: "连接中断")
                        }
                    )
                    latch.countDown()
                }
            },
        )
        latch.await(12, TimeUnit.SECONDS)
        runCatching { socket.close(1000, "done") }
        return outcome.get()
    }
}
