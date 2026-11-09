// T9BuildFallbackPreedit 单元测试
// 无候选时生成不含数字的字母编码兜底，避免显示层冻结在上一帧声母。
#include <gtest/gtest.h>

#include <string>

#include "t9_fallback_preedit.h"

using rime::T9BuildFallbackPreedit;
using rime::T9IsPureDigitInput;

static bool HasDigit(const std::string& s) {
    for (char c : s) {
        if (c >= '0' && c <= '9') return true;
    }
    return false;
}

TEST(T9FallbackPreeditTest, RepeatedDigitsGrowPerKey) {
    const std::string a = T9BuildFallbackPreedit("9");
    const std::string b = T9BuildFallbackPreedit("99");
    const std::string c = T9BuildFallbackPreedit("999");
    const std::string d = T9BuildFallbackPreedit("9999");
    EXPECT_FALSE(HasDigit(a));
    EXPECT_FALSE(HasDigit(b));
    EXPECT_FALSE(HasDigit(c));
    EXPECT_FALSE(HasDigit(d));
    EXPECT_EQ(1u, a.size());
    EXPECT_EQ(2u, b.size());
    EXPECT_EQ(3u, c.size());
    EXPECT_EQ(4u, d.size());
    EXPECT_EQ(a, d.substr(0, 1));
}

TEST(T9FallbackPreeditTest, SevenRepeatedDigits) {
    const std::string d = T9BuildFallbackPreedit("7777");
    EXPECT_FALSE(HasDigit(d));
    EXPECT_EQ(4u, d.size());
}

TEST(T9FallbackPreeditTest, NormalSyllableSplit) {
    const std::string r = T9BuildFallbackPreedit("53744");
    EXPECT_FALSE(HasDigit(r));
    EXPECT_EQ(5u, r.size());
}

TEST(T9FallbackPreeditTest, SeparatorPreserved) {
    EXPECT_EQ("j'ge", T9BuildFallbackPreedit("5'43"));
    EXPECT_EQ("jj'gg", T9BuildFallbackPreedit("55'66"));
}

TEST(T9FallbackPreeditTest, EmptyAndNonDigit) {
    EXPECT_EQ("", T9BuildFallbackPreedit(""));
    EXPECT_EQ("abc", T9BuildFallbackPreedit("abc"));
    EXPECT_EQ("j'abc", T9BuildFallbackPreedit("5'abc"));
}

TEST(T9FallbackPreeditTest, PureDigitPredicate) {
    EXPECT_TRUE(T9IsPureDigitInput("9999"));
    EXPECT_TRUE(T9IsPureDigitInput("5'43"));
    EXPECT_FALSE(T9IsPureDigitInput(""));
    EXPECT_FALSE(T9IsPureDigitInput("you'942"));
    EXPECT_FALSE(T9IsPureDigitInput("53744a"));
}
