package com.kingzcheung.xime.ui.keyboard

import org.junit.Assert.assertEquals
import org.junit.Test

class GestureThresholdsTest {

    @Test
    fun `无几何信息时回退 50dp`() {
        assertEquals(50f, GestureThresholds.vertical(0f, 1f), 0.01f)
        assertEquals(50f, GestureThresholds.horizontal(0f, 1f), 0.01f)
    }

    @Test
    fun `手机键高 50dp 保持原阈值`() {
        assertEquals(50f, GestureThresholds.vertical(50f, 1f), 0.01f)
        assertEquals(100f, GestureThresholds.vertical(100f, 2f), 0.01f)
    }

    @Test
    fun `大键提高阈值但限制在 60dp`() {
        assertEquals(60f, GestureThresholds.vertical(80f, 1f), 0.01f)
        assertEquals(60f, GestureThresholds.horizontal(90f, 1f), 0.01f)
    }

    @Test
    fun `矮键降低阈值但不低于 30dp`() {
        assertEquals(40f, GestureThresholds.vertical(40f, 1f), 0.01f)
        assertEquals(30f, GestureThresholds.vertical(20f, 1f), 0.01f)
    }

    @Test
    fun `窄键水平阈值不低于 40dp`() {
        assertEquals(40f, GestureThresholds.horizontal(32f, 1f), 0.01f)
        assertEquals(55f, GestureThresholds.horizontal(55f, 1f), 0.01f)
    }
}
