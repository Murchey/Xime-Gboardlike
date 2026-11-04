# -*- coding: utf-8 -*-
"""从 E:/ebook 抽取代表性中文正文，输出训练用纯文本。

每本书在独立子进程中解析，超时即杀，避免个别超大/图片型书籍阻塞整批。
"""
from __future__ import annotations

import argparse
import html
import multiprocessing as mp
import random
import re
import sys
from pathlib import Path

ROOT = Path(r"E:\ebook")
OUT = Path(__file__).resolve().parent.parent / "data"  # 相对路径

CJK = r"\u4e00-\u9fff"
KEEP = re.compile(rf"[{CJK}A-Za-z0-9，。！？、；：“”‘’（）《》…—·]")
NOISE = re.compile(
    r"(https?://\S+|www\.\S+|\S+@\S+\.\w+)"
    r"|(手机用户请|本章未完|请收藏|最新章节|百度搜索|一秒记住|无弹窗)"
    r"|(Z-Library|z-lib|lib\.io|\.mobi|\.epub|\.pdf)"
)
SENT_SPLIT = re.compile(rf"(?<=[。！？])")


def clean_block(text: str) -> str:
    text = html.unescape(re.sub(r"<[^>]+>", "", text))
    text = text.replace("\u3000", " ").replace("\xa0", " ")
    text = NOISE.sub("", text)
    lines = []
    for raw in text.splitlines():
        line = raw.strip()
        if len(line) >= 8 and KEEP.search(line):
            lines.append(line)
    return "\n".join(lines)


def split_sentences(text: str, min_len=8, max_len=120) -> list[str]:
    out = []
    for piece in SENT_SPLIT.split(text):
        s = "".join(ch for ch in piece if KEEP.fullmatch(ch)).strip()
        if min_len <= len(s) <= max_len:
            out.append(s)
    return out


def extract(path: Path) -> str:
    suffix = path.suffix.lower()
    if path.stat().st_size / 1e6 > 40:
        raise ValueError("超大文件")
    if suffix == ".mobi":
        import mobi
        tmpdir, _ = mobi.extract(str(path))
        parts = []
        for f in sorted(Path(tmpdir).rglob("*")):
            if f.is_file() and f.suffix.lower() in {".html", ".htm", ".xhtml"}:
                try:
                    parts.append(f.read_text(encoding="utf-8", errors="ignore"))
                except Exception:
                    pass
        return "\n".join(parts)
    if suffix == ".pdf":
        import fitz
        doc = fitz.open(path)
        try:
            return "\n".join(p.get_text("text") for p in doc)
        finally:
            doc.close()
    if suffix == ".epub":
        import ebooklib
        from ebooklib import epub
        book = epub.read_epub(str(path), options={"ignore_ncx": True})
        return "\n".join(
            it.get_content().decode("utf-8", "ignore")
            for it in book.get_items_of_type(ebooklib.ITEM_DOCUMENT)
        )
    if suffix == ".txt":
        return path.read_text(encoding="utf-8", errors="ignore")
    raise ValueError(f"不支持的格式 {suffix}")


def worker(path_str: str, conn) -> None:
    try:
        raw = extract(Path(path_str))
        conn.send(("ok", split_sentences(clean_block(raw))))
    except Exception as exc:  # noqa: BLE001
        conn.send(("err", str(exc)))
    finally:
        conn.close()


def run_book(path: Path, timeout: float) -> tuple[str, list[str]]:
    parent, child = mp.Pipe(duplex=False)
    proc = mp.Process(target=worker, args=(str(path), child), daemon=True)
    proc.start()
    child.close()
    try:
        if parent.poll(timeout):
            status, payload = parent.recv()
            return status, payload if status == "ok" else [str(payload)]
        return "err", ["超时"]
    except EOFError:
        return "err", ["子进程异常退出"]
    finally:
        parent.close()
        proc.join(timeout=2)
        if proc.is_alive():
            proc.terminate()
            proc.join(timeout=2)


def pick_books(per_dir: int, seed: int) -> list[Path]:
    rng = random.Random(seed)
    picks: list[Path] = []
    for sub in sorted(d for d in ROOT.iterdir() if d.is_dir()):
        books = sorted(p for p in sub.rglob("*")
                       if p.is_file() and p.suffix.lower() in {".mobi", ".pdf", ".epub", ".txt"})
        if books:
            rng.shuffle(books)
            picks.extend(books[:per_dir])
    top = sorted(p for p in ROOT.iterdir()
                 if p.is_file() and p.suffix.lower() in {".mobi", ".pdf", ".epub", ".txt"})
    rng.shuffle(top)
    picks.extend(top[:per_dir])
    return picks


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--per-dir", type=int, default=3)
    ap.add_argument("--seed", type=int, default=20261104)
    ap.add_argument("--sentences-per-book", type=int, default=8000)
    ap.add_argument("--timeout", type=float, default=90.0)
    ap.add_argument("--out", type=Path, default=OUT / "corpus_raw.txt")
    args = ap.parse_args()

    OUT.mkdir(parents=True, exist_ok=True)
    books = pick_books(args.per_dir, args.seed)
    print(f"选中 {len(books)} 本", flush=True)

    all_sents: list[str] = []
    for i, book in enumerate(books, 1):
        status, sents = run_book(book, args.timeout)
        if status == "ok" and len(sents) > args.sentences_per_book:
            rng = random.Random(hash(book.name) & 0xFFFF)
            sents = rng.sample(sents, args.sentences_per_book)
        if status == "ok":
            all_sents.extend(sents)
            print(f"[{i}/{len(books)}] {book.name[:58]} -> {len(sents)} 句", flush=True)
        else:
            print(f"[{i}/{len(books)}] FAIL {book.name[:50]}: {sents[0][:60]}", flush=True)

    if not all_sents:
        sys.exit("没有抽取到句子")

    seen, uniq = set(), []
    for s in all_sents:
        if s not in seen:
            seen.add(s)
            uniq.append(s)

    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text("\n".join(uniq) + "\n", encoding="utf-8")
    print(f"\n写入 {args.out}", flush=True)
    print(f"句子 {len(uniq)}（去重前 {len(all_sents)}），"
          f"字符 {sum(len(s) for s in uniq)}，字符集 {len(set(''.join(uniq)))}", flush=True)


if __name__ == "__main__":
    mp.freeze_support()
    main()
