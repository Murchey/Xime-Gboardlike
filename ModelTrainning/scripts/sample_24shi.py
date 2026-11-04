# -*- coding: utf-8 -*-
"""抽样观察二十四史的实际行文：文言原文 vs 白话译文的比例。"""
from __future__ import annotations
import re
from pathlib import Path
import mobi

p = Path(r"E:\ebook\5\二十四史：文白对照版（全12册）.mobi")
tmpdir, _ = mobi.extract(str(p))
buf = []
for f in sorted(Path(tmpdir).rglob("*")):
    if f.is_file() and f.suffix.lower() in {".html", ".htm", ".xhtml"}:
        s = re.sub(r"<[^>]+>", "", f.read_text(encoding="utf-8", errors="ignore"))
        buf.append(s)
text = "\n".join(buf)
lines = [l.strip() for l in text.splitlines() if len(l.strip()) > 10]
print(f"总行 {len(lines)}")
print("\n=== 前 12 行样本 ===")
for l in lines[:12]:
    print("  ", l[:78])
print("\n=== 中段 12 行样本 ===")
mid = len(lines) // 2
for l in lines[mid:mid + 12]:
    print("  ", l[:78])

# 逐行判定：含白话标记多 → 译文；否则文言
BAIHUA = set("的了着吗呢吧啊这那们很都就还又")
WENYAN = set("之乎者也矣焉哉其曰乃遂吾汝")
wy_lines = bh_lines = 0
for l in lines:
    bh = sum(1 for c in l if c in BAIHUA)
    wy = sum(1 for c in l if c in WENYAN)
    if bh > wy:
        bh_lines += 1
    elif wy > 0:
        wy_lines += 1
print(f"\n逐行统计：白话倾向 {bh_lines} 行 ({bh_lines/len(lines)*100:.1f}%)，"
      f"文言倾向 {wy_lines} 行 ({wy_lines/len(lines)*100:.1f}%)")
