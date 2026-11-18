package com.kingzcheung.xime.rime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 统一用户词库补丁：只在方案 custom.yaml 里补 translator/user_dict，不动已有配置。 */
class UnifiedUserDictPatchTest {

    private val key = "  \"translator/user_dict\": rime_ice"

    @Test
    fun `空文件生成patch块`() {
        val out = RimeConfigHelper.applyUnifiedUserDictPatch("", "rime_ice")!!
        assertEquals("patch:\n" + key + "\n", out)
    }

    @Test
    fun `已有patch块追加在块末尾`() {
        val src = "patch:\n  menu/page_size: 5\n"
        val out = RimeConfigHelper.applyUnifiedUserDictPatch(src, "rime_ice")!!
        assertEquals("patch:\n  menu/page_size: 5\n" + key + "\n", out)
    }

    @Test
    fun `patch块后面还有顶层键时插在块内`() {
        val src = "patch:\n  a: 1\nother:\n  b: 2\n"
        val out = RimeConfigHelper.applyUnifiedUserDictPatch(src, "rime_ice")!!
        val lines = out.trimEnd().lines()
        assertEquals("patch:", lines[0])
        assertEquals("  a: 1", lines[1])
        assertEquals(key, lines[2])
        assertEquals("other:", lines[3])
    }

    @Test
    fun `用户已配置user_dict时不覆盖`() {
        assertNull(
            RimeConfigHelper.applyUnifiedUserDictPatch(
                "patch:\n  \"translator/user_dict\": my_dict\n",
                "rime_ice",
            )
        )
    }

    @Test
    fun `只出现一个patch块`() {
        val out = RimeConfigHelper.applyUnifiedUserDictPatch("patch:\n  a: 1\n", "rime_ice")!!
        assertEquals(1, Regex("(?m)^patch:").findAll(out).count())
        assertTrue(out.contains(key))
    }
}
