# -*- coding: utf-8 -*-
"""语料清洗：修引号、去残留噪声、限制字符集、切分训练/验证集。"""
from __future__ import annotations

import argparse
import random
import re
from collections import Counter
from pathlib import Path

CJK = r"\u4e00-\u9fff"
KEEP = re.compile(rf"[{CJK}A-Za-z0-9，。！？、；：“”‘’（）《》…—·]")
BAD_QUOTE = re.compile(r"^[”“]+|[”“]+$")


def normalize(line: str) -> str:
    line = line.replace('"', "“").replace("'", "‘")
    line = BAD_QUOTE.sub("", line)              # 行首/行尾孤立引号
    line = re.sub(r"[“”]{2,}", "“", line)
    line = re.sub(r"[‘’]{2,}", "‘", line)
    line = "".join(ch for ch in line if KEEP.fullmatch(ch))
    return line.strip()


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--src", type=Path, default=Path("data/corpus_raw.txt"))
    ap.add_argument("--out", type=Path, default=Path("data/corpus.txt"))
    ap.add_argument("--val", type=Path, default=Path("data/val.txt"))
    ap.add_argument("--min-len", type=int, default=8)
    ap.add_argument("--max-len", type=int, default=100)
    ap.add_argument("--val-ratio", type=float, default=0.02)
    ap.add_argument("--seed", type=int, default=20261104)
    args = ap.parse_args()

    lines, seen = [], set()
    for raw in args.src.read_text(encoding="utf-8").splitlines():
        line = normalize(raw)
        if not (args.min_len <= len(line) <= args.max_len):
            continue
        if line in seen:
            continue
        seen.add(line)
        lines.append(line)

    rng = random.Random(args.seed)
    rng.shuffle(lines)
    n_val = max(200, int(len(lines) * args.val_ratio))
    val, train = lines[:n_val], lines[n_val:]

    args.out.write_text("\n".join(train) + "\n", encoding="utf-8")
    args.val.write_text("\n".join(val) + "\n", encoding="utf-8")

    chars = Counter("".join(train))
    print(f"训练集 {len(train)} 行 / {sum(len(l) for l in train)} 字符 -> {args.out}")
    print(f"验证集 {len(val)} 行 -> {args.val}")
    print(f"字符集 {len(chars)}，Top20: {''.join(c for c,_ in chars.most_common(20))}")
    rare = [c for c, n in chars.items() if n < 5]
    print(f"出现 <5 次的字符 {len(rare)} 个")


if __name__ == "__main__":
    main()
