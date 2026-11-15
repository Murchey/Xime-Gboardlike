package com.kingzcheung.xime.service

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * 真机回归：数字结尾不触发推理，文字结尾正常出联想。
 *
 * 走的是生产入口 [PredictionManager.getPrediction]——它会先做上下文判定，
 * 再决定是否初始化引擎、调用 AssociationManager。数字那条路径要求**立刻**回调空表，
 * 文字那条路径则要等真正的推理结果。
 */
@RunWith(AndroidJUnit4::class)
class PredictionContextDeviceTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private fun predictOnce(text: String, timeoutSec: Long): List<String> {
        val latch = CountDownLatch(1)
        val result = AtomicReference<List<String>>(emptyList())
        val manager = PredictionManager(context, scope) { list ->
            result.set(list)
            latch.countDown()
        }
        manager.getPrediction(text)
        latch.await(timeoutSec, TimeUnit.SECONDS)
        return result.get()
    }

    @Test
    fun digitEndingContextReturnsNothing() {
        val start = System.currentTimeMillis()
        val out = predictOnce("2024", 3)
        val cost = System.currentTimeMillis() - start
        println("数字结尾 2024 -> " + out + " (" + cost + "ms)")
        assertTrue("数字结尾必须直接返回空表", out.isEmpty())
        assertTrue("数字结尾不该走推理（应近乎立即回调），实际 " + cost + "ms", cost < 1000)
    }

    @Test
    fun letterEndingContextStillPredicts() {
        val out = predictOnce("今天天气", 10)
        println("文字结尾 今天天气 -> " + out)
        assertFalse("文字结尾应当有联想", out.isEmpty())
    }
}
