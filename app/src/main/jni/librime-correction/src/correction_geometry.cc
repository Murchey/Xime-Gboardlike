#include "correction_geometry.h"

#include <cmath>
#include <mutex>

namespace rime {
namespace correction {

// 实测键心（a..z）
const std::array<KeyGeom, 26> kKeyCenters = {{
    {0.1038f, 0.3701f},  // a
    {0.5991f, 0.6299f},  // b
    {0.3994f, 0.6299f},  // c
    {0.3007f, 0.3701f},  // d
    {0.2563f, 0.1098f},  // e
    {0.3998f, 0.3701f},  // f
    {0.4988f, 0.3701f},  // g
    {0.5979f, 0.3701f},  // h
    {0.7449f, 0.1098f},  // i
    {0.6969f, 0.3701f},  // j
    {0.7960f, 0.3701f},  // k
    {0.8950f, 0.3701f},  // l
    {0.7987f, 0.6299f},  // m
    {0.6989f, 0.6299f},  // n
    {0.8424f, 0.1098f},  // o
    {0.9399f, 0.1098f},  // p
    {0.0597f, 0.1098f},  // q
    {0.3546f, 0.1098f},  // r
    {0.2020f, 0.3701f},  // s
    {0.4524f, 0.1098f},  // t
    {0.6474f, 0.1098f},  // u
    {0.4992f, 0.6299f},  // v
    {0.1580f, 0.1098f},  // w
    {0.2995f, 0.6299f},  // x
    {0.5499f, 0.1098f},  // y
    {0.1997f, 0.6299f},  // z
}};

namespace {

std::mutex g_geometry_mutex;
std::array<KeyGeom, 26> g_centers = kKeyCenters;
float g_avg_key_w = kKeyW;
float g_avg_key_h = kKeyH;
float g_row_pitch = kRowPitch;

}  // namespace

const NeighborTable& NeighborTable::Instance() {
  static NeighborTable table;
  return table;
}

NeighborTable::NeighborTable() {
  Rebuild();
}

void NeighborTable::Rebuild() const {
  std::lock_guard<std::mutex> lock(neighbors_mutex_);
  for (int i = 0; i < 26; ++i) {
    neighbors_[i].clear();
    for (int j = 0; j < 26; ++j) {
      if (i == j)
        continue;
      if (Distance(i, j) <= kNeighborRadius)
        neighbors_[i].push_back(j);
    }
  }
}

float NeighborTable::Distance(int a, int b) const {
  std::lock_guard<std::mutex> lock(g_geometry_mutex);
  const float dx = (g_centers[a].x - g_centers[b].x) / g_avg_key_w;
  const float dy = (g_centers[a].y - g_centers[b].y) / g_avg_key_h;
  return std::sqrt(dx * dx + dy * dy);
}

bool SameRow(int a, int b) {
  std::lock_guard<std::mutex> lock(g_geometry_mutex);
  return std::fabs(g_centers[a].y - g_centers[b].y) < 0.5f * g_row_pitch;
}

void SetKeyGeometry(const float* centers_xy, const float* sizes_wh, int n) {
  if (!centers_xy || !sizes_wh || n < 52)
    return;
  std::array<KeyGeom, 26> centers;
  float sum_w = 0.0f, sum_h = 0.0f;
  for (int i = 0; i < 26; ++i) {
    const float x = centers_xy[i * 2];
    const float y = centers_xy[i * 2 + 1];
    const float w = sizes_wh[i * 2];
    const float h = sizes_wh[i * 2 + 1];
    if (!std::isfinite(x) || !std::isfinite(y) ||
        !std::isfinite(w) || !std::isfinite(h) || w <= 0.0f || h <= 0.0f) {
      return;  // 缺键：整张表不切，避免真实/静态混用
    }
    centers[i] = {x, y};
    sum_w += w;
    sum_h += h;
  }
  {
    std::lock_guard<std::mutex> lock(g_geometry_mutex);
    g_centers = centers;
    g_avg_key_w = sum_w / 26.0f;
    g_avg_key_h = sum_h / 26.0f;
    g_row_pitch = g_avg_key_h * 1.186f;
  }
  NeighborTable::Instance().Rebuild();
}

}  // namespace correction
}  // namespace rime
