package com.kingzcheung.xime.ui.emoji

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 表情面板的输入态与搜索态。
 *
 * 面板渲染在输入法窗口内（不是独立 Activity），窗口里没有宿主 InputConnection，
 * 也不能放 EditText——窗口内的 EditText 会抢走输入连接，把宿主输入框挤成不可见。
 * 查询词只能由键盘按键注入，见 [com.kingzcheung.xime.service.ImeKeyRouter]。
 *
 * 两种形态：
 * - 浏览态（[searchMode] = false）：面板半屏展开，按键照常进宿主输入框；
 * - 搜索态（[searchMode] = true）：面板收成键盘上方的一条，按键改由搜索框消费。
 *
 * 这样按键目标始终唯一：浏览态是宿主，搜索态是搜索框，不存在两边抢焦点。
 */
object EmojiSearchInput {
    /** 当前查询词。 */
    var query by mutableStateOf("")

    /** 面板是否展开。 */
    var panelOpen by mutableStateOf(false)

    /** 搜索态：面板收成一条，按键进搜索框。 */
    var searchMode by mutableStateOf(false)

    /** 面板关闭时整体复位。 */
    fun reset() {
        query = ""
        panelOpen = false
        searchMode = false
    }

    /** 打开面板：从半屏浏览态开始，不直接进搜索。 */
    fun open() {
        query = ""
        panelOpen = true
        searchMode = false
    }

    /** 点搜索条进入搜索态；面板收成一条，按键转向搜索框。 */
    fun enterSearch() {
        searchMode = true
    }

    /** 退出搜索态：清词并回到半屏浏览态。 */
    fun exitSearch() {
        query = ""
        searchMode = false
    }

    /**
     * 消费一次按键。返回 true 表示已由搜索框处理，调用方不得再走宿主上屏。
     * 只在搜索态生效；多字符功能键（symbol / number / ime_switch 等）不拦截，
     * 交由布局原有语义处理。
     */
    fun handleKey(key: String, isShifted: Boolean): Boolean {
        if (!searchMode) return false
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
