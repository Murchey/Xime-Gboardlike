#include <rime/common.h>
#include <rime/registry.h>

#include <string>
#include <vector>

#include "correction_translator.h"

using namespace rime;

namespace {

static void rime_correction_initialize() {
  LOG(INFO) << "registering components from module 'correction'.";
  Registry& r = Registry::instance();
  r.Register("correction_translator", new CorrectionTranslatorComponent);
}

static void rime_correction_finalize() {}

}  // namespace

// 与 T9 插件同款：组件注册名与注入配置在单一地点维护，Kotlin 侧经 JNI 查询，
// 部署时自动把 translator 注入目标 schema（用户无需手改 schema 文件）。
std::vector<std::string> GetCorrectionSchemaPatches() {
  return {
      // 放在 table_translator 之前：纠错候选先于原码候选入队，再经 uniquifier 按
      // quality 排序，保证纠错项永远排在真候选之后（只追加，不抢位）。
      "correction_translator|engine/translators/@before 0|correction_translator",
      "correction_translator|correction/enable|true",
      "correction_translator|correction/mode|append",
      "correction_translator|correction/lambda|1.0",
      "correction_translator|correction/margin|2.0",
      "correction_translator|correction/max_candidates|3",
      "correction_translator|correction/min_code_length|2",
      // P2 混合打分权重：spatial_weight>0 时启用 Gboard 式空间候选融合
      "correction_translator|correction/spatial_weight|0.5",
      "correction_translator|correction/channel_weight|1.0",
  };
}

RIME_REGISTER_MODULE(correction)
