package com.kingzcheung.xime.rime

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 简繁转换依赖 OpenCC 的 s2t 配置与词典：必须随 APK assets 分发。 */
class RimeOpenccAssetsTest {

    private val dir = File("src/main/assets/rime/opencc")

    @Test
    fun `s2t 配置与词典存在且指向 text 词典`() {
        val config = File(dir, "s2t.json")
        assertTrue("缺少 opencc/s2t.json", config.exists())
        val text = config.readText()
        assertTrue("s2t.json 未引用 STPhrases.txt", text.contains("STPhrases.txt"))
        assertTrue("s2t.json 未引用 STCharacters.txt", text.contains("STCharacters.txt"))

        val phrases = File(dir, "STPhrases.txt")
        val characters = File(dir, "STCharacters.txt")
        assertTrue("缺少 STPhrases.txt", phrases.exists())
        assertTrue("缺少 STCharacters.txt", characters.exists())
        assertTrue("STCharacters 缺少 汉→漢", characters.readText().contains("汉\t漢"))
        assertTrue("STPhrases 缺少短语映射", phrases.readText().contains("中国共产党\t中國共產黨"))
    }
}
