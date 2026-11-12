#!/usr/bin/env python3
"""Export golden encoder fixtures for Android/JVM tests.

The fixture contains the exact float inputs we feed to the encoder plus the
reference log-probability outputs, so the Kotlin CTC decoder can be tested
without any native runtime. It also contains the reference top-K from this
Python decoder, for cross-checking the production beam.

Usage:
    python tools/futo-eval/export_golden.py --data tmpImg/....jsonl
    python tools/futo-eval/export_golden.py --data ... --ids g111046-3,g111046-10
"""

from __future__ import annotations

import argparse
import json
import os
import sys
from pathlib import Path

from futo_eval import (
    BLANK_CLASS,
    DEFAULT_BEAM,
    DEFAULT_GAMMA,
    DEFAULT_LAMBDA,
    DEFAULT_MAX_LETTERS,
    DEFAULT_TOPK,
    MODEL_POINTS,
    FutoEncoder,
    SpellingConstraint,
    ctc_prefix_beam,
    load_traces,
    merge_slots,
    prepare_trace,
    spellings_for_schema,
    usable_traces,
)


def default_model_path() -> Path:
    here = Path(__file__).resolve()
    return here.parents[3] / "futo-swipe" / "honorable_sturgeon" / "model_fp32.pte"


def default_out_path() -> Path:
    here = Path(__file__).resolve()
    return here.parents[2] / "app" / "src" / "test" / "resources" / "futo" / "golden.json"


def rounded(values, digits: int):
    if hasattr(values, "tolist"):
        values = values.tolist()
    return [[round(float(v), digits) for v in row] for row in values] if values and isinstance(values[0], (list, tuple)) else [round(float(v), digits) for v in values]


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--data", required=True, help="glide_samples .jsonl")
    parser.add_argument("--model", default=os.environ.get("FUTO_MODEL_PATH", str(default_model_path())))
    parser.add_argument("--ids", help="comma separated trace ids; default first 3 usable")
    parser.add_argument("--count", type=int, default=3)
    parser.add_argument("--topk", type=int, default=DEFAULT_TOPK)
    parser.add_argument("--beam", type=int, default=DEFAULT_BEAM)
    parser.add_argument("--out", default=str(default_out_path()))
    args = parser.parse_args(argv)

    if not Path(args.model).exists():
        parser.error("model not found: " + args.model)
    traces, labels = load_traces(args.data)
    pairs = usable_traces(traces, labels)
    if args.ids:
        wanted = [x.strip() for x in args.ids.split(",") if x.strip()]
        pairs = [pair for pair in pairs if str(pair[0].get("id")) in wanted]
    else:
        pairs = pairs[: args.count]
    if not pairs:
        print("no matching usable traces", file=sys.stderr)
        return 2

    encoder = FutoEncoder(args.model)
    cases = []
    for trace, expected in pairs:
        schema = str(trace.get("schema", ""))
        spellings = spellings_for_schema(schema)
        if not spellings:
            print("skip unsupported schema:", schema, file=sys.stderr)
            continue
        prepared = prepare_trace(trace)
        log_emissions, intention = encoder.run(prepared)
        letter_logp, blank_logp = merge_slots(log_emissions, prepared)
        constraint = SpellingConstraint(spellings)
        scored = ctc_prefix_beam(
            letter_logp, blank_logp, prepared.alphabet, constraint,
            beam_width=args.beam, max_letters=DEFAULT_MAX_LETTERS,
        )
        cases.append({
            "id": str(trace.get("id", "")),
            "schema": schema,
            "expected": expected,
            "alphabet": prepared.alphabet,
            "slotCodes": prepared.slot_codes,
            "features": rounded(prepared.features, 6),
            "keys": rounded(prepared.keys, 6),
            "mask": [1 if v else 0 for v in prepared.mask],
            "logEmissions": rounded(log_emissions, 5),
            "intention": rounded(intention, 6),
            "spellings": spellings,
            "neuralTop": [
                {"letters": letters, "score": round(score, 6), "raw": round(raw, 6)}
                for score, letters, raw in scored[: args.topk]
            ],
            "geometryCode": str(trace.get("code", "")),
            "geometryCandidates": [str(c) for c in trace.get("candidates", [])],
        })

    payload = {
        "generatedBy": "tools/futo-eval/export_golden.py",
        "model": str(args.model),
        "gamma": DEFAULT_GAMMA,
        "lambda": DEFAULT_LAMBDA,
        "blankClass": BLANK_CLASS,
        "points": MODEL_POINTS,
        "timesteps": 32,
        "cases": cases,
    }
    out = Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(payload, ensure_ascii=False, indent=1), encoding="utf8")
    print("wrote", out, "cases:", len(cases), "bytes:", out.stat().st_size)
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
