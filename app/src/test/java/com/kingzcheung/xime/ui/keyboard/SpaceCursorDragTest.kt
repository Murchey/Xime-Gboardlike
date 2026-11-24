package com.kingzcheung.xime.ui.keyboard

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 空格键横划移动光标：步数计算逻辑的纯函数单测。
 *
 * 核心公式：每隔 stepPx 像素触发一步，积累不足一步的余量继续积累。
 * 测试不依赖 Compose 运行时，可在 JVM 上执行。
 */
class SpaceCursorDragTest {

    /** 模拟横划积累像素并计算步数（与 SpaceKey pointerInput 里的逻辑一一对应）。 */
    private fun simulateDrag(
        dxList: List<Float>,
        stepDp: Float = 20f,
        density: Float = 2.75f,  // 小米 21091116AC 屏幕密度
    ): List<Int> {
        val stepPx = stepDp * density
        var accumPx = 0f
        val results = mutableListOf<Int>()
        for (dx in dxList) {
            accumPx += dx
            val steps = (accumPx / stepPx).toInt()
            if (steps != 0) {
                results.add(steps)
                accumPx -= steps * stepPx
            }
        }
        return results
    }

    @Test
    fun singleStepLeftTriggersNegativeOne() {
        // 20dp * 2.75 = 55px; 拖动 -60px 应触发 steps=-1
        val steps = simulateDrag(listOf(-60f))
        assertEquals(listOf(-1), steps)
    }

    @Test
    fun singleStepRightTriggersPositiveOne() {
        val steps = simulateDrag(listOf(60f))
        assertEquals(listOf(1), steps)
    }

    @Test
    fun multipleStepsAccumulateCorrectly() {
        // 3次 -60px = -180px, stepPx=55 → steps=-3 (round toward zero: -180/55=-3.27→-3)
        val steps = simulateDrag(listOf(-60f, -60f, -60f))
        assertEquals(-3, steps.sum())
    }

    @Test
    fun smallDragsAccumulateUntilStep() {
        // 20px 不足一步 (stepPx=55)，累积到 60px 才触发
        val steps = simulateDrag(listOf(20f, 20f, 20f))
        assertEquals(listOf(1), steps)  // 60/55=1 步
    }

    @Test
    fun remainderCarriesOver() {
        // 60px → 1步，剩余 5px；再 55px → 1步（60/55=1 余5, 5+55=60 → 1步）
        val steps = simulateDrag(listOf(60f, 55f))
        assertEquals(2, steps.sum())
    }

    @Test
    fun noStepForSubThresholdDrag() {
        val steps = simulateDrag(listOf(10f, 10f, 10f))
        assertEquals(emptyList<Int>(), steps)
    }

    @Test
    fun exactlyOneStepAtBoundary() {
        // 恰好 55px = 1 步
        val steps = simulateDrag(listOf(55f))
        assertEquals(listOf(1), steps)
    }

    @Test
    fun twoStepsAtTwiceThreshold() {
        val steps = simulateDrag(listOf(110f))
        assertEquals(listOf(2), steps)
    }

    @Test
    fun directionChangeMidSwipe() {
        // 右滑积累 60px (1步) 然后左滑 -60px (-1步)
        val steps = simulateDrag(listOf(60f, -60f))
        assertEquals(0, steps.sum())
    }
}
