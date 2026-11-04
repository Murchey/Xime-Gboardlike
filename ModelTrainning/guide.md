# 模型训练指南（ModelTrainning）

本目录是 **XimeGboard 智能联想模型**的训练工作区。从原始书籍语料到可在 App 中导入的 `.zip` 模型包，全流程脚本化，手动启动即可复现。

---

## 目录结构

```
ModelTrainning/
├── scripts/                    # 全流程脚本
│   ├── extract_corpus.py       # 1. 从 E:/ebook 抽取通用语料
│   ├── extract_curated.py      # 2. 抽取精选书目（含文言/白话分流）
│   ├── clean_corpus.py         # 3. 语料清洗、去重、切分验证集
│   ├── build_dataset.py        # 4. 合并通用+精选，控制各来源权重
│   ├── train.py                # 5. 训练 GRU 语言模型
│   ├── export_onnx.py          # 6. 导出 ONNX + int8 动态量化
│   ├── eval_model.py           # 7. 离线评估（前缀预测 top-k）
│   ├── package_zip.py          # 8. 打包为 App 可导入的 zip
│   ├── profile_books.py        # 辅助：分析书目文言/白话占比
│   ├── sample_24shi.py         # 辅助：抽样二十四史文白构成
│   └── merge_corpus.py         # 辅助：多分片合并去重
├── data/                       # 语料（生成物，不入库）
├── models/                     # 模型产物（生成物，不入库）
├── dist/                       # 可导入 zip（生成物，不入库）
├── logs/                       # 训练日志（生成物，不入库）
└── guide.md                    # 本文件
```

---

## 环境准备

**Python 3.10+**（3.12 已验证），安装依赖：

```bash
pip install torch onnx onnxscript onnxruntime mobi pymupdf ebooklib numpy
```

> **GPU 说明**：默认使用 CPU 版 PyTorch（安装快，本任务完全够用）。
> 如需 GPU 加速，安装 CUDA 版：
> `pip install torch --index-url https://download.pytorch.org/whl/cu124`
> 脚本会自动检测 CUDA 并启用。

---

## 手动启动训练（完整流程）

以下命令均在 **仓库根目录**（`Xime-Gboardlike/`）下执行。

### 第 1 步：抽取通用语料（约 20 分钟）

从 `E:/ebook` 随机抽样 24 本书，每本最多 8000 句。每本书独立子进程解析，超时 90 秒自动跳过，避免个别超大/图片型书籍阻塞。

```bash
python ModelTrainning/scripts/extract_corpus.py --per-dir 3 --sentences-per-book 8000
```

输出：`ModelTrainning/data/corpus_raw.txt`

### 第 2 步：抽取精选书目（约 5 分钟）

抽取指定的 7 本代表性书目，对《二十四史》自动做文言/白话分流（只保留白话译文）：

```bash
python ModelTrainning/scripts/extract_curated.py
```

输出：`ModelTrainning/data/curated_*.txt`

### 第 3 步：清洗通用语料（约 1 分钟）

去 HTML/噪声、字符白名单、切分训练/验证集：

```bash
python ModelTrainning/scripts/clean_corpus.py
```

输出：`ModelTrainning/data/corpus.txt`（训练集）、`val.txt`（验证集）

### 第 4 步：合并语料（约 1 分钟）

合并通用+精选语料，控制二十四史权重在 8% 以内：

```bash
python ModelTrainning/scripts/build_dataset.py
```

输出：`ModelTrainning/data/train.txt`（约 23.5 万行 / 739 万字符）、`val2.txt`

### 第 5 步：训练（约 45 分钟 CPU）

```bash
python ModelTrainning/scripts/train.py \
  --corpus ModelTrainning/data/train.txt \
  --out ModelTrainning/models \
  --epochs 3 \
  --dim 384 \
  --layers 2 \
  --batch 512 \
  --max-vocab 8000 \
  --punct-weight 0.35
```

**关键参数说明**：

| 参数 | 默认值 | 说明 |
| --- | --- | --- |
| `--dim` | 384 | GRU 隐层维度。降到 320 可减小模型体积（约 9MB），质量损失很小 |
| `--layers` | 2 | GRU 层数 |
| `--epochs` | 3 | 训练轮次。loss 约 6.3 → 4.3 |
| `--punct-weight` | 0.35 | 标点 token 的损失权重。<1 表示降权（推荐 0.3–0.5） |
| `--max-vocab` | 8000 | 词表上限（实际约 6500） |
| `--ctx` | 64 | 上下文长度（字符），与 App 的 MAX_CONTEXT_LENGTH 一致 |

输出：`ModelTrainning/models/vocab.json`、`model.pt`

### 第 6 步：导出 ONNX + 量化（约 1 分钟）

```bash
python ModelTrainning/scripts/export_onnx.py --models ModelTrainning/models
```

输出：`model.onnx`（float32）、`model_int8_dynamic.onnx`（int8，约 8–12MB）

### 第 7 步：评估效果（约 10 秒）

```bash
python ModelTrainning/scripts/eval_model.py --models ModelTrainning/models --topk 5
```

人工检查常见前缀的预测质量，如：

```
我想      -> 要 | 到 | 你 | 我 | 起
可以      -> 说 | 看 | 在 | 让 | 把
北京      -> 的 | 城 | 人 | 有 | 一
```

### 第 8 步：打包可导入 zip（约 10 秒）

```bash
python ModelTrainning/scripts/package_zip.py --models ModelTrainning/models
```

输出：`ModelTrainning/models/XimeGboard-custom-predictive-v1.0.zip`

---

## 在 App 里导入

1. 把 zip 传到手机（USB / 网盘 / adb push）
2. App → 设置 → 扩展 → **本地模型** → 右上角 **导入模型**
3. 选择目标模型（如 predictive-text-small），再选你的 zip
4. App → 设置 → 智能 → **智能联想** → 开启 → 模型卡片点「使用」
5. 在输入框测试，观察候选栏联想

> **导入是覆盖同名文件**，不会自动备份。建议先用「导出模型」备份原模型。

---

## 真机验证

```bash
# 确认模型加载成功
adb logcat -s InferenceService NativeOnnxEngine AssociationManager
```

预期日志：

```
OnnxAssociationEngine: Using model: model_int8_dynamic.onnx (12170862 bytes)
OnnxAssociationEngine: Prediction model loaded via IPC
AssociationManager: initialized: model=true, cache=true
```

---

## 常见问题

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| 模型文件不完整 | zip 缺文件 | 确保含 vocab.json + model_int8_dynamic.onnx |
| 联想全是怪字 | 词表 id 错位 | 检查 id 0–3 是否保留，[BOS]=1、[UNK]=3 |
| 联想为空 | 候选 id < 4 被跳过 | 真实 token 的 id 必须 ≥ 4 |
| 标点抢占候选 | 语料标点密度高 | 增大 punct-weight 的分母（如 0.3）或增加实词语料 |
| 训练 loss 不下降 | 语料太少 | 至少 10 万句；补充通用语料 |
| 模型太大 | dim 过高 | 降到 --dim 320 或减少 vocab |

---

## 语料来源说明

详见 data/CORPUS_NOTES.md。要点：

- **通用语料**：从 E:/ebook 24 本书随机抽样，覆盖科幻、小说、人文、技术
- **精选书目**：金庸全集、诺贝尔文学奖集、KJ帕克、沈从文、围城、省委大院
- **二十四史**：只取白话译文（剔除文言原文），总量限制在 8%
- 文言虚词不会挤占"的/了/是"等功能字分布

---

## 相关文档

- App 端模型契约说明：modelGuide.md
- 仓库 README：../README.md
