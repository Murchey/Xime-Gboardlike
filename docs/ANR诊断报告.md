# ANR 诊断报告（2026-11-08）

## 结论先行

adb 调试期间的「无响应」**不是单一原因**，实测证据指向三层：

1. **冷启动主线程卡顿（我们可控，已定位）** —— 启动期主线程被重活占住约 1.5 秒
2. **系统侧干扰（不可控）** —— 小米注入的 `libMEOW` / `QT` 库在进程启动时同步加载
3. **调试方式放大（测量伪影）** —— 冷启动后立刻 adb 点击，落在初始化窗口内必然触发 5s ANR 阈值

## 实测数据

设备：Xiaomi 21091116AC / Android 13 (SDK 33) / 1080×2260 @2.75

```
am start -W 结果：
  TotalTime: 2171 ms

主线程卡顿证据：
  Choreographer: Skipped 51 frames!
  Choreographer: Skipped 83 frames!
  OpenGLRenderer: Davey! duration=1530ms
```

### 冷启动时间线

| 时间 | 事件 |
|---|---|
| 15:51:57.239 | `JNI_OnLoad success` |
| 15:51:57.385 | FileLogger 初始化 |
| 15:51:57.392 | CorrectionPriors init（含 ONNX 模型路径） |
| 15:51:57.441 | PluginManager 初始化（异步派发到 7023 线程） |
| **15:51:57.797** | **ModelRuntime attached** |
| 15:51:57.805–57.812 | `libMEOW` / `QT` 系统库加载（主线程 6996） |
| 15:51:58.877 | **Davey! duration=1530ms** ← 卡顿峰值 |
| 15:51:59.012 | `initRimeEngine: Starting initialization` |
| 15:51:59.978 | Rime 引擎初始化完成 |
| 15:52:01.425 | CorrectionPriors selfCheck |

关键观察：**真正的 1.5 秒卡顿发生在 Rime 初始化之前**，即 Application.onCreate → 首次绘制之间，
而不是 rime 部署或方案加载。

## 分层归因

### A. 我们可控的部分

| 项 | 现状 | 风险 |
|---|---|---|
| Application.onCreate 主线程重活 | FileLogger、CorrectionPriors、PluginManager、ModelRuntime 依次同步初始化 | 中 |
| `CorrectionPriors` ONNX 模型路径解析 | 在 onCreate 主线程 | 中 |
| `initRimeEngine` 首次 `KeysConfigHelper.loadConfig` | 已改为进程内只同步解析一次（上一轮修复） | 低（已优化） |
| 大方案（万象）引擎初始化 | 实测约 0.97s，且已在 IO 线程 | 中 |

### B. 不可控部分

- `libMEOW`（`libMEOW_gift.so`）与 `QT` 是**小米系统注入**的库，在进程启动时同步初始化，
  我们无法绕过。日志中的 `Support!!` / `[QT]file does not exist` 都来自它。

### C. 测量伪影

adb 脚本「启动后立刻点击」的模式，正好落在 Application 初始化 + 首帧渲染的窗口内。
系统判定 `Waited 5001ms for MotionEvent(action=DOWN)` 后弹 ANR——
这不是应用真的挂了，而是**点击得太早**。

本轮复现记录：15:20、15:22、15:29、15:30、15:32 连续 5 次，均为此模式；
等待 10–15 秒后再点击则从不复现。

## 改进方案（按收益/成本排序）

### P0：把 Application.onCreate 的主线程工作继续外移

- `CorrectionPriors.init`：ONNX 模型路径解析与状态加载改为懒加载（首次用到纠错时再初始化）
- `FileLogger`：日志文件创建改为后台线程（初始化本身只是建目录+打开流，但仍在主线程）
- 目标：把 onCreate 主线程耗时压到 200ms 内

### P1：启动期输入防护

在 IME 未就绪期间，对触摸事件做「吞掉并给出进度提示」而不是让系统等满 5 秒：

- `XimeInputMethodService` 已有 `isDeploying` 状态，可在该状态下把键盘视图设为
  `importantForAccessibility` + 拦截点击，避免系统判定输入超时

### P2：测量方法规范化

- 调试脚本统一「启动后等待 12s 再交互」
- 需要精确计时时用 `am start -W` 的 TotalTime，而不是靠点击是否响应推断

## 本次已修复（与本报告相关）

- `KeysConfigHelper` 全量 YAML 解析（真机约 1.8s）不再每次 `initRimeEngine` 都同步执行
- 插件备份扩展点超时从 180s 放宽到 300s 且不再停用插件

## 未验证项

`/data/anr/` 下的 trace 文件需要 root 才能读取，本机 adb shell 无 `su`，
因此**没有拿到 ANR 发生瞬间的 Java 堆栈**。上面的归因基于时间线日志与
Choreographer/Davey 证据，不是堆栈直证。
