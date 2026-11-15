package com.kingzcheung.xime.association

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 所有联想入口共用的上下文判定：末尾必须是文字（汉字/字母）。 */
class AssociationContextPolicyTest {

    @Test
    fun `空与空白不联想`() {
        assertFalse(AssociationContextPolicy.isValid(""))
        assertFalse(AssociationContextPolicy.isValid(" "))
        assertFalse(AssociationContextPolicy.isValid("\u3000"))
        assertFalse(AssociationContextPolicy.isValid("你好 "))
    }

    @Test
    fun `标点与表情不联想`() {
        assertFalse(AssociationContextPolicy.isValid("你好，"))
        assertFalse(AssociationContextPolicy.isValid("Hello."))
        assertFalse(AssociationContextPolicy.isValid("好的😊"))
    }

    @Test
    fun `数字结尾不联想`() {
        listOf("2024", "第3", "12.5", "13800138000", "版本2", "iPhone 15", "３", "0").forEach {
            assertFalse("数字结尾[$it]不应触发联想", AssociationContextPolicy.isValid(it))
        }
    }

    @Test
    fun `汉字与字母结尾联想`() {
        assertTrue(AssociationContextPolicy.isValid("你好"))
        assertTrue(AssociationContextPolicy.isValid("第3章"))
        assertTrue(AssociationContextPolicy.isValid("hello"))
        assertTrue(AssociationContextPolicy.isValid("OK"))
    }
}
