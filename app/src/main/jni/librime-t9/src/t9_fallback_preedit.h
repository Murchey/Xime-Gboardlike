#ifndef RIME_T9_FALLBACK_PREEDIT_H_
#define RIME_T9_FALLBACK_PREEDIT_H_

#include <string>

namespace rime {

// 无候选时（composition preedit 退化为原始数字串）生成不含数字的字母编码兜底。
// 仅用于 T9 会话的显示快照：按 ' 分段，纯数字段用贪婪音节切分逐位拼拼音。
std::string T9BuildFallbackPreedit(const std::string& input);

// 判定是否为纯 T9 数字编码（仅含 2-9 与分隔符 '），供调用方做触发条件判断。
bool T9IsPureDigitInput(const std::string& input);

}  // namespace rime

#endif  // RIME_T9_FALLBACK_PREEDIT_H_
