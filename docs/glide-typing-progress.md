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

### 1. 组合态干扰新滑动（待修）
Rime 处于组合态时，新的滑动不触发（无 GlideCtl 日志）。退格可缩短编码
（cvghyuio→cvghyui）证明组合态确实存在。需要明确「追加」还是「新建」策略。

### 2. 准确率尚未建立有效基线
此前的准确率判断部分基于错误手法，需更正：
- 用水平横滑顶行 `qwerty` 验证不成立——qwerty 非合法拼音串，解出「卡而」属合理结果。
- 用多段 `input swipe` 模拟折线不成立——每段是独立手势，会合成无意义轨迹。
- 应用**合法拼音串的一次连续手势**（如 nihao = n-i-h-a-o）建立基线后再评价。

## 待办
1. 滑动与既有组合的追加/新建策略。
2. 用合法拼音串建立准确率基线。
3. 空间模型改进（方向特征、中心穿越奖励）。
4. Gboard `Range` 分段候选对齐。
5. 英文模式滑动实测。
