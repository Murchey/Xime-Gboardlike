# FUTO Swipe 离线评测

用 FUTO Swipe 编码器回放 Xime 的真实滑动轨迹，回答一个问题：

> 换成神经编码器之后，「候选集合里有没有正解」能提高多少？

这一层只评估「轨迹 → 合法编码串」，不跑 Rime、不跑候选排序，也不改 Android
App。端到端效果（首选整句命中率）必须在真机上另测。

## 数据来源

评测输入是 [GlideTraceRecorder.kt](../../app/src/main/java/com/kingzcheung/xime/glide/GlideTraceRecorder.kt)
导出的 glide_samples.jsonl：

- 每行一个 JSON；e=trace 是轨迹（points / keys / code / candidates），
  e=label 是人工标注（expected，后写覆盖先写）。
- 标注为「无效内容」的样本自动跳过。
- 当前仓库外的基线样本：D:/Projects/vibecoding/InputMethod/tmpImg/xime_glide_20261111_112000.jsonl
  （20 条小鹤双拼，16 条可用）。

## 环境

需要 Python 3.13 与两个固定版本：

- executorch 1.5.1（与 Android 侧计划使用的运行时同版本）
- torch 2.15.0（CPU 版即可；ExecuTorch 的 Python 绑定需要它加载动态库）

建议用干净的 venv / uv，避免污染系统 Python：

~~~bash
uv venv .venv-futo
uv pip install --python .venv-futo/bin/python \
  --index-url https://download.pytorch.org/whl/cpu torch==2.15.0
uv pip install --python .venv-futo/bin/python -r requirements.txt
~~~

Windows 上把 .venv-futo/bin/python 换成 .venv-futo/Scripts/python.exe。
注意：ExecuTorch 的 Python 绑定只用于桌面回放；Android 侧走自建 C++ 运行时。

## 用法

~~~bash
python tools/futo-eval/eval.py \
  --data ../tmpImg/xime_glide_20261111_112000.jsonl \
  --model ../futo-swipe/honorable_sturgeon/model_fp32.pte
~~~

常用参数：

- --spellings FILE：覆盖方案编码表。FILE 可以是 JSON 列表，或每行一条编码的文本。
  App 里最准确的表来自 RimeEngine.getGlideSpellings；离线时默认按 schema 猜：
  含 flypy 的用 421 音节的小鹤两键投影，其它全拼方案用 421 音节本身，
  其余双拼方案直接跳过并提示（避免拿错表得出假指标）。
- --beam 200：CTC 前缀 beam 宽度。
- --topk 16：进入「候选集合」的编码条数，默认与 App 的 GLIDE_HYPOTHESES 一致。
- --gamma / --lambda：长度归一参数，默认 0（原始 CTC 分数）。
  FUTO 公开的 encoder-only 参数是 gamma=0.4056、lambda=0.0176，但它们按英文
  单词调过；在这批中文长句上反而少覆盖 1 条（7/16 而不是 8/16）。
- --limit / --json-out：限制条数、导出机器可读结果。

输出每个样本一行，最后给出汇总：

~~~text
=== FUTO offline evaluation ===
usable: 16
neural_top1_correct: 5
neural_top1_correct_pct: 31.2
neural_contains: 8
neural_contains_pct: 50.0
neural_edit_distance_le_1: 7
neural_edit_distance_le_1_pct: 43.8
geometry_contains: 5
geometry_contains_pct: 31.2
geometry_top1_correct: 4
geometry_top1_correct_pct: 25.0
union_contains: 8
union_contains_pct: 50.0
~~~

指标口径：

- neural_top1_correct：神经首选编码与 expected 完全一致。
- neural_contains：神经 top-K 里含 expected（解码器天花板）。
- geometry_contains：采集时记录的几何候选（8 条）里含 expected；与现有
  tools/glide-eval.mjs 的口径一致。
- union_contains：神经 top-K 与几何候选的并集含 expected。
- edit distance 按编码字符串计算，仅用于看错误量级，不等价于中文整句准确率。

## 当前基线（2026-11，同一批 16 条真实小鹤轨迹）

| 指标 | 几何（采集的 8 条候选） | FUTO 神经 top-16（gamma=0） |
|---|---|---|
| 含正解 | 5/16（31%） | 8/16（50%） |
| top-1 | 4/16（25%） | 5/16（31%） |
| 编辑距离 ≤1 | 7/16（44%） | 7/16（44%） |
| 与几何并集含正解 | — | 8/16（50%） |

补充观察：

- beam 100 / 200 / 600 的覆盖率与 top-1 完全相同，瓶颈不在搜索宽度。
- 神经覆盖了全部 5 条几何正解，并额外多出 3 条；并集没有超过神经本身。
- 评测口径必须只保留「完整音节边界」的最终串。早期临时脚本把未完成音节的
  半截前缀也算进 top-16，会虚高覆盖率；本工具在收尾时强制 is_complete。
- 这批样本全部是 1.2–7.5 秒的长句，超出「英文单词滑动」的训练分布；
  长轨迹分块是 Phase 0 要验证的下一项，不要直接把这组数字当最终结论。

## 导出测试夹具

~~~bash
python tools/futo-eval/export_golden.py \
  --data ../tmpImg/xime_glide_20261111_112000.jsonl \
  --ids g111046-3,g111046-10,g111046-12
~~~

默认写到 app/src/test/resources/futo/golden.json，包含：

- 归一化后的 features / keys / mask（与 App 侧 FutoFeaturePreparer 的输出格式一致）；
- 编码器原始 log emissions（32×65）与 intention（lambda）；
- Python 参考解码器的 top-K；
- 采集时的几何 code / candidates，便于在 JVM 测试里直接对比。

用途：Kotlin 侧 CtcTrieBeam 与 FeaturePreparer 的单测、Android 侧 JNI 输出的
golden 对齐（max-abs 误差目标 ≤1e-3）。

## 已知限制

- 只支持小鹤（double_pinyin_flypy）与全拼方案；其它双拼需用 --spellings 显式给表。
- 输入是单使用者的长句样本，统计意义有限；至少 100 条多使用者标注后再做最终结论。
- 不做 Rime 查询、分词、gram 重排；不测中文首选，只测编码集合。
- 不是生产代码：生产 decoder 在 App 的 Kotlin 侧实现，须与此处结果逐条对齐。
- 模型权重受 FUTO Model Weights License 1.0 约束；本目录不分发模型，只引用你本地的副本。
