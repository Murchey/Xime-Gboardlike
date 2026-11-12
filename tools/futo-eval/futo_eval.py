#!/usr/bin/env python3
"""FUTO Swipe encoder offline replay for Xime glide traces.

This module is the shared implementation behind eval.py and export_golden.py.
It is intentionally dependency-light (numpy + executorch + torch) so the same
numbers can be reproduced on any desktop before touching the Android app.

Design notes
------------
* The encoder is layout-agnostic: we pass the *actual* letter key centres from
  the trace, normalised into the keyboard frame, plus a boolean mask.
* Coordinates are normalised by the bounding box of the letter keys. For a
  standard 26-key QWERTY this reproduces FUTO's reference frame (x 0.05..0.95,
  y 0.167..0.833).
* Timestamps follow FUTO's reference preprocessing: uniform 60 Hz resampling,
  then exactly 64 points.
* Decoding is a CTC prefix beam constrained by the schema's legal key strings
  (pinyin syllables, or double-pinyin two-key codes). It is deliberately the
  simplest correct decoder; it is not the production Kotlin implementation.
* The model emits 64 key classes + blank. Class i corresponds to key slot i in
  the order we pass layout_keys. Duplicate physical keys sharing one code are
  merged with logsumexp after inference.
"""

from __future__ import annotations

import json
import math
from dataclasses import dataclass
from typing import Iterable, Sequence

import numpy as np

MODEL_POINTS = 64
MAX_KEY_SLOTS = 64
BLANK_CLASS = 64
# FUTO's published encoder-only ranking params (tuned on English words).
FUTO_ENCODER_GAMMA = 0.4056
FUTO_ENCODER_LAMBDA = 0.0176
# Measured on the 16 real Chinese traces: raw CTC ranking (gamma=0) keeps one
# more correct code in the top-16 than the English-tuned length normalisation.
# Keep both available; the CLI can override.
DEFAULT_GAMMA = 0.0
DEFAULT_LAMBDA = 0.0
DEFAULT_BEAM = 200
DEFAULT_MAX_LETTERS = 48
DEFAULT_TOPK = 16

# 421 complete pinyin syllables, extracted from
# Xime-Gboardlike/app/src/main/java/com/kingzcheung/xime/glide/GlideSyllableTrie.kt
SYLLABLES = ("a", "ai", "an", "ang", "ao", "ba", "bai", "ban", "bang", "bao", "bei", "ben", "beng", "bi", "bian", "biang", "biao", "bie", "bin", "bing", "bo", "bu", "ca", "cai", "can", "cang", "cao", "ce", "cen", "ceng", "cha", "chai", "chan", "chang", "chao", "che", "chen", "cheng", "chi", "chong", "chou", "chu", "chua", "chuai", "chuan", "chuang", "chui", "chun", "chuo", "ci", "cong", "cou", "cu", "cuan", "cui", "cun", "cuo", "da", "dai", "dan", "dang", "dao", "de", "dei", "den", "deng", "di", "dia", "dian", "diao", "die", "ding", "diu", "dong", "dou", "du", "duan", "dui", "dun", "duo", "e", "ei", "en", "er", "fa", "fan", "fang", "fei", "fen", "feng", "fiao", "fo", "fou", "fu", "ga", "gai", "gan", "gang", "gao", "ge", "gei", "gen", "geng", "gong", "gou", "gu", "gua", "guai", "guan", "guang", "gui", "gun", "guo", "ha", "hai", "han", "hang", "hao", "he", "hei", "hen", "heng", "hong", "hou", "hu", "hua", "huai", "huan", "huang", "hui", "hun", "huo", "ji", "jia", "jian", "jiang", "jiao", "jie", "jin", "jing", "jiong", "jiu", "ju", "juan", "jue", "jun", "jv", "jve", "ka", "kai", "kan", "kang", "kao", "ke", "kei", "ken", "keng", "kong", "kou", "ku", "kua", "kuai", "kuan", "kuang", "kui", "kun", "kuo", "la", "lai", "lan", "lang", "lao", "le", "lei", "leng", "li", "lia", "lian", "liang", "liao", "lie", "lin", "ling", "liu", "lo", "long", "lou", "lu", "luan", "lue", "lun", "luo", "lv", "lve", "ma", "mai", "man", "mang", "mao", "me", "mei", "men", "meng", "mi", "mian", "miao", "mie", "min", "ming", "miu", "mo", "mou", "mu", "na", "nai", "nan", "nang", "nao", "ne", "nei", "nen", "neng", "ni", "nian", "niang", "niao", "nie", "nin", "ning", "niu", "nong", "nou", "nu", "nuan", "nue", "nun", "nuo", "nv", "nve", "o", "ou", "pa", "pai", "pan", "pang", "pao", "pei", "pen", "peng", "pi", "pian", "piao", "pie", "pin", "ping", "po", "pou", "pu", "qi", "qia", "qian", "qiang", "qiao", "qie", "qin", "qing", "qiong", "qiu", "qu", "quan", "que", "qun", "qv", "qve", "ran", "rang", "rao", "re", "ren", "reng", "ri", "rong", "rou", "ru", "ruan", "rui", "run", "ruo", "sa", "sai", "san", "sang", "sao", "se", "sen", "seng", "sha", "shai", "shan", "shang", "shao", "she", "shei", "shen", "sheng", "shi", "shou", "shu", "shua", "shuai", "shuan", "shuang", "shui", "shun", "shuo", "si", "song", "sou", "su", "suan", "sui", "sun", "suo", "ta", "tai", "tan", "tang", "tao", "te", "teng", "ti", "tian", "tiao", "tie", "ting", "tong", "tou", "tu", "tuan", "tui", "tun", "tuo", "wa", "wai", "wan", "wang", "wei", "wen", "weng", "wo", "wu", "xi", "xia", "xian", "xiang", "xiao", "xie", "xin", "xing", "xiong", "xiu", "xu", "xuan", "xue", "xun", "xv", "xve", "ya", "yan", "yang", "yao", "ye", "yi", "yin", "ying", "yo", "yong", "you", "yu", "yuan", "yue", "yun", "yv", "yve", "za", "zai", "zan", "zang", "zao", "ze", "zei", "zen", "zeng", "zha", "zhai", "zhan", "zhang", "zhao", "zhe", "zhei", "zhen", "zheng", "zhi", "zhong", "zhou", "zhu", "zhua", "zhuai", "zhuan", "zhuang", "zhui", "zhun", "zhuo", "zi", "zong", "zou", "zu", "zuan", "zui", "zun", "zuo")

FLYPY_INITIALS = (
    "zh", "ch", "sh", "b", "p", "m", "f", "d", "t", "n", "l",
    "g", "k", "h", "j", "q", "x", "r", "z", "c", "s", "y", "w",
)

FLYPY_FINAL_KEYS = {
    "a": "a", "o": "o", "e": "e", "i": "i", "u": "u", "v": "v",
    "ai": "d", "ei": "w", "ui": "v", "ao": "c", "ou": "z",
    "iu": "q", "ie": "p", "ve": "t", "ue": "t", "er": "r",
    "an": "j", "en": "f", "in": "b", "un": "y", "vn": "y",
    "ang": "h", "eng": "g", "ing": "k", "ong": "s",
    "ia": "x", "ua": "x", "uo": "o", "uai": "k", "uan": "r",
    "uang": "l", "ian": "m", "iang": "l", "iao": "n", "iong": "s",
}

FLYPY_ZERO_INITIAL = {
    "a": "aa", "o": "oo", "e": "ee", "ang": "ah", "eng": "eg",
    "ong": "os", "er": "er",
}

FLYPY_BARE = {"ai", "ei", "ao", "ou", "an", "en"}


def _flypy_initial_key(initial: str) -> str:
    return {"zh": "v", "ch": "i", "sh": "u"}.get(initial, initial[0])


def flypy_code(syllable: str) -> str | None:
    """Project one real pinyin syllable to its two-key Xiaohe (flypy) code."""
    for initial in FLYPY_INITIALS:
        if syllable.startswith(initial) and len(syllable) > len(initial):
            final = syllable[len(initial):]
            key = FLYPY_FINAL_KEYS.get(final)
            return None if key is None else _flypy_initial_key(initial) + key
    if syllable in FLYPY_ZERO_INITIAL:
        return FLYPY_ZERO_INITIAL[syllable]
    if syllable in FLYPY_BARE:
        return syllable
    return None


def flypy_spellings() -> list[str]:
    return sorted({code for code in (flypy_code(s) for s in SYLLABLES) if code})


def full_pinyin_spellings() -> list[str]:
    return list(SYLLABLES)


def spellings_for_schema(schema: str) -> list[str] | None:
    """Best-effort default spellings.

    The exact Rime projection is available from RimeEngine.getGlideSpellings in
    the app; for offline work we only know the common cases. Callers can pass
    an explicit spellings file for anything else.
    """
    name = (schema or "").lower()
    if "flypy" in name or name in ("double_pinyin_flypy",):
        return flypy_spellings()
    if "double_pinyin" in name or "shuangpin" in name:
        return None  # other double-pinyin schemes need their own table
    return full_pinyin_spellings()


def spellings_from_file(path: str) -> list[str]:
    with open(path, "r", encoding="utf8") as handle:
        payload = json.load(handle)
    if isinstance(payload, dict):
        payload = payload.get("spellings", [])
    if not isinstance(payload, list) or not all(isinstance(x, str) and x for x in payload):
        raise ValueError("spellings file must contain a non-empty list of strings")
    return list(payload)


class SpellingConstraint:
    """Prefix-closed trie of legal key strings, with syllable boundaries.

    State is a small set of trie nodes. Node 0 is both the root and the
    "exactly at a syllable boundary" state, matching GlideSpellingTrie.
    """

    def __init__(self, spellings: Iterable[str]):
        self.children: list[dict[str, int]] = [{}]
        self.complete: list[bool] = [False]
        for spelling in sorted({s for s in spellings if s}):
            if any(ch not in "abcdefghijklmnopqrstuvwxyz;" for ch in spelling):
                raise ValueError("illegal spelling: " + spelling)
            node = 0
            for ch in spelling:
                nxt = self.children[node].get(ch)
                if nxt is None:
                    nxt = len(self.children)
                    self.children[node][ch] = nxt
                    self.children.append({})
                    self.complete.append(False)
                node = nxt
            self.complete[node] = True
        self.start: tuple[int, ...] = (0,)

    def extend(self, states: Sequence[int], ch: str) -> tuple[int, ...]:
        out: set[int] = set()
        for node in states:
            child = self.children[node].get(ch)
            if child is None:
                continue
            if self.complete[child]:
                out.add(0)
            if self.children[child]:
                out.add(child)
        return tuple(sorted(out))

    def is_complete(self, states: Sequence[int]) -> bool:
        return 0 in states


@dataclass
class PreparedSwipe:
    features: np.ndarray       # [2, 64]
    keys: np.ndarray           # [64, 2]
    mask: np.ndarray           # [64] bool
    slot_codes: list[str]      # per key slot, in layout_keys order
    alphabet: list[str]        # unique codes, letters first then ';'

    def raw_key_count(self) -> int:
        return len(self.slot_codes)


def _key_sort_key(code: str) -> tuple[int, str]:
    return (1 if code == ";" else 0, code)


def resample_swipe(px: Sequence[float], py: Sequence[float], pt: Sequence[float], points: int = MODEL_POINTS) -> np.ndarray:
    """FUTO reference preprocessing: 60 Hz uniform, then exactly 64 points."""
    x = np.asarray(px, dtype=np.float64)
    y = np.asarray(py, dtype=np.float64)
    t = np.asarray(pt, dtype=np.float64)
    if x.size != y.size or x.size != t.size or x.size < 2:
        raise ValueError("swipe needs at least two matched points")
    t = t - t[0]
    if t[-1] > 1e-3:
        n60 = max(2, round(t[-1] / (1000.0 / 60.0)) + 1)
        tt = np.linspace(0.0, t[-1], n60)
        x = np.interp(tt, t, x)
        y = np.interp(tt, t, y)
    idx = np.linspace(0, x.size - 1, points)
    rx = np.interp(idx, np.arange(x.size), x)
    ry = np.interp(idx, np.arange(y.size), y)
    return np.stack([rx, ry], axis=0).astype(np.float32)


def prepare_trace(trace: dict) -> PreparedSwipe:
    keys = []
    for key in trace.get("keys", []):
        code = key.get("code", "")
        if len(code) == 1 and (code in "abcdefghijklmnopqrstuvwxyz;"):
            keys.append(key)
    if not keys:
        raise ValueError("trace has no usable letter keys")

    lefts = [float(k["l"]) for k in keys]
    rights = [float(k["r"]) for k in keys]
    tops = [float(k["t"]) for k in keys]
    bottoms = [float(k["b"]) for k in keys]
    x0, x1 = min(lefts), max(rights)
    y0, y1 = min(tops), max(bottoms)
    width = x1 - x0
    height = y1 - y0
    if width <= 0 or height <= 0:
        raise ValueError("invalid key geometry")

    ordered = sorted(keys, key=lambda k: _key_sort_key(k["code"]))
    slot_codes = [k["code"] for k in ordered]
    alphabet = list(dict.fromkeys(slot_codes))
    alphabet.sort(key=_key_sort_key)

    centres = np.zeros((MAX_KEY_SLOTS, 2), dtype=np.float32)
    mask = np.zeros((MAX_KEY_SLOTS,), dtype=bool)
    for index, key in enumerate(ordered):
        centres[index, 0] = ((float(key["l"]) + float(key["r"])) / 2.0 - x0) / width
        centres[index, 1] = ((float(key["t"]) + float(key["b"])) / 2.0 - y0) / height
        mask[index] = True

    points = trace.get("points", [])
    px = [float(p[0]) for p in points]
    py = [float(p[1]) for p in points]
    pt = [float(p[2]) for p in points]
    features = resample_swipe(
        [(v - x0) / width for v in px],
        [(v - y0) / height for v in py],
        pt,
    )
    return PreparedSwipe(features=features, keys=centres, mask=mask,
                         slot_codes=slot_codes, alphabet=alphabet)


class FutoEncoder:
    """Thin wrapper around the ExecuTorch Python runtime (loaded lazily)."""

    def __init__(self, model_path: str):
        from executorch.runtime import Runtime  # imported lazily for tests

        self.model_path = model_path
        self.method = Runtime.get().load_program(model_path).load_method("forward")

    def run(self, prepared: PreparedSwipe) -> tuple[np.ndarray, np.ndarray]:
        import torch

        outputs = self.method.execute((
            torch.from_numpy(prepared.features[None].copy()),
            torch.from_numpy(prepared.keys[None].copy()),
            torch.from_numpy(prepared.mask[None].copy()),
        ))
        log_emissions = outputs[0].numpy()[0].astype(np.float64)  # [32, 65]
        intention = outputs[2].numpy()[0].reshape(-1).astype(np.float64)
        return log_emissions, intention


def merge_slots(log_emissions: np.ndarray, prepared: PreparedSwipe) -> tuple[np.ndarray, np.ndarray]:
    """Collapse duplicate key slots into one class per unique code."""
    groups: dict[str, list[int]] = {}
    for slot, code in enumerate(prepared.slot_codes):
        groups.setdefault(code, []).append(slot)
    merged = []
    for code in prepared.alphabet:
        columns = log_emissions[:, groups[code]]
        merged.append(np.logaddexp.reduce(columns, axis=1) if columns.shape[1] > 1 else columns[:, 0])
    return np.stack(merged, axis=1), log_emissions[:, BLANK_CLASS]


def ctc_prefix_beam(
    letter_logp: np.ndarray,
    blank_logp: np.ndarray,
    alphabet: Sequence[str],
    constraint: SpellingConstraint,
    beam_width: int = DEFAULT_BEAM,
    max_letters: int = DEFAULT_MAX_LETTERS,
    gamma: float = DEFAULT_GAMMA,
    lam: float = DEFAULT_LAMBDA,
) -> list[tuple[float, str, float]]:
    """Constrained CTC prefix beam search.

    Returns (length-normalised score, letters, raw log score), best first.
    Score follows FUTO's encoder-only ranking: raw / L**gamma + lam * L.
    """
    neg_inf = float("-inf")
    beam: dict[str, tuple[float, float, tuple[int, ...]]] = {
        "": (0.0, neg_inf, constraint.start),
    }

    for step in range(letter_logp.shape[0]):
        step_logp = letter_logp[step]
        blank = float(blank_logp[step])
        nxt_beam: dict[str, tuple[float, float, tuple[int, ...]]] = {}

        def add(prefix: str, pb: float, pnb: float, states: tuple[int, ...]) -> None:
            current = nxt_beam.get(prefix)
            if current is None:
                nxt_beam[prefix] = (pb, pnb, states)
            else:
                nxt_beam[prefix] = (
                    float(np.logaddexp(current[0], pb)),
                    float(np.logaddexp(current[1], pnb)),
                    states,
                )

        for prefix, (pb, pnb, states) in beam.items():
            add(prefix, pb + blank, pnb + blank, states)
            last = prefix[-1] if prefix else ""
            total = float(np.logaddexp(pb, pnb))
            for index, ch in enumerate(alphabet):
                emit = float(step_logp[index])
                if emit == neg_inf:
                    continue
                candidate_states = constraint.extend(states, ch)
                if not candidate_states:
                    continue
                candidate = prefix + ch
                if len(candidate) > max_letters:
                    continue
                if ch == last:
                    if pb == neg_inf:
                        continue
                    add(candidate, neg_inf, pb + emit, candidate_states)
                else:
                    if total == neg_inf:
                        continue
                    add(candidate, neg_inf, total + emit, candidate_states)

        ranked = sorted(
            nxt_beam.items(),
            key=lambda item: -float(np.logaddexp(item[1][0], item[1][1])),
        )[:beam_width]
        beam = dict(ranked)
        if not beam:
            return []

    scored: list[tuple[float, str, float]] = []
    for prefix, (pb, pnb, states) in beam.items():
        if not constraint.is_complete(states):
            continue
        raw = float(np.logaddexp(pb, pnb))
        length = max(len(prefix), 1)
        scored.append((raw / (length ** gamma) + lam * length, prefix, raw))
    scored.sort(key=lambda item: -item[0])
    return scored


def levenshtein(a: str, b: str) -> int:
    if a == b:
        return 0
    if not a:
        return len(b)
    if not b:
        return len(a)
    previous = list(range(len(b) + 1))
    for i, ca in enumerate(a, 1):
        current = [i] + [0] * len(b)
        for j, cb in enumerate(b, 1):
            current[j] = min(
                previous[j] + 1,
                current[j - 1] + 1,
                previous[j - 1] + (ca != cb),
            )
        previous = current
    return previous[-1]


@dataclass
class TraceResult:
    trace_id: str
    schema: str
    expected: str
    geometry_code: str
    geometry_candidates: list[str]
    neural_top: list[str]
    neural_top1: str
    neural_edit_distance: int
    geometry_distance: int
    geometry_contains: bool
    neural_contains: bool
    union_contains: bool
    neural_top1_correct: bool


def evaluate_trace(
    trace: dict,
    expected: str,
    encoder: FutoEncoder,
    spellings: Sequence[str],
    topk: int = DEFAULT_TOPK,
    beam_width: int = DEFAULT_BEAM,
    max_letters: int = DEFAULT_MAX_LETTERS,
    gamma: float = DEFAULT_GAMMA,
    lam: float = DEFAULT_LAMBDA,
) -> TraceResult:
    prepared = prepare_trace(trace)
    log_emissions, _intention = encoder.run(prepared)
    letter_logp, blank_logp = merge_slots(log_emissions, prepared)
    constraint = SpellingConstraint(spellings)
    scored = ctc_prefix_beam(
        letter_logp, blank_logp, prepared.alphabet, constraint,
        beam_width=beam_width, max_letters=max_letters, gamma=gamma, lam=lam,
    )
    neural_top = [letters for _score, letters, _raw in scored[:topk]]
    geometry_candidates = [str(c) for c in trace.get("candidates", [])]
    geometry_code = str(trace.get("code", ""))
    expected = (expected or "").strip().lower()

    neural_top1 = neural_top[0] if neural_top else ""
    geometry_distance = levenshtein(geometry_code, expected)
    return TraceResult(
        trace_id=str(trace.get("id", "")),
        schema=str(trace.get("schema", "")),
        expected=expected,
        geometry_code=geometry_code,
        geometry_candidates=geometry_candidates,
        neural_top=neural_top,
        neural_top1=neural_top1,
        neural_edit_distance=levenshtein(neural_top1, expected),
        geometry_distance=geometry_distance,
        geometry_contains=expected in geometry_candidates,
        neural_contains=expected in neural_top,
        union_contains=expected in (neural_top + geometry_candidates),
        neural_top1_correct=neural_top1 == expected,
    )


def load_traces(path: str) -> tuple[list[dict], dict[str, str]]:
    traces: list[dict] = []
    labels: dict[str, str] = {}
    with open(path, "r", encoding="utf8") as handle:
        for line in handle:
            line = line.strip()
            if not line:
                continue
            try:
                obj = json.loads(line)
            except json.JSONDecodeError:
                continue
            if obj.get("e") == "label":
                labels[str(obj.get("id", ""))] = str(obj.get("expected", ""))
            elif obj.get("e") == "trace":
                traces.append(obj)
    return traces, labels


DISCARD_LABEL = "无效内容"


def usable_traces(traces: list[dict], labels: dict[str, str]) -> list[tuple[dict, str]]:
    out = []
    for trace in traces:
        expected = labels.get(str(trace.get("id", "")), "")
        if expected and expected != DISCARD_LABEL:
            out.append((trace, expected))
    return out


def summarize(results: Sequence[TraceResult]) -> dict:
    total = len(results)
    if total == 0:
        return {"usable": 0}
    def pct(count: int) -> float:
        return round(count * 100.0 / total, 1)
    return {
        "usable": total,
        "neural_top1_correct": sum(1 for r in results if r.neural_top1_correct),
        "neural_top1_correct_pct": pct(sum(1 for r in results if r.neural_top1_correct)),
        "neural_contains": sum(1 for r in results if r.neural_contains),
        "neural_contains_pct": pct(sum(1 for r in results if r.neural_contains)),
        "neural_edit_distance_le_1": sum(1 for r in results if r.neural_edit_distance <= 1),
        "neural_edit_distance_le_1_pct": pct(sum(1 for r in results if r.neural_edit_distance <= 1)),
        "geometry_contains": sum(1 for r in results if r.geometry_contains),
        "geometry_contains_pct": pct(sum(1 for r in results if r.geometry_contains)),
        "geometry_top1_correct": sum(1 for r in results if r.geometry_distance == 0),
        "geometry_top1_correct_pct": pct(sum(1 for r in results if r.geometry_distance == 0)),
        "union_contains": sum(1 for r in results if r.union_contains),
        "union_contains_pct": pct(sum(1 for r in results if r.union_contains)),
    }
