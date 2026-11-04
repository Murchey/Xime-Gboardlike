# -*- coding: utf-8 -*-
"""抽取用户精选书目，并对《二十四史》做文言/白话分流。

分流策略（基于实测）：
- 二十四史是"原文+译文"双栏，逐行文言倾向 43.3% / 白话倾向 42.3%
- 只保留白话译文行，剔除文言原文，避免文言虚词挤占功能字分布
- 其余 6 本为纯白话，全量保留
"""
from __future__ import annotations

import html
import random
import re
from pathlib import Path

import mobi

OUT = Path(__file__).resolve().parent.parent / "data"  # 相对路径
CJK = r"\u4e00-\u9fff"
KEEP = re.compile(rf"[{CJK}A-Za-z0-9，。！？、；：“”‘’（）《》…—·]")
SENT_SPLIT = re.compile(rf"(?<=[。！？])")

# 文言虚词（判定用）：出现在同一行越多越偏文言
WENYAN_MARKS = set("之乎者也矣焉哉其曰乃遂吾汝厥奚孰岂弗毋")
# 白话标记
BAIHUA_MARKS = set("的了着吗呢吧啊这那们很都就还又")

BOOKS = {
    "kjparker": r"E:\ebook\5\KJ帕克作品集6册.mobi",
    "jinyong": r"E:\ebook\5\金庸作品全集（新修版）（全36册）.mobi",
    "nobel": r"E:\ebook\5\诺贝尔文学奖作品典藏书系全集（共31册）.mobi",
    "shencongwen": r"E:\ebook\5\沈从文文集.mobi",
    "shengwei": r"E:\ebook\5\省委大院.mobi",
    "weicheng": r"E:\ebook\5\围城.mobi",
    "ershisi": r"E:\ebook\5\二十四史：文白对照版（全12册）.mobi",
}


def raw_text(path: str) -> str:
    tmpdir, _ = mobi.extract(path)
    parts = []
    for f in sorted(Path(tmpdir).rglob("*")):
        if f.is_file() and f.suffix.lower() in {".html", ".htm", ".xhtml"}:
            try:
                s = f.read_text(encoding="utf-8", errors="ignore")
            except Exception:
                continue
            parts.append(re.sub(r"<[^>]+>", "", s))
    return html.unescape("\n".join(parts))


def split_sentences(text: str, min_len=8, max_len=100) -> list[str]:
    out = []
    for line in text.splitlines():
        line = line.replace("\u3000", " ").strip()
        if len(line) < min_len:
            continue
        for piece in SENT_SPLIT.split(line):
            s = "".join(ch for ch in piece if KEEP.fullmatch(ch)).strip()
            if min_len <= len(s) <= max_len:
                out.append(s)
    return out


# 文言专有标记（出现在白话译文中概率极低）
STRONG_CLASSICAL = re.compile(
    r"[之乎者也矣焉哉]|[曰薨殁殂崩]|[及至乃遂]|(?<!所)以之|不亦.*乎|[岂奚孰]"
)
# 白话译文常见标记
STRONG_VERNACULAR = re.compile(r"[的了着过]|(?<![一])[这那]|[们吗呢吧啊]|[0-9]|（|）”")


def is_classical(sentence: str) -> bool:
    """判定一句话偏文言还是白话（从严：宁可误杀文言，不放行）。"""
    if STRONG_VERNACULAR.search(sentence):
        return False
    wy = sum(1 for c in sentence if c in WENYAN_MARKS)
    bh = sum(1 for c in sentence if c in BAIHUA_MARKS)
    if wy >= 1 and bh == 0:
        return True
    if wy >= 2 and wy > bh:
        return True
    if STRONG_CLASSICAL.search(sentence) and bh == 0:
        return True
    # 无任何白话标记且较短，倾向文言
    if bh == 0 and wy == 0 and len(sentence) <= 24:
        return True
    return False


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    rng = random.Random(20261105)
    summary = []

    vernacular_books = [k for k in BOOKS if k != "ershisi"]
    for key in vernacular_books:
        text = raw_text(BOOKS[key])
        sents = split_sentences(text)
        # 单本上限，防止 36 册/31 册合集独占
        cap = 30000
        if len(sents) > cap:
            sents = rng.sample(sents, cap)
        path = OUT / f"curated_{key}.txt"
        path.write_text("\n".join(sents) + "\n", encoding="utf-8")
        summary.append((key, len(sents), 0, "白话全收"))
        print(f"[{key}] {len(sents)} 句 -> {path.name}", flush=True)

    # 二十四史：分流
    text = raw_text(BOOKS["ershisi"])
    sents = split_sentences(text)
    classical = [s for s in sents if is_classical(s)]
    vernacular = [s for s in sents if not is_classical(s)]
    cap = 22000
    if len(vernacular) > cap:
        vernacular = rng.sample(vernacular, cap)
    path = OUT / "curated_ershisi_vernacular.txt"
    path.write_text("\n".join(vernacular) + "\n", encoding="utf-8")
    dropped = OUT / "curated_ershisi_classical_dropped.txt"
    dropped.write_text("\n".join(classical[:5000]) + "\n", encoding="utf-8")
    summary.append(("ershisi", len(vernacular), len(classical), "仅白话"))
    print(f"[ershisi] 白话 {len(vernacular)} 句（保留） / 文言 {len(classical)} 句（丢弃）", flush=True)

    print("\n=== 汇总 ===")
    total = 0
    for key, kept, drop, note in summary:
        print(f"{key:<14} 保留 {kept:>6} 句" + (f"，丢弃文言 {drop} 句" if drop else "") + f"  [{note}]")
        total += kept
    print(f"精选语料合计 {total} 句")


if __name__ == "__main__":
    main()
