package com.kingzcheung.xime.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.rime.RimeConfigHelper
import com.kingzcheung.xime.rime.RimeEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * 运行时派生合并键方案的整条链路：生成文件 → 登记键面绑定 → 部署 → 切方案 → 按组码出候选。
 *
 * 用双拼做样本（自然码「中」= vs，14 键组码 vs → c a），能同时验证"双拼规则还在"和"组码规则叠上去了"。
 */
@RunWith(AndroidJUnit4::class)
class GridSchemaInstallerDeviceTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    /** ABCDE 排列的派生方案：组键按字母序重排，组码也跟着变。 */
    @Test
    fun derivedDoublePinyinFourteenKeyAbcdeDecodesZhong() = runBlocking {
        RimeConfigHelper.initializeRimeDataAsync(context)
        val target = GridSchemaInstaller.installAndEnable(context, "double_pinyin", "14", "abcde")
        assertNotNull("派生失败", target)
        assertEquals("double_pinyin_14jian_abcde", target)

        val file = File(SchemaManager.getRimeDir(context), "double_pinyin_14jian_abcde.schema.yaml")
        assertTrue("文件应已写盘", file.isFile)
        assertTrue("alphabet 应为 ABCDE 组码", file.readText().contains("  alphabet: acegijlnprsuwy"))

        KeysConfigHelper.loadConfig(context)
        assertEquals("qwerty_14_abcde", KeysConfigHelper.mergedSectionForSchema("double_pinyin_14jian_abcde"))

        RimeConfigHelper.ensureDeployment(context)
        val engine = RimeEngine.getInstance()
        var switched = false
        for (i in 0 until 20) {
            if (engine.switchSchema("double_pinyin_14jian_abcde")) { switched = true; break }
            Thread.sleep(2000)
        }
        assertTrue("切换失败（部署未完成？）", switched)
        engine.clearComposition()
        // 自然码「中」= vs；ABCDE 14 键：v 属 UV 组（码 u）、s 属 ST 组（码 s）
        for (c in "us") engine.processKey(c.code, 0)
        val cands = engine.getCandidates()
        println("派生双拼14ABC us -> " + cands.take(8).joinToString("|"))
        assertTrue("「中」应在候选里", cands.any { it.contains("中") })
    }

    @Test
    fun derivedDoublePinyinFourteenKeyDecodesZhong() = runBlocking {
        RimeConfigHelper.initializeRimeDataAsync(context)
        val target = GridSchemaInstaller.installAndEnable(context, "double_pinyin", "14", "qwerty")
        assertNotNull("派生失败", target)
        assertEquals("double_pinyin_14jian", target)

        val file = File(SchemaManager.getRimeDir(context), "double_pinyin_14jian.schema.yaml")
        assertTrue("文件应已写盘", file.isFile)
        val text = file.readText()
        assertTrue("schema_id 应为派生 id", text.contains("  schema_id: double_pinyin_14jian"))
        assertTrue("prism 应为派生 id", text.contains("  prism: double_pinyin_14jian"))

        KeysConfigHelper.loadConfig(context)
        assertEquals("qwerty_14", KeysConfigHelper.mergedSectionForSchema("double_pinyin_14jian"))

        // 部署与引擎初始化交给 Application/服务完成：测试里再 initialize 会重复初始化而卡住。
        // 只等 prism 编译完（切方案在部署期间会返回 false）。
        val engine = RimeEngine.getInstance()
        // prism 已在应用启动的部署里编好（测试只做切换 + 输入）；还没编好就等一会儿
        var switched = false
        for (i in 0 until 20) {
            if (engine.switchSchema("double_pinyin_14jian")) {
                switched = true
                break
            }
            Thread.sleep(2000)
        }
        assertTrue("切换失败（部署未完成？）", switched)
        // 刚部署完的方案可能还在编 prism，候选会短暂为空：轮询到出词为止
        var cands: Array<String> = emptyArray()
        for (i in 0 until 30) {
            engine.clearComposition()
            for (c in "ca") engine.processKey(c.code, 0)
            cands = engine.getCandidates()
            if (cands.isNotEmpty()) break
            Thread.sleep(1000)
        }
        println("派生双拼14 ca -> " + cands.take(8).joinToString("|"))
        assertTrue("「中」应在候选里：" + cands.take(8).joinToString("|"), cands.any { it.contains("中") })
    }
}