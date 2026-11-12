# 系统语音引擎接入（android.speech.SpeechRecognizer）

## 一、为什么必须走旁路

既有 ASR 架构是**流式**的：App 自己持 `AudioRecord` 采集，逐块调用
`AsrBackend.processAudioChunk()`。而系统 `SpeechRecognizer` 是黑盒——
它自己持有麦克风、经 `RecognitionListener` 回调结果，**无法接收外部音频块**。

硬塞进现有录音循环的后果：两路同时抢麦克风，谁都收不到音。
因此实现为旁路：`AsrBackend.capturesAudioItself` 标记为 `true` 时，
`SpeechRecognitionManager` 不建 `RecordingThread`、不建 `AudioRecord`，
由系统引擎自己驱动。

## 二、实现分层

| 层 | 文件 | 职责 |
|---|---|---|
| 探测 | `SystemSpeechAvailability` | `quickCheck`（查服务包 + `isRecognitionAvailable`，任意线程安全）/ `check`（追加真实创建实例校验） |
| 引擎 | `SttEngine.SYSTEM` | 枚举第四项，`prefValue = "system"` |
| 解析 | `SttEngineResolver` | 新增 `systemAvailable` 参数与 `SYSTEM_MISSING` |
| 后端 | `SystemSpeechAsrBackend` | `processAudioChunk` 空实现；`start` 切主线程并每次重建实例；AOSP 错误码映射 |
| 旁路 | `SpeechRecognitionManager` | 按标记跳过录音线程；`cancelRecognition` 显式调后端 cancel |
| UI | `SpeechToTextSettingsScreen` | 第四张单选卡片 + 配置区，如实展示探测结果 |

## 三、机型适配要点

1. **包可见性**：Android 11+ 需在 manifest 声明
   ```xml
   <queries><intent><action android:name="android.speech.RecognitionService" /></intent></queries>
   ```
   未声明时 `queryIntentServices` 返回空，会误报「本机没有可用的系统语音引擎」。
2. **主线程约束**：`createSpeechRecognizer` 在非主线程调用会抛异常，
   若用完整探测会被误判为「不可用」。真机实测过这个坑：
   `探测=available:true` 但 `factory=false`，改用 `quickCheck` 后一致。
3. **服务差异**：不同机型可能是 Google 语音服务、厂商引擎（如小米
   `com.xiaomi.mibrain.speech`）、或完全没有。只依赖 AOSP 标准回调，
   不假设任何厂商 extra。
4. **缺少中间结果**：`EXTRA_PARTIAL_RESULTS` 已开启，但部分服务不返回，
   面板需容忍 `onPartialResult` 不回调。
5. **无振幅/频谱**：系统引擎不给原始音频，`onAmplitude`/`onSpectrum` 无从产生，
   面板波纹降级显示。
6. **权限**：`RECORD_AUDIO` 未授予时回调「缺少录音权限，请在系统设置中授权」，
   不静默失败。

## 四、语义约定

- **不静默换引擎**：选系统引擎但本机不可用时只报 `SYSTEM_MISSING`，
  与 `PLUGIN` 缺失时可降级到内置在线的规则**刻意不同**——系统引擎
  跨机型差异极大，换引擎会让用户以为在跑系统识别，出问题无从判断。
- **`ERROR_NO_MATCH` 不当作错误**：用户可能只是没说话或环境太吵，
  提示「没有识别到内容」而非报错。
- **每次 `start()` 重建 `SpeechRecognizer`**：部分厂商复用已 destroy 的实例会抛异常。

## 五、真机验证记录

设备：Xiaomi 21091116AC（Android 13），识别服务 `com.xiaomi.mibrain.speech`。

| 验证项 | 结果 |
|---|---|
| 探测可用性 | `available=true, reason=OK` |
| 服务名 | 「系统语音引擎」（与标题同名，已做展示去重） |
| 解析 | `engine=SYSTEM, unavailable=null, isAvailable=true` |
| 生命周期（已授权） | `start()=true` → `LISTENING` → `IDLE`，`errors=[]` |
| 生命周期（未授权） | 回调「缺少录音权限」，不崩 |
| 回归 | 内置在线后端 `capturesAudioItself=false`，不抢麦克风 |

## 六、已知限制

- 语言固定 `zh-CN`：系统引擎的语言支持因机型而异，不支持时走
  `ERROR_SERVER` / `ERROR_NO_MATCH`，已映射为可读提示。
- 不做「引擎列表让用户挑具体服务」：不同机器服务名不统一，投入产出低。
- `capturesAudioItself` 为接口默认方法（默认 `false`），既有三个后端无需改动。

## 七、端到端验证（SpeechRecognitionManager 真实路径）

不只是单元级探测，还走了 `SpeechRecognitionManager` 的完整链路：

```
preload → createBackend → SystemSpeechAsrBackend.start() → cancelRecognition
```

真机结果（`stt_engine=system` 已落盘）：

```
states = [PROCESSING, LISTENING, IDLE, IDLE]
errors = []
进入 LISTENING = true
```

**全程无错误**，确认录音旁路（不建 AudioRecord）与取消路径都正常。

### 一个排查记录

首次跑这个测试时误报「无法初始化语音引擎」：原因是 instrumentation 进程
的 SharedPreferences 与 IME 进程**不共享**，`setSttEngine` 写入没落盘，
`getSttEngine` 读到默认值 `BUILTIN_ONLINE`（未配 Key）→ 后端为 null。
改用 UI 真实切换（`stt_engine=system` 落盘）后测试通过。
**涉及偏好的验证必须走真实进程**，这是本项目反复踩到的坑。

## 八、设置页实测

第四张卡片显示正常：

```
系统语音引擎
本机已就绪
```

点击后 `stt_engine` 写入 `system`，配置区展开显示「系统语音引擎」。

## 九、本机服务的一个真实情况

这台小米的 `com.xiaomi.mibrain.speech`（v1.5.2）**包本身 `enabled=0`**（已停用），
但 `AsrService` 仍可被查询到。实际调用时系统会弹 `AsrRemovalNotice`
（提示该服务已移除）。

这不是我们的代码问题，而是**厂商主动下架**——正好印证计划里
「不同机器服务差异极大」的判断，也说明「不可用时明说」比「静默回退」更合理。
