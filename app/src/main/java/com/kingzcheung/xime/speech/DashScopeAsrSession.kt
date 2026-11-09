package com.kingzcheung.xime.speech

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString.Companion.toByteString
import java.util.UUID

/**
 * 阿里百炼 DashScope 双向流式会话。
 *
 * 协议要点（与 plugins/funasr-asr 同源，该插件已在真机验证）：
 * 1. 连上后先发 `run-task`，等 `task-started`；
 * 2. 音频以**二进制帧**发送（不是塞进 JSON 的 base64）；
 * 3. `result-generated` 里取 `payload.output.sentence`，`sentence_end` 区分中间/最终；
 * 4. 结束发 `finish-task`，服务端回 `task-finished`。
 *
 * `task-started` 到达前收到的音频先缓存（[MAX_PREBUFFER] 帧≈30s），到达后按序冲刷，
 * 否则连接建立那几百毫秒内的开头会被丢掉（真机表现为"开头一两个字没识别"）。
 */
internal class DashScopeAsrSession(
    private val endpoint: String,
    private val apiKey: String,
    private val model: String,
    private val sampleRate: Int = 16000,
    private val client: OkHttpClient,
    private val callbacks: Callbacks,
) {

    interface Callbacks {
        fun onReady()
        fun onPartial(text: String)
        fun onFinal(text: String)
        fun onError(message: String)
    }

    companion object {
        private const val TAG = "DashScopeAsrSession"

        /** 连接建立前最多缓存的音频帧数（100ms/帧 → 30s）。 */
        private const val MAX_PREBUFFER = 300
    }

    private val taskId = UUID.randomUUID().toString().replace("-", "")

    @Volatile
    private var socket: WebSocket? = null

    @Volatile
    private var audioReady = false

    /** task-started 前收到的音频帧，按序保存。 */
    private val prebuffer = ArrayDeque<ByteArray>()

    /** task-started 前已请求结束：就绪后补发缓存再发 finish-task。 */
    @Volatile
    private var stopPending = false

    @Volatile
    private var closed = false

    /** 建立连接。返回 false 表示握手都已失败。 */
    fun connect(): Boolean {
        closed = false
        audioReady = false
        prebuffer.clear()
        stopPending = false
        val request = Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer $apiKey")
            .header("X-DashScope-DataInspection", "enable")
            .build()
        return runCatching {
            socket = client.newWebSocket(request, listener)
            true
        }.getOrElse {
            callbacks.onError(it.message ?: "无法建立 WebSocket 连接")
            false
        }
    }

    /** 送入一帧 PCM16 音频。 */
    @Synchronized
    fun feed(pcm: ByteArray) {
        if (closed) return
        if (!audioReady) {
            prebuffer.addLast(pcm)
            while (prebuffer.size > MAX_PREBUFFER) prebuffer.removeFirst()
            return
        }
        sendFrame(pcm)
    }

    /** 音频发送完毕：请求服务端收尾并回吐剩余结果。 */
    @Synchronized
    fun finish() {
        if (closed) return
        if (!audioReady) {
            // task-started 未到：此刻发 finish-task 会排在缓存音频之前（服务端已收尾，音频被丢弃），
            // 记为待发送，等 onMessage 收到 task-started 补发缓存后再发。
            stopPending = true
            return
        }
        socket?.send(AsrProtocolCodec.finishTaskJson(taskId))
    }

    /** 立即断开（取消/释放）。 */
    @Synchronized
    fun close() {
        closed = true
        audioReady = false
        prebuffer.clear()
        stopPending = false
        runCatching { socket?.close(1000, "done") }
        socket = null
    }

    private fun sendFrame(pcm: ByteArray) {
        val sent = runCatching { socket?.send(pcm.toByteString()) == true }.getOrDefault(false)
        if (!sent) Log.w(TAG, "音频帧发送失败（连接可能已断开）")
    }

    @Synchronized
    private fun flushPrebuffer() {
        while (prebuffer.isNotEmpty()) sendFrame(prebuffer.removeFirst())
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            webSocket.send(AsrProtocolCodec.runTaskJson(model, taskId, sampleRate))
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            when (val event = AsrProtocolCodec.parseDashScopeEvent(text)) {
                is AsrProtocolCodec.DashScopeEvent.Started -> {
                    audioReady = true
                    flushPrebuffer()
                    if (stopPending) {
                        stopPending = false
                        webSocket.send(AsrProtocolCodec.finishTaskJson(taskId))
                    }
                    callbacks.onReady()
                }

                is AsrProtocolCodec.DashScopeEvent.Partial -> callbacks.onPartial(event.text)
                is AsrProtocolCodec.DashScopeEvent.Final -> callbacks.onFinal(event.text)
                is AsrProtocolCodec.DashScopeEvent.Finished -> {
                    audioReady = false
                    runCatching { webSocket.close(1000, "finished") }
                }

                is AsrProtocolCodec.DashScopeEvent.Failed ->
                    callbacks.onError("识别失败 [${event.code}]：${event.message}")

                AsrProtocolCodec.DashScopeEvent.Unknown -> Unit
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            audioReady = false
            if (closed) return
            val detail = response?.let { " (HTTP ${it.code})" }.orEmpty()
            callbacks.onError((t.message ?: "连接中断") + detail)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            audioReady = false
        }
    }
}
