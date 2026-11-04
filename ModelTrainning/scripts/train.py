# -*- coding: utf-8 -*-
"""训练字符级下一词预测模型（XimeGboard 智能联想）。

模型契约（与 app/src/main/jni/onnx_jni.cc 对齐）：
  输入  input_ids : int64[1, L]，L <= 64
  输出  logits    : float[1, L, V]，App 取最后一个位置
  vocab 中 id 0-3 保留（[PAD]/[BOS]/[EOS]/[UNK]），真实字符从 4 开始
  [BOS] 必须是 1，[UNK] 必须是 3

本脚本产出 vocab.json / model.onnx / model_int8_dynamic.onnx。
"""
from __future__ import annotations

import argparse
import json
import math
import time
from pathlib import Path

import torch
import torch.nn as nn
from torch.utils.data import DataLoader, Dataset

SPECIALS = ["[PAD]", "[BOS]", "[EOS]", "[UNK]"]
PAD, BOS, EOS, UNK = 0, 1, 2, 3


class TextDataset(Dataset):
    """按行切成 (x, y)，y 为右移一位的下一 token。"""

    def __init__(self, lines: list[list[int]], ctx: int):
        self.samples = []
        for ids in lines:
            for start in range(0, max(1, len(ids) - 1), ctx):
                piece = ids[start:start + ctx + 1]
                if len(piece) >= 2:
                    self.samples.append(piece)

    def __len__(self):
        return len(self.samples)

    def __getitem__(self, i):
        ids = self.samples[i]
        x = ids[:-1]
        y = ids[1:]
        return x, y


def collate(batch):
    max_len = max(len(x) for x, _ in batch)
    xs = torch.full((len(batch), max_len), PAD, dtype=torch.long)
    ys = torch.full((len(batch), max_len), -100, dtype=torch.long)
    for i, (x, y) in enumerate(batch):
        xs[i, :len(x)] = torch.tensor(x, dtype=torch.long)
        ys[i, :len(y)] = torch.tensor(y, dtype=torch.long)
    return xs, ys


class NextTokenModel(nn.Module):
    def __init__(self, vocab_size: int, dim: int, layers: int, dropout: float):
        super().__init__()
        self.emb = nn.Embedding(vocab_size, dim, padding_idx=PAD)
        self.rnn = nn.GRU(dim, dim, layers, batch_first=True, dropout=dropout if layers > 1 else 0.0)
        self.drop = nn.Dropout(dropout)
        self.out = nn.Linear(dim, vocab_size)

    def forward(self, x):
        h, _ = self.rnn(self.emb(x))
        return self.out(self.drop(h))


def build_vocab(lines: list[str], max_size: int | None) -> dict[str, int]:
    from collections import Counter
    counter = Counter()
    for line in lines:
        counter.update(line)
    vocab = {s: i for i, s in enumerate(SPECIALS)}
    for ch, _ in counter.most_common():
        if len(vocab) >= (max_size or 10 ** 9):
            break
        if ch not in vocab:
            vocab[ch] = len(vocab)
    return vocab


def encode(line: str, vocab: dict[str, int], ctx: int) -> list[int]:
    ids = [BOS] + [vocab.get(ch, UNK) for ch in line][:ctx] + [EOS]
    return ids


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--corpus", type=Path, default=Path("data/corpus.txt"))
    ap.add_argument("--out", type=Path, default=Path("models"))
    ap.add_argument("--dim", type=int, default=256)
    ap.add_argument("--layers", type=int, default=2)
    ap.add_argument("--dropout", type=float, default=0.15)
    ap.add_argument("--ctx", type=int, default=64)
    ap.add_argument("--batch", type=int, default=256)
    ap.add_argument("--epochs", type=int, default=4)
    ap.add_argument("--lr", type=float, default=2e-3)
    ap.add_argument("--max-vocab", type=int, default=8000)
    ap.add_argument("--max-lines", type=int, default=0, help="0 表示全部")
    ap.add_argument("--punct-weight", type=float, default=0.35, help="标点 token 的损失权重")
    args = ap.parse_args()

    torch.manual_seed(20261104)
    device = "cuda" if torch.cuda.is_available() else "cpu"
    print(f"device={device}")

    lines = [l.strip() for l in args.corpus.read_text(encoding="utf-8").splitlines() if l.strip()]
    if args.max_lines:
        lines = lines[:args.max_lines]
    print(f"语料 {len(lines)} 行，{sum(len(l) for l in lines)} 字符")

    vocab = build_vocab(lines, args.max_vocab)
    V = len(vocab)
    print(f"词表 {V}（含 4 个特殊符）")

    encoded = [encode(l, vocab, args.ctx) for l in lines]
    dataset = TextDataset(encoded, args.ctx)
    loader = DataLoader(dataset, batch_size=args.batch, shuffle=True, collate_fn=collate,
                        num_workers=0, drop_last=True)
    print(f"样本 {len(dataset)}，batch {len(loader)}")

    model = NextTokenModel(V, args.dim, args.layers, args.dropout).to(device)
    n_params = sum(p.numel() for p in model.parameters())
    print(f"参数量 {n_params/1e6:.2f}M")

    # 标点降权：句末标点在语料中密度过高，会让模型过早预测标点。
    # 输入法场景更希望接续实词，故对标点 token 的损失乘以权重。
    punct_chars = set("，。！？、；：")
    weight = torch.ones(V, dtype=torch.float)
    for ch in punct_chars:
        idx = vocab.get(ch)
        if idx is not None:
            weight[idx] = args.punct_weight
    print(f"标点 token 已降权至 {args.punct_weight}（共 {sum(1 for c in punct_chars if c in vocab)} 个）")

    opt = torch.optim.AdamW(model.parameters(), lr=args.lr, weight_decay=0.01)
    total_steps = max(1, len(loader) * args.epochs)
    sched = torch.optim.lr_scheduler.OneCycleLR(opt, max_lr=args.lr, total_steps=total_steps)
    lossf = nn.CrossEntropyLoss(ignore_index=-100, weight=weight.to(device))

    best = math.inf
    args.out.mkdir(parents=True, exist_ok=True)
    for epoch in range(args.epochs):
        model.train()
        t0 = time.time()
        running = 0.0
        for step, (x, y) in enumerate(loader, 1):
            x, y = x.to(device), y.to(device)
            logits = model(x)
            loss = lossf(logits.reshape(-1, V), y.reshape(-1))
            opt.zero_grad(set_to_none=True)
            loss.backward()
            torch.nn.utils.clip_grad_norm_(model.parameters(), 1.0)
            opt.step()
            sched.step()
            running += loss.item()
            if step % 200 == 0:
                print(f"  epoch {epoch} step {step}/{len(loader)} loss {running/step:.4f} "
                      f"({time.time()-t0:.0f}s)")
        avg = running / max(1, len(loader))
        print(f"epoch {epoch} done, avg loss {avg:.4f}, {time.time()-t0:.0f}s")
        if avg < best:
            best = avg
            torch.save({"model": model.state_dict(), "vocab": vocab, "args": vars(args)},
                       args.out / "model.pt")

    json.dump({"vocab": vocab}, open(args.out / "vocab.json", "w", encoding="utf-8"),
              ensure_ascii=False, indent=0)
    print(f"已保存 {args.out/'vocab.json'} 与 model.pt（best loss {best:.4f}）")


if __name__ == "__main__":
    main()
