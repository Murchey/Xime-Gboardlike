# -*- coding: utf-8 -*-
"""合并精选语料 + 原通用语料，控制各来源权重后切分训练/验证集。"""
from __future__ import annotations

import argparse
import random
import re
from collections import Counter
from pathlib import Path

CJK = r"\u4e00-\u9fff"
KEEP = re.compile(rf"[{CJK}A-Za-z0-9，。！？、；：“”‘’（）《》…—·]")


def load(path: Path) -> list[str]:
    if not path.exists():
        return []
    return [l.strip() for l in path.read_text(encoding="utf-8").splitlines() if l.strip()]


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", type=Path, default=Path("data/train.txt"))
    ap.add_argument("--val", type=Path, default=Path("data/val2.txt"))
    ap.add_argument("--ershisi-ratio", type=float, default=0.08,
                    help="二十四史白话在最终语料中的上限占比")
    ap.add_argument("--seed", type=int, default=20261105)
    args = ap.parse_args()

    rng = random.Random(args.seed)
    data = Path("data")

    # 通用语料（上一轮）
    general = load(data / "corpus.txt")

    # 精选白话书目
    curated = {}
    for key in ("kjparker", "jinyong", "nobel", "shencongwen", "shengwei", "weicheng"):
        curated[key] = load(data / f"curated_{key}.txt")
    ershisi = load(data / "curated_ershisi_vernacular.txt")

    curated_total = sum(len(v) for v in curated.values())
    print("=== 各来源句数 ===")
    print(f"通用语料           {len(general):>7}")
    for k, v in curated.items():
        print(f"{k:<18} {len(v):>7}")
    print(f"二十四史(白话)     {len(ershisi):>7}")

    # 白话基座 = 通用 + 精选白话
    base = general + [s for v in curated.values() for s in v]

    # 二十四史按比例裁剪：ershisi <= ratio * (base + ershisi)
    # => ershisi_max = ratio/(1-ratio) * base * shrink
    max_er = int(len(base) * args.ershisi_ratio / (1 - args.ershisi_ratio))
    if len(ershisi) > max_er:
        ershisi = rng.sample(ershisi, max_er)
    print(f"\n二十四史上限 {max_er} 句，实际采用 {len(ershisi)} 句 "
          f"（占最终语料 {len(ershisi)/(len(base)+len(ershisi))*100:.1f}%）")

    all_lines = base + ershisi
    # 去重
    seen, uniq = set(), []
    for s in all_lines:
        s = "".join(ch for ch in s if KEEP.fullmatch(ch)).strip()
        if len(s) < 8 or len(s) > 100 or s in seen:
            continue
        seen.add(s)
        uniq.append(s)

    rng.shuffle(uniq)
    n_val = max(500, int(len(uniq) * 0.02))
    val, train = uniq[:n_val], uniq[n_val:]

    args.out.write_text("\n".join(train) + "\n", encoding="utf-8")
    args.val.write_text("\n".join(val) + "\n", encoding="utf-8")

    chars = Counter("".join(train))
    print(f"\n训练集 {len(train)} 行 / {sum(len(l) for l in train)} 字符 -> {args.out}")
    print(f"验证集 {len(val)} 行 -> {args.val}")
    print(f"字符集 {len(chars)}")
    # 功能字占比（判断文言污染的关键指标）
    total = sum(chars.values())
    for ch in "的了是在不和有这我们":
        print(f"  '{ch}' 占比 {chars[ch]/total*100:.2f}%", end="")
    print()


if __name__ == "__main__":
    main()
