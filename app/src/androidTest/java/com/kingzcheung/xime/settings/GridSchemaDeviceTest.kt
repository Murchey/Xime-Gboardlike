package com.kingzcheung.xime.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kingzcheung.xime.rime.RimeConfigHelper
import com.kingzcheung.xime.rime.RimeEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 真机验证 14/17/18 键的组码解码：切到对应方案后，按组码输入应得到正确候选。
 *
 * 「我」= wo：14 键里 w 属于 QW 键（组码 q）、o 属于 OP 键（组码 o），所以按 q、o。
 */
@RunWith(AndroidJUnit4::class)
class GridSchemaDeviceTest {

    companion object {
        private lateinit var context: Context
        private val engine get() = RimeEngine.getInstance()

        @BeforeClass
        @JvmStatic
        fun setUp() {
            context = ApplicationProvider.getApplicationContext()
            runBlocking {
                val (user, shared) = RimeConfigHelper.initializeRimeDataAsync(context)
                engine.initialize(user, shared)
            }
            engine.deploy()
            // 应用启动可能还在后台全量部署：阻塞等到结束（内部 synchronized、幂等）
            RimeConfigHelper.ensureDeployment(context)
        }


    }

    /** 同一进程连续切方案时 librime 只对第一次生效，所以每个方案单独跑一次（一个 -e class ...#method）。 */
    private fun switchOnce(schemaId: String): Boolean {
        repeat(60) {
            if (engine.switchSchema(schemaId)) return true
            Thread.sleep(1000)
        }
        return false
    }

    private fun type(text: String): Array<String> {
        engine.clearComposition()
        for (c in text) engine.processKey(c.code, 0)
        return engine.getCandidates()
    }

    @Test
    fun fourteenKeyAbcdeDecodesWo() {
        assertTrue(switchOnce("pinyin_14jian_abcde"))
        // ABCDE 14 键：w 属 WX 组（码 w）、o 属 NO 组（码 n）
        val cands = type("wn")
        println("14ABC wn -> " + cands.take(8).joinToString("|"))
        assertTrue(cands.any { it.contains("我") })
    }

    @Test
    fun seventeenKeyAbcdeDecodesWo() {
        assertTrue(switchOnce("pinyin_17jian_abcde"))
        // ABCDE 17 键：w 属 WX 组（码 w）、o 属 NO 组（码 n）
        val cands = type("wn")
        println("17ABC wn -> " + cands.take(8).joinToString("|"))
        assertTrue(cands.any { it.contains("我") })
    }

    @Test
    fun eighteenKeyAbcdeDecodesWo() {
        assertTrue(switchOnce("pinyin_18jian_abcde"))
        // ABCDE 18 键：w 属 VW 组（码 v）、o 属 OP 组（码 o）
        val cands = type("vo")
        println("18ABC vo -> " + cands.take(8).joinToString("|"))
        assertTrue(cands.any { it.contains("我") })
    }

    @Test
    fun fourteenKeyDecodesWo() {
        assertTrue("切不到 pinyin_14jian，当前=" + engine.getCurrentSchema(), engine.switchSchema("pinyin_14jian"))
        val cands = type("qo")
        println("14 键 qo -> " + cands.take(8).joinToString("|"))
        assertTrue("「我」应出现在候选里：" + cands.take(8).joinToString("|"), cands.any { it.contains("我") })
    }

    @Test
    fun seventeenKeyDecodesWo() {
        assertTrue(engine.switchSchema("pinyin_17jian"))
        // 17 键：w 属 WE 组（码 w）、o 属 OP 组（码 o）
        val cands = type("wo")
        println("17 键 wo -> " + cands.take(8).joinToString("|"))
        assertTrue(cands.any { it.contains("我") })
    }

    @Test
    fun eighteenKeyDecodesWo() {
        assertTrue(engine.switchSchema("pinyin_18jian"))
        // 18 键：w 属 WE 组（码 w）、o 属 IO 组（码 i）
        val cands = type("wi")
        println("18 键 wi -> " + cands.take(8).joinToString("|"))
        assertTrue(cands.any { it.contains("我") })
    }
}