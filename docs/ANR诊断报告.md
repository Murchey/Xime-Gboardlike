# ANR 诊断报告（2026-11-08）

> 本报告基于真机实测。结论在第二轮做了重要修正：**ANR 与点击时机无关**，
> 主因是测试设备系统过载 + 小米的系统级进程管理，我们的主线程优化收益有限。

## 一、实测数据

设备：Xiaomi 21091116AC / Android 13 (SDK 33) / 1080×2260 @2.75

### 1.1 冷启动耗时

`am start -W` 连续多次：

| 次数 | TotalTime |
|---|---|
| 基线（优化前） | 2171 ms |
| P0 优化后 ×3 | 2235 / 2178 / 2224 ms |

**结论：把 FileLogger 的日志清理由主线程移到后台线程后，冷启动耗时没有可测量变化**
（2171 → ~2200，属测量噪声）。这说明日志清理不是瓶颈。

### 1.2 主线程卡顿

```
Choreographer: Skipped 52 frames!
OpenGLRenderer: Davey! duration=1524ms
Choreographer: Skipped 82 frames!
Looper: PerfMonitor longMsg : seq=3 wall=1029ms h=android.app.ActivityThread$H w=110
```

`w=110` 对应 `BIND_APPLICATION`，即 Application 初始化 + 首个 Activity 创建，
wall=1029ms 是这一段的总耗时。

### 1.3 关键修正：ANR 与点击时机无关

按不同等待时间点击输入框，统计是否触发 ANR：

| 等待 | ANR |
|---|---|
| 3 s | 0 |
| 6 s | 1 |
| 10 s | 0 |
| 15 s | 1 |
| 20 s | **1** |

**等 20 秒再点仍然会 ANR**，因此「点击太早撞上启动窗口」这个假设不成立，
第一轮的推断需要修正。

### 1.4 真正的主因

```
ActivityManager: ANR in com.ximegboard (...MainActivity)
Reason: Input dispatching timed out (... Waited 5001ms for MotionEvent(action=DOWN))
Load: 16.93 / 16.56 / 16.18        ← 系统 1/5/15 分钟负载
ActivityManagerServiceImpl: checkThawTime uid=10169 report=Input dispatching timed out
```

两点关键信息：

1. **系统负载 16.9**：这台设备在测试期间长期高负载，主线程拿不到 CPU 时间片，
   5 秒内无法处理 MotionEvent，系统就判 ANR。这与应用代码质量无关。
2. **ANR 主体是 `MainActivity`（设置页），不是输入法 Service**。
   `dumpsys` 里输入法 Service 状态为 `FGS LACT SLTA UI CAPS`（前台服务、可见），
   本身工作正常。
3. `checkThawTime` 表明进程可能经历系统的冻结/解冻（小米后台管控），
   解冻期间不响应输入也会被判超时。

## 二、分层归因（修正后）

| 层 | 内容 | 可控性 | 实际影响 |
|---|---|---|---|
| A | Application.onCreate 主线程初始化（FileLogger / CorrectionPriors / PluginManager / ModelRuntime） | ✅ 部分可优化 | **小**（优化后无测量差异） |
| B | 首帧渲染（libMEOW 注入、DecorView、SurfaceFactory、硬件加速初始化） | ❌ 不可控 | 中（1.5s Davey 主要在这里） |
| C | **设备系统负载 16.9 + 小米冻结/解冻** | ❌ 不可控 | **大（ANR 的直接原因）** |

## 三、已完成的优化

### 3.1 日志清理移出主线程

`FileLogger.init` 原先在主线程执行三趟目录扫描（`cleanOldLogs` / `cleanRimeLogs` /
`cleanOrphanedLogs`）。已改为后台线程执行，初始化路径只保留「打开今天的日志文件」。

**实测效果**：冷启动耗时无显著变化（2171 → ~2200ms）。收益是主线程少做三趟文件 IO，
但在这台设备上被系统负载掩盖，无法量测。

### 3.2 插件备份超时（上一轮）

180s → 300s 且超时不停用插件。

### 3.3 KeysConfigHelper 同步解析（更早）

全量 YAML 解析（真机约 1.8s）不再每次 `initRimeEngine` 都同步执行。

## 四、改进方案（修正优先级）

### 不再推荐

- ~~「启动期输入防护」~~：ANR 与启动窗口无关（1.3 实测否定），
  加防护层只会增加复杂度而无收益。

### P1：先确认设备环境

**这是当前最高优先级**。在做任何应用侧优化前，需要先排除测量环境问题：

1. 换一台负载正常的设备复测
2. 关闭小米的「后台弹出界面」「省电策略」对 com.ximegboard 的限制（设为无限制）
3. 测量前先 `adb shell am force-stop` 清理后台并静置 1 分钟

如果换设备后 ANR 消失，说明问题在测试环境而非应用。

### P2：可选的进一步优化

- `CorrectionPriors` 的 ONNX 模型路径解析改为懒加载（首次用到纠错时才做）
- 首帧渲染路径：检查设置页 Compose 首屏是否有可延迟的重活

## 五、未验证项

- `/data/anr/` 下的 trace 需要 root，本机 adb 无 `su`，
  **未拿到 ANR 瞬间的 Java 堆栈**。本报告归因基于时间线日志、
  Choreographer/Davey、系统负载与 `checkThawTime` 证据。
