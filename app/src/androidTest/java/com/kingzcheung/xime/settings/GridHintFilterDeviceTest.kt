package com.kingzcheung.xime.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.rime.RimeEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** 组码 → 拼音提示：数据模块经 require 加载。 */
@RunWith(AndroidJUnit4::class)
class GridHintFilterDeviceTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun setupAndSwitch(schemaId: String, layout: String, order: String): RimeEngine {
        GridSchemaInstaller.install(context, "rime_ice", layout, order)
        val engine = RimeEngine.getInstance()
        for (i in 0 until 30) {
            if (engine.switchSchema(schemaId)) return engine
            Thread.sleep(2000)
        }
        error("切换失败: $schemaId")
    }

    private fun typeAndWait(engine: RimeEngine, keys: String): Array<com.kingzcheung.xime.rime.RimeCandidate> {
        var cands: Array<com.kingzcheung.xime.rime.RimeCandidate> = emptyArray()
        for (i in 0 until 20) {
            engine.clearComposition()
            for (c in keys) engine.processKey(c.code, 0)
            cands = engine.getCandidatesWithComments()
            if (cands.isNotEmpty()) break
            Thread.sleep(1000)
        }
        return cands
    }

    @Test
    fun pinyinHintReplacesGroupCode() = runBlocking {
        val engine = setupAndSwitch("rime_ice_14jian", "14", "qwerty")
        assertTrue(File(SchemaManager.getRimeDir(context), "lua/grid_pinyin.lua").isFile)
        val cands = typeAndWait(engine, "qo")
        println("14 键 qo 候选 -> " + cands.take(6).joinToString("|") { it.text + ":" + it.comment })
        assertTrue("应有候选", cands.isNotEmpty())
        assertEquals("我", cands[0].text)
        assertEquals("wo", cands[0].comment)
    }
}