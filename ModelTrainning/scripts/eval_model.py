# -*- coding: utf-8 -*-
"""离线评估：用训练好的 ONNX 模型对给定前缀预测 top-k，人工判断质量。"""
from __future__ import annotations

import argparse
import json
from pathlib import Path

import numpy as np
import onnxruntime as ort

BOS, UNK = 1, 3

CASES = ["今天天气", "我爱你", "谢谢", "我们", "这个问题", "所以说", "不知道", "你好",
         "在这种情况下", "北京", "时间", "他们", "可以", "我想", "中国"]


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--models", type=Path, default=Path("models"))
    ap.add_argument("--onnx", default="model_int8_dynamic.onnx")
    ap.add_argument("--topk", type=int, default=5)
    ap.add_argument("--cases", nargs="*", default=CASES)
    args = ap.parse_args()

    vocab = json.loads((args.models / "vocab.json").read_text(encoding="utf-8"))["vocab"]
    inv = {v: k for k, v in vocab.items()}
    sess = ort.InferenceSession(str(args.models / args.onnx),
                                providers=["CPUExecutionProvider"])
    print(f"模型 {args.onnx}，词表 {len(vocab)}\n")

    for prefix in args.cases:
        ids = [BOS] + [vocab.get(c, UNK) for c in prefix]
        logits = sess.run(None, {"input_ids": np.array([ids], dtype=np.int64)})[0][0, -1]
        top = logits.argsort()[-args.topk:][::-1]
        words = [inv.get(int(i), "?") for i in top if int(i) >= 4]
        print(f"{prefix:<10} -> {' | '.join(words)}")


if __name__ == "__main__":
    main()
