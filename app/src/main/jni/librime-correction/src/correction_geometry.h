// 邻键误触纠错：几何邻键表 + 码表词频评分（纯 C++，零外部依赖）。
//
// 与 jiucuo 的离线评估同口径：
//   · 邻键 = 键心归一化距离 <= 1.35 键宽/键高（含上下与斜向；librime 自带 keyboard_map
//     只有同行左右，会漏掉 q→a 这类最常见的上下误触）
//   · 码表评分：精确命中 → log(频次占比)；合法前缀 → PREFIX；非法 → INVALID
#ifndef RIME_CORRECTION_GEOMETRY_H_
#define RIME_CORRECTION_GEOMETRY_H_

#include <array>
#include <mutex>
#include <string>
#include <unordered_map>
#include <vector>

namespace rime {
namespace correction {

// 键心（归一化：x/键盘宽，y/按键区高）与键尺寸，取实测键盘几何（jiucuo/data/layout.json）
struct KeyGeom {
  float x, y;
};

// 26 个字母键的键心（a..z），实测自 1272x826@3.5 键盘
extern const std::array<KeyGeom, 26> kKeyCenters;
constexpr float kKeyW = 0.0881f;
constexpr float kKeyH = 0.2195f;
constexpr float kNeighborRadius = 1.35f;  // 邻键半径（键宽/高为单位）
constexpr float kRowPitch = 0.2604f;      // 行距（按键区高归一化，同 layout.json spacing.y）

// 同行判定（行距的一半为界）：用于跨行方向先验（真实误触 81% 同行，见
// jiucuo 对 TSI 的统计；跨行候选加 cross_row_penalty 抑制）
bool SameRow(int a, int b);

// 用 Kotlin 实测的 26 键几何覆盖静态表。
// centers_xy: [x0,y0,...,x25,y25]，x/键盘宽、y/按键区高；
// sizes_wh:   [w0,h0,...,w25,h25]，键宽/键盘宽、键高/按键区高。
// 无效项（NaN/<=0）回退静态值。
void SetKeyGeometry(const float* centers_xy, const float* sizes_wh, int n);

class NeighborTable {
 public:
  static const NeighborTable& Instance();

  // 字母 index（0..25）→ 邻键 index 列表（返回值拷贝，避免重建时读到半张表）
  std::vector<int> Neighbors(int letter) const {
    std::lock_guard<std::mutex> lock(neighbors_mutex_);
    return neighbors_[letter];
  }

  // 距离（键宽/高归一化），用于几何信道打分
  float Distance(int a, int b) const;

  // 几何变化后重建邻键表
  void Rebuild() const;

 private:
  NeighborTable();
  mutable std::array<std::vector<int>, 26> neighbors_;
  mutable std::mutex neighbors_mutex_;
};


}  // namespace correction
}  // namespace rime

#endif  // RIME_CORRECTION_GEOMETRY_H_
