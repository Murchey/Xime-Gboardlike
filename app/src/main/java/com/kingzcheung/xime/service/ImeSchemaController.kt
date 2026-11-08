package com.kingzcheung.xime.service

import com.kingzcheung.xime.R

import com.kingzcheung.xime.i18n.tr

import android.content.Intent
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.inputmethod.InputConnection
import android.widget.Toast
import com.kingzcheung.xime.MainActivity
import com.kingzcheung.xime.ui.keyboard.FloatingCardGeometry
import com.kingzcheung.xime.ui.keyboard.isHandwritingSchema
import com.kingzcheung.xime.settings.KeysConfigHelper
import com.kingzcheung.xime.settings.SchemaConfigHelper
import com.kingzcheung.xime.settings.SchemaManager
import com.kingzcheung.xime.rime.RimeConfigHelper
import com.kingzcheung.xime.settings.SettingsPreferences
import com.kingzcheung.xime.ui.theme.KeyboardThemes
import com.kingzcheung.xime.util.FileLogger
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 方案管理与输入模式切换。
 *
 * 承载方案切换（switchSchema/applyPageSizeSetting）、部署（reloadConfig/deploy/deploySchema/downloadSchema）、
 * 工具栏编辑动作、键盘高度与浮动模式调整。
 * 中英切换已收敛至 [AsciiModeController]。
 * 共享状态通过 service 引用访问。
 */
/**
 * 轮换目标：已启用方案列表中当前方案的下一个（末尾回到首个）。
 *
 * 当前方案不在列表内（列表过期/刚部署完）时取首个；列表为空返回 null。
 */
internal fun nextSchemaId(schemaIds: List<String>, currentSchemaId: String): String? {
    if (schemaIds.isEmpty()) return null
    val index = schemaIds.indexOf(currentSchemaId)
    return schemaIds[(index + 1) % schemaIds.size]
}

internal class ImeSchemaController(private val service: XimeInputMethodService) {
    internal fun reloadConfig() {
        
        service.mainHandler.post {
            service.requestHideSelf(0)
            android.widget.Toast.makeText(service, tr(R.string.s_fanganbushuzhong), android.widget.Toast.LENGTH_SHORT).show()
        }
        
        // 部署投递到 key-processing 队列：与按键/切换同队列串行执行，
        // 部署期间输入/切换操作排队等待，完成后自动恢复，
        // 不再因 rimeLock 被部署占用而失败或静默丢弃。
        service.keyRouter.postRimeJob {
            try {
                KeysConfigHelper.loadConfig(service)
                // 重新加载配色方案（用户可能在 xime.custom.yaml 中修改了 color_schemes）
                KeyboardThemes.reload(service)
                
                val userDataDir = File(service.filesDir, "rime")
                
                // 清空 build 目录，强制 Rime 全量重新编译
                val buildDir = File(userDataDir, "build")
                if (buildDir.exists()) {
                    buildDir.deleteRecursively()
                }
                
                service.rimeEngine.deploy()
                // 部署后记录 hash 与完成标记，否则下次启动会因 hash 不一致再次全量编译
                RimeConfigHelper.storeDeploymentHash(service)
                SettingsPreferences.setDeploymentDone(service, true)
                // 部署会编译数据目录里的所有 schema.yaml：清掉未启用方案的产物
                RimeConfigHelper.pruneUnusedSchemaArtifacts(service)
                
                // 部署完成后重新加载配置（Rime 可能在部署过程中改写文件）
                KeysConfigHelper.loadConfig(service)
                KeyboardThemes.reload(service)
                
                val availableSchemas = service.rimeEngine.getAvailableSchemas()
                
                val savedSchema = SettingsPreferences.getCurrentSchema(service)
                if (savedSchema in availableSchemas) {
                    applyPageSizeSetting(savedSchema)
                    service.rimeEngine.switchSchema(savedSchema)
                } else {
                    FileLogger.w(XimeInputMethodService.TAG, "Schema $savedSchema not found in available schemas")
                }
                
                // 直接在 key-processing 线程同步读取 name，避免嵌套协程的时序问题
                val currentSchemaId = service.rimeEngine.getCurrentSchema()
                val schemaName = SchemaManager.getSchemaDisplayName(
                    service, currentSchemaId
                ) ?: currentSchemaId

                withContext(Dispatchers.Main) {
                    service.uiState.value = service.uiState.value.copy(
                        schemaName = schemaName,
                        currentSchemaId = currentSchemaId,
                    )
                    service.updateUI()
                    android.widget.Toast.makeText(service, tr(R.string.s_fanganbushuwancheng), android.widget.Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                FileLogger.e(XimeInputMethodService.TAG, "Failed to reload config", e)
            }
        }
    }
    
    private fun deploySchema() {
        try {
            service.rimeEngine.deploy()
            // 部署后记录 hash 与完成标记，避免下次启动再次全量编译
            RimeConfigHelper.storeDeploymentHash(service)
            SettingsPreferences.setDeploymentDone(service, true)
            RimeConfigHelper.pruneUnusedSchemaArtifacts(service)
            val savedSchema = SettingsPreferences.getCurrentSchema(service)
            applyPageSizeSetting(savedSchema)
            service.rimeEngine.switchSchema(savedSchema)
            val currentSchemaId = service.rimeEngine.getCurrentSchema()
            service.uiState.value = service.uiState.value.copy(
                schemaName = SchemaManager.getSchemaDisplayName(service, currentSchemaId) ?: currentSchemaId,
                currentSchemaId = currentSchemaId,
            )
            service.updateUI()
        } catch (e: Exception) {
            FileLogger.e(XimeInputMethodService.TAG, "Failed to deploy schema", e)
        }
    }
    
    internal fun openSettings() {
        try {
            val intent = Intent(service, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            service.startActivity(intent)
        } catch (e: Exception) {
            FileLogger.e(XimeInputMethodService.TAG, "Failed to open settings", e)
        }
    }
    
    private var editSelAnchor = -1
    /** 选择模式下当前活动端点；不能用 getTextBeforeCursor 推断，因为选区建立后它指向较小端点。 */
    private var editSelCaret = -1
    private var verticalSelectionSerial = 0L

    internal fun handleToolbarEditingAction(action: String) {
        val ic = service.currentInputConnection ?: return
        when (action) {
            "select_all" -> ic.performContextMenuAction(android.R.id.selectAll)
            "copy" -> ic.performContextMenuAction(android.R.id.copy)
            "cut" -> ic.performContextMenuAction(android.R.id.cut)
            "paste" -> ic.performContextMenuAction(android.R.id.paste)
            "home" -> ic.setSelection(0, 0)
            "end" -> {
                val before = ic.getTextBeforeCursor(XimeInputMethodService.SAFE_TEXT_LIMIT, 0) ?: ""
                val after = ic.getTextAfterCursor(XimeInputMethodService.SAFE_TEXT_LIMIT, 0) ?: ""
                ic.setSelection(before.length + after.length, before.length + after.length)
            }
            "arrow_up" -> {
                moveCaretOutsideSelection(ic, -1, vertical = true) { moveEditorCaret(ic, -1, true) }
            }
            "arrow_down" -> {
                moveCaretOutsideSelection(ic, 1, vertical = true) { moveEditorCaret(ic, 1, true) }
            }
            "arrow_left" -> {
                moveCaretOutsideSelection(ic, -1, vertical = false) { moveEditorCaret(ic, -1, false) }
            }
            "arrow_right" -> {
                moveCaretOutsideSelection(ic, 1, vertical = false) { moveEditorCaret(ic, 1, false) }
            }

            "select_begin" -> {
                verticalSelectionSerial++
                editSelAnchor = -1
                editSelCaret = -1
            }
            "select_end" -> {
                verticalSelectionSerial++
                // 退出选择模式：折叠选区到活动端点（参考 fcitx5 cancelSelection）
                val sel = readEditorSelection(ic)
                if (sel != null && sel.first != sel.second) {
                    ic.setSelection(sel.second, sel.second)
                }
                editSelAnchor = -1
                editSelCaret = -1
            }
            "select_arrow_left" -> extendSelection(ic, -1)
            "select_arrow_right" -> extendSelection(ic, 1)
            "select_arrow_up" -> {
                extendVerticalSelection(ic, -1)
            }
            "select_arrow_down" -> {
                extendVerticalSelection(ic, 1)
            }
            // 选择态段首/段尾：锚点不动，活动端点跳到文本两端（仿 fcitx5 的 Shift+Home/End）
            "select_move_home" -> extendSelectionToBoundary(ic, toEnd = false)
            "select_move_end" -> extendSelectionToBoundary(ic, toEnd = true)
        }
    }

    /**
     * 普通方向键：先按方向折叠选区，横向到此为止、纵向继续移动一行。
     * 编辑器在选区边界再收到方向键会把选区原地折叠成单点，
     * 表现为「到顶/到底以后选中内容莫名消失」。
     */
    private fun moveCaretOutsideSelection(
        ic: InputConnection,
        direction: Int,
        vertical: Boolean,
        move: () -> Unit
    ) {
        val extracted = readEditorText(ic)
        if (extracted == null || extracted.selectionStart == extracted.selectionEnd) {
            move()
            return
        }
        val collapseTo = EditorCaretLogic.collapseTarget(
            extracted.selectionStart, extracted.selectionEnd, direction
        )
        ic.setSelection(collapseTo, collapseTo)
        if (!vertical) return
        move()
    }

    /**
     * 选择模式下把活动端点跳到整段文本首/尾：锚点不动，扩展选区。
     * 与「home / end」（整段文本首尾）同口径，不做行首语义。
     */
    private fun extendSelectionToBoundary(ic: InputConnection, toEnd: Boolean) {
        val extracted = readEditorText(ic) ?: return
        val anchor = resolveEditAnchor(ic, extracted.selectionStart)
        val target = if (toEnd) extracted.text.length else 0
        applySelectionQuietly(ic, minOf(anchor, target), maxOf(anchor, target))
        editSelCaret = target
    }

    /**
     * 选择模式左右扩展选区。同样只发 Shift+DPAD 组合键，让编辑器自己管理。
     */
    private fun extendSelection(ic: InputConnection, direction: Int) {
        val keyCode = if (direction < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
        sendEditorKey(ic, keyCode, extend = true)
    }

    /** 当前位置是否已经顶到整段文本的首/尾（此时编辑器不会再响应同方向移动）。 */
    private fun isAtVerticalBoundary(ic: InputConnection, position: Int, direction: Int): Boolean {
        val total = readEditorText(ic)?.text?.length ?: return false
        return EditorCaretLogic.isAtBoundary(position, direction, total)
    }

    /** 选择模式下建立选区的一端；未初始化时以编辑器当前折叠位置为准。 */
    private fun resolveEditAnchor(ic: InputConnection, fallback: Int): Int {
        if (editSelAnchor >= 0) return editSelAnchor
        val anchor = readEditorText(ic)?.selectionStart ?: fallback
        editSelAnchor = anchor
        return anchor
    }

    private fun applySelectionQuietly(ic: InputConnection, anchor: Int, caret: Int) {
        ic.beginBatchEdit()
        try {
            ic.setSelection(anchor, caret)
        } finally {
            ic.endBatchEdit()
        }
    }

    private fun readEditorText(ic: InputConnection): android.view.inputmethod.ExtractedText? =
        runCatching {
            ic.getExtractedText(
                android.view.inputmethod.ExtractedTextRequest(),
                InputConnection.GET_EXTRACTED_TEXT_MONITOR,
            )
        }.getOrNull()?.takeIf { it.text != null && it.selectionEnd >= 0 }

    private fun readEditorSelection(ic: InputConnection): Pair<Int, Int>? =
        readEditorText(ic)?.let { it.selectionStart to it.selectionEnd }

    /**
     * 选择模式上下扩展选区。
     *
     * 参考 fcitx5-android 的实现：只发 Shift+DPAD 组合键事件，让编辑器自己
     * 管理选区扩展，不调用 setSelection——之前的「折叠光标→发键→轮询→还原选区」
     * 流程每步都触发视觉更新，导致选区闪动。
     */
    private fun extendVerticalSelection(ic: InputConnection, direction: Int) {
        val keyCode = if (direction < 0) KeyEvent.KEYCODE_DPAD_UP else KeyEvent.KEYCODE_DPAD_DOWN
        sendEditorKey(ic, keyCode, extend = true)
    }

    private fun scheduleVerticalSelectionProbe(
        ic: InputConnection,
        direction: Int,
        current: Int,
        anchor: Int,
        serial: Long,
        attempt: Int,
    ) {
        val delayMs = 16L
        service.mainHandler.postDelayed({
            if (serial != verticalSelectionSerial || editSelAnchor != anchor) return@postDelayed
            runCatching {
                val moved = readEditorText(ic)?.selectionEnd
                if (moved != null && moved != current) {
                    applyVerticalSelection(ic, anchor, moved)
                    reinforceVerticalSelection(ic, anchor, moved, serial, attempt = 0)
                } else if (isAtVerticalBoundary(ic, current, direction)) {
                    // 已经贴到整段文本的首/尾：编辑器不会再移动，别再空转轮询。
                    // 这里也不回退 Shift+DPAD —— 原生扩展语义在边界会把选区整行跳变。
                    // 把折叠光标还原成原选区，边界上选中内容才不会消失。
                    applyVerticalSelection(ic, anchor, current)
                } else if (attempt < 50) {
                    // Flutter 可能在多个消息循环后才回报 selection，继续短轮询。
                    scheduleVerticalSelectionProbe(ic, direction, current, anchor, serial, attempt + 1)
                } else {
                    // 位置没变但不在文本首尾（多行文本框的段落边界 / Flutter 异步回报
                    // 始终不来）：沿用历史行为，最后尝试原生扩展语义。
                    sendEditorKey(
                        ic,
                        if (direction < 0) KeyEvent.KEYCODE_DPAD_UP else KeyEvent.KEYCODE_DPAD_DOWN,
                        extend = true,
                    )
                }
            }
        }, delayMs)
    }

    /**
     * 部分编辑器会在 setSelection 后异步回写一次折叠光标，短暂覆盖刚建立的选区。
     * 在同一轮操作内确认几次，避免 UI 偶尔只显示光标而不显示选中背景。
     */
    private fun applyVerticalSelection(ic: InputConnection, anchor: Int, caret: Int) {
        ic.beginBatchEdit()
        try {
            ic.setSelection(anchor, caret)
        } finally {
            ic.endBatchEdit()
        }
        editSelCaret = caret
    }

    private fun reinforceVerticalSelection(
        ic: InputConnection,
        anchor: Int,
        caret: Int,
        serial: Long,
        attempt: Int,
    ) {
        if (attempt >= 5) return
        service.mainHandler.postDelayed({
            if (serial != verticalSelectionSerial || editSelAnchor != anchor) return@postDelayed
            val selection = readEditorSelection(ic) ?: return@postDelayed
            val isExpected = selection.first == anchor && selection.second == caret ||
                selection.first == caret && selection.second == anchor
            if (!isExpected) {
                applyVerticalSelection(ic, anchor, caret)
            }
            reinforceVerticalSelection(ic, anchor, caret, serial, attempt + 1)
        }, 24L + attempt * 32L)
    }

    private fun moveEditorCaret(ic: InputConnection, direction: Int, vertical: Boolean) {
        if (vertical) {
            // Flutter EditableText 等编辑器的视觉行包含自动折行，InputConnection
            // 无法从文本内容还原像素行，只能使用编辑器自身的 DPAD 上下语义。
            sendEditorKey(
                ic,
                if (direction < 0) KeyEvent.KEYCODE_DPAD_UP else KeyEvent.KEYCODE_DPAD_DOWN,
            )
            return
        }
        val extracted = readEditorText(ic)
        if (extracted != null) {
            val current = extracted.selectionEnd
            val next = (current + direction).coerceIn(0, extracted.text.length)
            ic.setSelection(next, next)
            return
        }
        val keyCode = when {
            vertical && direction < 0 -> KeyEvent.KEYCODE_DPAD_UP
            vertical -> KeyEvent.KEYCODE_DPAD_DOWN
            direction < 0 -> KeyEvent.KEYCODE_DPAD_LEFT
            else -> KeyEvent.KEYCODE_DPAD_RIGHT
        }
        sendEditorKey(ic, keyCode)
    }

    /**
     * 发送方向键到编辑器。[extend] 时发送带真实 Shift 修饰键的组合键事件
     * （参考 fcitx5-android 的 sendCombinationKeyEvents）——只设 metaState 不发
     * Shift 按键事件，部分编辑器（含 Flutter）不会识别为扩展选区。
     */
    private fun sendEditorKey(ic: InputConnection, keyCode: Int, extend: Boolean = false) {
        val t = SystemClock.uptimeMillis()
        val meta = if (extend) {
            KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
        } else 0
        if (extend) {
            ic.sendKeyEvent(
                KeyEvent(t, t, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SHIFT_LEFT, 0,
                    KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON)
            )
        }
        ic.sendKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, keyCode, 0, meta))
        ic.sendKeyEvent(KeyEvent(t, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, keyCode, 0, meta))
        if (extend) {
            ic.sendKeyEvent(
                KeyEvent(t, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, KeyEvent.KEYCODE_SHIFT_LEFT, 0, 0)
            )
        }
    }

    internal fun applyPageSizeSetting(schemaId: String) {
        val userPageSize = SettingsPreferences.getPageSize(service)
        if (userPageSize > 0) {
            service.rimeEngine.setPageSize(schemaId, userPageSize)
        }
    }

    /**
     * 轮换切换到下一个已启用方案（菜单栏方案列表顺序）。
     *
     * 复用 [switchSchema]：中英模式、键盘类型、手写模型校验等行为与在菜单里选方案一致。
     * 只有一个方案（或列表尚未就绪）时不做任何事。
     */
    internal fun cycleSchema() {
        val state = service.uiState.value
        val target = nextSchemaId(state.schemas.map { it.schemaId }, state.currentSchemaId)
        if (target == null || target == state.currentSchemaId) {
            FileLogger.i(
                XimeInputMethodService.TAG,
                "cycleSchema skipped: current=" + state.currentSchemaId +
                    ", enabled=" + state.schemas.joinToString { it.schemaId }
            )
            return
        }
        switchSchema(target)
    }

    internal fun switchSchema(schemaId: String) {
        if (isHandwritingSchema(schemaId)) {
            // 检查手写模型文件是否已下载
            if (!com.kingzcheung.xime.handwriting.HandwritingEngine.hasModel(service)) {
                FileLogger.w(XimeInputMethodService.TAG, "Handwriting model not found, redirecting to download")
                android.widget.Toast.makeText(
                    service, tr(R.string.s_qingxianxiazaishouxiemoxing), android.widget.Toast.LENGTH_LONG
                ).show()
                val intent = android.content.Intent(
                    service, com.kingzcheung.xime.MainActivity::class.java
                ).apply {
                    flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                    putExtra("open_fragment", "model_management")
                }
                service.startActivity(intent)
                return
            }
            service.previousSchemaId = service.rimeEngine.getCurrentSchema()
            SettingsPreferences.setCurrentSchema(service, schemaId)
            service.keyboardViewModel.switchMain(com.kingzcheung.xime.keyboard.MainType.HANDWRITING)
            // 手写模型按"用键盘时加载"管理：切到手写方案即展示手写键盘，
            // 由 HandwritingKeyboardLayout 创建时（LaunchedEffect）负责加载，
            // 此处不重复加载（避免与布局初始化并发双 bind/load）
            service.sessionController.updateSchemaName()
            return
        }
        // 切离手写方案时释放手写引擎
        com.kingzcheung.xime.handwriting.HandwritingEngine.release()
        service.keyboardViewModel.switchMain(com.kingzcheung.xime.keyboard.MainType.FULL)
        try {
            SettingsPreferences.setCurrentSchema(service, schemaId)
            // 用户自定义候选词数：先写 custom.yaml 再切方案，Rime 会自动加载
            applyPageSizeSetting(schemaId)
            // 部署/编译进行中 switchSchema 返回 false（不阻塞等待），
            // 此时不应继续触发其他 native 调用进入编译中的引擎
            if (!service.rimeEngine.switchSchema(schemaId)) {
                if (service.rimeEngine.isMaintaining()) {
                    FileLogger.w(XimeInputMethodService.TAG, "switchSchema skipped: deployment in progress")
                    Toast.makeText(service, tr(R.string.s_cikubushuzhong_qingshaohouzaiq_7d5ce), Toast.LENGTH_SHORT).show()
                    return
                }
                // 引擎切换失败（常见：方案未部署/不在 schema_list，老版本升级残留）。
                // 必须回滚偏好到引擎实际方案：偏好留着目标值会让 UI 状态与引擎
                // 永久脱节（键盘已显示九键但数字键无人处理，候选栏始终 IDLE）。
                // getCurrentSchema 无会话时返回空串——此时不动偏好，保留原值由
                // IME 重启的恢复逻辑兜底，避免把空串写进偏好与 user.yaml
                val actual = service.rimeEngine.getCurrentSchema()
                if (actual.isNotEmpty()) {
                    SettingsPreferences.setCurrentSchema(service, actual)
                }
                FileLogger.w(XimeInputMethodService.TAG, "switchSchema failed: target=$schemaId actual=$actual")
                Toast.makeText(service, tr(R.string.s_fanganweibushu_qingzaifangangu_63cf5), Toast.LENGTH_SHORT).show()
                return
            }
            if (!service.rimeEngine.isAsciiMode()) {
                service.rimeEngine.setOption("ascii_punct", false)
            }
            service.sessionController.updateSchemaName()
            service.updateUI()
            // 确保键盘布局与方案匹配（如 T9 九键不应被 switchMain 重置为全键盘）
            service.keyboardViewModel.dispatch(
                com.kingzcheung.xime.ui.keyboard.KeyboardDispatchAction.AsciiModeChanged(
                    service.rimeEngine.isAsciiMode(), schemaId
                )
            )
            Toast.makeText(service, tr(R.string.s_yiqiehuanshurufangan), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            FileLogger.e(XimeInputMethodService.TAG, "Failed to switch schema", e)
        }
    }
    
    private fun downloadSchema(schemaId: String) {
        service.serviceScope.launch(Dispatchers.IO) {
            service.notifyDeploymentStatus(true, tr(R.string.s_zhengzaixiazai, schemaId))
            
            val success = SchemaConfigHelper.downloadSchema(service, schemaId)
            
            withContext(Dispatchers.Main) {
                if (success) {
                    Toast.makeText(service, tr(R.string.s_xiazaichenggong_qingdianjibushu, schemaId), Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(service, tr(R.string.s_xiazaishibai_2, schemaId), Toast.LENGTH_SHORT).show()
                }
                service.notifyDeploymentStatus(false, "")
            }
        }
    }
    
    private fun deploy() {
        // 部署投递到 key-processing 队列，与输入/切换串行执行，避免持锁饿死输入
        service.keyRouter.postRimeJob {
            // 部署前刷新手势配置和配色方案缓存
            KeysConfigHelper.loadConfig(service)
            KeyboardThemes.reload(service)
            
            service.notifyDeploymentStatus(true, tr(R.string.s_zhengzaibushu))
            // 先补齐内置基础文件（symbols/default 等），避免方案 __include 失败导致编译报错
            RimeConfigHelper.ensureBuiltinBaseFiles(service)

            val success = service.rimeEngine.deploy()
            if (success) {
                // 部署会编译数据目录里的所有 schema.yaml：清掉未启用方案的产物
                RimeConfigHelper.pruneUnusedSchemaArtifacts(service)
            }
            
            withContext(Dispatchers.Main) {
                if (success) {
                    Toast.makeText(service, tr(R.string.s_bushuchenggong), Toast.LENGTH_SHORT).show()
                    service.updateUI()
                } else {
                    Toast.makeText(service, tr(R.string.s_bushushibai), Toast.LENGTH_SHORT).show()
                }
                service.notifyDeploymentStatus(false, "")
            }
        }
    }
    
    private fun updateKeyboardHeightPreview(heightDp: Int) {
        service.keyboardContainer.updateHeight(heightDp)
    }
    
    internal fun setKeyboardHeight(heightDp: Int) {
        val isLandscape = service.resources.configuration.screenWidthDp > service.resources.configuration.screenHeightDp
        SettingsPreferences.setKeyboardHeightDp(service, heightDp, isLandscape)
        service.uiState.value = service.uiState.value.copy(keyboardHeightDp = heightDp)
        // 不弹 Toast：调节界面本身就是实时预览，确定时键盘已经变成目标高度，
        // 再提示"键盘高度已调整"是多余噪音
    }

    internal fun toggleFloatingMode(enabled: Boolean, floatingMinY: Int = 0) {
        if (enabled && service.isHostConstrained()) {
            // 独立 IME 面板：承载区比键盘还小，浮动卡片没有可拖动/可停靠的空间
            android.widget.Toast.makeText(
                service,
                tr(R.string.s_dangqianxianshiquyubuzhichifud_d72e1),
                android.widget.Toast.LENGTH_SHORT
            ).show()
            return
        }
        // 任何路径切悬浮都清掉拖动提示态（正常由拖动松手消费，这里兜底防光效残留）
        service.floatingExitHintState.value = false
        val isLandscape = service.resources.configuration.screenWidthDp > service.resources.configuration.screenHeightDp
        SettingsPreferences.setFloatingMode(service, enabled, isLandscape)
        val loadedX = SettingsPreferences.getFloatingOffsetX(service, isLandscape)
        val screenW = service.resources.configuration.screenWidthDp
        val screenH = service.resources.configuration.screenHeightDp
        val portraitWidth = minOf(screenW, screenH)
        val halfMargin = FloatingCardGeometry.halfMarginDp(screenW, portraitWidth)
        val clampedX = loadedX.coerceIn(-halfMargin, halfMargin)
        service.uiState.value = service.uiState.value.copy(
            isFloatingMode = enabled,
            floatingOffsetX = clampedX,
            // 开启悬浮归位到"拖动可达的最低点"：拖动钳制下界是 floatingMinY（导航栏高），
            // 归位到 0 会停在拖动到不了的位置（表现为比可达范围偏下一截）
            floatingOffsetY = if (enabled) floatingMinY else 0,
        )
        if (enabled) {
            service.closeToolPanel()
            // 清掉上一轮的实测矩形：新卡片矩形未建立前，触摸区/钳制走 FloatingCardGeometry 兜底
            service.floatingCardBounds = null
            service.currentFloatingCardHeightDp = 0
        }
        service.applyWindowBackground()
    }

}
