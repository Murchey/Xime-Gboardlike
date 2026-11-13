package com.kingzcheung.xime.ui.keyboard

import com.kingzcheung.xime.i18n.tr
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas

import com.kingzcheung.xime.service.PredictionManager
import android.annotation.SuppressLint
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.twotone.Edit
import androidx.compose.material.icons.twotone.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.kingzcheung.xime.R
import com.kingzcheung.xime.clipboard.ClipboardItem
import com.kingzcheung.xime.keyboard.KeyboardPage
import com.kingzcheung.xime.keyboard.OverlayRoute
import com.kingzcheung.xime.keyboard.PanelType
import com.kingzcheung.xime.keyboard.ToolbarAction
import com.kingzcheung.xime.settings.SettingsPreferences
import com.kingzcheung.xime.speech.RecognitionState
import com.kingzcheung.xime.ui.CLIPBOARD_CHIP_MAX_PX
import com.kingzcheung.xime.ui.rememberClipboardImageRequest
import java.io.File

@Immutable
data class CandidateBarVisuals(
    val backgroundColor: Color,
    val textColor: Color,
    val dividerColor: Color,
    val accentColor: Color = Color(0xFF1A73E8),
    val selectedTextColor: Color = Color(0xFF1A73E8),
    val isDarkTheme: Boolean = false,
    /** 候选注释（拼音/英文）颜色；与主候选同对比度，仅靠字号区分。 */
    val commentColor: Color = textColor,
    /** 编码气泡背景：传键盘背景色，避免暗色主题下气泡又白又灰。 */
    val preeditBubbleBackground: Color = backgroundColor,
    /** 编码气泡文字：传按键文字色，与键盘正文一致。 */
    val preeditBubbleTextColor: Color = textColor,
    /** 是否显著标注第一个候选词；关闭时首个候选不加底色、不用强调文字色。 */
    val highlightFirstCandidate: Boolean = true,
)

data class CandidateBarCallbacks(
    val onCandidateSelect: (Int) -> Unit,
    val onLogoClick: (() -> Unit)? = null,
    /** Gboard 式工具栏编辑入口（铅笔按钮）：打开定制工具栏拖动排布。 */
    val onEditToolbar: (() -> Unit)? = null,
    val onBack: (() -> Unit)? = null,
    val onHideKeyboard: (() -> Unit)? = null,
    val onShowMoreCandidates: (() -> Unit)? = null,
    val onClearAssociation: (() -> Unit)? = null,
    val onInputTextClick: (() -> Unit)? = null,
    val onAssociationSelect: ((Int) -> Unit)? = null,
    // 长按候选：抛事件给宿主（键盘视图内弹确认覆盖层，不弹独立窗口——
    // 焦点型弹窗会抢焦点导致 IME 被系统收起）。
    val onCandidateLongPress: ((Int) -> Unit)? = null,
    /** 常驻语音：点按候选栏的频谱区域结束识别（与轻触空格同一条结束路径）。 */
    val onVoiceStop: (() -> Unit)? = null
)

@Composable
fun CandidateBar(
    state: CandidateBarState,
    page: KeyboardPage = KeyboardPage.Main(com.kingzcheung.xime.keyboard.MainType.FULL),
    candidatePageExpanded: Boolean = false,
    toolbarActions: List<ToolbarAction> = emptyList(),
    visuals: CandidateBarVisuals,
    callbacks: CandidateBarCallbacks,
    inlineSuggestions: List<*> = listOf<Any>(),
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier,
    isFloatingMode: Boolean = false,
    isVoiceSticky: Boolean = false,
    voiceAmplitude: Float = 0f,
    voiceSpectrum: FloatArray = FloatArray(16),
    voiceRecognitionState: RecognitionState = RecognitionState.IDLE,
    voicePluginName: String = "",
    /** 剪贴板图片候选 → 本地文件（null 时渲染占位图标）。 */
    clipboardImageFileOf: ((ClipboardItem) -> File?)? = null,
) {
    val configuration = LocalConfiguration.current
    val isLandscape = !isFloatingMode && configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    // 候选栏左右留白：参考 Gboard，首个候选和末个操作按钮都不贴屏幕边缘
    val horizontalPadding = 12.dp
    val context = LocalContext.current

    // M3 角色色：图标按钮背景用 surface 与 primary 的混合色调（带种子色但不过于强烈），
    // 按压态用 onSurface 12% state layer
    val iconButtonContainer = androidx.compose.ui.graphics.lerp(
        MaterialTheme.colorScheme.surface,
        MaterialTheme.colorScheme.primary,
        0.15f
    )
    val iconButtonTint = MaterialTheme.colorScheme.onSurfaceVariant
    val showComments = SettingsPreferences.showCandidateComments(context)
    // 分割线开关：关掉后与上游排版一致（不占那 1dp，一屏多显示一两个候选）
    val showDivider = SettingsPreferences.showCandidateDivider(context)
    // 每页候选词数（默认 50，设置里可调 5~100）；候选栏与引擎分页同口径
    val pageSize = SettingsPreferences.getPageSize(context)
        .coerceIn(1, SettingsPreferences.MAX_PAGE_SIZE)
    val inputTextLocation = SettingsPreferences.getInputTextLocation(context)
    val showInputBoxStyle = inputTextLocation == SettingsPreferences.INPUT_TEXT_INPUT_BOX
    val candidateTextSize = SettingsPreferences.getCandidateTextSize(context)
    val candidateFontFamily = AppFonts.candidateFontFamily
    val commentFontFamily = AppFonts.commentFontFamily

    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val itemPaddingPx = with(density) { 16.dp.toPx() }
    val spacingPx = with(density) { 6.dp.toPx() }

    val screenWidthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    val rowPaddingPx = with(density) { 16.dp.toPx() }
    val rightSidePx = with(density) {
        val moreBtn = if (callbacks.onShowMoreCandidates != null) 38.dp.toPx() else 0f
        val clearBtn = if (callbacks.onClearAssociation != null) 38.dp.toPx() else 0f
        val hideBtn = if (callbacks.onHideKeyboard != null) 38.dp.toPx() else 0f
        rowPaddingPx + maxOf(moreBtn, clearBtn) + hideBtn + 8.dp.toPx()
    }

    // 候选行滚动状态：需在 state 分支前声明——ChineseCandidates 的 hasAnyMore
    // 叠加 canScrollForward 判断（见分支内注释）
    val candidateListState = rememberLazyListState()

    val content = CandidateBarContent.from(
        state = state,
        pageSize = pageSize,
        canScrollForward = candidateListState.canScrollForward,
    )
    val displayCandidates = content.candidates
    val displayComments = content.comments
    val displayImages = content.images
    val hasAnyMore = content.hasAnyMore
    val showLeftIcon = content.showLeftIcon

    val displayAssociation: List<String> = when (val s = state) {
        is CandidateBarState.ChineseCandidates -> remember(
            s.associationCandidates, displayCandidates, s.inputText, textMeasurer
        ) {
            if (displayCandidates.isEmpty()) {
                s.associationCandidates.take(PredictionManager.MAX_ASSOCIATION_COUNT)
            } else {
                val measureText = { text: String ->
                    textMeasurer.measure(
                        text = AnnotatedString(text),
                        style = TextStyle(fontSize = candidateTextSize.sp)
                    ).size.width.toFloat()
                }
                val leftPx = with(density) { rowPaddingPx + 32.dp.toPx() }
                val rowWidthPx = screenWidthPx - leftPx - rightSidePx
                val regularWidthPx = displayCandidates.sumOf { c ->
                    measureText(c).toDouble() + itemPaddingPx
                }.toFloat()
                val dividerWidthPx = with(density) { 12.dp.toPx() }
                val availablePx = rowWidthPx - regularWidthPx - dividerWidthPx

                var used = 0f
                val result = mutableListOf<String>()
                for (c in s.associationCandidates) {
                    val w = measureText(c) + itemPaddingPx + (if (result.isEmpty()) 0f else spacingPx)
                    if (used + w <= availablePx) {
                        used += w
                        result.add(c)
                    } else break
                }
                result
            }
        }
        is CandidateBarState.AssociationOnly ->
            s.candidates.take(PredictionManager.MAX_ASSOCIATION_COUNT)
        else -> emptyList()
    }
    val showInputTextRow = when (page) {
        is KeyboardPage.Overlay -> page.route !is OverlayRoute.Clipboard
        else -> true
    }

    LaunchedEffect(displayCandidates) {
        candidateListState.scrollToItem(0)
    }

    // 编码气泡：候选栏内计算编码文本后回写此状态，供 Column 的 drawBehind 读取绘制。
    // drawBehind 在下一帧读取最新值，无需同步；初始值取自当前 state 保证首帧即显示。
    var preeditBubbleText by remember(state) {
        mutableStateOf((state as? CandidateBarState.ChineseCandidates)?.preeditText
            ?: (state as? CandidateBarState.ChineseCandidates)?.inputText ?: "")
    }
    var preeditBubbleCaret by remember(state) {
        mutableStateOf((state as? CandidateBarState.ChineseCandidates)?.preeditCaretPos ?: -1)
    }
    val showPreeditBubble = showInputTextRow && preeditBubbleText.isNotEmpty() && !showInputBoxStyle

    val isLoading = (state as? CandidateBarState.ChineseCandidates)?.isLoading == true

    // 外层 Box：候选栏内容在下，底部叠一条细 loading 条（滑动解码未出结果时滚动）。
    // 用叠加而不是加高 Column，避免候选栏在滑动中变高导致键盘整体上移。
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(visuals.backgroundColor),
    ) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .drawPreeditBubble(
                text = preeditBubbleText,
                caretOffset = preeditBubbleCaret,
                enabled = showPreeditBubble,
                bubbleColor = visuals.preeditBubbleBackground,
                textColor = visuals.preeditBubbleTextColor,
                isDarkTheme = visuals.isDarkTheme,
            )
            .padding(horizontal = horizontalPadding),
        verticalArrangement = Arrangement.Center
    ) {
        if (isVoiceSticky) {
            // 常驻语音模式：候选栏显示语音引擎名 + 频谱。
            // 整条候选栏可点：轻触频谱即结束识别，不必非去点空格。
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .clickable(enabled = callbacks.onVoiceStop != null) {
                        callbacks.onVoiceStop?.invoke()
                    },
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (voicePluginName.isNotEmpty()) {
                    Text(
                        text = voicePluginName,
                        color = visuals.textColor.copy(alpha = 0.7f),
                        fontSize = 10.sp,
                        maxLines = 1,
                        textAlign = TextAlign.Center
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    AudioSpectrumAnimation(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp, vertical = 2.dp),
                        isActive = voiceRecognitionState == RecognitionState.LISTENING ||
                            voiceRecognitionState == RecognitionState.PROCESSING,
                        amplitude = voiceAmplitude,
                        spectrum = voiceSpectrum,
                        barWidthFactor = 4f,
                        barCount = 16,
                        spacingRatio = 1.6f,
                        heightScale = 0.6f
                    )
                }
            }
            return@Column
        }

        val displayText = (state as? CandidateBarState.ChineseCandidates)?.preeditText
            ?: (state as? CandidateBarState.ChineseCandidates)?.inputText ?: ""
        // 编码显示已改为候选栏顶部的悬浮气泡（drawBehind 绘制，见 drawPreeditBubble），
        // 栏内不再为编码保留布局空间——打字态与联想态的候选行共用同一垂直位置。
        preeditBubbleText = displayText
        preeditBubbleCaret = (state as? CandidateBarState.ChineseCandidates)?.preeditCaretPos ?: -1

        Row(
            modifier = Modifier
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showLeftIcon) {
                when (state) {
                    is CandidateBarState.Idle -> {
                        if (page is KeyboardPage.Overlay && page.route is OverlayRoute.SchemaList && callbacks.onBack != null) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(iconButtonContainer)
                                    .clickable { callbacks.onBack() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                    contentDescription = tr(R.string.s_fanhuicaidan),
                                    tint = visuals.accentColor,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(iconButtonContainer)
                                    .clickable { callbacks.onLogoClick?.invoke() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = if (visuals.isDarkTheme) R.drawable.logo_dark else R.drawable.logo),
                                    contentDescription = tr(R.string.s_xima_logo),
                                    tint = Color.Unspecified,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    is CandidateBarState.ClipboardDisplay -> {
                        Row(
                            modifier = Modifier.padding(end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = tr(R.string.s_jianqieban),
                                tint = visuals.accentColor,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    else -> {}
                }
            }

            if (inlineSuggestions.isNotEmpty()) {
                BoxWithConstraints(modifier = Modifier.weight(1f)) {
                    // 建议宽度不超过本行实际分到的栏宽（独占候选栏时仍为 180dp）：
                    // 与候选/剪贴板同显时本行只有一半栏宽，固定 180dp 会越界——
                    // InlineContentView 是独立 surface 渲染，clipToBounds 裁不住，
                    // 越界部分会压到右侧候选上（issue #1136）。
                    val suggestionWidth = minOf(maxWidth, 180.dp)
                    LazyRow(
                        // 占满配额：内容少时建议靠左、右侧留白到收起按钮（收起按钮
                        // 因此固定最右）；内容超出配额时占满并可横向滑动查看后续建议；
                        // clipToBounds：滑动时滑出边界的建议裁剪掉，避免与左侧 logo 重叠
                        modifier = Modifier
                            .fillMaxWidth()
                            .clipToBounds(),
                    ) {
                        itemsIndexed(inlineSuggestions, key = { index, _ -> index }) { _, suggestion ->
                            Box(modifier = Modifier.fillMaxHeight()) {
                                InlineSuggestionView(
                                    suggestion = suggestion,
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .width(suggestionWidth),
                                )
                                // 每条尾部 1dp 分隔线：条目之间为间隔，最后一条的尾线
                                // 同时充当与候选词区的分界（与旧平铺布局视觉一致）
                                InlineSuggestionDivider(
                                    modifier = Modifier.align(Alignment.CenterEnd),
                                    color = visuals.dividerColor,
                                )
                            }
                        }
                    }
                }
            }

            LazyRow(
                modifier = if (state is CandidateBarState.Idle) Modifier else Modifier.weight(1f),
                state = candidateListState,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                // 图片候选芯片比文字候选高：居中对齐，避免同排文字候选被顶到上沿
                verticalAlignment = Alignment.CenterVertically,
            ) {
                itemsIndexed(displayCandidates, key = { index, _ -> index }) { index, candidate ->
                    val imageItem = displayImages.getOrNull(index)
                    val hasFollowing = index < displayCandidates.lastIndex ||
                        displayAssociation.isNotEmpty()
                    if (imageItem != null) {
                        // 图片候选：Gboard 风格芯片（圆角矩形底 + 圆形缩略图 + "图片"）
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ClipboardImageCandidateItem(
                                file = clipboardImageFileOf?.invoke(imageItem),
                                isSelected = index == 0 && visuals.highlightFirstCandidate,
                                onClick = { callbacks.onCandidateSelect(index) },
                                onLongClick = if (callbacks.onCandidateLongPress != null) {
                                    { callbacks.onCandidateLongPress(index) }
                                } else null,
                                containerColor = visuals.textColor.copy(alpha = 0.10f),
                                textColor = visuals.textColor,
                                accentColor = visuals.accentColor,
                                dividerColor = visuals.dividerColor,
                                fontSize = candidateTextSize.sp,
                                fontFamily = candidateFontFamily,
                            )
                            if (hasFollowing && showDivider) CandidateBarDivider(visuals.dividerColor)
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CandidateItem(
                                text = candidate,
                                index = index,
                                onClick = { callbacks.onCandidateSelect(index) },
                                onLongClick = if (callbacks.onCandidateLongPress != null) {
                                    { callbacks.onCandidateLongPress(index) }
                                } else null,
                                textColor = visuals.textColor,
                                comment = if (showComments) {
                                    when (val s = state) {
                                        is CandidateBarState.ChineseCandidates -> s.comments.getOrElse(index) { "" }
                                        is CandidateBarState.EnglishCandidates -> s.comments.getOrElse(index) { "" }
                                        else -> ""
                                    }
                                } else "",
                                isSelected = index == 0 && visuals.highlightFirstCandidate,
                                accentColor = visuals.accentColor,
                                selectedTextColor = visuals.selectedTextColor,
                                commentColor = visuals.commentColor,
                                fontSize = candidateTextSize.sp,
                                candidateFontFamily = candidateFontFamily,
                                commentFontFamily = commentFontFamily,
                                minWidth = 46.dp,
                            )
                            if (hasFollowing && showDivider) CandidateBarDivider(visuals.dividerColor)
                        }
                    }
                }

                itemsIndexed(displayAssociation, key = { index, _ -> "assoc-$index" }) { index, candidate ->
                    val assocState = state as? CandidateBarState.AssociationOnly
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CandidateItem(
                            text = candidate,
                            index = -1,
                            onClick = { callbacks.onAssociationSelect?.invoke(index) },
                            textColor = visuals.textColor,
                            comment = displayComments.getOrElse(index) { "" },
                            isSelected = assocState?.highlightIndex == index,
                            accentColor = visuals.accentColor,
                            selectedTextColor = visuals.selectedTextColor,
                            commentColor = visuals.commentColor,
                            fontSize = candidateTextSize.sp,
                            candidateFontFamily = candidateFontFamily,
                            commentFontFamily = commentFontFamily,
                            minWidth = 46.dp,
                        )
                        if (showDivider && index < displayAssociation.lastIndex) {
                            CandidateBarDivider(visuals.dividerColor)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            when {
                // 展开页优先显示收起/返回键：选词后即使回到 Idle 也保留入口，
                // 不会出现展开页还在、却没有按钮可收起的空页。
                candidatePageExpanded -> {
                    if (callbacks.onBack != null) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(iconButtonContainer)
                                .clickable { callbacks.onBack() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowUp,
                                contentDescription = tr(R.string.s_fanhuijianpan),
                                tint = visuals.accentColor,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
                state is CandidateBarState.Idle -> {
                    // 显示内联建议时隐藏工具栏按钮区，把宽度让给建议；logo 与
                    // 收起按钮保留，退格回到 idle 时的状态感知不变
                    if (inlineSuggestions.isEmpty()) {
                        // Gboard AccessPointsBar 排列（对照反编译 onMeasure/onLayout）：
                        // 每按钮占等宽槽 t = min(((icon+gap)/2 + 宽)/(n+1), 宽/n)，
                        // 整组水平居中、按钮在槽内居中——等距铺开而非右对齐堆叠
                        BoxWithConstraints(
                            modifier = Modifier.weight(1f, fill = true),
                            contentAlignment = Alignment.Center,
                        ) {
                            val count = toolbarActions.size
                            if (count > 0) {
                                val slotBias = 23.dp // (38dp 触区 + 8dp 间距) / 2
                                val slot = minOf(
                                    (maxWidth + slotBias) / (count + 1),
                                    maxWidth / count,
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    toolbarActions.forEach { action ->
                                        val interactionSource = remember { MutableInteractionSource() }
                                        val isPressed by interactionSource.collectIsPressedAsState()
                                        // 槽位即触区（Gboard 按槽宽测量子视图），
                                        // 38dp 圆形层只作按压视觉
                                        Box(
                                            modifier = Modifier
                                                .width(slot)
                                                .fillMaxHeight()
                                                .tolerantClick(
                                                    showRipple = false,
                                                    interactionSource = interactionSource,
                                                    onClick = action.onClick
                                                ),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(38.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        if (isPressed) iconButtonContainer.copy(alpha = 0.55f)
                                                        else Color.Transparent
                                                    ),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                ToolbarButtonIcon(
                                                    item = action.item,
                                                    tint = if (isPressed) iconButtonTint.copy(alpha = 0.6f) else iconButtonTint,
                                                    modifier = Modifier.size(24.dp),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Gboard 式编辑入口：工具栏末端的铅笔按钮（收起键盘按钮之前）
                    if (inlineSuggestions.isEmpty() && callbacks.onEditToolbar != null) {
                        val editToolbarInteractionSource = remember { MutableInteractionSource() }
                        val isEditToolbarPressed by editToolbarInteractionSource.collectIsPressedAsState()
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 3.dp)
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isEditToolbarPressed) iconButtonContainer.copy(alpha = 0.55f)
                                    else Color.Transparent
                                )
                                .tolerantClick(
                                    showRipple = false,
                                    interactionSource = editToolbarInteractionSource,
                                    onClick = { callbacks.onEditToolbar() }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.TwoTone.Edit,
                                contentDescription = tr(R.string.s_dingzhigongjulan),
                                tint = if (isEditToolbarPressed) iconButtonTint.copy(alpha = 0.6f) else iconButtonTint,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    if (callbacks.onHideKeyboard != null) {
                        val hideKeyboardInteractionSource = remember { MutableInteractionSource() }
                        val isHideKeyboardPressed by hideKeyboardInteractionSource.collectIsPressedAsState()

                        Spacer(modifier = Modifier.width(2.dp))

                        // 我们特有的收起键盘按钮：保持在工具栏最右，触区同步放大
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isHideKeyboardPressed) iconButtonContainer.copy(alpha = 0.55f)
                                    else Color.Transparent
                                )
                                .clickable(
                                    interactionSource = hideKeyboardInteractionSource,
                                    indication = null,
                                    onClick = { callbacks.onHideKeyboard() }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = tr(R.string.s_shouqijianpan),
                                tint = if (isHideKeyboardPressed) iconButtonTint.copy(alpha = 0.6f) else iconButtonTint,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
                displayAssociation.isNotEmpty() && callbacks.onClearAssociation != null -> {
                    val clearInteractionSource = remember { MutableInteractionSource() }
                    val isClearPressed by clearInteractionSource.collectIsPressedAsState()

                    Spacer(modifier = Modifier.width(4.dp))
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(28.dp)
                            .background(visuals.dividerColor).padding(end = 1.dp)
                    )
                    Box(
                        modifier = Modifier
                            .width(30.dp)
                            .height(24.dp)
                            .clip(CircleShape)
                            .background(
                                if (isClearPressed) (if (visuals.isDarkTheme) Color.White.copy(alpha = 0.15f) else Color.Black.copy(
                                    alpha = 0.1f
                                ))
                                else Color.Transparent
                            )
                            .clickable(
                                interactionSource = clearInteractionSource,
                                indication = null,
                                onClick = { callbacks.onClearAssociation() }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tr(R.string.s_qingkong),
                            color = if (isClearPressed) visuals.textColor.copy(alpha = 0.6f) else visuals.textColor,
                            fontSize = 11.sp
                        )
                    }
                }
                hasAnyMore && callbacks.onShowMoreCandidates != null -> {
                    val moreInteractionSource = remember { MutableInteractionSource() }
                    val isMorePressed by moreInteractionSource.collectIsPressedAsState()

                    Spacer(modifier = Modifier.width(4.dp))
                    Box(
                        modifier = Modifier
                            .width(30.dp)
                            .height(24.dp)
                            .clip(CircleShape)
                            .background(
                                if (isMorePressed) (if (visuals.isDarkTheme) Color.White.copy(alpha = 0.15f) else Color.Black.copy(
                                    alpha = 0.1f
                                ))
                                else Color.Transparent
                            )
                            .clickable(
                                interactionSource = moreInteractionSource,
                                indication = null,
                                onClick = { callbacks.onShowMoreCandidates() }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = tr(R.string.s_gengduohouxuan),
                            tint = if (isMorePressed) visuals.textColor.copy(alpha = 0.6f) else visuals.textColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }

        // 候选栏顶缘的细 loading 条：仅滑动解码进行中显示。
        // 放顶缘而不是底缘：候选栏底边紧贴按键区，1.5dp 会被按键背景吃掉；
        // 顶缘是候选栏与上方应用的天然分界，最不打扰也最容易被看到。
        if (isLoading) {
            GlideDecodingBar(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth(),
                color = visuals.accentColor,
                trackColor = visuals.dividerColor,
            )
        }
    }
}

/**
 * 候选栏顶缘的滑动解码指示条：2dp 高的轨道上有一段高亮在往复滚动。
 *
 * 只做视觉提示，不拦截触摸（不挂 clickable/pointerInput），也不改变候选栏高度——
 * 它叠在候选栏底部像素上，避免滑动过程中键盘整体跳动。
 */
@Composable
private fun GlideDecodingBar(
    modifier: Modifier = Modifier,
    color: Color = Color(0xFF1A73E8),
    trackColor: Color = Color.Transparent,
    barHeightDp: androidx.compose.ui.unit.Dp = 2.dp,
) {
    val transition = rememberInfiniteTransition(label = "glide-decoding")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "glide-decoding-progress",
    )
    Canvas(
        modifier = modifier
            .height(barHeightDp)
            .clipToBounds(),
    ) {
        val h = size.height
        if (trackColor.alpha > 0.01f) {
            drawRect(color = trackColor.copy(alpha = 0.35f), size = size)
        }
        // 高亮段占轨道 35%，从左侧外侧滑入到右侧外侧
        val segment = size.width * 0.35f
        val start = (size.width + segment) * progress - segment
        drawRect(
            color = color,
            topLeft = Offset(start, 0f),
            size = Size(segment, h),
        )
    }
}

/**
 * 候选栏的**图片候选**芯片（Gboard 剪贴板样式）：
 * 大圆角矩形底 + 左侧圆形缩略图 + 右侧「图片」二字；整体高度与候选栏行高一致。
 *
 * 「图片」只是标注型标签（主角是缩略图），字号取候选字号的 70% 并夹在 11–16sp：
 * 直接沿用候选字号会比 28dp 缩略图还抢眼，且各机型候选字号档位差异很大。
 *
 * [file] 为 null（图片已被配额淘汰/清理）时退化为占位图标，点击仍按图片链路处理
 * （服务层会提示"图片已不在本地"）。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ClipboardImageCandidateItem(
    file: File?,
    onClick: () -> Unit,
    containerColor: Color,
    textColor: Color,
    accentColor: Color,
    dividerColor: Color = Color.Unspecified,
    isSelected: Boolean = false,
    fontSize: androidx.compose.ui.unit.TextUnit = 16.sp,
    fontFamily: androidx.compose.ui.text.font.FontFamily = androidx.compose.ui.text.font.FontFamily.Default,
    onLongClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (isSelected) accentColor.copy(alpha = 0.2f) else containerColor
            )
            .tolerantClick(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 5.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(accentColor.copy(alpha = 0.15f))
                .then(
                    if (dividerColor != Color.Unspecified) {
                        Modifier.border(1.dp, dividerColor, CircleShape)
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            if (file != null) {
                AsyncImage(
                    // 显式上限：28dp 圆形缩略图按 128px 方框降采样，不整图解码
                    model = rememberClipboardImageRequest(file, CLIPBOARD_CHIP_MAX_PX),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.TwoTone.Image,
                    contentDescription = null,
                    tint = textColor.copy(alpha = 0.45f),
                    modifier = Modifier.size(15.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = tr(R.string.s_tupian_2),
            color = textColor,
            // 标注型标签：比候选文字小一档，并夹住上下限（超大字号设置下也不至于撑满芯片）
            fontSize = (fontSize.value * 0.7f).coerceIn(11f, 16f).sp,
            fontFamily = fontFamily,
            maxLines = 1
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CandidateBarDivider(color: Color) {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(18.dp)
            .background(color.copy(alpha = 0.5f))
    )
}

@Composable
fun CandidateItem(
    text: String,
    index: Int,
    onClick: () -> Unit,
    textColor: Color,
    comment: String = "",
    isSelected: Boolean = false,
    accentColor: Color = Color(0xFF1A73E8),
    selectedTextColor: Color = Color(0xFF1A73E8),
    /** 注释（拼音/英文）颜色；缺省回退主候选色。 */
    commentColor: Color = textColor,
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier,
    fontSize: androidx.compose.ui.unit.TextUnit = 19.sp,
    candidateFontFamily: androidx.compose.ui.text.font.FontFamily = androidx.compose.ui.text.font.FontFamily.Default,
    commentFontFamily: androidx.compose.ui.text.font.FontFamily = androidx.compose.ui.text.font.FontFamily.Default,
    onLongClick: (() -> Unit)? = null,
    /** 单字候选的最小占位宽度：参考 Gboard 的宽松单元格，避免候选挤在一起。 */
    minWidth: Dp = 0.dp,
) {
    Row(
        modifier = modifier
            .widthIn(min = minWidth)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isSelected) accentColor.copy(alpha = 0.12f)
                else Color.Transparent
            )
            // 候选栏在 LazyRow 内且外层还有滚动手势：用 tolerantClick 容忍轻点的小位移，
            // 避免 ROM 手势判定吃掉点击（部分平板/高采样率屏幕会表现为“点候选没反应”）
            .tolerantClick(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = text,
            color = if (isSelected) selectedTextColor else textColor,
            fontSize = fontSize,
            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1,
            fontFamily = candidateFontFamily
        )
        if (comment.isNotEmpty()) {
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = comment,
                // 拼音注释不再用 50% 透明度：浅灰底上会几乎不可见。
                // 与主候选同对比度，靠更小字号区分层级。
                color = if (isSelected) selectedTextColor else commentColor,
                fontSize = (fontSize.value * 11f / 19f).sp,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                fontFamily = commentFontFamily
            )
        }
    }
}

/**
 * 编码悬浮气泡：在候选栏顶部之上（栏外）绘制一个圆角胶囊气泡显示当前拼音编码。
 *
 * 参考 SwipeBubble 的锚定方式，但为纯绘制实现：
 * - 锚定宿主（候选栏 Column）左上角，气泡体向上悬浮于栏外空间；
 * - drawBehind 绘制不参与布局、不拦截触摸事件——候选栏上方的快捷发送表单/
 *   手写区等 UI 不受任何布局影响；
 * - IME 窗口为 MATCH_PARENT 全屏（onConfigureWindow），栏外绘制不会被窗口裁剪。
 *
 * 视觉：浅色模式近白底/深色模式深灰底的圆角胶囊（92% 不透明），无边框无阴影；
 * 编码文字在气泡内垂直居中；气泡与候选栏顶部之间留 2dp 间隙；宽度自适应，
 * 超出宿主右缘时左移钳制。
 *
 * 注意：不能使用传入的主题背景色——CandidateBarVisuals.backgroundColor 为
 * Color.Transparent（真实背景由外层绘制），以其合成会导致气泡无底色、
 * 文字与 app 内容混叠不可读。此处以候选文字亮度推断深浅模式取对比底色。
 */
private fun Modifier.drawPreeditBubble(
    text: String,
    enabled: Boolean,
    bubbleColor: Color,
    textColor: Color,
    isDarkTheme: Boolean,
    caretOffset: Int = -1
): Modifier = composed {
    val density = LocalDensity.current
    val cornerRadiusPx = with(density) { 4.dp.toPx() }
    val horizontalPaddingPx = with(density) { 8.dp.toPx() }
    val verticalPaddingPx = with(density) { 3.dp.toPx() }
    val bubbleBottomGapPx = with(density) { 2.dp.toPx() }
    val screenMarginPx = with(density) { 4.dp.toPx() }
    val textSizePx = with(density) { 12.sp.toPx() }
    val caretWidthPx = with(density) { 1.5.dp.toPx() }

    // 气泡底色直接取键盘背景色，文字取按键文字色：暗色主题不再出现白底灰字。
    // 仅当调用方仍传透明色（旧调用）时按主题深浅兜底。
    val bubbleBgColor = if (bubbleColor.alpha > 0.01f) {
        bubbleColor
    } else {
        if (isDarkTheme) Color(0xFF1E1F21) else Color(0xFFF1F3F4)
    }

    // 文本画笔：与 SwipeBubble 同款 nativeCanvas 绘制方式
    val bubbleTextPaint = remember(textColor, textSizePx) {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textSize = textSizePx
            color = textColor.toArgb()
        }
    }

    drawBehind {
        if (!enabled || text.isEmpty()) return@drawBehind

        val fontMetrics = bubbleTextPaint.fontMetrics
        val textWidth = bubbleTextPaint.measureText(text)
        // 文本实际渲染高度以可见字形区间（ascent..descent）计，避免 lineHeight 参与导致偏移
        val textHeight = fontMetrics.descent - fontMetrics.ascent
        val bubbleWidth = textWidth + horizontalPaddingPx * 2
        val bubbleHeight = textHeight + verticalPaddingPx * 2

        // 气泡贴候选栏左缘，右向延伸；超出宿主右缘时整体左移钳制。
        val clampedLeft = maxOf(
            screenMarginPx,
            minOf(0f, size.width - bubbleWidth - screenMarginPx).coerceAtLeast(screenMarginPx / 4f)
        )
        // 底部间隙：气泡底缘距候选栏顶缘 1dp
        val top = -bubbleBottomGapPx - bubbleHeight

        // 气泡主体
        drawRoundRect(
            color = bubbleBgColor,
            topLeft = Offset(clampedLeft, top),
            size = Size(bubbleWidth, bubbleHeight),
            cornerRadius = CornerRadius(cornerRadiusPx)
        )

        // 文本垂直居中：基线 = 气泡顶 + (气泡高 - (ascent + descent)) / 2，
        // ascent/descent 均为负/正相对基线的偏移，该式把字形区中点对准气泡中点。
        drawIntoCanvas { composeCanvas ->
            val baselineY = top + (bubbleHeight - (fontMetrics.ascent + fontMetrics.descent)) / 2f
            composeCanvas.nativeCanvas.drawText(text, clampedLeft + horizontalPaddingPx, baselineY, bubbleTextPaint)
        }

        // 编码编辑光标：caret 在中间（offset < 文本长度）时在对应字符边界画竖线；
        // 末尾/未知（-1）不画——默认打字态光标恒在编码末尾，保持观感不变
        if (caretOffset in 0 until text.length) {
            val caretCharIndex = caretOffset.coerceIn(0, text.length)
            val caretX = clampedLeft + horizontalPaddingPx +
                bubbleTextPaint.measureText(text, 0, caretCharIndex)
            drawLine(
                color = textColor,
                start = Offset(caretX, top + verticalPaddingPx),
                end = Offset(caretX, top + bubbleHeight - verticalPaddingPx),
                strokeWidth = caretWidthPx
            )
        }
    }
}

