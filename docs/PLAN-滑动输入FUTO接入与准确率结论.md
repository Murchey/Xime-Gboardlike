# 滑动输入 FUTO Swipe 接入：实现、测试与 70% 目标结论

## 0. 摘要

- 已把 FUTO Swipe 编码器（honorable_sturgeon）接入 26 键中文全拼/双拼滑动输入：
  轨迹预处理 → ExecuTorch 推理 → 受方案编码约束的 CTC 前缀 beam → 现有 Rime
  查询/重排 → 候选栏；模型不可用或失败时自动回退几何解码。
- 运行时不依赖自建 ET 原生库：官方 executorch-android 1.5.1 AAR 可用，但 Java 层
  没有 BOOL Tensor；用一个继承 Tensor 的 FutoBoolTensor 解决 layout_mask 传入问题，
  已在 x86_64 模拟器上验证。
- 只在 arm64-v8a / x86_64 上启用（AAR 只带这两个 ABI），32 位设备自动回退。
- 在同一批 16 条真实小鹤双拼轨迹上：

| 指标 | 几何解码 | FUTO 神经 |
|---|---|---|
| 编码 top-1 完全正确 | 4/16（25.0%） | 5/16（31.3%） |
| 编码 top-5 含正解 | 5/16（31.3%） | 5/16（31.3%） |
| 编码 top-16 含正解 | 5/16（31.3%） | 8/16（50.0%） |
| 编码 top-64 含正解 | — | 10/16（62.5%） |
| 编辑距离 ≤1 | 7/16（43.8%） | 7/16（43.8%） |
| 字符级准确率（1 - 总编辑距离/总长度） | — | 84.1% |

- 结论：**编码 top-1 的 70% 目标没有达成**，当前上限是 top-64 覆盖 10/16（62.5%）。
  6 条轨迹的正解即使把 beam 加到 2000 也不出现，属于模型/输入分布问题，不是重排能补的。
  下面列出已做过的两轮调参、失败原因、以及要真正达到 70% 还需要什么。

## 1. 目标与范围

目标（用户要求）：
- 执行已批准的技术方案，把 FUTO Swipe 编码器用于滑动输入改造；
- 改造后跑测试，再做一轮修改；
- 首选正确率至少 70%；做不到则在 docs 写出原因；
- 可用 adb、可中文 commit、不推送。

范围：
- 只接编码器，不接英文 decoder（magic_macaw）和英文 context LM；
- 不改变滑动交互、候选确认、Rime 语义；
- 不改 T9、五笔、手写、语音、对象存储。

## 2. 实现

### 2.1 运行时

- 依赖：org.pytorch:executorch-android:1.5.1（官方 AAR）。
- AAR 只含 arm64-v8a、x86_64 的 libexecutorch.so；16KB 页对齐已满足。
- Java 层 Tensor 没有 BOOL dtype，而模型 layout_mask 必须是 bool；新增
  FutoBoolTensor（extends Tensor，dtype=BOOL，直接 ByteBuffer）后，
  JNI 校验通过，forward 正常。
- 模型资产：app/src/main/assets/futo-swipe/honorable_sturgeon/model_fp32.pte
  （2.65MB）与 metadata.json、LICENSE.md；首次使用时复制到 filesDir 供加载。
- 预热：XimeApplication 启动时在 IO 协程里 initialize，失败只记日志。

### 2.2 解码链路

- FutoFeaturePreparer：
  - 用所有字母键矩形并集 bbox 归一化到 [0,1]（标准 26 键正好复现 FUTO 的
    0.05..0.95 / 0.167..0.833 坐标系）；
  - 60Hz 时间均匀重采样后取 64 点；
  - 键按编码排序，重复键原样保留，由 FutoEmissions 做 logsumexp 合并；
- FutoSwipeEncoder：Module.load + forward，输入 [1,2,64]、[1,64,2]、bool[1,64]，
  输出 log_emissions [1,32,65]、intention [1,32,1]；输出数组显式 copyOf。
- CtcTrieBeam：受 GlideSpellingTrie（真实方案编码）约束的 CTC 前缀 beam，
  beam=600、最多 48 字母、收尾只保留完整音节边界；默认 gamma=0（原始 CTC）。
- FutoHypothesisProvider：轨迹 → 预处理 → 推理 → beam，返回 top-64 假设；
  设置开关关闭、模型缺失、ABI 不支持、异常时返回 null。
- GlideKeyboardController：预览继续几何解码，抬手 final 才尝试神经；神经为空/异常
  回退几何；仍复用原有 Rime 查询 + 位次/gram 融合，不自动上屏。
- 设置：新增「神经滑动解码（实验）」开关（默认开），文案中带
  Powered by FUTO Swipe 可见署名；关于页与 NOTICE.txt 增加许可证条目。

### 2.3 许可证

- FUTO Model Weights License 1.0：允许使用/分发，但必须向最终用户展示
  powered by "FUTO Swipe"；随包附 LICENSE.md；不得移除版权声明。
- 本项目不在 F-Droid 发布，模型资产随 GitHub/Gitee 包分发。

## 3. 评测方法与数据

数据：tmpImg/xime_glide_20261111_112000.jsonl
- 20 条真实小鹤双拼轨迹，4 条标注「无效内容」被排除，16 条可用；
- 全部是 1.2–7.5 秒、6–16 键的中长句，来自同一位使用者；
- 标注是**拼音编码**（如 ilwddetmqi），不是中文文本，因此本文只能严谨地测
  「编码是否正确」，中文首选需要另有 ground truth 才能测。

基线：
- 几何解码（采集时记录的 8 条候选）：top-1 4/16，top-5 5/16，含正解 5/16；
- 神经：top-1 5/16，top-16 含正解 8/16，top-64 含正解 10/16。

评测工具：
- tools/futo-eval/eval.py（Python + ExecuTorch 1.5.1 + torch CPU）
- app 的 JVM 测试 CtcTrieBeamGoldenTest（与 Python 逐条对齐，覆盖 10/16）
- 模拟器 instrumentation 测试 FutoEncoderGoldenTest（AAR 推理 + beam，
  覆盖 10/16，top-1 与 Python 全部一致）

## 4. 逐条结果（gamma=0，top-64）

| 轨迹 | 期望编码 | 神经 top-1 | 距离 | 正解排名 |
|---|---|---|---|---|
| g111046-1 | ilwddetmqi | ilwdertmqi | 2 | 未进入 2000 |
| g111046-3 | jqxlui | jqxlui | 0 | 0 |
| g111046-4 | nidobmdebnqk | nidobmdebmql | 2 | 32 |
| g111046-6 | yj | yuhj | 2 | 7 |
| g111046-7 | yipwwogu | yowogu | 3 | 未进入 2000 |
| g111046-9 | kfdejifgklxkqisi | kfeifgklxkqisi | 2 | 36 |
| g111046-10 | mnxnycfgda | mnxnycfgda | 0 | 0 |
| g111046-11 | pkgouzji | pkgouzji | 0 | 0 |
| g111046-12 | jtqulkui | jtqulkui | 0 | 0 |
| g111046-13 | jixmtnvjvrjquimk | jixmtnvjvejquimk | 1 | 11 |
| g111046-14 | dahdhhxkkcdouz | dahdhxkcdouz | 2 | 未进入 2000 |
| g111046-15 | wuwwqiytxx | wuqiux | 5 | 未进入 2000 |
| g111046-16 | veuihxdsuurufa | veiuhxsurufa | 4 | 未进入 2000 |
| g111046-18 | vfxligwwbdwjfuwg | vxligwbdwjfuwf | 3 | 未进入 2000 |
| g111046-19 | vhgr | vhgr | 0 | 0 |
| g111046-20 | wovebaqldjdcpmzo | wovebaqldhdcpmzo | 1 | 5 |

- top-1 5/16 = 31.3%；
- 正解在 top-64 的 10 条；不在 top-2000 的 6 条；
- 字符级准确率 = (170 - 27) / 170 = 84.1%；
- 编辑距离 ≤1：7/16；≤2：12/16（75%）。

## 5. 改造后的第二轮调参（均已实测，未采用）

| 实验 | 结果 | 结论 |
|---|---|---|
| FUTO 英文 gamma=0.4056 + lambda=0.0176 | top-16 覆盖 7/16 | 比 gamma=0（8/16）更差，改用 gamma=0 |
| beam 100 / 200 / 600 / 2000 | 覆盖 8 / 8 / 8 / 10（top-16 / top-64） | beam 不是瓶颈 |
| FUTO 长度剪枝 gamma_prune=0.4234 beta_prune=1.0382 | top-16 6/16 | 中文长句上更差 |
| 弧长重采样（64 点均匀按空间） | top-16 5/16，top-1 2/16 | 明显更差 |
| 1.5s / 1.2s 重叠窗口 emissions 拼接 | top-16 7/16 与 8/16，top-1 2/16 与 3/16 | 更差，且窗口推理变慢 |
| 词典词码约束 beam（787684 个词码） | top-16 8/16，top-64 8/16，top-1 5/16 | 过度剪枝，4 条非词串被排除 |
| 音节 bigram 重排（rime-ice 词频） | alpha=2 时 top-16 10/16 但 top-1 降到 3/16 | 首选变差，不采用 |
| intention(lambda) 加权 | 无正收益 | 不采用 |

因此当前保留的配置是：时间重采样、gamma=0、beam=600、top-64、几何回退。

## 6. 为什么 70% 没做到

1. **模型训练分布不匹配**
   - FUTO 编码器在英文单词滑动上训练；本批数据是 1.2–7.5 秒的中文长句，
     64 点输入 + 32 个输出步，越长每键分到的步数越少；
   - 6 条轨迹的期望编码在 beam=2000 里都不出现，说明不是排序问题，
     而是模型没有产生这条字母路径。

2. **中文语言侧缺位**
   - 只接 encoder；英文 decoder 和英文 context LM 不能用于中文；
   - 尝试在 beam 里加音节 bigram / 词码约束，首选反而变差或覆盖下降；
   - 现有 Rime + gram 重排发生在候选阶段，救不回「编码集合里没有正解」的样本。

3. **数据量太小、标注口径限制**
   - 16 条可用轨迹、1 位使用者，没有留出集；调参容易过拟合；
   - 4 条无效样本被排除；没有短词/常用短语样本；
   - 标注是拼音编码而不是中文文本，无法严谨评估「首选整句是否等于用户想打的句子」。

4. **指标口径**
   - 如果「正确率」指编码 top-1 完全一致：31.3%，且理论上限只有 62.5%
     （6 条正解不在任何候选里）；
   - 如果指候选列表（top-64）含正解：62.5%；
   - 如果指字符级准确率：84.1%；编辑距离 ≤2 为 75%。
     本文按更严格的口径（编码 top-1）判定为未达标。

5. **设备侧已知差异**
   - g111046-19 的 emissions 在 ET Android 后端（x86_64 模拟器与 arm64 真机
     表现一致，max-abs 均为 2.268）与桌面 Python 参考差异较大（756/2080 个值
     >0.1，top-1 仍一致），其余 15 条 max-abs ≤1e-2；属 ET 后端数值路径差异，
     非 x86 特有，已记录为已知问题，未影响 top-1/覆盖。

## 7. 要达到 70%（编码 top-1）还需要什么

1. 数据：至少 100–300 条多使用者、已标注中文文本的滑动轨迹，并覆盖短词/短语、
   全拼/双拼、分号 ing、共线键；当前 16 条不足以调参或验收。
2. 模型：用中文滑动数据微调 encoder，或训练中文 decoder（在 encoder 特征上）；
   现有英文训练权重对中文长句的字母召回不足。
3. 语言模型：把中文词/短语 LM 放进 beam（现在只在候选阶段重排），
   并让 LM 约束「哪些字母路径可能存在」；仅靠后处理无法恢复缺失路径。
4. 长句：对 >3 秒轨迹做更可靠的词边界切分/分段解码（本轮的固定窗口拼接更差，
   需要按停顿/词边界做，而不是按时间等分）。
5. 评测：用中文文本 ground truth 测首选整句/前五整句，并按长度分组；
   目前只能用拼音编码近似。

## 8. 复现

- 几何基线：
  node tools/glide-eval.mjs tmpImg/xime_glide_20261111_112000.jsonl
- 神经离线评测：
  python tools/futo-eval/eval.py --data tmpImg/xime_glide_20261111_112000.jsonl --topk 64
- JVM 回归：
  ./gradlew :app:testDebugUnitTest --tests "com.kingzcheung.xime.glide.neural.*"
  （CtcTrieBeamGoldenTest 会打印 coverage=10/16）
- 模拟器/真机：
  ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.kingzcheung.xime.glide.futo.FutoEncoderGoldenTest
  （需 arm64 真机或 x86_64 模拟器；断言 top-1 全对、覆盖 ≥10/16）

## 9. 结论

- 改造已完成：编码器接入、约束 CTC 解码、自动回退、开关、许可与测试均已落地；
- 与几何基线相比，候选集合含正解从 5/16 提升到 10/16（31% → 62.5%），
  但编码 top-1 仅 5/16（31%），**未达到 70% 的首选正确率目标**；
- 未达标的主因是模型对中文长句的字母召回不足、中文语言模型缺位、评测数据过小；
  这些不是靠继续调 beam/参数能解决的，需要数据、微调/中文 decoder 与 beam 内 LM。
