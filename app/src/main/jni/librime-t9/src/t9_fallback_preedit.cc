#include "t9_fallback_preedit.h"

#include <cctype>
#include <string>
#include <vector>

#include "t9_pinyin_map.h"

namespace rime {

bool T9IsPureDigitInput(const std::string& input) {
    if (input.empty()) return false;
    bool has_digit = false;
    for (char c : input) {
        if (c >= '2' && c <= '9') {
            has_digit = true;
        } else if (c != '\'') {
            return false;
        }
    }
    return has_digit;
}

static std::string ConvertDigitSegment(const std::string& segment) {
    std::string result;
    size_t pos = 0;
    while (pos < segment.size()) {
        auto options = T9PinyinMap::Instance().FirstSyllableOptions(
            segment.substr(pos), 1);
        if (options.empty()) {
            // 无法映射的数字：按单键首字母占位，保证输出长度与输入一致。
            const auto& letters = T9PinyinMap::Instance().LettersForDigit(segment[pos]);
            result += letters.empty() ? segment[pos] : letters.front();
            ++pos;
            continue;
        }
        result += options.front().pinyin;
        pos += static_cast<size_t>(options.front().digit_length);
    }
    return result;
}

std::string T9BuildFallbackPreedit(const std::string& input) {
    if (input.empty()) return std::string();

    std::string result;
    size_t start = 0;
    while (start <= input.size()) {
        size_t sep = input.find('\'', start);
        size_t end = (sep == std::string::npos) ? input.size() : sep;
        std::string segment = input.substr(start, end - start);
        if (!segment.empty()) {
            bool all_digits = true;
            for (char c : segment) {
                if (c < '2' || c > '9') { all_digits = false; break; }
            }
            result += all_digits ? ConvertDigitSegment(segment) : segment;
        }
        if (sep == std::string::npos) break;
        result += '\'';
        start = sep + 1;
    }
    return result;
}

}  // namespace rime
