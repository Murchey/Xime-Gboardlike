#!/usr/bin/env python3
"""Evaluate the FUTO Swipe encoder on recorded Xime glide traces.

Usage:
    python tools/futo-eval/eval.py --data tmpImg/xime_glide_20261111_112000.jsonl
    python tools/futo-eval/eval.py --data ... --json-out /tmp/futo-eval.json
    python tools/futo-eval/eval.py --data ... --spellings /tmp/spellings.json

Requires a working ExecuTorch Python runtime; see README.md.
"""

from __future__ import annotations

import argparse
import dataclasses
import json
import os
import sys
from pathlib import Path

from futo_eval import (
    DEFAULT_BEAM,
    DEFAULT_GAMMA,
    DEFAULT_LAMBDA,
    DEFAULT_MAX_LETTERS,
    DEFAULT_TOPK,
    FutoEncoder,
    evaluate_trace,
    load_traces,
    spellings_for_schema,
    spellings_from_file,
    summarize,
    usable_traces,
)


def default_model_path() -> Path:
    here = Path(__file__).resolve()
    # Xime-Gboardlike/tools/futo-eval/eval.py -> InputMethod/futo-swipe/...
    return here.parents[3] / "futo-swipe" / "honorable_sturgeon" / "model_fp32.pte"


def load_spellings(path: str) -> list[str]:
    if path.lower().endswith(".json"):
        return spellings_from_file(path)
    with open(path, "r", encoding="utf8") as handle:
        return [line.strip() for line in handle if line.strip()]


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--data", required=True, help="glide_samples .jsonl")
    parser.add_argument("--model", default=os.environ.get("FUTO_MODEL_PATH", str(default_model_path())))
    parser.add_argument("--spellings", help="JSON list or one-spelling-per-line text file")
    parser.add_argument("--beam", type=int, default=DEFAULT_BEAM)
    parser.add_argument("--topk", type=int, default=DEFAULT_TOPK)
    parser.add_argument("--max-letters", type=int, default=DEFAULT_MAX_LETTERS)
    parser.add_argument("--gamma", type=float, default=DEFAULT_GAMMA)
    parser.add_argument("--lambda", dest="lam", type=float, default=DEFAULT_LAMBDA)
    parser.add_argument("--limit", type=int, default=0)
    parser.add_argument("--json-out", help="write per-trace results and summary as JSON")
    parser.add_argument("--quiet", action="store_true")
    args = parser.parse_args(argv)

    if not Path(args.model).exists():
        parser.error("model not found: " + args.model + " (pass --model)")
    if not Path(args.data).exists():
        parser.error("data not found: " + args.data)

    traces, labels = load_traces(args.data)
    pairs = usable_traces(traces, labels)
    if args.limit > 0:
        pairs = pairs[: args.limit]
    if not pairs:
        print("no usable labelled traces", file=sys.stderr)
        return 2

    encoder = FutoEncoder(args.model)
    results = []
    skipped = []
    for trace, expected in pairs:
        schema = str(trace.get("schema", ""))
        spellings = load_spellings(args.spellings) if args.spellings else spellings_for_schema(schema)
        if not spellings:
            skipped.append((str(trace.get("id", "")), schema))
            continue
        try:
            result = evaluate_trace(
                trace, expected, encoder, spellings,
                topk=args.topk, beam_width=args.beam, max_letters=args.max_letters,
                gamma=args.gamma, lam=args.lam,
            )
        except Exception as exc:  # keep going: one malformed trace must not hide others
            skipped.append((str(trace.get("id", "")), "error: " + str(exc)))
            continue
        results.append(result)
        if not args.quiet:
            verdict = "OK" if result.neural_top1_correct else "d=" + str(result.neural_edit_distance)
            print(
                result.trace_id,
                "exp=" + result.expected,
                "neu=" + result.neural_top1,
                verdict,
                "geo=" + ("hit" if result.geometry_contains else "miss"),
                "neu_hit=" + ("1" if result.neural_contains else "0"),
                "union=" + ("1" if result.union_contains else "0"),
            )

    summary = summarize(results)
    if skipped:
        summary["skipped"] = [{"id": trace_id, "reason": reason} for trace_id, reason in skipped]
    print()
    print("=== FUTO offline evaluation ===")
    for key, value in summary.items():
        if key in ("skipped",):
            continue
        print(key + ":", value)
    if skipped:
        print("skipped:", skipped)

    if args.json_out:
        payload = {
            "summary": summary,
            "results": [dataclasses.asdict(result) for result in results],
        }
        out = Path(args.json_out)
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf8")
        print("wrote", out)
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
