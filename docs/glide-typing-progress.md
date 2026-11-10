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

## 待办
1. 滑动与既有组合的追加/新建策略。
2. 用合法拼音串建立准确率基线。
3. 空间模型改进（方向特征、中心穿越奖励）。
4. Gboard `Range` 分段候选对齐。
5. 英文模式滑动实测。
