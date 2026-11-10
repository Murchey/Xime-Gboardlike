package com.kingzcheung.xime.glide

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val glideDecodeDispatcher = Dispatchers.Default.limitedParallelism(1)

/** Initial pass 采集整条轨迹；预览与最终解码串行在后台执行。 */
fun glideTouchModifier(
    controller: GlideKeyboardController,
    onGlideCode: (String) -> Unit,
    hostToken: String,
    schemaFingerprint: String,
    asciiMode: Boolean = false,
    origin: () -> Offset = { Offset.Zero },
    onTrailChange: (List<GlidePoint>) -> Unit = {},
    onGestureFinished: () -> Unit = {},
    onGlideCaptured: () -> Unit = {},
    onPreview: (String, List<String>) -> Unit = { _, _ -> },
): Modifier = Modifier.pointerInput(controller, schemaFingerprint, asciiMode) {
    coroutineScope {
        val scope = this
        var finalJob: Job? = null
        try {
            awaitEachGesture {
                val down = awaitFirstDown(pass = PointerEventPass.Initial, requireUnconsumed = false)
                if (finalJob?.isActive == true) {
                    finalJob?.cancel()
                    onPreview("", emptyList())
                }
                var previewJob: Job? = null
                var lastPreviewTime = Long.MIN_VALUE
                val id = down.id.value
                var offset = origin()
                controller.onDown(id, down.position.x + offset.x, down.position.y + offset.y,
                    hostToken, schemaFingerprint, asciiMode, down.uptimeMillis)
                onTrailChange(controller.trail)
                // 不消费 DOWN，保留普通点按与原地长按。
                var upgraded = false
                var finished = false
                try {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id.value == id } ?: break
                        val second = event.changes.count { it.pressed } > 1
                        offset = origin()
                        for (sample in change.historical) {
                            controller.onMove(id, sample.position.x + offset.x, sample.position.y + offset.y, second, sample.uptimeMillis)
                        }
                        controller.onMove(id, change.position.x + offset.x, change.position.y + offset.y, second, change.uptimeMillis)
                        if (controller.isGliding && !upgraded) {
                            upgraded = true
                            onGlideCaptured()
                        }
                        onTrailChange(controller.trail)
                        if (!change.pressed) {
                            previewJob?.cancel()
                            if (upgraded) {
                                change.consume()
                                val request = controller.finishRequest(id, hostToken)
                                if (request != null) finalJob = scope.launch {
                                    var applied = false
                                    try {
                                        val result = withContext(glideDecodeDispatcher) { controller.decode(request) }
                                        if (controller.accept(result)) {
                                            if (result.candidates.isNotEmpty()) onGlideCode(result.rawInput)
                                            else onPreview("", emptyList())
                                            applied = true
                                        }
                                    } finally {
                                        if (!applied && controller.isCurrent(request)) {
                                            controller.onCancel()
                                            onPreview("", emptyList())
                                        }
                                    }
                                } else onPreview("", emptyList())
                            } else controller.onUp(id, hostToken)
                            onTrailChange(emptyList())
                            onGestureFinished()
                            finished = true
                            break
                        }
                        if (upgraded) {
                            event.changes.forEach { it.consume() }
                            if (previewJob?.isActive != true &&
                                (lastPreviewTime == Long.MIN_VALUE || change.uptimeMillis - lastPreviewTime >= 100L)) {
                                lastPreviewTime = change.uptimeMillis
                                previewJob = scope.launch {
                                    delay(60L)
                                    val request = controller.previewRequest() ?: return@launch
                                    val result = withContext(glideDecodeDispatcher) { controller.decode(request) }
                                    if (controller.accept(result)) {
                                    if (result.candidates.isNotEmpty()) onPreview(result.rawInput, result.candidates.map { it.text })
                                    else if (result.request.prefix.isNotEmpty()) onPreview(result.rawInput, emptyList())
                                }
                                }
                            }
                        }
                    }
                } finally {
                    previewJob?.cancel()
                    if (!finished) {
                        controller.onCancel()
                        onPreview("", emptyList())
                        onTrailChange(emptyList())
                        onGestureFinished()
                    }
                }
            }
        } finally {
            if (controller.isGliding || finalJob?.isActive == true) {
                finalJob?.cancel()
                controller.onCancel()
                onPreview("", emptyList())
                onGestureFinished()
            }
        }
    }
}
