#!/usr/bin/env python3
"""把 XimeGboard 导出的用户学习数据转成训练语料。

用法：
    python scripts/export_ngram.py xime_user_ngram_20261104_101500.json \
        --out corpus.txt

输入是 App「智能联想 → 用户学习数据 → 导出学习数据」导出的 JSON（含 meta 段）。
输出是按行拼接的纯文本，可直接喂给训练脚本做字符级/词级语言模型。

注意：n-gram 缓存只保存「相邻 token 组合 + 计数」，不保存完整原文，
因此重建出的是接续序列而不是原始输入历史。
"""
from __future__ import annotations

import argparse
import json
import sys
from collections import Counter
from pathlib import Path


def load(path: Path) -> dict:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        sys.exit(f"不是合法 JSON：{exc}")


def iter_ngrams(data: dict, field: str, size: int):
    """产出 (tokens, count)，跳过结构不符的条目。"""
    for entry in data.get(field) or []:
        tokens = entry.get("tokens")
        count = entry.get("count")
        if not isinstance(tokens, list) or len(tokens) != size:
            continue
        if not isinstance(count, int) or count <= 0:
            continue
        yield tuple(str(t) for t in tokens), count


def build_corpus(data: dict, repeat_trigrams: bool) -> list[str]:
    lines: list[str] = []

    # 近期输入是最接近真实句子的数据，优先直出。
    recent = [str(t) for t in (data.get("recentInputs") or [])]
    if len(recent) > 1:
        lines.append("".join(recent))

    # 三元组比二元组信息量更大；按需重复以放大权重。
    for tokens, count in iter_ngrams(data, "trigrams", 3):
        lines.append("".join(tokens))
        if repeat_trigrams and count > 1:
            lines.extend(["".join(tokens)] * min(count - 1, 3))

    for tokens, count in iter_ngrams(data, "bigrams", 2):
        lines.append("".join(tokens))

    return [line for line in lines if line]


def main() -> None:
    parser = argparse.ArgumentParser(description="把用户学习数据 JSON 转成训练语料")
    parser.add_argument("source", type=Path, help="导出得到的 JSON 文件")
    parser.add_argument("-o", "--out", type=Path, default=Path("corpus.txt"), help="输出文本路径")
    parser.add_argument("--repeat-trigrams", action="store_true",
                        help="按出现次数重复三元组，放大高频组合权重")
    parser.add_argument("--stats", action="store_true", help="只打印统计信息")
    args = parser.parse_args()

    data = load(args.source)
    meta = data.get("meta") or {}
    corpus = build_corpus(data, args.repeat_trigrams)

    charset = Counter("".join(corpus))
    print(f"源文件       : {args.source}")
    print(f"导出版本     : {meta.get('format_version', '未知')}")
    print(f"App 版本     : {meta.get('app_version', '未知')}")
    print(f"bigram/trigram/recent : "
          f"{meta.get('bigram_count', 0)} / {meta.get('trigram_count', 0)} / {meta.get('recent_count', 0)}")
    print(f"输出行数     : {len(corpus)}")
    print(f"总字符数     : {sum(len(line) for line in corpus)}")
    print(f"不同字符数   : {len(charset)}")
    print("高频字符 Top10: " + " ".join(f"{ch}:{n}" for ch, n in charset.most_common(10)))

    if not args.stats:
        if not corpus:
            sys.exit("没有可用语料：导出文件为空或结构不符")
        args.out.write_text("\n".join(corpus) + "\n", encoding="utf-8")
        print(f"已写入       : {args.out}")


if __name__ == "__main__":
    main()
