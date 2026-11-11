#!/usr/bin/env node
/**
 * 滑动轨迹评测：读 glide_samples.jsonl，输出 top-k / 可达性 / 错误分型。
 *
 * 用法: node tools/glide-eval.mjs <glide_samples.jsonl> [--key-width 108]
 *
 * 数据格式见 GlideTraceRecorder：e=trace 是轨迹本体，e=label 是标注（同 id 后写覆盖）。
 * 标注为「无效内容」的样本视为放弃样本，不参与准确率。
 */
import { readFileSync } from "node:fs";

const DISCARD_LABEL = "无效内容";

function levenshtein(a, b) {
  const m = a.length, n = b.length;
  const dp = Array.from({ length: m + 1 }, (_, i) =>
    Array.from({ length: n + 1 }, (_, j) => (i === 0 ? j : j === 0 ? i : 0)));
  for (let i = 1; i <= m; i++)
    for (let j = 1; j <= n; j++)
      dp[i][j] = Math.min(dp[i-1][j] + 1, dp[i][j-1] + 1, dp[i-1][j-1] + (a[i-1] === b[j-1] ? 0 : 1));
  return dp[m][n];
}

function editOps(a, b) {
  const m = a.length, n = b.length;
  const dp = Array.from({ length: m + 1 }, (_, i) =>
    Array.from({ length: n + 1 }, (_, j) => (i === 0 ? j : j === 0 ? i : 0)));
  for (let i = 1; i <= m; i++)
    for (let j = 1; j <= n; j++)
      dp[i][j] = Math.min(dp[i-1][j] + 1, dp[i][j-1] + 1, dp[i-1][j-1] + (a[i-1] === b[j-1] ? 0 : 1));
  const out = []; let i = m, j = n;
  while (i > 0 || j > 0) {
    if (i > 0 && j > 0 && a[i-1] === b[j-1]) { i--; j--; continue; }
    if (i > 0 && j > 0 && dp[i][j] === dp[i-1][j-1] + 1) { out.push(["SUB", i-1, a[i-1], b[j-1]]); i--; j--; continue; }
    if (i > 0 && dp[i][j] === dp[i-1][j] + 1) { out.push(["EXTRA", i-1, a[i-1], null]); i--; continue; }
    out.push(["MISSING", i, b[j-1], null]); j--;
  }
  return out.reverse();
}

const QWERTY = {
  q: [0,0], w: [0,1], e: [0,2], r: [0,3], t: [0,4], y: [0,5], u: [0,6], i: [0,7], o: [0,8], p: [0,9],
  a: [1,0], s: [1,1], d: [1,2], f: [1,3], g: [1,4], h: [1,5], j: [1,6], k: [1,7], l: [1,8],
  z: [2,0], x: [2,1], c: [2,2], v: [2,3], b: [2,4], n: [2,5], m: [2,6],
};
const keyDistance = (a, b) =>
  QWERTY[a] && QWERTY[b]
    ? Math.max(Math.abs(QWERTY[a][0] - QWERTY[b][0]), Math.abs(QWERTY[a][1] - QWERTY[b][1]))
    : -1;

const file = process.argv[2];
const keyWidth = Number(process.argv.includes("--key-width")
  ? process.argv[process.argv.indexOf("--key-width") + 1] : 108);
if (!file) { console.error("usage: node glide-eval.mjs <glide_samples.jsonl>"); process.exit(2); }

const objs = readFileSync(file, "utf8").split("\n").filter(l => l.trim()).map(l => {
  try { return JSON.parse(l); } catch { return null; }
}).filter(Boolean);

const traces = objs.filter(o => o.e === "trace");
const labelById = new Map();
for (const o of objs.filter(o => o.e === "label")) labelById.set(o.id, o.expected);

const rows = [];
for (const t of traces) {
  const expected = labelById.get(t.id) ?? "";
  const usable = expected.length > 0 && expected !== DISCARD_LABEL;
  const pts = t.points || [];
  let arc = 0;
  for (let i = 1; i < pts.length; i++) arc += Math.hypot(pts[i][0]-pts[i-1][0], pts[i][1]-pts[i-1][1]);
  const keys = arc / keyWidth;
  const needSamples = Math.ceil(keys / 0.25) + 1;
  const cands = t.candidates || [];
  rows.push({
    id: t.id, code: t.code, expected, usable, cands, keys,
    saturated: needSamples > 128,
    dist: usable ? levenshtein(t.code, expected) : null,
    correctRank: usable ? cands.indexOf(expected) : -1,
    codeRank: cands.indexOf(t.code),
    ops: usable ? editOps(t.code, expected) : [],
  });
}

const usable = rows.filter(r => r.usable);
const pct = (n, d) => d ? (n / d * 100).toFixed(0) + "%" : "-";
const avg = a => a.length ? a.reduce((x, y) => x + y, 0) / a.length : 0;

console.log("=== 样本 ===");
console.log(`轨迹 ${rows.length} 条，可用 ${usable.length} 条，放弃 ${rows.length - usable.length} 条`);

console.log("\n=== 准确率 ===");
console.log(`top-1 完全正确   ${usable.filter(r => r.dist === 0).length}/${usable.length}  (${pct(usable.filter(r => r.dist === 0).length, usable.length)})`);
console.log(`top-5 命中       ${usable.filter(r => r.correctRank >= 0 && r.correctRank < 5).length}/${usable.length}  (${pct(usable.filter(r => r.correctRank >= 0 && r.correctRank < 5).length, usable.length)})`);
console.log(`编辑距离<=1     ${usable.filter(r => r.dist <= 1).length}/${usable.length}  (${pct(usable.filter(r => r.dist <= 1).length, usable.length)})`);
console.log(`几何候选含正解   ${usable.filter(r => r.correctRank >= 0).length}/${usable.length}  (${pct(usable.filter(r => r.correctRank >= 0).length, usable.length)})  <- 解码器天花板`);

console.log("\n=== 错误分型（code -> expected 的编辑操作）===");
let extra = 0, missing = 0, sub = 0, subAdjacent = 0;
for (const r of usable) {
  if (!r.ops.length) continue;
  const desc = r.ops.map(([k, pos, a, b]) => {
    if (k === "SUB") {
      sub++;
      const d = keyDistance(a, b);
      if (d >= 0 && d <= 1) subAdjacent++;
      return `替换 ${a}->${b}@${pos}(键距${d})`;
    }
    if (k === "EXTRA") { extra++; return `多键 ${a}@${pos}`; }
    missing++; return `缺键 ${b}@${pos}`;
  }).join(", ");
  console.log(`${r.id} d=${r.dist} ${r.code} -> ${r.expected}\n    ${desc}`);
}
console.log(`\n多键 ${extra} / 缺键 ${missing} / 替换 ${sub}（其中相邻键 ${subAdjacent}）`);

console.log("\n=== 重采样饱和（MAX_SAMPLES=128）===");
const sat = usable.filter(r => r.saturated), norm = usable.filter(r => !r.saturated);
console.log(`饱和 ${sat.length} 条，平均编辑距离 ${avg(sat.map(r => r.dist)).toFixed(2)}`);
console.log(`未饱和 ${norm.length} 条，平均编辑距离 ${avg(norm.map(r => r.dist)).toFixed(2)}`);

console.log("\n=== 连续重复字母 ===");
const withDup = usable.filter(r => /(.)\1/.test(r.code));
const collapseFix = usable.filter(r => r.dist > 0 && levenshtein(r.code.replace(/(.)\1+/g, "$1"), r.expected) < r.dist);
const expWithDup = usable.filter(r => /(.)\1/.test(r.expected));
console.log(`解码结果含重复 ${withDup.length} 条；折叠后距离下降 ${collapseFix.length} 条`);
console.log(`期望本身含重复 ${expWithDup.length} 条  <- 无脑折叠会误伤，需按音节边界判定`);
