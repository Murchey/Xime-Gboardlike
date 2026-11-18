package com.kingzcheung.xime.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 词库名校验：只允许字母/数字/下划线/短横线，1–32 位，不能重名。 */
class UserDictBookManagerTest {

    @Test
    fun `合法名称通过`() {
        assertNull(UserDictBookManager.validateName("my_dict-1", listOf("rime_ice")))
        assertNull(UserDictBookManager.validateName("  abc  ", emptyList()))
    }

    @Test
    fun `空名与非法字符被拒`() {
        assertEquals("名称不能为空", UserDictBookManager.validateName("   ", emptyList()))
        assertEquals(
            "只能用字母、数字、下划线或短横线（1–32 个字符）",
            UserDictBookManager.validateName("我的词库", emptyList()),
        )
        assertEquals(
            "只能用字母、数字、下划线或短横线（1–32 个字符）",
            UserDictBookManager.validateName("a".repeat(33), emptyList()),
        )
    }

    @Test
    fun `重名被拒`() {
        assertEquals("同名词库已存在", UserDictBookManager.validateName("rime_ice", listOf("rime_ice")))
    }
}
