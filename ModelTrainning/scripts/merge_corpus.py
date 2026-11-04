# -*- coding: utf-8 -*-
"""把多个语料分片合并、去重、截断，输出最终训练语料。"""
from __future__ import annotations

import argparse
from pathlib import Path


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--inputs", nargs="+", type=Path, required=True)
    ap.add_argument("--out", type=Path, required=True)
    ap.add_argument("--max-chars", type=int, default=0, help="0 表示不限制")
    ap.add_argument("--min-len", type=int, default=8)
    args = ap.parse_args()

    seen: set[str] = set()
    lines: list[str] = []
    total = 0
    for path in args.inputs:
        if not path.exists():
            continue
        for raw in path.read_text(encoding="utf-8").splitlines():
            line = raw.strip()
            if len(line) < args.min_len or line in seen:
                continue
            seen.add(line)
            lines.append(line)
            total += len(line)
            if args.max_chars and total >= args.max_chars:
                break
        if args.max_chars and total >= args.max_chars:
            break

    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"写入 {args.out}: {len(lines)} 行, {total} 字符, 去重后字符集 {len(set(''.join(lines)))}")


if __name__ == "__main__":
    main()
