# -*- coding: utf-8 -*-
"""把 vocab.json + model_int8_dynamic.onnx 打成 App 可导入的扁平 zip。"""
from __future__ import annotations

import argparse
import hashlib
import json
import zipfile
from pathlib import Path


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--models", type=Path, default=Path("models"))
    ap.add_argument("--model-id", default="custom-predictive")
    ap.add_argument("--model-name", default="自训练联想模型")
    ap.add_argument("--version", default="v1.0")
    ap.add_argument("--out", type=Path, default=None)
    args = ap.parse_args()

    vocab = args.models / "vocab.json"
    onnx_file = args.models / "model_int8_dynamic.onnx"
    if not onnx_file.exists():
        onnx_file = args.models / "model.onnx"
    for p in (vocab, onnx_file):
        if not p.exists():
            raise SystemExit(f"缺少 {p}")

    files = [
        {"name": p.name, "size_bytes": p.stat().st_size, "sha256": sha256(p)}
        for p in (vocab, onnx_file)
    ]
    manifest = {
        "format_version": 1,
        "model_id": args.model_id,
        "model_name": args.model_name,
        "version": args.version,
        "source": "ModelTrainning",
        "files": files,
    }

    out = args.out or (args.models / f"XimeGboard-{args.model_id}-{args.version}.zip")
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED, compresslevel=6) as z:
        z.write(vocab, arcname="vocab.json")
        z.write(onnx_file, arcname="model_int8_dynamic.onnx")
        z.writestr("manifest.json", json.dumps(manifest, ensure_ascii=False, indent=2))

    print(f"打包 {out} ({out.stat().st_size/1e6:.2f}MB)")
    for f in files:
        print(f"  {f['name']}: {f['size_bytes']/1e6:.2f}MB sha256={f['sha256'][:16]}…")


if __name__ == "__main__":
    main()
