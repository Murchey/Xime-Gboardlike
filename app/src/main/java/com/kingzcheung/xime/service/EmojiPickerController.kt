package com.kingzcheung.xime.service

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import android.view.inputmethod.EditorInfo
import com.kingzcheung.xime.ui.emoji.EmojiPickerActivity

/** 只在原编辑器重新连接后提交独立窗口的结果。 */
internal class EmojiPickerController(private val service: XimeInputMethodService) {
    private var target: Target? = null
    private var pending: Bundle? = null
    private var generation = 0
    private var deliveryRequest = 0
    private var deadline = 0L
    private val handler = Handler(Looper.getMainLooper())
    private data class Target(val packageName: String, val fieldId: Int, val fieldName: String?, val inputType: Int) {
        fun matches(info: EditorInfo) = packageName == info.packageName && fieldId == info.fieldId &&
            fieldName == info.fieldName && inputType == info.inputType
    }
    private var disposed = false

    fun observeEditor(info: EditorInfo?) {
        val origin = target ?: return
        if (info?.privateImeOptions?.contains(SEARCH_EDITOR) == true ||
            (info?.packageName == service.packageName && info.inputType == 0)
        ) return
        if (info != null && !origin.matches(info)) {
            target = null
            pending = null
        }
    }

    fun dispose() {
        disposed = true
        generation++
        deliveryRequest++
        target = null
        pending = null
        handler.removeCallbacksAndMessages(null)
    }

    fun open() {
        if (disposed) return
        val info = service.currentInputEditorInfo ?: return
        if (info.privateImeOptions?.contains(SEARCH_EDITOR) == true) return
        val request = ++generation
        target = Target(info.packageName, info.fieldId, info.fieldName, info.inputType)
        deadline = android.os.SystemClock.elapsedRealtime() + 10 * 60_000
        pending = null
        service.currentInputConnection?.finishComposingText()
        service.keyboardViewModel.closeOverlay()
        val receiver = object : ResultReceiver(handler) {
            override fun onReceiveResult(code: Int, data: Bundle?) {
                if (disposed || request != generation) return
                if (code == 1 && data != null) {
                    pending = data
                    scheduleDelivery(request)
                } else {
                    target = null
                }
            }
        }
        try {
            service.startActivity(Intent(service, EmojiPickerActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra("receiver", receiver)
                putExtra("dark", (service.uiState.value.darkMode == 1 || (service.uiState.value.darkMode == 2 && service.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES)))
            })
        } catch (error: Exception) {
            target = null
            android.util.Log.w("EmojiPicker", "无法打开表情窗口", error)
        }
    }

    private fun scheduleDelivery(request: Int) {
        val attempt = ++deliveryRequest
        handler.postDelayed({
            if (disposed || request != generation || attempt != deliveryRequest) return@postDelayed
            val info = service.currentInputEditorInfo
            val origin = target ?: return@postDelayed
            if (info != null && !origin.matches(info) &&
                info.privateImeOptions?.contains(SEARCH_EDITOR) != true
            ) {
                return@postDelayed
            }
            deliver(info)
            if (pending != null && android.os.SystemClock.elapsedRealtime() <= deadline) {
                scheduleDelivery(request)
            }
        }, 120)
    }

    fun deliver(info: EditorInfo?) {
        val origin = target ?: return
        val result = pending ?: return
        if (android.os.SystemClock.elapsedRealtime() > deadline) { target = null; pending = null; return }
        if (info == null || info.privateImeOptions?.contains(SEARCH_EDITOR) == true) return
        if (!origin.matches(info)) return
        if (service.currentInputConnection == null) return
        pending = null
        target = null
        deliveryRequest++
        when {
            result.containsKey("image") -> {
                val image = result.getString("image") ?: return
                if (!service.textCommit.commitImage(image)) {
                    val copied = service.clipboardManager.copyImageToSystemClipboard(image)
                    android.widget.Toast.makeText(
                        service,
                        if (copied) com.kingzcheung.xime.R.string.s_yifuzhibiaoqing_kezhantiefasong else com.kingzcheung.xime.R.string.s_fuzhishibai,
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
                }
            }
            result.getBoolean("delete") -> service.keyRouter.handleKeyPress("delete", false)
            else -> service.commitPastedText(result.getString("text") ?: return)
        }
    }

    companion object { const val SEARCH_EDITOR = "xime.emoji.search" }
}
