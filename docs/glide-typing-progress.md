# 滑动输入实现进度

按 `docs/glide-typing-plan.md` 阶段 A→E 推进。本文件补充 `PROGRESS.md`
（该文件在 .gitignore 中，不随仓库分发），只记录滑动输入这条线。

## 已完成

### 阶段 A/B 地基
- 提交契约：`GlideCompositionSession`（IDLE/COLLECTING/DECODING/PENDING_CONFIRM/CONFIRMED/CANCELLED）、
  `GlideCoordinator`、`GlideSentenceLookup`。
- 几何与轨迹：`GlideGeometry`、`GlideGeometryRegistry`（容差聚类推导 row/col）、
  `GlideTrace`（重采样、特征提取）。
- 手势：`GlideGestureArbiter`；glide 开启时字母键的拖动/点击 pointerInput 整体不挂载，
  整条手势交给父层采集（解决逐键上屏乱码）。
- 已移除 26 键整区横滑光标（用户要求，无空格替代方案）。

### 阶段 C 解码
- `GlideTrajectoryDecoder`：空间概率 beam search + 回摆去重（窗口 3）+ 弧长重采样；
  转折点（>0.9rad）/停留点（≥80ms）作为强按键意图；行列跳跃惩罚。
- 候选投影：`onGlideCandidates` → `applyGlideCandidates` 写入候选栏（不提交）。

## 关键修复记录

### 确认后键盘失联（已修，c27a2169）
- 现象：确认候选后点字母完全无字符，整个键盘失效。
- 根因：候选用 `CandidateAction.plugin` 直接 commitText，而 `setInput` 留下的
  Rime 组合态未清，导致 InputConnection 失联（dumpsys: mServedView=null）。
- 修复：候选优先映射为 `CandidateAction.engine(index)`，走 Rime 正常选词让引擎自行清组合。
- 真机验证：连续三轮滑动 + 确认均正常。

## 已知问题

### 1. 组合态干扰新滑动（代码已改，真机未验证）
Rime 处于组合态时，新的滑动行为异常；退格可缩短编码（cvghyuio→cvghyui）
证明组合态确实存在。已采用**新建语义**并实现：

- `GlideKeyboardController.onGlideStart` 在首次升级为 Glide 时回调一次；
- UI 层转给 `KeyboardCallbacks.onGlideStart` → `XimeInputMethodService.beginGlideComposition()`，
  清空 Rime 组合与候选态（不动已上屏文本）；
- 单测 `GlideStartSemanticsTest` 4 项锁定：点按不触发、一次滑动只触发一次、
  两次独立滑动各触发一次、glide 关闭不触发。

**真机验证受阻**：本轮设备上小米备忘录 `isActive()=false`（第三方输入法输入连接
未激活，点字母无字符），设置页测试框又反复失焦，未能取得端到端截图。
下次需换稳定宿主（如短信/浏览器地址栏）重新验证。

### 2. 准确率尚未建立有效基线
此前的准确率判断部分基于错误手法，需更正：
- 用水平横滑顶行 `qwerty` 验证不成立——qwerty 非合法拼音串，解出「卡而」属合理结果。
- 用多段 `input swipe` 模拟折线不成立——每段是独立手势，会合成无意义轨迹。
- 应用**合法拼音串的一次连续手势**（如 nihao = n-i-h-a-o）建立基线后再评价。

### 3. 点按被误禁用（本轮已修）
早前为阻止滑动时逐键上屏，把字母键的**点击与长按** pointerInput 也一并卸载，
导致 glide 开启时普通点按无法输入。现只卸载**拖动**手势（滑动需独占），
点击与长按恢复。注意：设备上 Xime 主题应用窗口的输入连接存在
`isActive()=false` 的环境现象（glide 关闭时点按同样无响应），
与本次改动无关，验证时需先确认输入连接可用。

### 4. DOWN 被无条件消费导致整键盘失效（本轮已修）
`glideTouchModifier` 在 Initial pass 对 DOWN 调用了 `down.consume()`，
使所有子键（含空格）收不到按下事件。真机现象：glide 开启后点任何键都无字符。
修复：不再消费 DOWN，只在升级为 Glide 后消费 MOVE/UP。
（注：该轮真机未能取得截图验证，依据为 Compose 事件分发语义与现象一致性。）

### 环境排查记录（重要）
本轮查明前几轮「键盘显示但点任何键无字符」的**真正原因**：
设备当前输入法被切换成了 **小企鹅输入法（org.fcitx.fcitx5.android）**，
Xime 只是被「启用」但未被「选中」。`dumpsys input_method` 中 `mCurId` 指向 fcitx5 即为证据。

排查命令：
```
adb shell ime list -s                       # 列出可用 IME
adb shell ime set <Xime IME id>             # 切换回 Xime
adb shell dumpsys input_method | grep mCurId # 确认 mCurId 与 mBoundToMethod=true
```

**教训**：真机验证前必须先确认 `mCurId` 是 Xime，否则所有"功能失效"的结论都不可信。
这也意味着此前几轮里部分「点按无响应」的观察需要重新验证。
设备曾出现 `mBoundToMethod=false / mVisibleBound=false`，导致键盘显示但不响应。
用 `ime disable` → `ime enable` → `ime set` 重置后可恢复为
`mBoundToMethod=true mVisibleBound=true`。后续真机验证前应先确认这两项。

### 5. 真机端到端已打通（本轮，在正确前提下）
前提：`adb shell dumpsys input_method | grep mCurId` 必须指向 Xime。

在小米备忘录 + 万象全拼、确认 `mCurId` 正确后实测：
- 点按字母：能上屏并出候选（验证了移除 `down.consume()` 的修复）；
- 连续滑动两次：均正常产生中文候选（`szxdfgv` → 实质性的风格 / 实质性的 / 市中），
  第二次滑动还追加了编码 —— 说明**连续滑动可用**。

### 6. 触摸坐标偏移（本轮已修）
实测 `GlideTouch: pos=174,461` 对应屏幕 (180,2135)，即 `down.position` 是相对键盘根容器的
局部坐标，比屏幕坐标少了约 1674px 的垂直偏移；而几何注册用的是 `boundsInRoot` 的 root 坐标。
两者坐标系不一致，导致滑动命中的键与视觉位置有偏差。

**修复**：`GlideGeometryRegistry` 增加 originX/originY 字段，`beginFrame` 接收键盘
root 原点，`snapshot()` 输出时把键矩形减去该原点，统一为键盘局部坐标，
与 `down.position` 一致。新增 `GlideOriginTest` 4 项锁定。

**真机验证（修正后）**：滑屏幕 Z 行 (180→560, y=2135) 得到 `zxcv`（此前为 `szxdfgv`），
精确命中 Z-X-C-V，候选为 自行车/展现出/周星驰/在现 —— 坐标系已对齐。

### 7. 候选栏在 IME 触摸区之外（新发现，阻塞阶段D）
`dumpsys window` 显示 IME 窗口 frame 为 `[0,96][1080,2400]`，但
`touchable region=SkRegion((0,1542,1080,2400))` —— 从 y=1542 开始。
而候选栏渲染在 y≈1287 附近，**落在触摸区之外，点不到**。
`onComputeInsets` 非浮动分支用 `keyboardContainer.getLocationInWindow` 的 top 作为
`contentTopInsets` 并设 `TOUCHABLE_INSETS_VISIBLE`，说明候选栏位于 keyboardContainer 之上
（在容器外绘制），导致触摸区不含候选栏。

影响：抬手后的候选**无法点选确认**，只能靠空格/回车等键确认（用户认可的备选路径）。

这属既有布局问题，非 glide 引入；但阻塞阶段D「候选点击确认」的端到端验证。

**本轮尝试与结果**：
- 把 `onComputeInsets` 改为 `TOUCHABLE_INSETS_REGION` 并显式 set 全窗口 region，
  dumpsys 确认触摸区变为 `(0,96,1080,2400)`（已覆盖候选栏 y≈1287）；
- 但**点击候选仍然无效**（无任何点击日志、画面不变，多次不同坐标探测一致）；
- 说明阻塞点不在窗口触摸区，而在候选栏自身不接收触摸（很可能绘制在键盘内容 Box 之外）。
- 该改动已回退，未提交（无法验证的改动不留）。

**结论**：候选点击是既有缺陷，修复需要调整键盘内容容器与候选栏的层级/高度关系，
超出 glide 范围且风险较高。当前阶段D 依赖空格/回车确认路径。

**决定性对照实验（本轮）**：
- 用**原生输入**（点按 w）产生候选「我/为/握/万/问/外」，点候选「我」**同样无反应**，
  画面完全不变。
- 这证明候选栏点击无效是**全系统既有缺陷**，与 glide 投影无关，也不是本次改动引入。
- 空格确认路径实测可用：点空格后「我」正常上屏（正文 1 字）。

**onGlideStart 清空组合已验证生效**：在存在旧组合的情况下滑动，
编码区为干净的 `qwert`（无旧编码残留），候选为 请问儿童/去玩儿/七五二/气。

### 8. 准确率评估的方法论限制（本轮）
尝试用 adb 建立准确率基线未成功，原因不是解码缺陷，而是**输入手段本身不成立**：

- `adb shell input swipe x1 y1 x2 y2` 只能画**直线**。真实滑动 nihao 是沿
  n→i→h→a→o 的**折线**；用直线从 n 连到 i 会途经 r/t/y/u，解码器正确解出
  `rtyuio`，这不是错误。
- 分段多次 swipe 同样不成立：每段是独立手势，会被 `beginGlide` 当作新一次输入。
- 键盘高度会随候选栏出现/消失变化（实测 Q 行 y 在 1392 与 1560 之间跳变），
  固定屏幕坐标会命中错行。

**结论**：准确率评估不能用 adb 直线滑动。需要：
1. 用 Instrumentation/Compose 测试直接注入 `GlidePoint` 序列（可构造折线），
   在 JVM/仪器层跑解码器；或
2. 人工真机滑动 + 截图判读（可采信但样本少）。
建议采用 1：把「轨迹→编码→候选」做成可注入的纯函数测试，用真实折线样本回归。

## 待办
1. 滑动与既有组合的追加/新建策略。
2. 用合法拼音串建立准确率基线。
3. 空间模型改进（方向特征、中心穿越奖励）。
4. Gboard `Range` 分段候选对齐。
5. 英文模式滑动实测。
