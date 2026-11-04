# 训练你自己的 XimeGboard 预测模型

本文档面向想把「智能联想」换成自己训练的下一词预测模型的用户。全程在你自己的电脑上完成，App 只负责导数据和加载模型。

- 对应仓库：`D:\Projects\vibecoding\InputMethod\Xime-Gboardlike`
- 对应设置页：App → 设置 → 智能 → 智能联想
- 模型契约来源：`app/src/main/jni/onnx_jni.cc`、`app/src/main/java/com/kingzcheung/xime/service/InferenceService.kt`

---

## 1. 模型在 App 里做什么

| 项目 | 说明 |
| --- | --- |
| 任务 | 给定已输入的上下文，预测「下一个 token」的候选，供候选栏联想 |
| 输入 | `input_ids`：`int64[1, L]`，L ≤ 64（超出截断为最后 64 个字符） |
| 输出 | `logits`：`[1, L, V]`，App 只取**最后一个位置**做排序 |
| 词表 | `vocab.json`，token → id 的映射 |
| 特殊 id | `[BOS]`=1、`[UNK]`=3；**id 0–3 保留**，候选扫描从 id 4 开始 |
| 分词 | 字符级 +「最长匹配多字词」贪心；序列前自动插入 BOS |
| 运行时 | ONNX Runtime，优先 NNAPI，失败回退 CPU；动态量化 int8 |

> 这意味着：你的模型必须是「字符/词级自回归语言模型」，输入纯 id 序列，输出每个位置的词表 logits，**不需要 attention mask、不需要 KV cache**。

---

## 2. 采集数据

### 2.1 从 App 导出用户学习数据

App 会记录你上屏内容的相邻组合（bigram / trigram）与最近输入序列：

1. 装上你自己的 debug 或 release 包，正常输入一段时间（建议累计上万字符）。
2. 进入 **设置 → 智能 → 智能联想 → 用户学习数据**。
3. 先点 **保存数据**（把内存缓存刷到磁盘），再点 **导出学习数据**。
4. 在系统文件选择器里选一个你能访问的目录（例如 `Download`），得到 `xime_user_ngram_<时间戳>.json`。

导出文件结构：

```json
{
  "bigrams":  [{ "tokens": ["你", "好"], "count": 12 }],
  "trigrams": [{ "tokens": ["我", "爱", "你"], "count": 3 }],
  "recentInputs": ["今", "天", "天", "气", "不", "错"],
  "meta": { "format_version": 1, "app_version": "1.0.7", "android_sdk": 33,
            "exported_at_ms": 1793700000000, "bigram_count": 1, "trigram_count": 1,
            "recent_count": 6 }
}
```

> 隐私：n-gram 缓存只保存相邻 token 组合与计数，**不保存完整原文**。导出前请自行确认无敏感内容。

### 2.2 转成训练语料

```powershell
cd D:\Projects\vibecoding\InputMethod\Xime-Gboardlike
python scripts/export_ngram.py "$env:USERPROFILE\Downloads\xime_user_ngram_20261104_101500.json" --out corpus.txt
```

先看统计（不写文件）：

```powershell
python scripts/export_ngram.py "$env:USERPROFILE\Downloads\xime_user_ngram_20261104_101500.json" --stats
```

数据太少时（几百字符）训不出可用模型，优先补充通用语料。

### 2.3 补充通用语料

个人数据只覆盖你的用词习惯。建议再混入通用中文语料（新闻、百科、字幕均可，注意授权），拼成 `corpus.txt`，一行一句。经验配比：个人数据 20%–30%，通用语料 70%–80%。

---

## 3. 构建词表

`vocab.json` 支持两种布局，App 都能读：

```json
{ "model": { "vocab": { "[PAD]": 0, "[BOS]": 1, "[EOS]": 2, "[UNK]": 3, "你": 4, "好": 5 } } }
```

或直接：

```json
{ "vocab": { "[PAD]": 0, "[BOS]": 1, "[EOS]": 2, "[UNK]": 3, "你": 4, "好": 5 } }
```

规则：

1. **id 0–3 必须留空**（`[PAD]/[BOS]/[EOS]/[UNK]`），真实 token 从 4 开始。
2. `[BOS]` 的 id 必须是 1，`[UNK]` 必须是 3；App 按名字查找，找不到才退回默认值。
3. 想支持多字词，把词直接放进词表，App 的分词器会做「最长匹配」。
4. 词表大小 = 模型输出维度 V，务必与 ONNX 输出的最后一维一致。

生成脚本示例：

```python
import json
from collections import Counter

chars = Counter()
for line in open("corpus.txt", encoding="utf-8"):
    chars.update(line.strip())

vocab = {"[PAD]": 0, "[BOS]": 1, "[EOS]": 2, "[UNK]": 3}
for ch, _ in chars.most_common():
    if ch not in vocab:
        vocab[ch] = len(vocab)

json.dump({"vocab": vocab}, open("vocab.json", "w", encoding="utf-8"), ensure_ascii=False, indent=2)
print("vocab size:", len(vocab))
```

---

## 4. 训练模型

以下以 PyTorch 为例。目标：约 2–8M 参数的字符级 GRU，手机 CPU 上单次推理毫秒级。

```python
import json, torch, torch.nn as nn

vocab = json.load(open("vocab.json", encoding="utf-8"))["vocab"]
V, BOS, UNK = len(vocab), vocab["[BOS]"], vocab["[UNK]"]
CTX = 64

class NextTokenModel(nn.Module):
    def __init__(self, V, dim=256, layers=2):
        super().__init__()
        self.emb = nn.Embedding(V, dim)
        self.rnn = nn.GRU(dim, dim, layers, batch_first=True)
        self.out = nn.Linear(dim, V)

    def forward(self, x):
        h, _ = self.rnn(self.emb(x))
        return self.out(h)          # [B, L, V]

def encode(line):
    ids = [BOS] + [vocab.get(ch, UNK) for ch in line]
    return ids

def batches(path, batch=64):
    buf = []
    for line in open(path, encoding="utf-8"):
        ids = encode(line.strip())[: CTX + 1]
        if len(ids) < 3:
            continue
        buf.append(ids)
        if len(buf) == batch:
            yield pad(buf); buf = []
    if buf:
        yield pad(buf)

def pad(batch):
    L = max(len(x) for x in batch)
    x = torch.full((len(batch), L), 0, dtype=torch.long)
    y = torch.full((len(batch), L), -100, dtype=torch.long)
    for i, ids in enumerate(batch):
        x[i, :len(ids)-1] = torch.tensor(ids[:-1])
        y[i, :len(ids)-1] = torch.tensor(ids[1:])
    return x, y

model = NextTokenModel(V)
opt = torch.optim.AdamW(model.parameters(), lr=3e-4)
lossf = nn.CrossEntropyLoss(ignore_index=-100)

for epoch in range(6):
    total = 0.0
    for x, y in batches("corpus.txt"):
        loss = lossf(model(x).reshape(-1, V), y.reshape(-1))
        opt.zero_grad(); loss.backward(); opt.step()
        total += loss.item()
    print(f"epoch {epoch} loss {total:.4f}")

torch.save(model.state_dict(), "model.pt")
```

想要更好的效果，把 GRU 换成 2–4 层 Transformer（因果 mask），但注意**不要**引入 attention mask 输入，App 只喂 `input_ids`。

---

## 5. 导出 ONNX

输入名必须是 `input_ids`，输出名必须是 `logits`：

```python
import torch
from NextTokenModel import NextTokenModel   # 或把上面的类复制过来

model = NextTokenModel(V); model.load_state_dict(torch.load("model.pt")); model.eval()
dummy = torch.randint(0, V, (1, 16), dtype=torch.long)
torch.onnx.export(
    model, dummy, "model.onnx",
    input_names=["input_ids"], output_names=["logits"],
    dynamic_axes={"input_ids": {1: "seq"}, "logits": {1: "seq"}},
    opset_version=17,
)
```

自检：

```python
import onnxruntime as ort, numpy as np
s = ort.InferenceSession("model.onnx")
out = s.run(None, {"input_ids": np.array([[1, vocab["你"], vocab["好"]]], dtype=np.int64)})
print(out[0].shape)   # 期望 (1, 3, V)
```

---

## 6. 动态量化（生成 `model_int8_dynamic.onnx`）

```python
from onnxruntime.quantization import quantize_dynamic, QuantType

quantize_dynamic("model.onnx", "model_int8_dynamic.onnx", weight_type=QuantType.QInt8)
```

量化后体积通常降到 1/4，精度损失对小词表模型可接受。若发现候选质量明显下降，改回 float32 的 `model.onnx` 直接重命名亦可（App 只认文件名）。

---

## 7. 打包成 App 可导入的 zip

App 的「本地模型 → 导入模型」接受单文件、`.zip`、`.tar.bz2`。推荐 zip，且**不要带子目录**：

```powershell
Compress-Archive -Path vocab.json, model_int8_dynamic.onnx -DestinationPath my-predictive.zip -Force
```

也可以在 App 里用「本地模型 → 导出模型」导出一个现成 zip 作为模板，替换其中两个文件即可（`manifest.json` 可以删掉，不影响导入）。

---

## 8. 在 App 里加载

1. 把 zip 传到手机（USB / 网盘 / `adb push` 都行）。
2. App → 设置 → 扩展 → 本地模型 → 右上角 **导入模型**。
3. 选择要覆盖的目标模型（如 `predictive-text-small`），再选你的 zip。
4. 回到 **设置 → 智能 → 智能联想**，确认开关打开、模型显示「已下载 / 使用中」。
5. 在「点击此处开始输入测试」里输入一段话，观察候选栏联想。

> 导入是**覆盖**同名文件，不会自动备份旧模型。建议先「导出模型」存一份原版。

---

## 9. 验证与排错

### 看日志

```powershell
adb logcat -s InferenceService NativeOnnxEngine AssociationManager
```

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| `Vocab file not found` | zip 里文件名不对 | 必须是 `vocab.json` 与 `model_int8_dynamic.onnx`，不能带子目录 |
| `NativeOnnxEngine.initialize failed` | ONNX 损坏或算子不支持 | 换 `opset_version=17` 重新导出；确认没有自定义算子 |
| 输出维度不是 `[1, L, V]` | 模型结构不含 batch/seq 维 | 确保 forward 返回 `[B, L, V]` |
| 联想全是单个怪字 | 词表 id 错位 | 检查 id 0–3 是否保留、`[BOS]` 是否为 1 |
| 联想为空但模型已加载 | 候选 id < 4 被跳过 | 真实 token 的 id 必须 ≥ 4 |
| 首次输入卡顿 | 模型太大 / 未预热 | 减到 ≤ 10MB；App 启动会做一次预热推理 |

### 质量自检

```python
# 用同样的分词逻辑，看模型对常见前缀的 top5
for prefix in ["今天天气", "我爱你", "谢谢"]:
    ids = [BOS] + [vocab.get(c, UNK) for c in prefix]
    logits = s.run(None, {"input_ids": np.array([ids], dtype=np.int64)})[0][0, -1]
    top = logits.argsort()[-5:][::-1]
    inv = {v: k for k, v in vocab.items()}
    print(prefix, "->", [inv.get(int(i), "?") for i in top])
```

人工看 20 个前缀，命中 60% 以上就算可用。

---

## 10. 用 App 导出的模型做版本管理

App 导出的 zip 带 `manifest.json`（模型 id、版本、文件大小与 SHA-256、导出时间），可以直接当发布物：

```powershell
# 校验导出包完整性
python -c "import zipfile,hashlib,sys; z=zipfile.ZipFile('my-model.zip'); [print(n, hashlib.sha256(z.read(n)).hexdigest()[:16]) for n in z.namelist()]"
```

把它放进你自己的 `models/index.yaml`（结构见 `app/src/main/java/com/kingzcheung/xime/model/ModelIndexLoader.kt`），就能在「模型管理」页里像官方模型一样下载与切换。

---

## 11. 常见问题

**Q：能不能只用 App 里的学习数据训练？**
可以，但数据量通常不够。建议个人数据 + 通用语料混合。

**Q：会不会上传我的数据？**
不会。导出完全本地，App 不做任何上传；n-gram 缓存也只存在 `filesDir/user_ngram_cache.json`。

**Q：训练要多长时间？**
CPU 上 200 万字符、6 epoch、2 层 GRU 大约 20–40 分钟；有 GPU 会快数倍。

**Q：模型多大合适？**
联想的收益随体积快速递减。8MB 以内（int8）覆盖日常使用足够；超过 30MB 在低端机上加载明显变慢。

**Q：可以同时保留官方模型吗？**
可以。把 id 换成新的（如 `my-predictive`），在 `ModelImportCatalog` 或你的 `index.yaml` 里登记，App 会并列显示。

---

## 12. 参考

- ONNX Runtime 量化文档：<https://onnxruntime.ai/docs/performance/model-optimizations/quantization.html>
- PyTorch ONNX 导出：<https://pytorch.org/docs/stable/onnx.html>
- 上游预测模型：<https://github.com/ximeiorg/predictive-text>
- 推理实现：`app/src/main/jni/onnx_jni.cc`、`app/src/main/java/com/kingzcheung/xime/service/InferenceService.kt`
