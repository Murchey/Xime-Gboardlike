# -*- coding: utf-8 -*-
"""测量每本书的文言/白话构成，用于决定是否纳入训练语料。"""
from __future__ import annotations
import re
import sys
from pathlib import Path

import mobi

CJK = r"\u4e00-\u9fff"
KEEP = re.compile(rf"[{CJK}]")
# 文言高频虚词/代词（在白话中显著低频）
WENYAN = set("之乎者也矣焉哉其曰乃遂莫曰弗毋吾汝尔厥奚孰岂奚")
# 白话高频标记
BAIHUA = set("的了着吗呢吧啊嗯么这那们很都就还又")

BOOKS = [
    r"E:\ebook\5\KJ帕克作品集6册.mobi",
    r"E:\ebook\5\金庸作品全集（新修版）（全36册）.mobi",
    r"E:\ebook\5\诺贝尔文学奖作品典藏书系全集（共31册）.mobi",
    r"E:\ebook\5\沈从文文集.mobi",
    r"E:\ebook\5\省委大院.mobi",
    r"E:\ebook\5\围城.mobi",
    r"E:\ebook\5\二十四史：文白对照版（全12册）.mobi",
]


def text_of(path: Path, limit_chars: int = 400_000) -> str:
    tmpdir, _ = mobi.extract(str(path))
    buf = []
    total = 0
    for f in sorted(Path(tmpdir).rglob("*")):
        if f.is_file() and f.suffix.lower() in {".html", ".htm", ".xhtml"}:
            try:
                s = f.read_text(encoding="utf-8", errors="ignore")
            except Exception:
                continue
            s = re.sub(r"<[^>]+>", "", s)
            buf.append(s)
            total += len(s)
            if total >= limit_chars:
                break
    return "".join(buf)


def profile(text: str) -> dict:
    chars = [ch for ch in text if KEEP.match(ch)]
    total = len(chars) or 1
    wy = sum(1 for ch in chars if ch in WENYAN) / total
    bh = sum(1 for ch in chars if ch in BAIHUA) / total
    # 抽样句子判断典型句式
    return {"chars": total, "wenyan_ratio": wy * 100, "baihua_ratio": bh * 100}


def main() -> None:
    print(f"{'书名':<34}{'字数':>9}{'文言词%':>9}{'白话词%':>9}  判定")
    print("-" * 78)
    for b in BOOKS:
        p = Path(b)
        try:
            text = text_of(p)
            prof = profile(text)
            ratio = prof["wenyan_ratio"]
            verdict = "文言为主" if ratio > 3.0 else ("文白混排" if ratio > 1.2 else "白话")
            print(f"{p.name[:32]:<34}{prof['chars']:>9}{prof['wenyan_ratio']:>8.2f}{prof['baihua_ratio']:>9.2f}  {verdict}")
        except Exception as exc:
            print(f"{p.name[:32]:<34} FAIL {exc}")


if __name__ == "__main__":
    main()
