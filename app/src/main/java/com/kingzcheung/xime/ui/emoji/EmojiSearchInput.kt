package com.kingzcheung.xime.ui.emoji

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 表情面板搜索框的唯一状态源。
 *
 * 面板渲染在输入法窗口内（不是独立 Activity），窗口里没有宿主 InputConnection，
 * 也不能放 EditText——窗口内的 EditText 会抢走输入连接，把宿主输入框挤成不可见。
 * 所以查询词只能由键盘按键注入，见 [com.kingzcheung.xime.service.ImeKeyRouter]。
 *
 * 面板打开期间搜索框是**唯一**按键目标：不存在"已聚焦/未聚焦"的中间态。
 * 之前留过一个回车退出输入态的开关，实测会让字母键落进 Rime 组合（候选栏出现
 * 拼音候选却不上屏），焦点在搜索框与宿主输入框之间摇摆。现在只有 [panelOpen]
 * 一个判据，面板关闭即整体复位。
 */
object EmojiSearchInput {
    /** 当前查询词（空串表示浏览态，但按键仍然进搜索框）。 */
    var query by mutableStateOf("")

    /** 面板是否展开：为 true 时键盘按键全部进搜索框，不落到宿主输入框。 */
    var panelOpen by mutableStateOf(false)

    /** 面板打开/关闭时整体复位，避免下次打开残留上次的关键词。 */
    fun reset() {
        query = ""
        panelOpen = false
    }

    /** 面板展开。 */
    fun open() {
        query = ""
        panelOpen = true
    }

    /**
     * 消费一次按键。返回 true 表示已由搜索框处理，调用方不得再走宿主上屏。
     * 多字符功能键（symbol / number / ime_switch 等）不拦截，交由布局原有语义处理。
     */
    fun handleKey(key: String, isShifted: Boolean): Boolean {
        if (!panelOpen) return false
        when (key) {
            "delete" -> if (query.isNotEmpty()) query = query.dropLast(1)
            "enter" -> Unit
            "space" -> query += " "
            else -> {
                if (key.length != 1) return false
                query += if (isShifted) key.uppercase() else key
            }
        }
        return true
    }
}
