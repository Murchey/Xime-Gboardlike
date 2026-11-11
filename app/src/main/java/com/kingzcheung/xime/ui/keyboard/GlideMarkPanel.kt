package com.kingzcheung.xime.ui.keyboard

import com.kingzcheung.xime.R

import com.kingzcheung.xime.i18n.tr

import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.kingzcheung.xime.glide.GlideTraceRecorder
import com.kingzcheung.xime.service.GlideMarkEditTextHolder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val MARK_PANEL_HEIGHT = 232

/**
 * 开发者「滑动标记」面板（候选栏上方，与快捷发送同位置）：
 *
 * - 顶部开关：滑动轨迹采集（与「滑动轨迹数据」设置页同一偏好）
 * - 最近轨迹列表：点一条 → 在上方输入框写下"我实际想打出的内容" → 回车保存标注
 *
 * 轨迹在**抬手时就已落盘**，解码错了也不影响留档；这里补的是真实意图。
 */
@Composable
fun GlideMarkPanel(
    backgroundColor: Color,
    textColor: Color,
    accentColor: Color,
    cardBgColor: Color,
    recording: Boolean,
    onRecordingChange: (Boolean) -> Unit,
    entries: List<GlideTraceRecorder.TraceEntry>,
    selectedId: String?,
    isFocused: Boolean,
    onSelect: (String) -> Unit,
    onFocusChange: (Boolean) -> Unit,
    onClose: () -> Unit,
) {
    val closeButtonBg = androidx.compose.ui.graphics.lerp(
        MaterialTheme.colorScheme.surface,
        MaterialTheme.colorScheme.primary,
        0.35f
    )
    CandidateBarOverlayPanel(
        heightDp = MARK_PANEL_HEIGHT,
        backgroundColor = backgroundColor,
        cardBgColor = cardBgColor,
        closeButtonBg = closeButtonBg,
        closeButtonColor = accentColor,
        title = tr(R.string.s_debug_huadongbiaoj),
        titleColor = textColor,
        onCloseClick = onClose,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = tr(R.string.s_yijilu_tiao_guiji, entries.size),
                    color = textColor,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (recording) tr(R.string.s_jiluzhong) else tr(R.string.s_yitingzhi),
                    color = if (recording) accentColor else textColor.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.width(8.dp))
                Switch(
                    checked = recording,
                    onCheckedChange = onRecordingChange,
                )
            }
            Spacer(Modifier.height(6.dp))

            if (selectedId != null) {
                MarkInput(
                    textColor = textColor,
                    isFocused = isFocused,
                    onFocusChange = onFocusChange,
                )
                Spacer(Modifier.height(6.dp))
            }

            if (entries.isEmpty()) {
                Text(
                    text = tr(R.string.s_zanwuguidingshuru),
                    color = textColor.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(entries, key = { it.id }) { entry ->
                        TraceRow(
                            entry = entry,
                            selected = entry.id == selectedId,
                            textColor = textColor,
                            accentColor = accentColor,
                            onClick = { onSelect(entry.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TraceRow(
    entry: GlideTraceRecorder.TraceEntry,
    selected: Boolean,
    textColor: Color,
    accentColor: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) accentColor.copy(alpha = 0.18f) else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = formatTime(entry.timeMs) + "  " + entry.code.ifEmpty { tr(R.string.s_weijiechubianma) },
                color = textColor,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (entry.expected.isNotEmpty()) {
                    tr(R.string.s_yibiaoj_, entry.expected)
                } else {
                    tr(R.string.s_weibiaoj)
                },
                color = if (entry.expected.isNotEmpty()) accentColor else textColor.copy(alpha = 0.55f),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * 单行标注输入框：复用 IME 内嵌 EditText + Holder 的既有机制，
 * 按键经服务层路由直接进这里，回车由面板统一保存。
 */
@Composable
private fun MarkInput(
    textColor: Color,
    isFocused: Boolean,
    onFocusChange: (Boolean) -> Unit,
) {
    AndroidView(
        factory = { context ->
            val d = context.resources.displayMetrics.density
            android.widget.EditText(context).apply {
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                setTextColor(textColor.hashCode())
                setHintTextColor(textColor.copy(alpha = 0.4f).hashCode())
                hint = tr(R.string.s_shuruwoxiangdade)
                textSize = 15f
                isSingleLine = true
                gravity = Gravity.CENTER_VERTICAL or Gravity.START
                setPadding((10 * d).toInt(), 0, (10 * d).toInt(), 0)

                setImeActionLabel(tr(R.string.s_baocun), EditorInfo.IME_ACTION_DONE)
                imeOptions = EditorInfo.IME_FLAG_NO_ENTER_ACTION or EditorInfo.IME_ACTION_DONE

                onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
                    onFocusChange(hasFocus)
                }
                setOnClickListener { requestFocus() }
                GlideMarkEditTextHolder.editText = this
                if (isFocused) post { requestFocus() }
            }
        },
        update = { editText ->
            if (isFocused && !editText.isFocused) editText.post { editText.requestFocus() }
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0x22FFFFFF)),
    )
}

private fun formatTime(ms: Long): String =
    SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(ms))
