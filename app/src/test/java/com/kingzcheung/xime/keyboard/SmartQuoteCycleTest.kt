package com.kingzcheung.xime.keyboard

import org.junit.Assert.assertEquals
import org.junit.Test

class SmartQuoteCycleTest {

    @Test
    fun `左右引号交替`() {
        val cycle = SmartQuoteCycle()
        assertEquals("“", cycle.next())
        assertEquals("”", cycle.next())
        assertEquals("“", cycle.next())
    }

    @Test
    fun `reset 后回到左引号`() {
        val cycle = SmartQuoteCycle()
        cycle.next()
        cycle.next()
        cycle.reset()
        assertEquals("“", cycle.next())
    }
}
