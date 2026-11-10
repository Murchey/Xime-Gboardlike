package com.kingzcheung.xime.glide

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

/**
 * 阶段B：把 [GlideKeyboardController] 接到 Compose 的 pointer 事件的 Modifier。
 *
 * 用 PointerEventPass.Initial 先于子键读取；Glide 升级后消费全部 MOVE/UP，
 * 从根上避免 KeyButton 的幽灵点击、上滑符号与补点击；未升级的点按不消费 UP，
 * 交还原点击链路。绘轨迹由调用方在 drawWithContent 完成。
 */
fun glideTouchModifier(
    controller: GlideKeyboardController,
    onGlideCode: (String) -> Unit,
    hostToken: String,
    schemaFingerprint: String,
    /** 当前是否为英文模式：滑动结果随中英文模式变化。 */
    asciiMode: Boolean = false,
    /** 键盘根容器在屏幕中的原点，用于把 pointer 局部坐标转成与几何快照一致的 root 坐标。 */
    origin: () -> Offset = { Offset.Zero },
    /** 轨迹点变化时通知外部重绘（Compose state 由调用方持有）。 */
    onTrailChange: (List<GlidePoint>) -> Unit = {},
): Modifier = Modifier.pointerInput(controller) {
    awaitEachGesture {
        val down = awaitFirstDown(pass = PointerEventPass.Initial, requireUnconsumed = false)
        val id = down.id.value
        var o = origin()
        controller.onDown(id, down.position.x + o.x, down.position.y + o.y, hostToken, schemaFingerprint, asciiMode)
        onTrailChange(controller.trail)
        down.consume()
        var upgraded = false
        var finished = false
        try {
            do {
                val ev = awaitPointerEvent(PointerEventPass.Initial)
                val ch = ev.changes.firstOrNull { it.id.value == id } ?: break
                val second = ev.changes.count { it.pressed } > 1
                if (!ch.pressed) {
                    if (upgraded) {
                        ch.consume()
                        val up = controller.onUp(id, hostToken)
                        if (up is GlideKeyboardController.UpResult.Candidates) {
                            // 候选已由 GlideCompositionSession 管理并投影到候选栏；
                            // 这里不再逐字符送 onKeyPress，避免把整句候选退化成逐键输入。
                            onGlideCode(controller.rawInputForDisplay())
                        }
                    } else {
                        controller.onUp(id, hostToken)
                    }
                    onTrailChange(emptyList())
                    finished = true
                    break
                }
                o = origin()
                controller.onMove(id, ch.position.x + o.x, ch.position.y + o.y, second)
                onTrailChange(controller.trail)
                if (controller.isGliding) upgraded = true
                if (upgraded) ev.changes.forEach { it.consume() }
            } while (true)
        } finally {
            if (!finished) controller.onCancel()
        }
    }
}
