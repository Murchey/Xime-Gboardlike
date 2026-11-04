# -*- coding: utf-8 -*-
"""把训练好的 PyTorch 模型导出为 App 可用的 ONNX（含动态量化）。

契约与 app/src/main/jni/onnx_jni.cc 一致：
  输入名 input_ids : int64[batch, seq]
  输出名 logits    : float[batch, seq, vocab]
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path

import torch

from train import NextTokenModel  # 同目录训练脚本


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--models", type=Path, default=Path("models"))
    ap.add_argument("--opset", type=int, default=17)
    ap.add_argument("--quantize", action="store_true", default=True)
    args = ap.parse_args()

    ckpt = torch.load(args.models / "model.pt", map_location="cpu", weights_only=False)
    vocab = ckpt["vocab"]
    cfg = ckpt["args"]
    V = len(vocab)

    model = NextTokenModel(V, cfg["dim"], cfg["layers"], cfg["dropout"])
    model.load_state_dict(ckpt["model"])
    model.eval()

    dummy = torch.randint(4, V, (1, 16), dtype=torch.long)
    onnx_path = args.models / "model.onnx"
    torch.onnx.export(
        model, dummy, str(onnx_path),
        input_names=["input_ids"], output_names=["logits"],
        dynamic_axes={"input_ids": {0: "batch", 1: "seq"}, "logits": {0: "batch", 1: "seq"}},
        opset_version=args.opset,
    )
    print(f"导出 {onnx_path} ({onnx_path.stat().st_size/1e6:.2f}MB)")

    if args.quantize:
        from onnxruntime.quantization import quantize_dynamic, QuantType
        q_path = args.models / "model_int8_dynamic.onnx"
        quantize_dynamic(str(onnx_path), str(q_path), weight_type=QuantType.QInt8)
        print(f"量化 {q_path} ({q_path.stat().st_size/1e6:.2f}MB)")

    # 自检：与 ONNX Runtime 对齐输出维度
    import numpy as np
    import onnxruntime as ort
    sess = ort.InferenceSession(str(args.models / ("model_int8_dynamic.onnx" if args.quantize else "model.onnx")))
    probe = np.array([[1, vocab.get("你", 4), vocab.get("好", 4)]], dtype=np.int64)
    out = sess.run(None, {"input_ids": probe})[0]
    print(f"自检输出形状 {out.shape}（期望 [1, 3, {V}]）")
    assert out.shape == (1, 3, V), "输出维度与词表不一致"


if __name__ == "__main__":
    main()
